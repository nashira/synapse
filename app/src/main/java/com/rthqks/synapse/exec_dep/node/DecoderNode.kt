package com.rthqks.synapse.exec_dep.node

import android.graphics.SurfaceTexture
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaFormat
import android.net.Uri
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.opengl.GLES30.GL_CLAMP_TO_EDGE
import android.opengl.GLES30.GL_LINEAR
import android.os.SystemClock
import android.util.Log
import android.util.Size
import android.view.Surface
import com.rthqks.synapse.codec.Decoder
import com.rthqks.synapse.exec.ExecutionContext
import com.rthqks.synapse.exec_dep.NodeExecutor
import com.rthqks.synapse.exec_dep.link.*
import com.rthqks.synapse.gl.Texture2d
import com.rthqks.synapse.logic.MediaUri
import com.rthqks.synapse.logic.Properties
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class DecoderNode(
    context: ExecutionContext,
    private val properties: Properties
) : NodeExecutor(context) {
    private val glesManager = context.glesManager
    private var size: Size = Size(0, 0)
    private var surfaceRotation = 0
    private var frameRate = 0
    private var audioInput: Channel<Decoder.Event>? = null
    private var videoInput: Channel<Decoder.Event>? = null
    private var audioSession = 0
    private var audioFormat: AudioFormat? = null
    private var videoJob: Job? = null
    private var audioJob: Job? = null
    private var outputSurface: Surface? = null
    private var outputSurfaceTexture: SurfaceTexture? = null
    private val outputTexture = Texture2d(
        GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
        GL_CLAMP_TO_EDGE,
        GL_LINEAR
    )
    private val decoder = Decoder(context.context, glesManager.backgroundHandler)

    private val uri: Uri get() = properties[MediaUri]

    override suspend fun onCreate() {
        if (uri.scheme == "none") {
            Log.d(TAG, "no uri")
            return
        }
        decoder.setDataSource(uri)
        surfaceRotation = decoder.surfaceRotation
        size = decoder.size
        decoder.outputAudioFormat?.let {
            val encoding = if (it.containsKey(MediaFormat.KEY_PCM_ENCODING))
                it.getInteger(MediaFormat.KEY_PCM_ENCODING)
            else
                AudioFormat.ENCODING_PCM_16BIT

            val channelCount = it.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val channelMask = when {
                it.containsKey(MediaFormat.KEY_CHANNEL_MASK) -> it.getInteger(MediaFormat.KEY_CHANNEL_MASK)
                channelCount == 6 -> AudioFormat.CHANNEL_OUT_5POINT1
                channelCount == 2 -> AudioFormat.CHANNEL_OUT_STEREO
                else -> AudioFormat.CHANNEL_OUT_MONO
            }

            audioFormat = AudioFormat.Builder()
                .setEncoding(encoding)
                .setSampleRate(it.getInteger(MediaFormat.KEY_SAMPLE_RATE))
                .setChannelMask(channelMask)
                .build()
        }

        frameRate = decoder.outputVideoFormat?.getInteger(MediaFormat.KEY_FRAME_RATE) ?: 0
    }

    override suspend fun onInitialize() {
        if (decoder.hasVideo) {
            connection(VIDEO)?.let {
                if (it.config.acceptsSurface) {
                    repeat(3) { _ -> it.prime(VideoEvent()) }
                } else {
                    glesManager.glContext {
                        outputTexture.initialize()
                    }

                    outputSurfaceTexture = SurfaceTexture(outputTexture.id)
                    outputSurfaceTexture?.setDefaultBufferSize(size.width, size.height)
                    outputSurface = Surface(outputSurfaceTexture)
                    it.prime(VideoEvent(outputTexture), VideoEvent(outputTexture))
                }
                videoInput = Channel(Channel.UNLIMITED)
            }
        }

        if (decoder.hasAudio) {
            connection(AUDIO)?.let {
                repeat(3) { n -> it.prime(AudioEvent()) }
                audioInput = Channel(Channel.UNLIMITED)
            }
        }
    }

    override suspend fun onStart() {
        audioSession++
        val videoConfig = config(VIDEO)
        when {
            videoInput == null -> {
                Log.w(TAG, "no connection, not starting")
            }
            videoConfig?.acceptsSurface == true -> {
                videoJob = scope.launch { start2() }
            }
            videoConfig?.acceptsSurface == false -> {
                videoJob = scope.launch { startTexture() }
            }
        }
        if (audioInput != null) {
            audioJob = scope.launch {
                startAudio()
            }
        }
    }

    private suspend fun start2() {
        val connection = channel(VIDEO) ?: return
        val videoInput = videoInput ?: return
        val config = config(VIDEO) ?: return
        val surface = config.surface.get()

        decoder.start(surface, videoInput, audioInput)

        var count = 0
        val startTime = SystemClock.elapsedRealtimeNanos()
        var firstTime = -1L
        do {
            val frame = connection.receive()
            val event = videoInput.receive()
            val eos = event.info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
            frame.eos = eos
            frame.count = count++
            frame.timestamp = event.info.presentationTimeUs * 1000


            if (firstTime == -1L) {
                firstTime = frame.timestamp
            }

//                Log.d(TAG, "video available ${eos} ${frame.timestamp}")
            val time = SystemClock.elapsedRealtimeNanos() - startTime
            val diff = frame.timestamp - firstTime - time
            if (diff > 1_000_000) {
                delay(diff / 1_000_000)
            }

            if (!config.surface.has()) {
                frame.eos = true
            }
            decoder.releaseVideoBuffer(event.index, frame.eos)
            frame.queue()
        } while (!frame.eos)
    }

    private suspend fun startAudio() {
        val audioChannel = channel(AUDIO) ?: return
        val audioInput = audioInput ?: return

        var count = 0
        do {
            val audioEvent = audioChannel.receive()

            val event = audioInput.receive()
            val eos = event.info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0

            if (audioEvent.session == audioSession) {
                decoder.releaseAudioBuffer(audioEvent.index, audioEvent.eos)
            }
            audioEvent.session = audioSession
            audioEvent.index = event.index
            audioEvent.count = count++
            audioEvent.eos = eos
            event.buffer?.let {
                audioEvent.buffer = it
            }
            audioEvent.queue()
            if (eos) {
                decoder.releaseAudioBuffer(audioEvent.index, audioEvent.eos)
            }
        } while (!eos)
    }

    private suspend fun startTexture() {
        val connection = channel(VIDEO) ?: return
        val surface = outputSurface ?: return
        val videoInput = videoInput ?: return

        decoder.start(surface, videoInput, audioInput)

        var copyMatrix = true
        setOnFrameAvailableListener {
            runBlocking {
                onFrame(connection, it, copyMatrix)
                copyMatrix = false
            }
        }

        var count = 0
        val startTime = SystemClock.elapsedRealtimeNanos()
        var firstTime = -1L
        do {
            val event = videoInput.receive()
            val eos = event.info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
            val timestamp = event.info.presentationTimeUs * 1000

            if (firstTime == -1L) {
                firstTime = timestamp
            }

//                Log.d(TAG, "video available ${eos} ${frame.timestamp}")
            val time = SystemClock.elapsedRealtimeNanos() - startTime
            val diff = timestamp - firstTime - time
            if (diff > 1_000_000) {
                delay(diff / 1_000_000)
            }

            decoder.releaseVideoBuffer(event.index, eos)
            count ++
        } while (!eos)

        Log.d(TAG, "got EOS from decoder")
        Log.d(TAG, "sent frames ${count-1}")
        outputSurfaceTexture?.setOnFrameAvailableListener(null)

        val event = connection.receive()
        event.eos = true
        event.queue()
    }

    private fun setOnFrameAvailableListener(block: (SurfaceTexture) -> Unit) {
        Log.d(TAG, "setOnFrameAvailableListener $outputSurfaceTexture")
//        val uniform = program.getUniform(Uniform.Type.Mat4, "texture_matrix0")
//        var textureMatrix = uniform.data
//        uniform.dirty = true
        outputSurfaceTexture?.setOnFrameAvailableListener(block, glesManager.backgroundHandler)
    }

    private suspend fun onFrame(
        connection: ReceiveChannel<VideoEvent>,
        surfaceTexture: SurfaceTexture,
        copyMatrix: Boolean
    ) {

        val event = connection.receive()
        glesManager.glContext {
            surfaceTexture.updateTexImage()
            if (copyMatrix) {
                surfaceTexture.getTransformMatrix(event.matrix)
            }
//            Log.d(TAG, "surface ${surfaceTexture.timestamp}")
        }
        event.eos = false
        event.queue()
    }

    override suspend fun onStop() {
        decoder.stop()
        videoJob?.join()
        audioJob?.join()
    }

    override suspend fun onRelease() {
        decoder.release()
        glesManager.glContext {
            outputSurface?.release()
            outputSurfaceTexture?.release()
            outputTexture.release()
        }
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun <C : Config, E : Event> makeConfig(key: Connection.Key<C, E>): C {
        return when (key) {
            VIDEO -> {
                val rotatedSize =
                    if (surfaceRotation == 90 || surfaceRotation == 270)
                        Size(size.height, size.width) else size

                VideoConfig(
                    GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                    rotatedSize.width,
                    rotatedSize.height,
                    GLES30.GL_RGB8,
                    GLES30.GL_RGB,
                    GLES30.GL_UNSIGNED_BYTE,
                    frameRate,
                    surfaceRotation,
                    offersSurface = true
                ) as C
            }
            AUDIO -> {
                val audioFormat = audioFormat ?: error("missing audio format")
                AudioConfig(
                    audioFormat,
                    0
                ) as C
            }
            else -> error("unknown key $key")
        }
    }

    companion object {
        const val TAG = "DecoderNode"
        val VIDEO = Connection.Key<VideoConfig, VideoEvent>("video_1")
        val AUDIO = Connection.Key<AudioConfig, AudioEvent>("audio_1")
    }
}
