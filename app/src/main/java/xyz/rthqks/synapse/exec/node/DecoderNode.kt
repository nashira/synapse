package xyz.rthqks.synapse.exec.node

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaFormat
import android.opengl.GLES11Ext
import android.opengl.GLES32
import android.opengl.GLES32.GL_CLAMP_TO_EDGE
import android.opengl.GLES32.GL_LINEAR
import android.os.SystemClock
import android.util.Log
import android.util.Size
import android.view.Surface
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import xyz.rthqks.synapse.codec.Decoder
import xyz.rthqks.synapse.exec.NodeExecutor
import xyz.rthqks.synapse.exec.edge.*
import xyz.rthqks.synapse.gl.GlesManager
import xyz.rthqks.synapse.gl.Texture

class DecoderNode(
    private val glesManager: GlesManager,
    private val context: Context,
    private val uri: String
) : NodeExecutor() {
    private lateinit var size: Size
    private var surfaceRotation = 0
    private var audioInput: Channel<Decoder.Event>? = null
    private var videoInput: Channel<Decoder.Event>? = null
    private var audioSession = 0
    private var audioFormat: AudioFormat? = null
    private var videoJob: Job? = null
    private var audioJob: Job? = null
    private var outputSurface: Surface? = null
    private var outputSurfaceTexture: SurfaceTexture? = null
    private val outputTexture = Texture(
        GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
        GL_CLAMP_TO_EDGE,
        GL_LINEAR
    )
    private val decoder = Decoder(context, glesManager.backgroundHandler)

    override suspend fun create() {
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
    }

    override suspend fun initialize() {
        if (decoder.hasVideo) {
            connection(VIDEO)?.let {
                if (it.config.acceptsSurface) {
                    repeat(3) { n -> it.prime(VideoEvent()) }
                } else {
                    glesManager.withGlContext {
                        outputTexture.initialize()
                    }

                    outputSurfaceTexture = SurfaceTexture(outputTexture.id)
                    outputSurfaceTexture?.setDefaultBufferSize(size.width, size.height)
                    outputSurface = Surface(outputSurfaceTexture)
                    it.prime(VideoEvent(outputTexture))
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

    override suspend fun start() = coroutineScope {
        audioSession++
        val videoConfig = config(VIDEO)
        when {
            videoInput == null -> {
                Log.w(TAG, "no connection, not starting")
            }
            videoConfig?.acceptsSurface == true -> {
                videoJob = launch { start2() }
            }
            videoConfig?.acceptsSurface == false -> {
                videoJob = launch { startTexture() }
            }
        }
        if (audioInput != null) {
            audioJob = launch {
                startAudio()
            }
        }
    }

    private suspend fun start2() {
        val connection = connection(VIDEO) ?: return
        val videoInput = videoInput ?: return
        val config = connection.config
        val surface = config.surface.get()

        decoder.start(surface, videoInput, audioInput)

        var count = 0
        val startTime = SystemClock.elapsedRealtimeNanos()
        var firstTime = -1L
        do {
            val frame = connection.dequeue()
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
            connection.queue(frame)

        } while (!frame.eos)
    }

    private suspend fun startAudio() {
        val audioConnection = connection(AUDIO) ?: return
        val audioInput = audioInput ?: return

        var count = 0
        do {
            val audioEvent = audioConnection.dequeue()

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
            audioConnection.queue(audioEvent)
            if (eos) {
                decoder.releaseAudioBuffer(audioEvent.index, audioEvent.eos)
            }
        } while (!eos)
    }

    private suspend fun startTexture() = coroutineScope {
        val connection = connection(VIDEO) ?: return@coroutineScope
        val surface = outputSurface ?: return@coroutineScope
        val videoInput = videoInput ?: return@coroutineScope

        decoder.start(surface, videoInput, audioInput)

        var copyMatrix = true
        setOnFrameAvailableListener {
            launch {
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

        val event = connection.dequeue()
        event.eos = true
        connection.queue(event)
    }

    private fun setOnFrameAvailableListener(block: (SurfaceTexture) -> Unit) {
        Log.d(TAG, "setOnFrameAvailableListener $outputSurfaceTexture")
//        val uniform = program.getUniform(Uniform.Type.Mat4, "texture_matrix0")
//        var textureMatrix = uniform.data
//        uniform.dirty = true
        outputSurfaceTexture?.setOnFrameAvailableListener(block, glesManager.backgroundHandler)
    }

    private suspend fun onFrame(
        connection: Connection<VideoConfig, VideoEvent>,
        surfaceTexture: SurfaceTexture,
        copyMatrix: Boolean
    ) {

        val event = connection.dequeue()
        glesManager.withGlContext {
            surfaceTexture.updateTexImage()
            if (copyMatrix) {
                surfaceTexture.getTransformMatrix(event.matrix)
            }
//            Log.d(TAG, "surface ${surfaceTexture.timestamp}")
        }
        event.eos = false
        connection.queue(event)
    }

    override suspend fun stop() {
        decoder.stop()
        videoJob?.join()
        audioJob?.join()
    }

    override suspend fun release() {
        decoder.release()
        outputSurface?.release()
        outputSurfaceTexture?.release()
        outputTexture.release()
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
                    GLES32.GL_RGB8,
                    GLES32.GL_RGB,
                    GLES32.GL_UNSIGNED_BYTE,
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
