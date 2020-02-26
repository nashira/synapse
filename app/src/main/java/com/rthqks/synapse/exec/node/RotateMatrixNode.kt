package com.rthqks.synapse.exec.node

import android.opengl.Matrix
import android.os.SystemClock
import com.rthqks.synapse.exec.NodeExecutor
import com.rthqks.synapse.exec.link.*
import com.rthqks.synapse.logic.FrameRate
import com.rthqks.synapse.logic.Properties
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.Math.max

class RotateMatrixNode(
    private val properties: Properties
) : NodeExecutor() {
    private val frameDuration: Long get() = 1000L / properties[FrameRate]
    private var startJob: Job? = null
    private var running = false

    @Suppress("UNCHECKED_CAST")
    override suspend fun <C : Config, E : Event> makeConfig(key: Connection.Key<C, E>): C {
        return MatrixConfig() as C
    }

    override suspend fun create() {

    }

    override suspend fun initialize() {
        connection(OUTPUT)?.prime(MatrixEvent(), MatrixEvent())
    }

    override suspend fun start() = coroutineScope {
        startJob = launch {
            val output = channel(OUTPUT) ?: return@launch

            running = true
            var frameCount = 0
            var last = 0L
            var lastMatrix = output.receive()
            output.send(lastMatrix)
            while (running) {
                val delayTime = max(
                    0,
                    frameDuration - (SystemClock.elapsedRealtime() - last)
                )
                delay(delayTime)

                val event = output.receive()
                Matrix.translateM(event.matrix, 0, lastMatrix.matrix, 0, 0.5f, 0.5f, 0.5f)
                Matrix.rotateM(event.matrix, 0, 4f, 1f, 1f, 1f)
                Matrix.translateM(event.matrix, 0, -0.5f, -0.5f, -0.5f)
                event.eos = false
                event.count = ++frameCount
                output.send(event)

                lastMatrix = event
                last = SystemClock.elapsedRealtime()
            }
        }
    }

    override suspend fun stop() {
        running = false
        startJob?.join()
    }

    override suspend fun release() {

    }

    companion object {
        val OUTPUT = Connection.Key<MatrixConfig, MatrixEvent>("output_matrix")
    }
}