package xyz.rthqks.synapse.core.node

import android.util.Size
import android.view.Surface
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import xyz.rthqks.synapse.core.CameraManager
import xyz.rthqks.synapse.core.Connection
import xyz.rthqks.synapse.core.Node
import xyz.rthqks.synapse.core.edge.SurfaceConnection
import xyz.rthqks.synapse.data.PortType
import kotlin.coroutines.coroutineContext

class CameraNode(
    private val cameraManager: CameraManager,
    private val facing: Int,
    private val size: Size,
    private val frameRate: Int
) : Node() {
    private var connection: SurfaceConnection? = null
    private var surface: Surface? = null
    private var startJob: Job? = null

    override suspend fun initialize() {
        cameraManager.initialize()
    }

    override suspend fun start() = coroutineScope {
        val surface = surface ?: return@coroutineScope
        val connection = connection ?: return@coroutineScope
        var frameCount = 0
        startJob = launch {
            cameraManager.start(surface, facing, frameRate) {
                val frame = connection.dequeue()
                frame.count = frameCount++
                connection.queue(frame)
            }
        }
    }

    override suspend fun stop() {
        cameraManager.stop()
        startJob?.join()
    }

    override suspend fun release() {
        cameraManager.release()
    }

    override suspend fun output(key: String): Connection<*>? = when (key) {
        PortType.SURFACE_1 -> SurfaceConnection().also {
            connection = it
            it.configure(size) {
                surface = it
            }
        }
        else -> null
    }

    override suspend fun <T> input(key: String, connection: Connection<T>) {

    }

    companion object {
        private val TAG = CameraNode::class.java.simpleName
    }
}
