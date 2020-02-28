package com.rthqks.synapse.exec.node

import android.opengl.GLES30
import android.util.Log
import android.util.Size
import android.view.Surface
import com.rthqks.synapse.codec.Encoder
import com.rthqks.synapse.exec.ExecutionContext
import com.rthqks.synapse.exec.NodeExecutor
import com.rthqks.synapse.exec.link.*
import com.rthqks.synapse.gl.*
import com.rthqks.synapse.logic.Properties
import com.rthqks.synapse.logic.Recording
import com.rthqks.synapse.logic.Rotation
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.whileSelect

class EncoderNode(
    context: ExecutionContext,
    private val properties: Properties
) : NodeExecutor(context) {
    private val assetManager = context.assetManager
    private val glesManager = context.glesManager
    private val videoStorage = context.videoStorage
    private var videoJob: Job? = null
    private var audioJob: Job? = null
    private var startJob: Job? = null
    private var inputSize: Size = Size(0, 0)
    private val mesh = Quad()

    private val program = Program()
    private val encoder = Encoder(videoStorage)
    private var startTimeVideo = -1L
    private var startTimeAudio = -1L
    private var surface: Surface? = null
    private var windowSurface: WindowSurface? = null

    private var recording = false
    private var frameCount = 0

    override suspend fun onCreate() {
    }

    override suspend fun onInitialize() {

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
            surface = encoder.setVideo(inputSize, config.fps, 0)
            updateWindowSurface()
        }

        config(INPUT_AUDIO)?.let { config ->
            encoder.setAudio(config.audioFormat)
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
                    windowSurface = it.createWindowSurface(surface)
                }
            }
        }
    }

    private fun startRecording() {
        encoder.startEncoding(properties[Rotation])
        recording = true
        startTimeVideo = -1L
        startTimeAudio = -1L
    }

    private suspend fun stopRecording() {
        recording = false
        encoder.stopEncoding()
    }

    override suspend fun onStart() {
        frameCount = 0

        startJob = scope.launch {
            val inputLinked = linked(INPUT_VIDEO)
            val lutLinked = linked(INPUT_AUDIO)
            if (!inputLinked && !lutLinked) {
                Log.d(TAG, "no connection")
                return@launch
            }
//            val running = AtomicInteger()
            var running = 0
            var copyMatrix = true
            val videoIn = channel(INPUT_VIDEO)
            val audioIn = channel(INPUT_AUDIO)
            if (inputLinked) running++
            if (lutLinked) running++

            Log.d(TAG, "video linked $inputLinked audio $lutLinked running=${running}")

//            if (videoIn != null)
//                videoJob = launch {
//                    do {
//                        val it = videoIn.receive()
//                        if (copyMatrix) {
//                            copyMatrix = false
//                            val uniform = program.getUniform(Uniform.Type.Mat4, "texture_matrix0")
//                            System.arraycopy(it.matrix, 0, uniform.data!!, 0, 16)
//                            uniform.dirty = true
//                        }
//                        updateRecording()
//
//                        if (recording && !it.eos) {
//                            if (startTimeVideo == -1L) {
//                                startTimeVideo = it.timestamp
//                            }
//                            executeGl(it.texture, it.timestamp - startTimeVideo)
//                        }
//                        videoIn.send(it)
//                        if (it.eos) {
//                            running.decrementAndGet()
//                            Log.d(TAG, "video eos")
//                        }
//                    } while (!it.eos)
//                }
//
//            if (audioIn != null)
//                audioJob = launch {
//                    do {
//                        val it = audioIn.receive()
//
//                        updateRecording()
//
//                        if (recording && !it.eos) {
//                            if (startTimeAudio == -1L) {
//                                startTimeAudio = it.timestamp
//                            }
//                            encoder.writeAudio(it.buffer, it.timestamp - startTimeAudio)
//                        }
//
//                        audioIn.send(it)
//                        if (it.eos) {
//                            running.decrementAndGet()
//                            Log.d(TAG, "audio eos")
//
//                        }
//                    } while (!it.eos)
//                }

            whileSelect {
                videoIn?.onReceive {
//                    Log.d(TAG, "agent receive ${it.eos}")
                    if (copyMatrix) {
                        copyMatrix = false
                        val uniform = program.getUniform(Uniform.Type.Mat4, "texture_matrix0")
                        System.arraycopy(it.matrix, 0, uniform.data!!, 0, 16)
                        uniform.dirty = true
                    }
                    updateRecording()

                    if (recording && !it.eos) {
                        if (startTimeVideo == -1L) {
                            startTimeVideo = it.timestamp
                        }
                        executeGl(it.texture, it.timestamp - startTimeVideo)
                    }
                    videoIn.send(it)
                    if (it.eos) {
                        running--
                        Log.d(TAG, "video eos")
                    }
                    running > 0
                }
                audioIn?.onReceive {
//                    Log.d(TAG, "env receive ${it.eos}")
                    updateRecording()

                    if (recording && !it.eos) {
                        if (startTimeAudio == -1L) {
                            startTimeAudio = it.timestamp
                        }
                        encoder.writeAudio(it.buffer, it.timestamp - startTimeAudio)
                    }

                    audioIn.send(it)
                    if (it.eos) {
                        running--
                        Log.d(TAG, "audio eos")

                    }
                    running > 0
                }
            }

//            videoJob?.join()
//            audioJob?.join()
        }
    }

    private suspend fun updateRecording() {
        val r = properties[Recording]
        if (r && !recording) {
            startRecording()
        } else if (!r && recording) {
            stopRecording()
        }
    }

    private suspend fun executeGl(texture: Texture2d, timestamp: Long) {
        glesManager.glContext {
            windowSurface?.makeCurrent()
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
            GLES30.glUseProgram(program.programId)
            GLES30.glViewport(0, 0, inputSize.width, inputSize.height)

            texture.bind(GLES30.GL_TEXTURE0)

            program.bindUniforms()

            mesh.execute()
            windowSurface?.setPresentationTime(timestamp)
            windowSurface?.swapBuffers()
        }
    }

    override suspend fun onStop() {
        Log.d(TAG, "stopping")
        startJob?.join()

        Log.d(TAG, "got eos, recording = $recording")
        if (recording) {
            stopRecording()
            //TODO: need to have encoder serialize it's own commands
            // this is here because a release is probably coming and we should wait
            delay(500)
        }
        Log.d(TAG, "stopped")
    }

    override suspend fun onRelease() {
        glesManager.glContext {
            windowSurface?.release()
            surface?.release()
            mesh.release()
            program.release()
            encoder.release()
        }
    }


    companion object {
        const val TAG = "EncoderNode"
        val INPUT_VIDEO = Connection.Key<VideoConfig, VideoEvent>("input_video")
        val INPUT_AUDIO = Connection.Key<AudioConfig, AudioEvent>("input_audio")
    }
}
