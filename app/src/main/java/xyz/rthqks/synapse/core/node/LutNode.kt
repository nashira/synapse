package xyz.rthqks.synapse.core.node

import android.util.Log
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import xyz.rthqks.synapse.core.Connection
import xyz.rthqks.synapse.core.Node
import xyz.rthqks.synapse.core.edge.SurfaceConnection
import xyz.rthqks.synapse.core.gl.GlesManager
import xyz.rthqks.synapse.data.PortType

class LutNode(
    private val glesManager: GlesManager
) : Node() {
    private var outputConnection: SurfaceConnection? = null
    private var inputConnection: SurfaceConnection? = null
    private var startJob: Job? = null

    override suspend fun initialize() {

    }

    override suspend fun start() = coroutineScope {
        val input = inputConnection ?: return@coroutineScope
        val output = outputConnection ?: return@coroutineScope

        startJob = launch {
            input.setSurface(output.getSurface())

            var inEvent = input.acquire()
            while (!inEvent.eos) {
                val outEvent = output.dequeue()
                outEvent.eos = false
                outEvent.count = inEvent.count



                output.queue(outEvent)
                input.release(inEvent)
                inEvent = input.acquire()
            }
            input.release(inEvent)

            Log.d(TAG, "got EOS")

            val outEvent = output.dequeue()
            Log.d(TAG, "sending EOS")
            outEvent.eos = true
            output.queue(outEvent)
        }
    }

    override suspend fun stop() {
        startJob?.join()
    }

    override suspend fun release() {

    }

    override suspend fun output(key: String): Connection<*>? = when (key) {
        PortType.SURFACE_1 -> SurfaceConnection().also { connection ->
            outputConnection = connection
            inputConnection?.let {
                connection.configure(it.getSize())
            }
        }
        else -> null
    }

    override suspend fun <T> input(key: String, connection: Connection<T>) {
        when (key) {
            PortType.SURFACE_1 -> {
                inputConnection = connection as SurfaceConnection
                outputConnection?.configure(connection.getSize())
            }
        }
    }

    companion object {
        private val TAG = LutNode::class.java.simpleName
    }
}