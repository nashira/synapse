package xyz.rthqks.synapse.exec.node

import android.opengl.GLES32.*
import android.opengl.Matrix
import android.util.Log
import android.util.Size
import android.view.Surface
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.whileSelect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import xyz.rthqks.synapse.assets.AssetManager
import xyz.rthqks.synapse.exec.NodeExecutor
import xyz.rthqks.synapse.exec.edge.*
import xyz.rthqks.synapse.gl.*

class OverlayFilterNode(
    private val glesManager: GlesManager,
    private val assetManager: AssetManager
) : NodeExecutor() {
    private var startJob: Job? = null
    private var contentSize = Size(0, 0)
    private val connectMutex = Mutex(true)

    private var texture: Texture? = null
    private var framebuffer: Framebuffer? = null

    private var outputSurfaceWindow: WindowSurface? = null

    private val mesh = Quad()
    private val program = Program()

    override suspend fun create() {

    }

    override suspend fun initialize() {
        val connection = connection(INPUT_CONTENT) ?: error("missing input connection")
        val config = connection.config
        val vertexSource = assetManager.readTextAsset("overlay_filter.vert")
        val fragmentSource = assetManager.readTextAsset("overlay_filter.frag").let {
            if (config.isOes) {
                it.replace("#{EXT}", "#define EXT")
            } else {
                it
            }
        }
        texture = Texture(
            GL_TEXTURE_2D,
            GL_CLAMP_TO_EDGE,
            GL_LINEAR
        )
        framebuffer = Framebuffer()
        glesManager.withGlContext {
            val texture = texture!!
            val framebuffer = framebuffer!!

            texture.initialize()
            texture.initData(
                0,
                config.internalFormat,
                contentSize.width,
                contentSize.height,
                config.format,
                config.type
            )
            Log.d(TAG, "glGetError() ${glGetError()}")

            framebuffer.initialize(texture.id)
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

                addUniform(
                    Uniform.Type.Int,
                    "input_texture0",
                    0
                )

                addUniform(
                    Uniform.Type.Int,
                    "input_texture1",
                    1
                )

            }
        }


        connection(OUTPUT)?.let {
            if (it.config.acceptsSurface) {
                repeat(3) { n -> it.prime(VideoEvent()) }
            } else {
                it.prime(VideoEvent(texture!!))
            }
        }
    }

    override suspend fun start() {
        when (config(OUTPUT)?.acceptsSurface) {
            true -> startSurface()
            false -> startTexture()
            else -> startNoOutput()
        }
    }

    private suspend fun startTexture() = coroutineScope {
        val output = channel(OUTPUT) ?: error("no output connection")
        val input = channel(INPUT_CONTENT) ?: error("no content connection")
        val mask = channel(INPUT_MASK) ?: error("no mask connection")

        var copyMatrix = true

        startJob = launch {
            while (isActive) {
                val inEvent1 = input.receive()
                val maskEvent = mask.receive()

                val outEvent = output.receive()
                outEvent.eos = inEvent1.eos
                outEvent.count = inEvent1.count
                outEvent.timestamp = inEvent1.timestamp

                if (copyMatrix) {
                    copyMatrix = false
                    val uniform = program.getUniform(Uniform.Type.Mat4, "texture_matrix0")
                    System.arraycopy(inEvent1.matrix, 0, uniform.data!!, 0, 16)
                    uniform.dirty = true
                }
                glesManager.withGlContext {
                    framebuffer?.bind()
                    executeGl(inEvent1.texture, maskEvent.texture)
                }

                input.send(inEvent1)
                mask.send(maskEvent)

                output.send(outEvent)

                if (outEvent.eos) {
                    Log.d(TAG, "got EOS")
                    startJob?.cancel()
                }
            }
        }
    }

    private suspend fun startSurface() = coroutineScope {
        val output = channel(OUTPUT) ?: return@coroutineScope
        val config = config(OUTPUT) ?: return@coroutineScope
        val input = channel(INPUT_CONTENT) ?: return@coroutineScope
        val mask = channel(INPUT_MASK) ?: error("no mask connection")

        updateOutputSurface(config.surface.get())

        var copyMatrix = true

        startJob = launch {

            while (isActive) {
                var inEventRaw: VideoEvent? = null
                var maskEventRaw: VideoEvent? = null

                whileSelect {
                    input.onReceive {
                        inEventRaw?.let {
                            input.send(it)
                        }
                        inEventRaw = it
                        maskEventRaw == null
                    }
                    mask.onReceive {
                        maskEventRaw?.let {
                            mask.send(it)
                        }
                        maskEventRaw = it
                        inEventRaw == null
                    }
                }

                val inEvent = inEventRaw!!
                val maskEvent = maskEventRaw!!

                val outEvent = output.receive()
                outEvent.eos = inEvent.eos
                outEvent.count = inEvent.count
                outEvent.timestamp = inEvent.timestamp

                if (config.surface.has()) {
                    if (copyMatrix) {
                        copyMatrix = false

                        val uniform = program.getUniform(Uniform.Type.Mat4, "texture_matrix0")
                        System.arraycopy(inEvent.matrix, 0, uniform.data!!, 0, 16)
                        uniform.dirty = true
                    }
                    glesManager.withGlContext {
                        glBindFramebuffer(GL_FRAMEBUFFER, 0)
                        executeGl(inEvent.texture, maskEvent.texture)
                        outputSurfaceWindow?.swapBuffers()
                    }
                }

                input.send(inEvent)
                mask.send(maskEvent)
                output.send(outEvent)

                if (outEvent.eos) {
                    Log.d(TAG, "got EOS")
                    startJob?.cancel()
                }
            }
        }
    }

    private suspend fun startNoOutput() = coroutineScope {
        val input = channel(INPUT_CONTENT) ?: error("no content connection")
        val mask = channel(INPUT_MASK) ?: error("no mask connection")

        startJob = launch {
            var eos = false
            whileSelect {
                input.onReceive {
                    input.send(it)
                    val stop = eos && it.eos
                    eos = it.eos
                    !stop
                }
                mask.onReceive {
                    mask.send(it)
                    val stop = eos && it.eos
                    eos = it.eos
                    !stop
                }
            }

            Log.d(TAG, "got EOS")
            startJob?.cancel()
        }
    }

    private suspend fun updateOutputSurface(surface: Surface) {
        Log.d(TAG, "creating output surface")
        glesManager.withGlContext {
            outputSurfaceWindow?.release()
            outputSurfaceWindow = it.createWindowSurface(surface)
            outputSurfaceWindow?.makeCurrent()
        }
    }

    private fun executeGl(content: Texture, mask: Texture) {
        glUseProgram(program.programId)
        glViewport(0, 0, contentSize.width, contentSize.height)

        content.bind(GL_TEXTURE0)
        mask.bind(GL_TEXTURE1)

        program.bindUniforms()

        mesh.execute()
    }

    override suspend fun stop() {
        startJob?.join()
    }

    override suspend fun release() {
        outputSurfaceWindow?.release()

        glesManager.withGlContext {
            mesh.release()
            program.release()

            texture?.release()
            framebuffer?.release()
        }
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun <C : Config, E : Event> makeConfig(key: Connection.Key<C, E>): C {
        return when (key) {
            OUTPUT -> {
                connectMutex.withLock {}
                val config = config(INPUT_CONTENT)!!
                contentSize = config.size

                VideoConfig(
                    GL_TEXTURE_2D,
                    contentSize.width,
                    contentSize.height,
                    config.internalFormat,
                    config.format,
                    config.type,
                    offersSurface = true
                ) as C
            }
            else -> error("unknown key $key")
        }
    }

    override suspend fun <C : Config, E : Event> setConfig(key: Connection.Key<C, E>, config: C) {
        super.setConfig(key, config)
        if (key == INPUT_CONTENT) connectMutex.unlock()
    }

    companion object {
        const val TAG = "OverlayFilterNode"
        val INPUT_CONTENT = Connection.Key<VideoConfig, VideoEvent>("video_1")
        val INPUT_MASK = Connection.Key<VideoConfig, VideoEvent>("video_2")
        val OUTPUT = Connection.Key<VideoConfig, VideoEvent>("video_3")
    }
}