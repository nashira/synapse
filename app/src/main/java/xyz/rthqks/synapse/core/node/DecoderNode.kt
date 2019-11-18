package xyz.rthqks.synapse.core.node

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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import xyz.rthqks.synapse.codec.Decoder
import xyz.rthqks.synapse.core.Node
import xyz.rthqks.synapse.core.edge.*
import xyz.rthqks.synapse.data.PortType
import xyz.rthqks.synapse.gl.GlesManager
import xyz.rthqks.synapse.gl.Texture

class DecoderNode(
    private val glesManager: GlesManager,
    private val context: Context,
    private val uri: String
) : Node() {
    private lateinit var size: Size
    private var surfaceRotation = 0
    private var surfaceConnection: Connection<SurfaceConfig, SurfaceEvent>? = null
    private var textureConnection: Connection<TextureConfig, TextureEvent>? = null
    private var audioConnection: Connection<AudioConfig, AudioEvent>? = null
    private var audioSession = 0
    private var audioFormat: AudioFormat? = null
    private var videoJob: Job? = null
    private var audioJob: Job? = null
    private val mutex = Mutex()
    private val connectMutex = Mutex()
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
        textureConnection?.let {
            glesManager.withGlContext {
                outputTexture.initialize()
            }

            outputSurfaceTexture = SurfaceTexture(outputTexture.id)
            outputSurfaceTexture?.setDefaultBufferSize(size.width, size.height)
            outputSurface = Surface(outputSurfaceTexture)
            it.prime(TextureEvent(outputTexture, FloatArray(16)))
        }

        surfaceConnection?.let {
            it.prime(SurfaceEvent())
            it.prime(SurfaceEvent())
            it.prime(SurfaceEvent())
        }

        audioConnection?.let {
            it.prime(AudioEvent(0))
            it.prime(AudioEvent(0))
            it.prime(AudioEvent(0))
        }
    }

    override suspend fun start() {
        audioSession++
        when {
            surfaceConnection != null -> start2()
            textureConnection != null -> startTexture()
            else -> {
                Log.w(TAG, "no connection, not starting")
                Unit
            }
        }
    }

    private suspend fun start2() = coroutineScope {
        val connection = surfaceConnection ?: return@coroutineScope
        val config = connection.config
        val surface = config.getSurface()
        val audio = audioConnection?.let { Channel<Decoder.Event>(Channel.UNLIMITED) }
        val video = Channel<Decoder.Event>(Channel.UNLIMITED)

        decoder.start(surface, video, audio)

        videoJob = launch {
            var count = 0L
            val startTime = SystemClock.elapsedRealtimeNanos()
            var firstTime = -1L
            do {
                val event = video.receive()
                val frame = connection.dequeue()
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

                decoder.releaseVideoBuffer(event.index, eos)
                connection.queue(frame)

            } while (!eos)
        }

        if (audio != null)
        audioJob = launch {
            var count = 0
            val audioConnection = audioConnection!!
            do {
                val event = audio.receive()
                val eos = event.info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0

                val audioEvent = audioConnection.dequeue()
                if (audioEvent.session == audioSession) {
                    decoder.releaseAudioBuffer(audioEvent.index, audioEvent.eos)
                }
                audioEvent.session = audioSession
                audioEvent.index = event.index
                audioEvent.frame = count++
                audioEvent.eos = eos
                event.buffer?.let {
                    audioEvent.buffer = it
                }
                audioConnection.queue(audioEvent)
            } while (!eos)
        }
    }

    private suspend fun startTexture() = coroutineScope {
        val connection = textureConnection ?: return@coroutineScope
        val surface = outputSurface ?: return@coroutineScope
        mutex.lock()

        videoJob = launch {

            //            cameraManager.start(cameraId, surface, frameRate) { count, timestamp, eos ->
//                //                launch {
////                    val frame = connection.dequeue()
////                    frame.eos = eos
////                    frame.count = count
////                    frame.timestamp = timestamp
////                    connection.queue(frame)
////                Log.d(TAG, "camera $timestamp")
//                if (eos) {
//                    Log.d(TAG, "got EOS from cam")
//                    Log.d(TAG, "sent frames $count")
//                    outputSurfaceTexture?.setOnFrameAvailableListener(null)
//
//                    launch {
//                        val event = connection.dequeue()
//                        event.eos = true
//                        connection.queue(event)
//                        mutex.unlock()
//                    }
//                }
//            }

            var copyMatrix = true
            setOnFrameAvailableListener {
                launch {
                    onFrame(connection, it, copyMatrix)
                    copyMatrix = false
                }
            }

            // lock again to suspend until we unlock on EOS
            mutex.withLock { }
        }
    }

    private fun setOnFrameAvailableListener(block: (SurfaceTexture) -> Unit) {
        Log.d(TAG, "setOnFrameAvailableListener $outputSurfaceTexture")
//        val uniform = program.getUniform(Uniform.Type.Mat4, "texture_matrix0")
//        var textureMatrix = uniform.data
//        uniform.dirty = true
        outputSurfaceTexture?.setOnFrameAvailableListener(block, glesManager.backgroundHandler)
    }

    private suspend fun onFrame(
        connection: Connection<TextureConfig, TextureEvent>,
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
    }

    override suspend fun release() {
        decoder.release()
        outputSurface?.release()
        outputSurfaceTexture?.release()
        outputTexture.release()
    }

    override suspend fun output(key: String): Connection<*, *>? = when (key) {
        PortType.SURFACE_1 -> SingleConsumer<SurfaceConfig, SurfaceEvent>(
            SurfaceConfig(size, surfaceRotation)
        ).also {
            surfaceConnection = it
        }
        PortType.TEXTURE_1 -> {

            connectMutex.withLock {
                val rotatedSize =
                    if (surfaceRotation == 90 || surfaceRotation == 270)
                        Size(size.height, size.width) else size
                val con = textureConnection
                when (con) {
                    null -> {
                        SingleConsumer(
                            TextureConfig(
                                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                                rotatedSize.width,
                                rotatedSize.height,
                                GLES32.GL_RGB8,
                                GLES32.GL_RGB,
                                GLES32.GL_UNSIGNED_BYTE
                            )
                        )
                    }
                    is SingleConsumer -> {
                        MultiConsumer<TextureConfig, TextureEvent>(con.config).also {
                            it.consumer(con.duplex)
                        }
                    }
                    else -> {
                        textureConnection
                    }
                }.also {
                    textureConnection = it
                }
            }
        }
        PortType.AUDIO_1 -> {

            connectMutex.withLock {
                val con = audioConnection
                val audioFormat = audioFormat ?: return@withLock null

                when (con) {
                    null -> {
                        SingleConsumer(
                            AudioConfig(
                                audioFormat,
                                0
                            )
                        )
                    }
                    is SingleConsumer -> {
                        MultiConsumer<AudioConfig, AudioEvent>(con.config).also {
                            it.consumer(con.duplex)
                        }
                    }
                    else -> {
                        audioConnection
                    }
                }.also {
                    audioConnection = it
                }
            }
        }
        else -> null
    }

    override suspend fun <C : Config, T : Event> input(key: String, connection: Connection<C, T>) {
        throw IllegalStateException("$TAG has no inputs")
    }

    companion object {
        const val TAG = "DecoderNode"
    }
}
