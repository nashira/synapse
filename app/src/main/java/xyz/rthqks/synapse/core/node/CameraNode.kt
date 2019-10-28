package xyz.rthqks.synapse.core.node

import android.util.Log
import android.util.Size
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import xyz.rthqks.synapse.core.CameraManager
import xyz.rthqks.synapse.core.Connection
import xyz.rthqks.synapse.core.Node
import xyz.rthqks.synapse.core.edge.SurfaceConnection
import xyz.rthqks.synapse.data.PortType

class CameraNode(
    private val cameraManager: CameraManager,
    private val facing: Int,
    private val size: Size,
    private val frameRate: Int
) : Node() {
    private var connection: SurfaceConnection? = null
    private var startJob: Job? = null
    private var frameCount = 0

    override suspend fun initialize() {

    }

    override suspend fun start() = coroutineScope {
        val connection = connection ?: return@coroutineScope
        val surface = connection.getSurface()
        frameCount = 0
        startJob = launch {
            cameraManager.start(surface, facing, frameRate) {
                val frame = connection.dequeue()
                frame.eos = false
                frame.count = frameCount++
                connection.queue(frame)
            }
        }
    }

    override suspend fun stop() {
        cameraManager.stop()
        startJob?.join()

        Log.d(TAG, "sent frames $frameCount")

        connection?.dequeue()?.let {
            Log.d(TAG, "sending EOS")
            it.eos = true
            connection?.queue(it)
        }
    }

    override suspend fun release() {
    }

    override suspend fun output(key: String): Connection<*>? = when (key) {
        PortType.SURFACE_1 -> SurfaceConnection().also {
            connection = it
            it.configure(size)
        }
        else -> null
    }

    override suspend fun <T> input(key: String, connection: Connection<T>) {
        throw IllegalStateException("$TAG has no inputs")
    }

    companion object {
        private val TAG = CameraNode::class.java.simpleName
    }
}
