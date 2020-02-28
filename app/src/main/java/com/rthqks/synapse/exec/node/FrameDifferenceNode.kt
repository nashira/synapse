package com.rthqks.synapse.exec.node

import android.opengl.GLES30.*
import android.opengl.Matrix
import android.util.Log
import android.util.Size
import com.rthqks.synapse.exec.ExecutionContext
import com.rthqks.synapse.exec.NodeExecutor
import com.rthqks.synapse.exec.link.*
import com.rthqks.synapse.gl.*
import com.rthqks.synapse.logic.Properties
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class FrameDifferenceNode(
    context: ExecutionContext,
    private val properties: Properties
) : NodeExecutor(context) {
    private val assetManager = context.assetManager
    private val glesManager = context.glesManager
    private var startJob: Job? = null
    private var size = Size(0, 0)
    private val connectMutex = Mutex(true)

    private val mesh = Quad()
    private val program = Program()
    private val diffTexture1 = Texture2d()
    private val diffTexture2 = Texture2d()
    private val lastFrameTexture1 = Texture2d()
    private val lastFrameTexture2 = Texture2d()
    private val framebuffer1 = Framebuffer()
    private val framebuffer2 = Framebuffer()

    private var framebuffer = framebuffer1

    override suspend fun onCreate() {
    }

    override suspend fun onInitialize() {
        config(INPUT)?.let {
            createProgram(it.isOes)
            createTextures()
        }

        connection(OUTPUT)?.prime(
            VideoEvent(diffTexture1),
            VideoEvent(diffTexture2)
        )
    }

    private suspend fun createTextures() {
        val config = config(INPUT) ?: error("missing input connection")

        val intFormat = config.internalFormat
        val format = config.format
        val type = config.type

        glesManager.glContext {
            diffTexture1.initialize()
            diffTexture2.initialize()
            lastFrameTexture1.initialize()
            lastFrameTexture2.initialize()
            diffTexture1.initData(
                0,
                config.internalFormat,
                size.width, size.height,
                config.format,
                config.type
            )
            Log.d(TAG, "1 glGetError() ${glGetError()}")
            diffTexture2.initData(
                0,
                config.internalFormat,
                size.width, size.height,
                config.format,
                config.type
            )
            Log.d(TAG, "1 glGetError() ${glGetError()}")
            lastFrameTexture1.initData(
                0,
                intFormat, size.width, size.height,
                format,
                type
            )
            Log.d(TAG, "2 glGetError() ${glGetError()}")
            lastFrameTexture2.initData(
                0,
                intFormat, size.width, size.height,
                format,
                type
            )
            Log.d(TAG, "3 glGetError() ${glGetError()}")

            framebuffer1.initialize(lastFrameTexture1, diffTexture1)
            framebuffer2.initialize(lastFrameTexture2, diffTexture2)
        }
    }

    private suspend fun createProgram(oesTexture: Boolean) {
        val vertexSource = assetManager.readTextAsset("shader/frame_difference.vert")
        val fragmentSource = assetManager.readTextAsset("shader/frame_difference.frag").let {
            if (oesTexture) {
                it.replace("//{EXT}", "#define EXT")
            } else {
                it
            }
        }

        glesManager.glContext {
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
    }


    override suspend fun onStart() {
        val output = channel(OUTPUT) ?: return
        val input = channel(INPUT) ?: return

        startJob = scope.launch {
            var copyMatrix = 0
            while (isActive) {
                val outEvent = output.receive()
                val inEvent = input.receive()

                outEvent.apply {
                    eos = inEvent.eos
                    count = inEvent.count
                    timestamp = inEvent.timestamp
                }

                if (copyMatrix < 2) {
                    copyMatrix++
                    val uniform = program.getUniform(Uniform.Type.Mat4, "texture_matrix0")
                    System.arraycopy(inEvent.matrix, 0, uniform.data!!, 0, 16)
                    uniform.dirty = true
                }

                glesManager.glContext {
                    framebuffer.bind()
                    executeGl(inEvent.texture)
                }

                input.send(inEvent)

                framebuffer = if (framebuffer == framebuffer1) framebuffer2 else framebuffer1

                output.send(outEvent)

                if (inEvent.eos) {
                    Log.d(TAG, "got EOS")
                    startJob?.cancel()
                }
            }
        }
    }

    private fun executeGl(texture: Texture2d) {
        glUseProgram(program.programId)
        glViewport(0, 0, size.width, size.height)

        texture.bind(GL_TEXTURE0)

        if (framebuffer == framebuffer1) {
            lastFrameTexture2.bind(GL_TEXTURE1)
        } else {
            lastFrameTexture1.bind(GL_TEXTURE1)
        }

        program.bindUniforms()

        mesh.execute()
    }

    override suspend fun onStop() {
        startJob?.join()
    }

    override suspend fun onRelease() {
        connection(INPUT)?.let {
            glesManager.glContext {
                diffTexture1.release()
                diffTexture2.release()
                lastFrameTexture1.release()
                lastFrameTexture2.release()
                framebuffer1.release()
                framebuffer2.release()
                mesh.release()
                program.release()
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun <C : Config, E : Event> makeConfig(key: Connection.Key<C, E>): C {
        return when (key) {
            OUTPUT -> {
                connectMutex.withLock {}
                val config = config(INPUT)!!
                size = config.size
                VideoConfig(
                    GL_TEXTURE_2D,
                    size.width,
                    size.height,
                    config.internalFormat,
                    config.format,
                    config.type,
                    config.fps
                ) as C
            }
            else -> error("unknown key $key")
        }
    }

    override suspend fun <C : Config, E : Event> setConfig(key: Connection.Key<C, E>, config: C) {
        super.setConfig(key, config)
        when (key) {
            INPUT -> {
                connectMutex.unlock()
            }
        }
    }

    companion object {
        const val TAG = "FrameDifferenceNode"
        val INPUT = Connection.Key<VideoConfig, VideoEvent>("video_1")
        val OUTPUT = Connection.Key<VideoConfig, VideoEvent>("video_2")
    }
}