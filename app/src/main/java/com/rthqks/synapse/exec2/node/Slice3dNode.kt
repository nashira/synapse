package com.rthqks.synapse.exec2.node

import android.opengl.GLES30
import android.util.Log
import android.util.Size
import com.rthqks.synapse.exec.ExecutionContext
import com.rthqks.synapse.exec2.Connection
import com.rthqks.synapse.exec2.Message
import com.rthqks.synapse.exec2.NodeExecutor
import com.rthqks.synapse.gl.*
import com.rthqks.synapse.logic.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class Slice3dNode(
    context: ExecutionContext,
    private val properties: Properties
) : NodeExecutor(context) {
    private val assetManager = context.assetManager
    private val glesManager = context.glesManager
    private var startJob: Job? = null
    private val frameDuration: Long get() = 1000L / properties[FrameRate]
    private val sliceDepth: Float get() = properties[SliceDepth]
    private val sliceDirection: Int get() = properties[SliceDirection]
    private var outputSize = Size(0, 0)

    private val texture1 = Texture2d()
    private val texture2 = Texture2d()
    private val framebuffer1 = Framebuffer()
    private val framebuffer2 = Framebuffer()

    private val program = Program()
    private val quadMesh = Quad()

    private var t3dEvent: Message<Texture3d>? = null

    private var needsPriming: Boolean = true

    override suspend fun onSetup() {
        glesManager.glContext {
            texture1.initialize()
            texture2.initialize()
        }
    }

    override suspend fun <T> onConnect(key: Connection.Key<T>, producer: Boolean) {
        when (key) {
            OUTPUT -> {
                if (needsPriming) {
                    needsPriming = false
                    connection(OUTPUT)?.prime(texture1, texture2)
                }
            }
            INPUT_3D -> {
                if (startJob == null) {
                    startJob = scope.launch {
                        onStart()
                    }
                }
            }
        }
    }

    override suspend fun <T> onDisconnect(key: Connection.Key<T>, producer: Boolean) {
        when (key) {
            INPUT_3D -> {
                onStop()
            }
            OUTPUT -> {}
        }
    }

    private suspend fun checkConfig(texture3d: Texture3d) {
        val createProgram = program.programId == 0
        val sizeChanged = outputSize.width != texture3d.width
                || outputSize.height != texture3d.height

        if (createProgram) {
            glesManager.glContext {

                val vertex = assetManager.readTextAsset("shader/slice_3d.vert")
                val frag = assetManager.readTextAsset("shader/slice_3d.frag")

                quadMesh.initialize()

                program.apply {
                    initialize(vertex, frag)
                    addUniform(
                        Uniform.Type.Mat4,
                        MATRIX,
                        GlesManager.identityMat()
                    )
//                addUniform(
//                    Uniform.Type.Int,
//                    "input_texture",
//                    INPUT_TEXTURE_LOCATION
//                )
                    addUniform(
                        Uniform.Type.Int,
                        T3D_TEXTURE,
                        LUT_TEXTURE_LOCATION
                    )
                    addUniform(
                        Uniform.Type.Float,
                        T3D_LAYER,
                        0f
                    )
                    addUniform(
                        Uniform.Type.Float,
                        T3D_DEPTH,
                        (texture3d.depth - 1f) / texture3d.depth
                    )
                    addUniform(
                        Uniform.Type.Int,
                        SliceDirection.name,
                        sliceDirection
                    )
                }
            }
        }

        if (sizeChanged) {
            outputSize = Size(texture3d.width, texture3d.height)
            glesManager.glContext {
                initRenderTarget(framebuffer1, texture1, texture3d)
                initRenderTarget(framebuffer2, texture2, texture3d)
            }
        }
    }

    private fun initRenderTarget(
        framebuffer: Framebuffer,
        texture: Texture2d,
        config: Texture3d
    ) {
        texture.initData(
            0,
            config.internalFormat,
            config.width,
            config.height,
            config.format,
            config.type
        )
        Log.d(TAG, "glGetError() ${GLES30.glGetError()}")
        framebuffer.release()
        framebuffer.initialize(texture)
    }

    private suspend fun onStart() {
        val t3dIn = channel(INPUT_3D) ?: error("missing input")

        for (msg in t3dIn) {
            t3dEvent?.release()
            t3dEvent = msg
            checkConfig(msg.data)
            execute()
        }

        t3dEvent?.let { Log.d(TAG, "got ${it.count} lut events") }
        t3dEvent?.release()
        t3dEvent = null
    }

    private suspend fun onStop() {
        startJob?.join()
        startJob = null
    }

    override suspend fun onRelease() {
        glesManager.glContext {
            texture1.release()
            texture2.release()
            framebuffer1.release()
            framebuffer2.release()
            quadMesh.release()
            program.release()
        }
    }

    private suspend fun execute() {
        val output = channel(OUTPUT) ?: return
        val outEvent = output.receive()

        val t3dTexture = t3dEvent?.data ?: glesManager.emptyTexture3d
        val depth = t3dTexture.depth.toFloat()
        val t3dLayer = t3dTexture.index / depth

        val framebuffer = if (outEvent.data == texture1) {
            framebuffer1
        } else {
            framebuffer2
        }

        val slice = t3dLayer + 0.5f / depth
//        Log.d(TAG, "slice $t3dLayer $sliceDepth $slice")
        program.getUniform(Uniform.Type.Float, T3D_LAYER).set(slice)
        program.getUniform(Uniform.Type.Int, SliceDirection.name).set(sliceDirection)

        glesManager.glContext {
            GLES30.glUseProgram(program.programId)
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, framebuffer.id)
            GLES30.glViewport(0, 0, outputSize.width, outputSize.height)
//            inputTexture.bind(GLES30.GL_TEXTURE0)
            t3dTexture.bind(GLES30.GL_TEXTURE0)
            program.bindUniforms()
            quadMesh.execute()
        }

        outEvent.let {
            it.timestamp = t3dEvent?.timestamp ?: 0
            it.queue()
        }
    }

    companion object {
        const val TAG = "Slice3dNode"
        const val INPUT_TEXTURE_LOCATION = 0
        const val LUT_TEXTURE_LOCATION = 0
        const val MATRIX = "input_matrix"
        const val T3D_TEXTURE = "t3d_texture"
        const val T3D_LAYER = "t3d_layer"
        const val T3D_DEPTH = "t3d_depth"

        //        val INPUT = Connection.Key<VideoConfig, VideoEvent>("input")
        val INPUT_3D = Connection.Key<Texture3d>("input_3d")
        val OUTPUT = Connection.Key<Texture2d>("output")
    }
}