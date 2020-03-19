package com.rthqks.synapse.exec2.node

import android.opengl.GLES30
import android.util.Log
import android.util.Size
import android.view.Surface
import com.rthqks.synapse.codec.Encoder
import com.rthqks.synapse.exec.ExecutionContext
import com.rthqks.synapse.exec2.Connection
import com.rthqks.synapse.exec2.NodeExecutor
import com.rthqks.synapse.exec2.link.AudioData
import com.rthqks.synapse.gl.*
import com.rthqks.synapse.logic.FrameRate
import com.rthqks.synapse.logic.Properties
import com.rthqks.synapse.logic.Recording
import com.rthqks.synapse.logic.Rotation
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

class EncoderNode(
    context: ExecutionContext,
    private val properties: Properties
) : NodeExecutor(context) {
    private val assetManager = context.assetManager
    private val glesManager = context.glesManager
    private var videoJob: Job? = null
    private var audioJob: Job? = null
    private var inputSize: Size = Size(0, 0)
    private val mesh = Quad()

    private val program = Program()
    private val encoder = Encoder(context)
    private var startTimeVideo = -1L
    private var startTimeAudio = -1L
    private var surface: Surface? = null
    private var windowSurface: WindowSurface? = null

    private val frameRate: Int get() = properties[FrameRate]
    private var recording = AtomicInteger()

    private var needAudioConfig = true
    private var needVideoConfig = true
    private var previousFormat: Texture2d? = null

    override suspend fun onSetup() {
        Log.d(TAG, "onSetup")
    }

    override suspend fun <T> onConnect(key: Connection.Key<T>, producer: Boolean) {
        when (key) {
            INPUT_AUDIO -> {
                audioJob = scope.launch {
                    startAudio()
                }
            }
            INPUT_VIDEO -> {
                videoJob = scope.launch {
                    startVideo()
                }
            }
        }
    }

    override suspend fun <T> onDisconnect(key: Connection.Key<T>, producer: Boolean) {
        when (key) {
            INPUT_AUDIO -> {
                audioJob?.join()
            }
            INPUT_VIDEO -> {
                videoJob?.join()
            }
        }
    }

    private suspend fun checkVideo(texture: Texture2d) {
        if (previousFormat?.format != texture.format
            || previousFormat?.oes != texture.oes) {
            needVideoConfig = true
        }

        val sizeChanged = inputSize.width != texture.width
                || inputSize.height != texture.height

        if (needVideoConfig) {
            needVideoConfig = false
            previousFormat = texture
            val grayscale = texture.format == GLES30.GL_RED

            val vertexSource = assetManager.readTextAsset("shader/vertex_texture.vert")
            val fragmentSource = assetManager.readTextAsset("shader/copy.frag").let {
                if (texture.oes) it.replace("//{EXT}", "#define EXT") else it
            }.let {
                if (grayscale) it.replace("//{RED}", "#define RED") else it
            }
            glesManager.glContext {
                mesh.initialize()
                program.apply {
                    release()
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

        if (sizeChanged) {
            val size = Size(texture.width, texture.height)

            surface = encoder.setVideo(size, frameRate)
            updateWindowSurface()
            inputSize = size
        }
    }

    private fun checkAudio(audioData: AudioData) {
        if (needAudioConfig) {
            needAudioConfig = false
            encoder.setAudio(audioData.audioFormat)
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
        startTimeVideo = -1L
        startTimeAudio = -1L
        Log.d(TAG, "startRecording ${properties[Rotation]}")
        encoder.startEncoding(properties[Rotation])
        val check = recording.getAndSet(RECORDING)
        if (check != START_RECORDING) {
            Log.w(TAG, "start recording unexpected state $check")
        }
    }

    private suspend fun stopRecording() {
        encoder.stopEncoding()
        val check = recording.getAndSet(NOT_RECORDING)
        if (check != STOP_RECORDING) {
            Log.w(TAG, "stop recording unexpected state $check")
        }
    }

    private suspend fun updateRecording() {
        if (linked(INPUT_AUDIO) && needAudioConfig
            || linked(INPUT_VIDEO) && needVideoConfig) {
            Log.d(TAG, "not recording, waiting for init")
            return
        }
        val r = properties[Recording]
        if (r && recording.compareAndSet(NOT_RECORDING, START_RECORDING)) {
            startRecording()
        } else if (!r && recording.compareAndSet(RECORDING, STOP_RECORDING)) {
            stopRecording()
        }
    }

    private suspend fun startVideo() {
        val videoIn = channel(INPUT_VIDEO) ?: error("missing video input")
        for (msg in videoIn) {
            val data = msg.data
            checkVideo(data)
            val uniform = program.getUniform(Uniform.Type.Mat4, "texture_matrix0")
            System.arraycopy(data.matrix, 0, uniform.data!!, 0, 16)
            uniform.dirty = true
            updateRecording()

            if (recording.get() == RECORDING) {
                if (startTimeVideo == -1L) {
                    startTimeVideo = msg.timestamp
                }
                executeGl(data, msg.timestamp - startTimeVideo)
            }
            msg.release()
        }
    }

    private suspend fun startAudio() {
        val audioIn = channel(INPUT_AUDIO) ?: error("missing audio input")
        for (msg in audioIn) {
            val data = msg.data

            checkAudio(data)

            updateRecording()

            if (recording.get() == RECORDING) {
                if (startTimeAudio == -1L) {
                    startTimeAudio = msg.timestamp
                }
                encoder.writeAudio(data.buffer, msg.timestamp - startTimeAudio)
            }
            msg.release()
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

    private suspend fun onStop() {
        Log.d(TAG, "stopping")

        audioJob?.join()
        videoJob?.join()

        Log.d(TAG, "got eos, recording = $recording")
        if (recording.compareAndSet(RECORDING, STOP_RECORDING)) {
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
        const val NOT_RECORDING = 0
        const val START_RECORDING = 1
        const val STOP_RECORDING = 2
        const val RECORDING = 3
        val INPUT_VIDEO = Connection.Key<Texture2d>("input_video")
        val INPUT_AUDIO = Connection.Key<AudioData>("input_audio")
    }
}
