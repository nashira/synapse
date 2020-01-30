package com.rthqks.synapse.exec.node

import android.opengl.GLES32
import android.opengl.Matrix
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.core.view.updateLayoutParams
import com.rthqks.synapse.assets.AssetManager
import com.rthqks.synapse.exec.NodeExecutor
import com.rthqks.synapse.exec.link.*
import com.rthqks.synapse.gl.*
import com.rthqks.synapse.logic.CropToFit
import com.rthqks.synapse.logic.FixedWidth
import com.rthqks.synapse.logic.Properties
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.actor

class SurfaceViewNode(
    private val scope: CoroutineScope,
    private val assetManager: AssetManager,
    private val glesManager: GlesManager,
    private val properties: Properties
) : NodeExecutor(), SurfaceHolder.Callback {
    private var surface: Surface? = null
    private var running: Boolean = false
    private var playJob: Job? = null
    private var inputSize: Size = Size(0, 0)
    private var outputSize: Size = Size(0, 0)
    private var surfaceViewSize: Size = Size(0, 0)
    private var surfaceView: SurfaceView? = null
    private val commandChannel = scope.actor<suspend () -> Unit>(capacity = Channel.UNLIMITED) {
        for (action in channel) {
            action()
        }
    }

    private val mesh = Quad()
    private val program = Program()
    private var windowSurface: WindowSurface? = null

    private val cropCenter: Boolean = properties[CropToFit]
    private val fixedWidth: Boolean = properties.find(FixedWidth)?.value ?: false

    private var surfaceState = SurfaceState.Unavailable
    private var previousTexture: Texture? = null
    //    private var surfaceDeferred: CompletableDeferred<Unit>? = null
    val string = toString()
    private val TAG = "SVN ${string.substring(string.length - 8, string.length)}"

    override suspend fun create() {}

    suspend fun setSurfaceView(surfaceView: SurfaceView) = commandChannel.send {
        val valid = surfaceView.holder.surface?.isValid ?: false
        surfaceViewSize = Size(surfaceView.width, surfaceView.height)
        Log.d(TAG, "setSurfaceView $valid $surfaceViewSize")

//        surfaceView.setTag(R.id.surface_view, this)
        surfaceState = SurfaceState.Waiting
//        surfaceDeferred = CompletableDeferred()
        this.surfaceView?.holder?.removeCallback(this)
        this.surfaceView = surfaceView
        surfaceView.holder.addCallback(this)

        config(INPUT)?.let {
            inputSize = it.size
            val rotation = it.rotation
            updateSurfaceViewConfig(inputSize, rotation)
        }
    }

    override fun surfaceChanged(
        holder: SurfaceHolder?,
        format: Int,
        width: Int,
        height: Int
    ) {
        runBlocking {
            commandChannel.send {
                Log.d(TAG, "surfaceChanged: $holder $format $width $height")
                outputSize = Size(width, height)
                setSurface(holder!!.surface)
            }
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder?) {
        Log.d(TAG, "surfaceDestroyed: $holder")
        runBlocking {
            commandChannel.send {
                setSurface(null)
            }
        }
    }

    private fun destroySurface() {

    }

    override fun surfaceCreated(holder: SurfaceHolder?) {
        Log.d(TAG, "surfaceCreated: $holder")
    }

    override suspend fun initialize() {
        val config = config(INPUT) ?: return
        if (true || !config.offersSurface) {
            val grayscale = config.format == GLES32.GL_RED

            val vertexSource = assetManager.readTextAsset("shader/vertex_texture.vert")
            val fragmentSource = assetManager.readTextAsset("shader/copy.frag").let {
                if (config.isOes) it.replace("//{EXT}", "#define EXT") else it
            }.let {
                if (grayscale) it.replace("//{RED}", "#define RED") else it
            }
            glesManager.glContext {
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

    private suspend fun updateWindowSurface() {
        Log.d(TAG, "updating input surface")
        glesManager.glContext {
            windowSurface?.release()
            windowSurface = null
            surface?.also { surface ->
                if (surface.isValid) {
                    Log.d(TAG, "surf creating new input surface")
//                    surfaceView?.tag?.let {
//                        (it as? SurfaceViewNode)?.setSurface(null)
//                    }
                    windowSurface = it.createWindowSurface(surface)
//                    surfaceView?.tag = this@SurfaceViewNode
                }
            }

            if (windowSurface == null) {
                surfaceState = SurfaceState.Unavailable
            } else {
                surfaceState = SurfaceState.Available
//                surfaceDeferred?.complete(Unit)
            }
            Log.d(TAG, "surfaceState $surfaceState")
        }
    }

    private suspend fun startTexture() = coroutineScope {
        playJob = launch {
            val input = channel(INPUT) ?: return@launch

            var copyMatrix = true
            while (isActive) {
                val inEvent = input.receive()
                previousTexture = inEvent.texture

//                Log.d(TAG, "loop surfaceState $surfaceState")

                if (copyMatrix) {
                    Log.d(TAG, "copy matrix $outputSize $windowSurface")
                    copyMatrix = false
                    val uniform = program.getUniform(Uniform.Type.Mat4, "texture_matrix0")
                    val matrix = uniform.data!!
                    System.arraycopy(inEvent.matrix, 0, matrix, 0, 16)
                    // center crop
                    if (cropCenter) {
                        val inAspect = inputSize.width / inputSize.height.toFloat()
                        val outAspect = outputSize.width / outputSize.height.toFloat()

                        val scaleX = if (inAspect >= outAspect) outAspect / inAspect else 1f
                        val scaleY = if (inAspect <= outAspect) inAspect / outAspect else 1f

                        Matrix.translateM(matrix, 0, 0.5f, 0.5f, 0f)
                        Matrix.scaleM(matrix, 0, scaleX, scaleY, 1f)
                        Matrix.translateM(matrix, 0, -0.5f, -0.5f, 0f)
                    }

                    uniform.dirty = true
                }

                if (windowSurface != null) {
//                    Log.d(TAG, "loop pre render")
                    glesManager.glContext {
                        //                        Log.d(TAG, "loop render")
                        windowSurface?.makeCurrent()
                        GLES32.glBindFramebuffer(GLES32.GL_FRAMEBUFFER, 0)
                        executeGl(inEvent.texture)
                        windowSurface?.swapBuffers()
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
        glesManager.glContext {
            windowSurface?.release()
            surfaceView?.holder?.removeCallback(this@SurfaceViewNode)
            mesh.release()
            program.release()
        }
        commandChannel.close()
        debug.remove(this)
    }

    override suspend fun <C : Config, E : Event> setConfig(key: Connection.Key<C, E>, config: C) {
        super.setConfig(key, config)
        config(INPUT)?.let {
            //            it.acceptsSurface = true
            inputSize = it.size
            val rotation = it.rotation
            commandChannel.send {
                updateSurfaceViewConfig(inputSize, rotation)
            }
        }
    }

    private suspend fun updateSurfaceViewConfig(
        size: Size,
        rotation: Int
    ) {
        Log.d(TAG, "updateSurfaceViewConfig")
        val surfaceView = surfaceView ?: return
        withContext(Dispatchers.Main) {

            val inSize = inputSize
            val outSize = surfaceViewSize

            outputSize = if (cropCenter) {
                outSize
            } else {
                val inAspect = inSize.width / inSize.height.toFloat()
                val outAspect = outSize.width / outSize.height.toFloat()
                val scaleX = if (inAspect < outAspect) inAspect / outAspect else 1f
                val scaleY = if (inAspect > outAspect) outAspect / inAspect else 1f
                Size((outSize.width * scaleX).toInt(), (outSize.height * scaleY).toInt())
            }

            Log.d(TAG, "updateSurfaceViewConfig $inSize $outSize $outputSize")
//            outputSize = Size(surfaceView.measuredWidth, surfaceView.measuredHeight)

            surfaceView.updateLayoutParams {
                width = outputSize.width
                height = outputSize.height
            }
//                if (rotation == 90 || rotation == 270) Size(size.height, size.width) else size
//            ConstraintSet().also {
//                val constraintLayout = surfaceView.parent as ConstraintLayout
//                it.clone(constraintLayout)
//                it.setDimensionRatio(R.id.surface_view, "${outputSize.width}:${outputSize.height}")
//                it.applyTo(constraintLayout)
//            }
        }
        setSurface(surfaceView.holder.surface)
    }

    private suspend fun setSurface(surface: Surface?) {
        Log.d(TAG, "setSurface prev ${this.surface} new $surface ${surface?.isValid}")
        val hasChanged = this.surface != surface
        this.surface = surface
//        updateWindowSurface()
        if (hasChanged) {
            updateWindowSurface()
        }


        if (surface?.isValid == true) {
            config(INPUT)?.surface?.set(surface)
            debug.add(this)
        } else {
            debug.remove(this)
        }

//        previousTexture?.let { texture ->
//            windowSurface ?: return@let
//            Log.d(TAG, "surf render")
//            glesManager.glContext {
//                windowSurface?.makeCurrent()
//                GLES32.glBindFramebuffer(GLES32.GL_FRAMEBUFFER, 0)
//                executeGl(texture)
//                windowSurface?.swapBuffers()
//            }
//        }
        Log.d(TAG, "active surfaces ${debug.size}")
    }

    companion object {
        //        const val TAG = "SurfaceViewNode"
        val INPUT = Connection.Key<VideoConfig, VideoEvent>("video_1")
        val debug = mutableSetOf<SurfaceViewNode>()
    }
}

private enum class SurfaceState {
    Waiting,
    Available,
    Unavailable
}