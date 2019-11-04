package xyz.rthqks.synapse.core.node

import android.media.AudioFormat
import android.opengl.GLES32.*
import android.opengl.Matrix
import android.os.SystemClock
import android.util.Log
import android.util.Size
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import xyz.rthqks.synapse.assets.AssetManager
import xyz.rthqks.synapse.core.Connection
import xyz.rthqks.synapse.core.Node
import xyz.rthqks.synapse.core.edge.AudioConnection
import xyz.rthqks.synapse.core.edge.SurfaceConnection
import xyz.rthqks.synapse.core.gl.*
import xyz.rthqks.synapse.data.PortType
import java.nio.ByteBuffer

class AudioWaveformNode(
    private val glesManager: GlesManager,
    private val assetManager: AssetManager
) : Node() {
    private var running: Boolean = false
    private var startJob: Job? = null
    private var bufferSize: Int = 0
    private lateinit var audioFormat: AudioFormat
    private var inputConnection: AudioConnection? = null
    private var outputConnection: SurfaceConnection? = null
    private var outputSurfaceWindow: WindowSurface? = null
    private val mesh = Quad()
    private val program = Program()
    private val texture = Texture(
        GL_TEXTURE_2D,
        GL_TEXTURE0,
        GL_CLAMP_TO_EDGE,
        GL_NEAREST)

    override suspend fun initialize() {

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
            it.makeCurrent()
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

                addUniform(Uniform.Type.Integer, "isSigned", 0)

                addTexture("audio_texture", texture)
            }
        }
    }

    override suspend fun start() = coroutineScope {
        val output = outputConnection ?: return@coroutineScope
        val input = inputConnection ?: return@coroutineScope

        updateOutputSurface(output)

        startJob = launch {
            running = true
            var numFrames = -1
            while (running) {
                numFrames++

                val audioBuffer = input.acquire()
                if (audioBuffer.eos) {
                    Log.d(TAG, "got EOS")
                    running = false
                }

                updateAudioTexture(audioBuffer.buffer)

                input.release(audioBuffer)
                val surfaceEvent = output.dequeue()
                surfaceEvent.count = numFrames.toLong()
                surfaceEvent.timestamp = SystemClock.elapsedRealtimeNanos()
                surfaceEvent.eos = audioBuffer.eos

                if (output.hasSurface()) {
                    glesManager.withGlContext {
                        outputSurfaceWindow?.makeCurrent()
                        glViewport(0, 0, 1080, 1080)
                        glUseProgram(program.programId)

                        program.bindTextures()

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
            outputSurfaceWindow?.makeCurrent()
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
                    format,
                    type,
                    buffer
                )
            }
        }
    }

    private suspend fun updateOutputSurface(output: SurfaceConnection) {
        val surface = output.getSurface()
        Log.d(TAG, "creating output surface")
        glesManager.withGlContext {
            it.makeCurrent()
            outputSurfaceWindow?.release()
            outputSurfaceWindow = it.createWindowSurface(surface)
        }
    }

    override suspend fun stop() {
        startJob?.join()
    }

    override suspend fun release() {
        outputSurfaceWindow?.release()
        glesManager.withGlContext {
            mesh.release()
            program.release()
        }
    }

    override suspend fun output(key: String): Connection<*>? = when (key) {
        PortType.SURFACE_1 -> {
            SurfaceConnection().also { connection ->
                outputConnection = connection
                connection.configure(Size(1080, 1080), 0)
            }
        }
        else -> null
    }

    override suspend fun <T> input(key: String, connection: Connection<T>) {
        if (key == PortType.AUDIO_1) {
            inputConnection = connection as AudioConnection
            audioFormat = connection.audioFormat
            bufferSize = connection.audioBufferSize

            createProgram()

            val uniform = program.getUniform(Uniform.Type.Integer, "isSigned")
            uniform.data = if (audioFormat.encoding == AudioFormat.ENCODING_PCM_8BIT) 0 else 1
            uniform.dirty
            Log.d(TAG, "signed ${uniform.data}")

            updateAudioTexture(null)
        }
    }

    companion object {
        private val TAG = AudioWaveformNode::class.java.simpleName
    }
}