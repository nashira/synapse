package com.rthqks.synapse.exec.node

import android.opengl.GLES30
import android.os.SystemClock
import android.util.Size
import com.rthqks.synapse.exec.Connection
import com.rthqks.synapse.exec.ExecutionContext
import com.rthqks.synapse.exec.NodeExecutor
import com.rthqks.synapse.gl.*
import com.rthqks.synapse.logic.FrameRate
import com.rthqks.synapse.logic.Properties
import com.rthqks.synapse.logic.Property
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class CellularAutoNode(
    context: ExecutionContext,
    private val properties: Properties
) : NodeExecutor(context) {
    private var running = false
    private var primed = false
    private var startJob: Job? = null
    private val gl = context.glesManager
    private val am = context.assetManager
    private var gridSize = Size(0, 0)
    private val frameDuration: Long get() = 1000L / properties[FrameRate]

    private val texture1 = Texture2d(filter = GLES30.GL_NEAREST, repeat = GLES30.GL_REPEAT)
    private val texture2 = Texture2d(filter = GLES30.GL_NEAREST, repeat = GLES30.GL_REPEAT)
    private val framebuffer1 = Framebuffer()
    private val framebuffer2 = Framebuffer()
    private val program = Program()
    private val geo = Quad()
    private val RD = true

    override suspend fun onSetup() {
        gl.glContext {
            texture1.initialize()
            texture2.initialize()
            geo.initialize()
        }
    }

    override suspend fun onPause() {
        onStop()
    }

    override suspend fun onResume() {
        maybeStart()
    }

    override suspend fun <T> onConnect(key: Connection.Key<T>, producer: Boolean) {
        if (!primed) {
            primed = true
            connection(OUTPUT)?.prime(texture1, texture2)
        }
        maybeStart()
    }

    override suspend fun <T> onDisconnect(key: Connection.Key<T>, producer: Boolean) {
    }

    override suspend fun onRelease() {
        gl.glContext {
            texture1.release()
            texture2.release()
            framebuffer1.release()
            framebuffer2.release()
            geo.release()
            program.release()
        }
    }


    private fun maybeStart() {
        if (isResumed && startJob == null && connected(OUTPUT)) {
            startJob = scope.launch {
                onStart()
            }
        }
    }

    private suspend fun onStart() {
        val output = channel(OUTPUT) ?: error("missing output")

        running = true
        var lastTime = 0L
        while (running) {
            val delayTime = Math.max(
                0,
                frameDuration - (SystemClock.elapsedRealtimeNanos() - lastTime) / 1_000_000
            )
            delay(delayTime)
            checkConfig()

            val msg = output.receive()
            msg.timestamp = SystemClock.elapsedRealtimeNanos()
            val fb = if (msg.data == texture1) framebuffer1 else framebuffer2
            val tx = if (msg.data == texture1) texture2 else texture1

            program.getUniform(Uniform.Type.Vec2, GridSize.name).let {
                it.data?.set(0, 1f / gridSize.width)
                it.data?.set(1, 1f / gridSize.height)
                it.dirty = true
            }

            gl.glContext {
                GLES30.glUseProgram(program.programId)
                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fb.id)
                GLES30.glViewport(0, 0, gridSize.width, gridSize.height)
                tx.bind(GLES30.GL_TEXTURE0)
                program.bindUniforms()
                geo.execute()
            }

            msg.queue()

            lastTime = msg.timestamp
        }
    }

    private suspend fun onStop() {
        running = false
        startJob?.join()
        startJob = null
    }

    private suspend fun checkConfig() {
        val sizeChanged = properties[GridSize] != gridSize

        if (sizeChanged) {
            gridSize = properties[GridSize]

            gl.glContext {
                texture1.initData(
                    0,
                    GLES30.GL_RGB16F,
                    gridSize.width,
                    gridSize.height,
                    GLES30.GL_RGB,
                    GLES30.GL_FLOAT
                )
                texture2.initData(
                    0,
                    GLES30.GL_RGB16F,
                    gridSize.width,
                    gridSize.height,
                    GLES30.GL_RGB,
                    GLES30.GL_FLOAT
                )
                framebuffer1.release()
                framebuffer1.initialize(texture1)
                framebuffer2.release()
                framebuffer2.initialize(texture2)
                gl.fillRandom(framebuffer1, gridSize.width, gridSize.height)
                gl.fillRandom(framebuffer2, gridSize.width, gridSize.height)
            }
        }

        if (program.programId == 0) {
            val (vertex, frag) = if (RD) {
                 Pair(am.readTextAsset("shader/reaction_diffusion.vert"),
                     am.readTextAsset("shader/reaction_diffusion.frag"))
            } else {
                Pair(am.readTextAsset("shader/cellular_auto.vert"),
                    am.readTextAsset("shader/cellular_auto.frag"))
            }
            gl.glContext {
                program.apply {
                    initialize(vertex, frag)
                    addUniform(
                        Uniform.Type.Vec2,
                        GridSize.name,
                        floatArrayOf(1f / gridSize.width, 1f / gridSize.height)
                    )

                    addUniform(Uniform.Type.Int, "grid", 0)
                }
            }
        }
    }

    companion object {
        val GridSize = Property.Key("grid_size", Size::class.java)
        val OUTPUT = Connection.Key<Texture2d>("grid_out")
    }
}