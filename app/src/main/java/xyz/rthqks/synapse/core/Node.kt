package xyz.rthqks.synapse.core

abstract class Node {

    abstract fun initialize()
    abstract fun start()
    abstract fun stop()
    abstract fun release()




    companion object {
        private val TAG = Node::class.java.simpleName
    }
}
