package xyz.rthqks.synapse.exec

import xyz.rthqks.synapse.exec.edge.Config
import xyz.rthqks.synapse.exec.edge.Connection
import xyz.rthqks.synapse.exec.edge.Event

abstract class NodeExecutor {

    abstract suspend fun create()
    abstract suspend fun initialize()
    abstract suspend fun start()
    abstract suspend fun stop()
    abstract suspend fun release()
    abstract suspend fun output(key: String): Connection<*, *>?
    abstract suspend fun <C : Config, T : Event> input(key: String, connection: Connection<C, T>)

//    abstract suspend fun <C : Config, T : Event> fout(key: Connection.Key<C, T>): Connection<C, T>
//    abstract suspend fun <C : Config, T : Event> fin(key: Connection.Key<C, T>, connection: Connection<C, T>)

    companion object {
        const val TAG = "NodeExecutor"
    }
}
