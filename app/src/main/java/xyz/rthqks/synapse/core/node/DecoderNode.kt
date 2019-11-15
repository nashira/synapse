package xyz.rthqks.synapse.core.node

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.*
import android.media.MediaMetadataRetriever.*
import android.opengl.GLES11Ext
import android.opengl.GLES32
import android.opengl.GLES32.GL_CLAMP_TO_EDGE
import android.opengl.GLES32.GL_LINEAR
import android.util.Log
import android.util.Size
import android.view.Surface
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import xyz.rthqks.synapse.codec.Decoder
import xyz.rthqks.synapse.core.Node
import xyz.rthqks.synapse.core.edge.*
import xyz.rthqks.synapse.data.PortType
import xyz.rthqks.synapse.gl.GlesManager
import xyz.rthqks.synapse.gl.Texture
import java.nio.ByteBuffer

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
    private var startJob: Job? = null
    private val mutex = Mutex()
    private val connectMutex = Mutex()
    private var outputSurface: Surface? = null
    private var outputSurfaceTexture: SurfaceTexture? = null
    private val outputTexture = Texture(
        GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
        GL_CLAMP_TO_EDGE,
        GL_LINEAR
    )
    private val decoder = Decoder(glesManager.backgroundHandler)

    override suspend fun create() {
        decoder.setDataSource(uri)
        surfaceRotation = decoder.surfaceRotation
        size = decoder.size
    }

    override suspend fun initialize() {
        glesManager.withGlContext {
            outputTexture.initialize()
        }
        outputSurfaceTexture = SurfaceTexture(outputTexture.id)
        outputSurfaceTexture?.setDefaultBufferSize(size.width, size.height)
        outputSurface = Surface(outputSurfaceTexture)

        textureConnection?.prime(TextureEvent(outputTexture, FloatArray(16)))

        surfaceConnection?.prime(SurfaceEvent())
        surfaceConnection?.prime(SurfaceEvent())
        surfaceConnection?.prime(SurfaceEvent())
    }

    override suspend fun start() = when {
        surfaceConnection != null -> startSurface()
        textureConnection != null -> startTexture()
        else -> {
            Log.w(TAG, "no connection, not starting")
            Unit
        }
    }

    private suspend fun startSurface() = coroutineScope {
        val connection = surfaceConnection ?: return@coroutineScope
        val config = connection.config
        val surface = config.getSurface()
        var count = 0L
        mutex.lock()
        startJob = launch {
            decoder.start(surface, {
                launch {
                    val frame = connection.dequeue()
                    frame.eos = (it.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                    frame.count = count++
                    frame.timestamp = it.presentationTimeUs * 1000

                    Log.d(TAG, "video available ${frame.eos}")

                    connection.queue(frame)
                    if (frame.eos) {
                        Log.d(TAG, "sending EOS")
                        Log.d(TAG, "sent frames $count")
                        mutex.unlock()
                    }
                }
            }, { index, byteBuffer, bufferInfo ->
                val eos = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                Log.d(TAG, "audio available $eos")
                if (!eos) {
                    decoder.releaseAudioBuffer(index)
                }
            })
            // lock again to suspend until we unlock on EOS
            mutex.withLock { }
        }
    }

    private suspend fun startTexture() = coroutineScope {
        val connection = textureConnection ?: return@coroutineScope
        val surface = outputSurface ?: return@coroutineScope
        mutex.lock()

        startJob = launch {

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
        startJob?.join()
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
                val format = decoder.outputAudioFormat ?: return@withLock null
//                val encoding = format.getInteger()
                val audioFormat = AudioFormat.Builder()
                    .setSampleRate(format.getInteger(MediaFormat.KEY_SAMPLE_RATE)).build()
//                    .setEncoding()
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
