package com.rthqks.synapse.exec.node

import android.opengl.Matrix
import android.os.SystemClock
import android.util.Log
import com.rthqks.synapse.exec.ExecutionContext
import com.rthqks.synapse.exec.NodeExecutor
import com.rthqks.synapse.exec.link.*
import com.rthqks.synapse.logic.FrameRate
import com.rthqks.synapse.logic.Properties
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.Math.max

class RotateMatrixNode(
    context: ExecutionContext,
    private val properties: Properties
) : NodeExecutor(context) {
    private val frameDuration: Long get() = 1000L / properties[FrameRate]
    private var startJob: Job? = null
    private var running = false

    @Suppress("UNCHECKED_CAST")
    override suspend fun <C : Config, E : Event> makeConfig(key: Connection.Key<C, E>): C {
        return MatrixConfig() as C
    }

    override suspend fun onCreate() {

    }

    override suspend fun onInitialize() {
        connection(OUTPUT)?.prime(MatrixEvent(), MatrixEvent())
    }

    override suspend fun onStart() {
        startJob = scope.launch {
            val output = channel(OUTPUT) ?: return@launch

            running = true
            var frameCount = 0
            var last = 0L
            var lastMatrix = output.receive()
            lastMatrix.queue()
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
                event.queue()

                lastMatrix = event
                last = SystemClock.elapsedRealtime()
            }
        }
    }

    override suspend fun onStop() {
        running = false
        startJob?.join()
        val output = channel(OUTPUT)

        output?.receive()?.also {
            it.eos = true
            it.queue()
            Log.d(TAG, "sent ${it.count}")
        }
    }

    override suspend fun onRelease() {

    }

    companion object {
        val OUTPUT = Connection.Key<MatrixConfig, MatrixEvent>("output_matrix")
    }
}