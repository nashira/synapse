package xyz.rthqks.synapse.core

abstract class Node {

    abstract fun initialize()
    abstract fun start()
    abstract fun stop()
    abstract fun release()

    abstract fun <T> output(key: String, connection: Connection<T>)

    abstract fun <T> input(key: String, connection: Connection<T>)

    companion object {
        private val TAG = Node::class.java.simpleName
    }
}
