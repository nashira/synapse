package com.rthqks.synapse.exec.node

import android.opengl.GLES32.*
import android.opengl.Matrix
import android.util.Log
import android.util.Size
import android.view.Surface
import com.rthqks.synapse.assets.AssetManager
import com.rthqks.synapse.exec.NodeExecutor
import com.rthqks.synapse.exec.link.*
import com.rthqks.synapse.gl.*
import com.rthqks.synapse.logic.Properties
import com.rthqks.synapse.logic.ScaleFactor
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class GrayscaleNode(
    private val glesManager: GlesManager,
    private val assetManager: AssetManager,
    private val properties: Properties
) : NodeExecutor() {
    private var startJob: Job? = null
    private val connectMutex = Mutex(true)
    private var size = Size(0, 0)

    private var texture: Texture? = null
    private var framebuffer: Framebuffer? = null

    private var outputSurfaceWindow: WindowSurface? = null

    private val mesh = Quad()
    private val program = Program()

    private val scale: Int get() = properties[ScaleFactor]

    override suspend fun create() {

    }

    override suspend fun initialize() {
        val config = config(INPUT) ?: error("missing input connection")
        val vertexSource = assetManager.readTextAsset("vertex_texture.vert")
        val fragmentSource = assetManager.readTextAsset("grayscale.frag").let {
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
            texture.initData(0, GL_R8, size.width, size.height, GL_RED, GL_UNSIGNED_BYTE)
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
            else -> Log.w(TAG, "no connection on start")
        }
    }

    private suspend fun startTexture() = coroutineScope {
        startJob = launch {
            val output = channel(OUTPUT) ?: return@launch
            val input = channel(INPUT) ?: return@launch

            var copyMatrix = true

            while (isActive) {
                val inEvent = input.receive()

                val outEvent = output.receive()
                outEvent.eos = inEvent.eos
                outEvent.count = inEvent.count
                outEvent.timestamp = inEvent.timestamp


                if (copyMatrix) {
                    copyMatrix = false
                    val uniform = program.getUniform(Uniform.Type.Mat4, "texture_matrix0")
                    System.arraycopy(inEvent.matrix, 0, uniform.data!!, 0, 16)
                    uniform.dirty = true
                }
                glesManager.withGlContext {
                    framebuffer?.bind()
                    executeGl(inEvent.texture)
                }

                input.send(inEvent)
                output.send(outEvent)

                if (outEvent.eos) {
                    Log.d(TAG, "got EOS")
                    startJob?.cancel()
                }
            }
        }
    }

    private suspend fun startSurface() = coroutineScope {
        startJob = launch {
            val output = channel(OUTPUT) ?: return@launch
            val input = channel(INPUT) ?: return@launch
            val config = config(OUTPUT) ?: return@launch

            updateOutputSurface(config.surface.get())

            var copyMatrix = true

            while (isActive) {
                val inEvent = input.receive()

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
                        executeGl(inEvent.texture)
                        outputSurfaceWindow?.swapBuffers()
                    }
                }

                input.send(inEvent)
                output.send(outEvent)

                if (outEvent.eos) {
                    Log.d(TAG, "got EOS")
                    startJob?.cancel()
                }
            }
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

    private fun executeGl(texture: Texture) {
        glUseProgram(program.programId)
        glViewport(0, 0, size.width, size.height)

        texture.bind(GL_TEXTURE0)

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
                val config = config(INPUT)!!

                val inputSize = config.size
                size = Size(inputSize.width / scale, inputSize.height / scale)

                VideoConfig(
                    GL_TEXTURE_2D,
                    size.width,
                    size.height,
                    GL_R8,
                    GL_RED,
                    GL_UNSIGNED_BYTE,
                    offersSurface = true
                ) as C
            }
            else -> error("unknown key $key")
        }
    }

    override suspend fun <C : Config, E : Event> setConfig(key: Connection.Key<C, E>, config: C) {
        super.setConfig(key, config)
        if (key == INPUT) connectMutex.unlock()
    }

    companion object {
        const val TAG = "GrayscaleNode"
        val INPUT = Connection.Key<VideoConfig, VideoEvent>("video_1")
        val OUTPUT = Connection.Key<VideoConfig, VideoEvent>("video_2")
    }
}