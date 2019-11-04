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
    private val requestedSize: Size,
    private val frameRate: Int
) : Node() {
    private lateinit var size: Size
    private lateinit var cameraId: String
    private var surfaceRotation = 0
    private var connection: SurfaceConnection? = null
    private var startJob: Job? = null

    override suspend fun initialize() {
        val conf = cameraManager.resolve(facing, requestedSize, frameRate)
        Log.d(TAG, conf.toString())
        cameraId = conf.id
        size = conf.size
        surfaceRotation = conf.rotation
    }

    override suspend fun start() = coroutineScope {
        val connection = connection ?: return@coroutineScope
        val surface = connection.getSurface()
        startJob = launch {
            cameraManager.start(cameraId, surface, frameRate) { count, timestamp, eos ->
                if (eos) {
                    Log.d(TAG, "sending EOS")
                    Log.d(TAG, "sent frames $count")
                }
                val frame = connection.dequeue()
                frame.eos = eos
                frame.count = count
                frame.timestamp = timestamp
                connection.queue(frame)
            }
        }
    }

    override suspend fun stop() {
        cameraManager.stop()
        startJob?.join()
    }

    override suspend fun release() {
    }

    override suspend fun output(key: String): Connection<*>? = when (key) {
        PortType.SURFACE_1 -> SurfaceConnection().also {
            connection = it
            it.configure(size, surfaceRotation)
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
