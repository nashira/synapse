package com.rthqks.synapse.exec.node

import android.opengl.GLES30.*
import android.util.Log
import android.util.Size
import android.view.Surface
import com.rthqks.synapse.exec.ExecutionContext
import com.rthqks.synapse.exec.NodeExecutor
import com.rthqks.synapse.exec.link.*
import com.rthqks.synapse.gl.*
import com.rthqks.synapse.logic.Properties
import com.rthqks.synapse.logic.ScaleFactor
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class GrayscaleNode(
    context: ExecutionContext,
    private val properties: Properties
) : NodeExecutor(context) {
    private val assetManager = context.assetManager
    private val glesManager = context.glesManager
    private var startJob: Job? = null
    private var size = Size(0, 0)

    private var texture1: Texture2d? = null
    private var framebuffer1: Framebuffer? = null

    private var texture2: Texture2d? = null
    private var framebuffer2: Framebuffer? = null

    private var outputSurfaceWindow: WindowSurface? = null

    private val mesh = Quad()
    private val program = Program()

    private val scale: Int get() = properties[ScaleFactor]

    private var prevEvent: VideoEvent? = null

    override suspend fun onCreate() {
    }

    override suspend fun onInitialize() {
        val config = config(INPUT) ?: error("missing input connection")
        val vertexSource = assetManager.readTextAsset("shader/vertex_texture.vert")
        val fragmentSource = assetManager.readTextAsset("shader/grayscale.frag").let {
            if (config.isOes) {
                it.replace("//{EXT}", "#define EXT")
            } else {
                it
            }
        }

        glesManager.glContext {

            texture1 = Texture2d().also { texture ->
                texture.initialize()
                texture.initData(0, GL_R8, size.width, size.height, GL_RED, GL_UNSIGNED_BYTE)
                Log.d(TAG, "glGetError() ${glGetError()}")
                framebuffer1 = Framebuffer().also {
                    it.initialize(texture)
                }
            }

            texture2 = Texture2d().also { texture ->
                texture.initialize()
                texture.initData(0, GL_R8, size.width, size.height, GL_RED, GL_UNSIGNED_BYTE)
                Log.d(TAG, "glGetError() ${glGetError()}")
                framebuffer2 = Framebuffer().also {
                    it.initialize(texture)
                }
            }

            mesh.initialize()

            program.apply {
                initialize(vertexSource, fragmentSource)

                addUniform(
                    Uniform.Type.Mat4,
                    "vertex_matrix0",
                    GlesManager.identityMat())

                addUniform(
                    Uniform.Type.Mat4,
                    "texture_matrix0",
                    GlesManager.identityMat())

                addUniform(
                    Uniform.Type.Int,
                    "input_texture0",
                    0
                )

            }
        }

        connection(OUTPUT)?.let {
            if (it.config.acceptsSurface) {
                repeat(3) { _ -> it.prime(VideoEvent()) }
            } else {
                it.prime(VideoEvent(texture1!!), VideoEvent(texture2!!))
            }
        }
    }

    override suspend fun onStart() {
        when (config(OUTPUT)?.acceptsSurface) {
            true -> startSurface()
            false -> startTexture()
            else -> Log.w(TAG, "no connection on start")
        }
    }

    private suspend fun startTexture() {
        startJob = scope.launch {
            val output = channel(OUTPUT) ?: return@launch
            val input = channel(INPUT) ?: return@launch

            var copyMatrix = true

            while (isActive) {

                val inEvent = input.receive()

//                prevEvent?.let {
//                    input.send(it)
//                    prevEvent = null
//                }

//                Log.d(TAG, "event received ${inEvent.count}")
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
                glesManager.glContext {
                    if (outEvent.texture == texture1) {
                        framebuffer1?.bind()
                    } else {
                        framebuffer2?.bind()
                    }
                    executeGl(inEvent.texture)
                }

//                prevEvent = inEvent
                inEvent.release()
                outEvent.queue()

                if (outEvent.eos) {
                    Log.d(TAG, "got EOS")
//                    input.send(inEvent)
//                    prevEvent = null
                    startJob?.cancel()
                }
            }
        }
    }

    private suspend fun startSurface() {
        startJob = scope.launch {
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
                    glesManager.glContext {
                        glBindFramebuffer(GL_FRAMEBUFFER, 0)
                        executeGl(inEvent.texture)
                        outputSurfaceWindow?.swapBuffers()
                    }
                }

                inEvent.release()
                outEvent.queue()

                if (outEvent.eos) {
                    Log.d(TAG, "got EOS")
                    startJob?.cancel()
                }
            }
        }
    }

    private suspend fun updateOutputSurface(surface: Surface) {
        Log.d(TAG, "creating output surface")
        glesManager.glContext {
            outputSurfaceWindow?.release()
            outputSurfaceWindow = it.createWindowSurface(surface)
            outputSurfaceWindow?.makeCurrent()
        }
    }

    private fun executeGl(texture: Texture2d) {
        glUseProgram(program.programId)
        glViewport(0, 0, size.width, size.height)

        texture.bind(GL_TEXTURE0)

        program.bindUniforms()

        mesh.execute()
    }

    override suspend fun onStop() {
        startJob?.join()
    }

    override suspend fun onRelease() {

        glesManager.glContext {
            outputSurfaceWindow?.release()
            mesh.release()
            program.release()

            texture1?.release()
            framebuffer1?.release()
            texture2?.release()
            framebuffer2?.release()
        }
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun <C : Config, E : Event> makeConfig(key: Connection.Key<C, E>): C {
        return when (key) {
            OUTPUT -> {
                val config = configAsync(INPUT).await()

                val inputSize = config.size
                size = Size(inputSize.width / scale, inputSize.height / scale)

                VideoConfig(
                    GL_TEXTURE_2D,
                    size.width,
                    size.height,
                    GL_R8,
                    GL_RED,
                    GL_UNSIGNED_BYTE,
                    config.fps,
                    offersSurface = true
                ) as C
            }
            else -> error("unknown key $key")
        }
    }

    companion object {
        const val TAG = "GrayscaleNode"
        val INPUT = Connection.Key<VideoConfig, VideoEvent>("video_1")
        val OUTPUT = Connection.Key<VideoConfig, VideoEvent>("video_2")
    }
}