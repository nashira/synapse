package com.rthqks.synapse.exec.node

import android.opengl.GLES30
import android.opengl.Matrix
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.core.view.updateLayoutParams
import com.rthqks.synapse.exec.ExecutionContext
import com.rthqks.synapse.exec.NodeExecutor
import com.rthqks.synapse.exec.link.*
import com.rthqks.synapse.gl.*
import com.rthqks.synapse.logic.CropToFit
import com.rthqks.synapse.logic.FixedWidth
import com.rthqks.synapse.logic.Properties
import kotlinx.coroutines.*

class SurfaceViewNode(
    context: ExecutionContext,
    private val properties: Properties
) : NodeExecutor(context),
    SurfaceHolder.Callback {
    private val assetManager = context.assetManager
    private val glesManager = context.glesManager
    private var outScaleY: Float = 1f
    private var outScaleX: Float = 1f
    private var surface: Surface? = null
    private var running: Boolean = false
    private var playJob: Job? = null
    private var inputSize: Size = Size(0, 0)
    private var outputSize: Size = Size(0, 0)
    private var surfaceViewSize: Size = Size(0, 0)
    private var surfaceView: SurfaceView? = null

    private val mesh = Quad()
    private val program = Program()
    private var windowSurface: WindowSurface? = null

    private val cropCenter: Boolean = properties[CropToFit]
    private val fixedWidth: Boolean = properties.getOrNull(FixedWidth)?.value ?: false

    private var surfaceState = SurfaceState.Unavailable
    private var previousTexture: Texture2d? = null
    //    private var surfaceDeferred: CompletableDeferred<Unit>? = null
    val string = toString()
    private val TAG = "SVN ${string.substring(string.length - 8, string.length)}"

    override suspend fun onCreate() {}

    suspend fun setSurfaceView(surfaceView: SurfaceView) = exec {
        val valid = surfaceView.holder.surface?.isValid ?: false
        if (surfaceViewSize.width == 0 || surfaceView != this.surfaceView) {
            surfaceViewSize = Size(surfaceView.width, surfaceView.height)
        }
        Log.d(TAG, "setSurfaceView $valid $surfaceViewSize")

//        surfaceView.setTag(R.id.surface_view, this)
        surfaceState = SurfaceState.Waiting
//        surfaceDeferred = CompletableDeferred()
        this.surfaceView?.holder?.removeCallback(this)
        this.surfaceView = surfaceView
        surfaceView.holder.addCallback(this)

        config(INPUT)?.let {
            inputSize = it.size
            updateSurfaceViewConfig()
        }
    }

    override fun surfaceChanged(
        holder: SurfaceHolder?,
        format: Int,
        width: Int,
        height: Int
    ) {
        scope.launch {
            exec {
                Log.d(TAG, "surfaceChanged: $holder $format $width $height")
                outputSize = Size(width, height)
                setSurface(holder!!.surface)
            }
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder?) {
        runBlocking {
            Log.d(TAG, "surfaceDestroyed: $holder")
            setSurface(null)
        }
    }

    private fun destroySurface() {

    }

    override fun surfaceCreated(holder: SurfaceHolder?) {
        Log.d(TAG, "surfaceCreated: $holder")
    }

    override suspend fun onInitialize() {
        val config = config(INPUT) ?: return
        if (true || !config.offersSurface) {
            val grayscale = config.format == GLES30.GL_RED

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
                        GlesManager.identityMat()
                    )
                    addUniform(
                        Uniform.Type.Mat4,
                        "texture_matrix0",
                        GlesManager.identityMat()
                    )
                    addUniform(
                        Uniform.Type.Int,
                        "input_texture0",
                        0
                    )
                }
            }
        }
    }

    override suspend fun onStart() {
        when (config(INPUT)?.offersSurface) {
//            true -> startSurface()
            true,
            false -> startTexture()
            else -> Log.w(TAG, "no connection on start")
        }
    }

    private suspend fun startSurface() {
        playJob = scope.launch {
            val connection = channel(INPUT) ?: return@launch
            running = true
            while (running) {
                val surfaceEvent = connection.receive()
                if (surfaceEvent.eos) {
                    Log.d(TAG, "got EOS ${surfaceEvent.count}")
                    running = false
                }
                surfaceEvent.release()
            }
        }
    }

    private suspend fun updateWindowSurface() {
        Log.d(TAG, "updating input surface")
        glesManager.glContext {
            windowSurface?.release()
            windowSurface = null
            val surface = surface
            if (surface?.isValid == true) {
                Log.d(TAG, "surf creating new input surface")
                windowSurface = it.createWindowSurface(surface)
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

    private suspend fun startTexture() {
        playJob = scope.launch {
            val input = channel(INPUT) ?: return@launch

            var copyMatrix = true
            while (isActive) {
                val inEvent = input.receive()
                previousTexture = inEvent.texture

//                Log.d(TAG, "loop surfaceState $surfaceState")

                if (windowSurface != null) {
                    if (copyMatrix) {
                        Log.d(TAG, "copy matrix $outputSize $windowSurface")
                        copyMatrix = false
                        val uniform = program.getUniform(Uniform.Type.Mat4, "texture_matrix0")
                        val matrix = uniform.data!!

                        System.arraycopy(inEvent.matrix, 0, matrix, 0, 16)
                        // center crop
                        if (cropCenter) {
                            Matrix.translateM(matrix, 0, 0.5f, 0.5f, 0f)
                            Matrix.scaleM(matrix, 0, outScaleX, outScaleY, 1f)
                            Matrix.translateM(matrix, 0, -0.5f, -0.5f, 0f)
                        }

                        uniform.dirty = true
                    }
//                    Log.d(TAG, "loop pre render")
                    glesManager.glContext {
                        //                        Log.d(TAG, "loop render")
                        windowSurface?.makeCurrent()
                        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
                        executeGl(inEvent.texture)
                        windowSurface?.swapBuffers()
                    }
                }

                inEvent.release()

                if (inEvent.eos) {
                    Log.d(TAG, "got EOS ${inEvent.count}")
                    playJob?.cancel()
                    previousTexture = null
                }
            }
        }
    }

    private fun executeGl(texture: Texture2d) {
        GLES30.glUseProgram(program.programId)
        GLES30.glViewport(0, 0, outputSize.width, outputSize.height)

        texture.bind(GLES30.GL_TEXTURE0)

        program.bindUniforms()

        mesh.execute()
    }

    override suspend fun onStop() {
        playJob?.join()
    }

    override suspend fun onRelease() {
        glesManager.glContext {
            surfaceView?.holder?.removeCallback(this@SurfaceViewNode)
            windowSurface?.release()
            mesh.release()
            program.release()
        }
        debug.remove(this)
    }

    override suspend fun <C : Config, E : Event> setConfig(key: Connection.Key<C, E>, config: C) {
        super.setConfig(key, config)
        config(INPUT)?.let {
            //            it.acceptsSurface = true
            inputSize = it.size
            exec {
                updateSurfaceViewConfig()
            }
        }
    }

    private suspend fun updateSurfaceViewConfig() {
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

            val inAspect = inputSize.width / inputSize.height.toFloat()
            val outAspect = outputSize.width / outputSize.height.toFloat()

            outScaleX = if (inAspect > outAspect) outAspect / inAspect else 1f
            outScaleY = if (inAspect < outAspect) inAspect / outAspect else 1f

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
//                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
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