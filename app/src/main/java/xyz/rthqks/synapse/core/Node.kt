package xyz.rthqks.synapse.core

abstract class Node {

    abstract suspend fun create()
    abstract suspend fun initialize()
    abstract suspend fun start()
    abstract suspend fun stop()
    abstract suspend fun release()
    abstract suspend fun output(key: String): Connection<*>?
    abstract suspend fun <T : Event> input(key: String, connection: Connection<T>)

    companion object {
        private val TAG = Node::class.java.simpleName
    }
}
