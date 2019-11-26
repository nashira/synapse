package xyz.rthqks.synapse.exec.node

import android.media.AudioFormat
import android.opengl.GLES32.*
import android.opengl.Matrix
import android.os.SystemClock
import android.util.Log
import android.util.Size
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import xyz.rthqks.synapse.assets.AssetManager
import xyz.rthqks.synapse.data.PortType
import xyz.rthqks.synapse.exec.NodeExecutor
import xyz.rthqks.synapse.exec.edge.*
import xyz.rthqks.synapse.gl.*
import java.nio.ByteBuffer

class AudioWaveformNode(
    private val glesManager: GlesManager,
    private val assetManager: AssetManager
) : NodeExecutor() {
    private var running: Boolean = false
    private var startJob: Job? = null
    private var bufferSize: Int = 0
    private lateinit var audioFormat: AudioFormat
    private var inputConnection: Connection<AudioConfig, AudioEvent>? = null
    private var inputChannel: Channel<AudioEvent>? = null
    private var surfaceConfig: SurfaceConfig? = null
    private var outputConnection: Connection<SurfaceConfig, SurfaceEvent>? = null
    private var outputSurfaceWindow: WindowSurface? = null
    private val mesh = Quad()
    private val program = Program()
    private val texture = Texture(
        GL_TEXTURE_2D,
        GL_CLAMP_TO_EDGE,
        GL_NEAREST)

    override suspend fun create() {

    }

    override suspend fun initialize() {
        createProgram()

        val uniform = program.getUniform(Uniform.Type.Int, "isSigned")
        uniform.data = if (audioFormat.encoding == AudioFormat.ENCODING_PCM_8BIT) 0 else 1
        uniform.dirty
        Log.d(TAG, "signed ${uniform.data}")

        updateAudioTexture(null)

        outputConnection?.prime(SurfaceEvent())
        outputConnection?.prime(SurfaceEvent())
        outputConnection?.prime(SurfaceEvent())
    }

    private suspend fun createProgram() {
        val vertexSource = assetManager.readTextAsset("vertex_texture.vert")
        val fragmentSource = assetManager.readTextAsset("audio_waveform.frag").let {
            if (audioFormat.encoding == AudioFormat.ENCODING_PCM_16BIT) {
                it.replace("#{INT_TEXTURE}", "#define INT_TEXTURE")
            } else {
                it
            }
        }

        glesManager.withGlContext {
            texture.initialize()
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

                addUniform(Uniform.Type.Int, "isSigned", 0)

                addUniform(Uniform.Type.Int, "audio_texture", 0)

            }
        }
    }

    override suspend fun start() = coroutineScope {
        val output = outputConnection ?: return@coroutineScope
        val surfaceConfig = surfaceConfig ?: return@coroutineScope
        val input = inputChannel ?: return@coroutineScope

        updateOutputSurface(surfaceConfig)

        startJob = launch {
            running = true
            var numFrames = -1
            while (running) {
                numFrames++

                val audioBuffer = input.receive()
                if (audioBuffer.eos) {
                    Log.d(TAG, "got EOS")
                    running = false
                }

                updateAudioTexture(audioBuffer.buffer)

                input.send(audioBuffer)
                val surfaceEvent = output.dequeue()
                surfaceEvent.count = numFrames.toLong()
                surfaceEvent.timestamp = SystemClock.elapsedRealtimeNanos()
                surfaceEvent.eos = audioBuffer.eos

                if (surfaceConfig.hasSurface()) {
                    glesManager.withGlContext {

                        glViewport(0, 0, 1080, 1080)
                        glUseProgram(program.programId)

                        texture.bind(GL_TEXTURE0)

                        program.bindUniforms()

                        mesh.execute()

                        outputSurfaceWindow?.setPresentationTime(surfaceEvent.timestamp)
                        outputSurfaceWindow?.swapBuffers()
                    }
                }
                output.queue(surfaceEvent)
            }
            Log.d(TAG, "wrote frames $numFrames")
        }
    }

    private suspend fun updateAudioTexture(buffer: ByteBuffer?) {
        buffer?.position(0)
        val (width, internalFormat, format, type) = when(audioFormat.encoding) {
            AudioFormat.ENCODING_PCM_16BIT -> listOf(
                (bufferSize / 2).coerceAtMost(2048),
                GL_R16I, GL_RED_INTEGER, GL_SHORT
            )
            AudioFormat.ENCODING_PCM_8BIT ->  listOf(
                (bufferSize).coerceAtMost(2048),
                GL_R8, GL_RED, GL_UNSIGNED_BYTE
            )
            AudioFormat.ENCODING_PCM_FLOAT ->  listOf(
                (bufferSize / 4).coerceAtMost(2048),
                GL_R16F, GL_RED, GL_FLOAT
            )
            else -> emptyList()
        }

        glesManager.withGlContext {
            if (buffer == null) {
                texture.initData(
                    0,
                    internalFormat,
                    width,
                    1,
                    format,
                    type,
                    buffer
                )
            } else {
                texture.updateData(
                    0,
                    0,
                    0,
                    width,
                    1,
                    buffer
                )
            }
        }
    }

    private suspend fun updateOutputSurface(surfaceConfig: SurfaceConfig) {
        val surface = surfaceConfig.getSurface()
        Log.d(TAG, "creating output surface")
        glesManager.withGlContext {
            outputSurfaceWindow?.release()
            outputSurfaceWindow = it.createWindowSurface(surface)
            outputSurfaceWindow?.makeCurrent()
        }
    }

    override suspend fun stop() {
        startJob?.join()
    }

    override suspend fun release() {
        outputSurfaceWindow?.release()
        glesManager.withGlContext {
            texture.release()
            mesh.release()
            program.release()
        }
    }

    override suspend fun output(key: String): Connection<*, *>? = when (key) {
        PortType.SURFACE_1 -> {
            surfaceConfig = SurfaceConfig(Size(1080, 1080), 0)
            SingleConsumer<SurfaceConfig, SurfaceEvent>(
                surfaceConfig!!
            ).also { connection ->
                outputConnection = connection
            }
        }
        else -> null
    }

    override suspend fun <C: Config, T : Event> input(key: String, connection: Connection<C, T>) {
        if (key == PortType.AUDIO_1) {
            inputConnection = connection as Connection<AudioConfig, AudioEvent>
            inputChannel = connection.consumer()
            audioFormat = connection.config.audioFormat
            bufferSize = connection.config.audioBufferSize
        }
    }

    companion object {
        private val TAG = AudioWaveformNode::class.java.simpleName
    }
}