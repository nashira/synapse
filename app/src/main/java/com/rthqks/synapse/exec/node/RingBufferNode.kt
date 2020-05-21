package com.rthqks.synapse.exec.node

import android.opengl.GLES30
import android.util.Log
import android.util.Size
import com.rthqks.synapse.exec.Connection
import com.rthqks.synapse.exec.ExecutionContext
import com.rthqks.synapse.exec.NodeExecutor
import com.rthqks.synapse.exec.Properties
import com.rthqks.synapse.gl.*
import com.rthqks.synapse.logic.NodeDef.RingBuffer
import com.rthqks.synapse.logic.NodeDef.RingBuffer.Depth
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class RingBufferNode(
    context: ExecutionContext,
    private val properties: Properties
) : NodeExecutor(context) {
    private val assetManager = context.assetManager
    private val glesManager = context.glesManager
    private var startJob: Job? = null
    private val depth: Int get() = properties[Depth]
    private var inputSize = Size(0, 0)
    private var prevConfig: Texture2d? = null

    private val texture = Texture3d()
    private var currentLevel = 0
    private var framebuffers = emptyList<Framebuffer>()

    private val program = Program()
    private val quadMesh = Quad()
    private var needsPriming = true

    override suspend fun onSetup() {
        glesManager.glContext {
            texture.initialize()
            quadMesh.initialize()
        }
    }

    override suspend fun <T> onConnect(key: Connection.Key<T>, producer: Boolean) {
        when (key) {
            OUTPUT -> {
                if (needsPriming) {
                    needsPriming = false
                    connection(OUTPUT)?.prime(texture, texture)
                }
            }
            INPUT -> {
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
            INPUT -> {
                onStop()
            }
            OUTPUT -> {}
        }
    }

    private suspend fun checkConfig(texture2d: Texture2d) {
        val programChanged = prevConfig?.oes != texture2d.oes

        val sizeChanged = inputSize.width != texture2d.width
                || inputSize.height != texture2d.height

        val depthChanged = texture.depth != depth

        if (programChanged) {
            prevConfig = texture2d
            glesManager.glContext {
                val vertex = assetManager.readTextAsset("shader/crop_resize.vert")
                val frag = assetManager.readTextAsset("shader/crop_resize.frag").let {
                    if (texture2d.oes) {
                        it.replace("//{EXT}", "#define EXT")
                    } else {
                        it
                    }
                }

                program.apply {
                    release()
                    initialize(vertex, frag)
                    addUniform(
                        Uniform.Type.Mat4,
                        "input_matrix",
                        GlesManager.identityMat()
                    )
                    addUniform(
                        Uniform.Type.Int,
                        "input_texture",
                        INPUT_TEXTURE_LOCATION
                    )
                }
            }
        }

        if (sizeChanged || depthChanged) {
            inputSize = Size(texture2d.width, texture2d.height)

            Log.d(TAG, "input $inputSize")

            glesManager.glContext {
                texture.initData(
                    0,
                    texture2d.internalFormat,
                    inputSize.width,
                    inputSize.height,
                    depth,
                    texture2d.format,
                    texture2d.type
                )
                framebuffers.forEachIndexed { index, framebuffer ->
                    framebuffer.release()
                }
                framebuffers = List(depth) { Framebuffer() }
                framebuffers.forEachIndexed { index, framebuffer ->
                    framebuffer.initialize(texture, index)
                    Log.d(TAG, "glGetError() ${GLES30.glGetError()}")
                }
                currentLevel = 0
            }
        }
    }

    private suspend fun onStart() {
        val inputIn = channel(INPUT) ?: error("missing input")
        for (msg in inputIn) {
            val data = msg.data
            checkConfig(data)
            val uniform = program.getUniform(Uniform.Type.Mat4, "input_matrix")
            val matrix = uniform.data!!
            System.arraycopy(data.matrix, 0, matrix, 0, 16)
            uniform.dirty = true

            channel(OUTPUT)?.receive()?.let {
                execute(data)
                it.timestamp = msg.timestamp
                it.data.index = currentLevel
                it.queue()
            }
            msg.release()
        }
        Log.d(TAG, "end of input")
    }

    private suspend fun onStop() {
        startJob?.join()
        startJob = null
    }

    override suspend fun onRelease() {
        glesManager.glContext {
            framebuffers.forEach { it.release() }
            texture.release()
            quadMesh.release()
            program.release()
        }
    }

    private suspend fun execute(inputTexture: Texture2d) {
        val framebuffer = framebuffers[currentLevel]
        currentLevel = (currentLevel + 1) % framebuffers.size

        glesManager.glContext {
            GLES30.glUseProgram(program.programId)
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, framebuffer.id)
            GLES30.glViewport(0, 0, inputSize.width, inputSize.height)
            inputTexture.bind(GLES30.GL_TEXTURE0)
            program.bindUniforms()
            quadMesh.execute()
        }
    }

    companion object {
        const val TAG = "RingBufferNode"
        const val INPUT_TEXTURE_LOCATION = 0
        val INPUT = Connection.Key<Texture2d>(RingBuffer.INPUT.key)
        val OUTPUT = Connection.Key<Texture3d>(RingBuffer.OUTPUT.key)
    }
}