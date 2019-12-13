package xyz.rthqks.synapse.exec.node

import android.opengl.GLES32
import android.opengl.Matrix
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlinx.coroutines.*
import xyz.rthqks.synapse.assets.AssetManager
import xyz.rthqks.synapse.exec.NodeExecutor
import xyz.rthqks.synapse.exec.edge.*
import xyz.rthqks.synapse.gl.*

class SurfaceViewNode(
    private val assetManager: AssetManager,
    private val glesManager: GlesManager,
    private var surfaceView: SurfaceView
) : NodeExecutor(), SurfaceHolder.Callback {
    private var surface: Surface? = null
    private var running: Boolean = false
    private var playJob: Job? = null
    private var inputSize: Size = Size(0, 0)
    private var outputSize: Size = Size(0, 0)

    private val mesh = Quad()
    private val program = Program()
    private var windowSurface: WindowSurface? = null

    override suspend fun create() {
        Log.d(TAG, "adding callback ${surfaceView.holder.surface}")

        setSurfaceView(surfaceView)
    }

    suspend fun setSurfaceView(surfaceView: SurfaceView) {
        Log.d(TAG, "setSurfaceView $surfaceView")
        this.surfaceView.holder.removeCallback(this)
        this.surfaceView = surfaceView
        surfaceView.holder.addCallback(this)

        connection(INPUT)?.let {
            val size = it.config.size
            val rotation = it.config.rotation
            updateSurfaceViewConfig(size, rotation)
        } ?: run {
            setSurface(surfaceView.holder.surface)
        }
    }

    override fun surfaceChanged(
        holder: SurfaceHolder?,
        format: Int,
        width: Int,
        height: Int
    ) {
        Log.d(TAG, "surfaceChanged: $holder $format $width $height")
        outputSize = Size(width, height)
        runBlocking { setSurface(holder!!.surface) }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder?) {
        Log.d(TAG, "surfaceDestroyed: $holder")
        runBlocking { setSurface(null) }
    }

    override fun surfaceCreated(holder: SurfaceHolder?) {
        Log.d(TAG, "surfaceCreated: $holder")
    }

    override suspend fun initialize() {
        val config = config(INPUT) ?: return
        if (true || !config.offersSurface) {
            Log.d(TAG, "NEED TO READ FROM A TEXTURE!!")
            val grayscale = config.format == GLES32.GL_RED

            val vertexSource = assetManager.readTextAsset("vertex_texture.vert")
            val fragmentSource = assetManager.readTextAsset("copy.frag").let {
                if (config.isOes) it.replace("#{EXT}", "#define EXT") else it
            }.let {
                if (grayscale) it.replace("#{RED}", "#define RED") else it
            }
            glesManager.withGlContext {
                mesh.initialize()
                program.apply {
                    initialize(vertexSource, fragmentSource)
                    addUniform(
                        Uniform.Type.Mat4,
                        "vertex_matrix0",
                        FloatArray(16).also { Matrix.setIdentityM(it, 0) })
                    addUniform(
                        Uniform.Type.Mat4,
                        "texture_matrix0",
                        FloatArray(16).also { Matrix.setIdentityM(it, 0) })
                    addUniform(
                        Uniform.Type.Int,
                        "input_texture0",
                        0
                    )
                }
            }
        }
    }

    override suspend fun start() {
        when (config(INPUT)?.offersSurface) {
//            true -> startSurface()
            true,
            false -> startTexture()
            else -> Log.w(TAG, "no connection on start")
        }
    }

    private suspend fun startSurface() = coroutineScope {
        playJob = launch {
            val connection = channel(INPUT) ?: return@launch
            running = true
            while (running) {
                val surfaceEvent = connection.receive()
                if (surfaceEvent.eos) {
                    Log.d(TAG, "got EOS ${surfaceEvent.count}")
                    running = false
                }
                connection.send(surfaceEvent)
            }
        }
    }

    private suspend fun updateOutputSurface() {
        Log.d(TAG, "creating input surface")
        val surface = surface ?: return
        glesManager.withGlContext {
            windowSurface?.release()
            windowSurface = it.createWindowSurface(surface)
            windowSurface?.makeCurrent()
        }
    }

    private suspend fun startTexture() = coroutineScope {
        playJob = launch {
            val input = channel(INPUT) ?: return@launch
            updateOutputSurface()
            val windowSurface = windowSurface ?: return@launch

            var copyMatrix = true
            while (isActive) {
                val inEvent = input.receive()

                if (copyMatrix) {
                    copyMatrix = false
                    val uniform = program.getUniform(Uniform.Type.Mat4, "texture_matrix0")
                    val matrix = uniform.data!!
                    System.arraycopy(inEvent.matrix, 0, matrix, 0, 16)
                    // center crop
                    val inAspect = inputSize.width / inputSize.height.toFloat()
                    val outAspect = outputSize.width / outputSize.height.toFloat()

                    val scaleX = if (inAspect > outAspect) outAspect / inAspect else 1f
                    val scaleY = if (inAspect < outAspect) inAspect / outAspect else 1f

                    Matrix.translateM(matrix, 0, 0.5f, 0.5f, 0f)
                    Matrix.scaleM(matrix, 0, scaleX, scaleY, 1f)
                    Matrix.translateM(matrix, 0, -0.5f, -0.5f, 0f)

                    uniform.dirty = true
                }
                if (surface != null) {
                    glesManager.withGlContext {
                        windowSurface.makeCurrent()
                        GLES32.glBindFramebuffer(GLES32.GL_FRAMEBUFFER, 0)
                        executeGl(inEvent.texture)
                        windowSurface.swapBuffers()
                    }
                }

                input.send(inEvent)

                if (inEvent.eos) {
                    Log.d(TAG, "got EOS ${inEvent.count}")
                    playJob?.cancel()
                }
            }
        }
    }

    private fun executeGl(texture: Texture) {
        GLES32.glUseProgram(program.programId)
        GLES32.glViewport(0, 0, outputSize.width, outputSize.height)

        texture.bind(GLES32.GL_TEXTURE0)

        program.bindUniforms()

        mesh.execute()
    }

    override suspend fun stop() {
        playJob?.join()
    }

    override suspend fun release() {
        glesManager.withGlContext {
            windowSurface?.release()
            mesh.release()
            program.release()
        }
    }

    override suspend fun <C : Config, E : Event> setConfig(key: Connection.Key<C, E>, config: C) {
        super.setConfig(key, config)
        config(INPUT)?.let {
//            it.acceptsSurface = true
            inputSize = it.size
            val rotation = it.rotation
            updateSurfaceViewConfig(inputSize, rotation)
        }
    }

    private suspend fun updateSurfaceViewConfig(
        size: Size,
        rotation: Int
    ) {
        withContext(Dispatchers.Main) {
            outputSize = Size(surfaceView.measuredWidth, surfaceView.measuredHeight)
            val outSize = size
//                if (rotation == 90 || rotation == 270) Size(size.height, size.width) else size
//            surfaceView.holder.setFixedSize(outSize.width, outSize.height)
//            ConstraintSet().also {
//                val constraintLayout = surfaceView.parent as ConstraintLayout
//                it.clone(constraintLayout)
//                it.setDimensionRatio(R.id.surface_view, "${size.width}:${size.height}")
//                it.applyTo(constraintLayout)
//            }
        }
        setSurface(surface)
    }

    private suspend fun setSurface(surface: Surface?) {
        Log.d(TAG, "setSurface $surface")
        this.surface = surface
        config(INPUT)?.surface?.set(surface)
    }

    companion object {
        const val TAG = "SurfaceViewNode"
        val INPUT = Connection.Key<VideoConfig, VideoEvent>("video_1")
    }
}

/*
pass surface into execVM -> graph -> surfaceviewnode
listen to surfaceholder events

 */