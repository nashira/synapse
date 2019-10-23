package xyz.rthqks.synapse.core

abstract class Node {

    abstract suspend fun initialize()
    abstract suspend fun start()
    abstract suspend fun stop()
    abstract suspend fun release()
    abstract suspend fun <T> output(key: String, connection: Connection<T>)
    abstract suspend fun <T> input(key: String, connection: Connection<T>)

    companion object {
        private val TAG = Node::class.java.simpleName
    }
}
