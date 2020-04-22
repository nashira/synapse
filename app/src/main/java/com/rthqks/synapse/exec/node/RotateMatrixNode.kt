package com.rthqks.synapse.exec.node

import android.opengl.Matrix
import android.os.SystemClock
import com.rthqks.synapse.exec.Connection
import com.rthqks.synapse.exec.ExecutionContext
import com.rthqks.synapse.exec.NodeExecutor
import com.rthqks.synapse.gl.GlesManager
import com.rthqks.synapse.logic.NodeDef.RotateMatrix
import com.rthqks.synapse.logic.NodeDef.RotateMatrix.FrameRate
import com.rthqks.synapse.logic.NodeDef.RotateMatrix.Speed
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
    private val rotationSpeed: Float get() = properties[Speed]
    private var startJob: Job? = null
    private var running = false
    private var needsPriming = true

    override suspend fun onSetup() {

    }

    override suspend fun onPause() {
        onStop()
    }

    override suspend fun onResume() {
        maybeStart()
    }

    private fun maybeStart() {
        if (startJob == null && isResumed && connected(OUTPUT)) {
            startJob = scope.launch {
                onStart()
            }
        }
    }

    override suspend fun <T> onConnect(key: Connection.Key<T>, producer: Boolean) {
        when (key) {
            OUTPUT -> {
                if (needsPriming) {
                    needsPriming = false
                    connection(OUTPUT)?.prime(GlesManager.identityMat(), GlesManager.identityMat())
                }
                maybeStart()
            }
        }
    }

    override suspend fun <T> onDisconnect(key: Connection.Key<T>, producer: Boolean) {
        if (key == OUTPUT && !linked(OUTPUT)) {
            onStop()
        }
    }

    private suspend fun onStart() {
        val output = channel(OUTPUT) ?: error("missing output")

        running = true
        var lastMatrix = output.receive()
        lastMatrix.queue()
        while (running) {
            val delayTime = max(
                0,
                frameDuration - (SystemClock.elapsedRealtime() - lastMatrix.timestamp)
            )
            delay(delayTime)

            val msg = output.receive()
            Matrix.translateM(msg.data, 0, lastMatrix.data, 0, 0.5f, 0.5f, 0.5f)
            Matrix.rotateM(msg.data, 0, rotationSpeed, 1f, 1f, 1f)
            Matrix.translateM(msg.data, 0, -0.5f, -0.5f, -0.5f)
            msg.timestamp = SystemClock.elapsedRealtime()
            msg.queue()

            lastMatrix = msg
        }
    }

    private suspend fun onStop() {
        running = false
        startJob?.join()
        startJob = null
    }

    override suspend fun onRelease() {

    }

    companion object {
        val OUTPUT = Connection.Key<FloatArray>(RotateMatrix.OUTPUT.key)
    }
}