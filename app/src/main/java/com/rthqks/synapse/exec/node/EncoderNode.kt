package com.rthqks.synapse.exec.node

import android.content.Context
import android.opengl.GLES30
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.SurfaceHolder
import com.rthqks.synapse.assets.AssetManager
import com.rthqks.synapse.codec.Encoder
import com.rthqks.synapse.exec.NodeExecutor
import com.rthqks.synapse.exec.link.*
import com.rthqks.synapse.gl.*
import com.rthqks.synapse.logic.Properties
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.whileSelect

class EncoderNode(
    private val context: Context,
    private val scope: CoroutineScope,
    private val assetManager: AssetManager,
    private val glesManager: GlesManager,
    private val properties: Properties
) : NodeExecutor(), SurfaceHolder.Callback {
    private var surface: Surface? = null
    private var startJob: Job? = null
    private var inputSize: Size = Size(0, 0)

    private val mesh = Quad()
    private val program = Program()
    private val encoder = Encoder(context, glesManager.backgroundHandler)
    private var recording = false
    private var windowSurface: WindowSurface? = null
    private var frameCount = 0

    override suspend fun create() {

    }

    override fun surfaceCreated(holder: SurfaceHolder?) {
        Log.d(TAG, "surfaceCreated: $holder")
    }

    override fun surfaceChanged(
        holder: SurfaceHolder?,
        format: Int,
        width: Int,
        height: Int
    ) {

    }

    override fun surfaceDestroyed(holder: SurfaceHolder?) {
    }

    override suspend fun initialize() {

        config(INPUT_VIDEO)?.let { config ->

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
            inputSize = config.size
            encoder.setVideo(inputSize, config.fps, 0)
        }

        val configAudio = config(INPUT_AUDIO)





        // encoder
        // setVideo(size, fps)
        // setAudio(audioFormat)
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
        }
    }

    override suspend fun start() = coroutineScope {
        frameCount = 0


        startJob = launch {
            val inputLinked = linked(INPUT_VIDEO)
            val lutLinked = linked(INPUT_AUDIO)
            if (!inputLinked && !lutLinked) {
                Log.d(TAG, "no connection")
                return@launch
            }
            var running = 0
            var copyMatrix = true
            val inputIn = channel(INPUT_VIDEO)
            val lutIn = channel(INPUT_AUDIO)
            if (inputLinked) running++
            if (lutLinked) running++

            whileSelect {
                inputIn?.onReceive {
//                    Log.d(TAG, "agent receive ${it.eos}")
                    if (copyMatrix) {
                        copyMatrix = false
                        val uniform = program.getUniform(Uniform.Type.Mat4, "texture_matrix0")
                        System.arraycopy(it.matrix, 0, uniform.data!!, 0, 16)
                        uniform.dirty = true
                    }
                    inputIn.send(it)
                    if (it.eos) running--
                    running > 0
                }
                lutIn?.onReceive {
//                    Log.d(TAG, "env receive ${it.eos}")
                    lutIn.send(it)
                    if (it.eos) running--
                    running > 0
                }
            }
        }
    }

    private fun executeGl(texture: Texture2d) {
        GLES30.glUseProgram(program.programId)
        GLES30.glViewport(0, 0, inputSize.width, inputSize.height)

        texture.bind(GLES30.GL_TEXTURE0)

        program.bindUniforms()

        mesh.execute()
    }

    override suspend fun stop() {
        startJob?.join()
    }

    override suspend fun release() {
        glesManager.glContext {
            //            windowSurface?.release()
            mesh.release()
            program.release()
        }
    }


    companion object {
        const val TAG = "EncoderNode"
        val INPUT_VIDEO = Connection.Key<VideoConfig, VideoEvent>("input_video")
        val INPUT_AUDIO = Connection.Key<AudioConfig, AudioEvent>("input_audio")
    }
}
