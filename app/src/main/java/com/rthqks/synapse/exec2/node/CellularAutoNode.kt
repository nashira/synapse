package com.rthqks.synapse.exec2.node

import android.opengl.GLES30
import android.os.SystemClock
import android.util.Size
import com.rthqks.synapse.exec.ExecutionContext
import com.rthqks.synapse.exec2.Connection
import com.rthqks.synapse.exec2.NodeExecutor
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
                    GLES30.GL_RGB8,
                    gridSize.width,
                    gridSize.height,
                    GLES30.GL_RGB,
                    GLES30.GL_UNSIGNED_BYTE
                )
                texture2.initData(
                    0,
                    GLES30.GL_RGB8,
                    gridSize.width,
                    gridSize.height,
                    GLES30.GL_RGB,
                    GLES30.GL_UNSIGNED_BYTE
                )
                framebuffer1.release()
                framebuffer1.initialize(texture1)
                framebuffer2.release()
                framebuffer2.initialize(texture2)
                Program().apply {
                    val vertex = am.readTextAsset("shader/cellular_auto.vert")
                    val frag = am.readTextAsset("shader/random.frag")
                    initialize(vertex, frag)
                    GLES30.glUseProgram(programId)
                    GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, framebuffer2.id)
                    GLES30.glViewport(0, 0, gridSize.width, gridSize.height)
                    geo.execute()
                    release()
                }
            }
        }

        if (program.programId == 0) {
            val vertex = am.readTextAsset("shader/cellular_auto.vert")
            val frag = am.readTextAsset("shader/cellular_auto.frag")
            gl.glContext {
                program.apply {
                    initialize(vertex, frag)
                    addUniform(
                        Uniform.Type.Vec2,
                        "res",
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