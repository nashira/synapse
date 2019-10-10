package xyz.rthqks.synapse.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.isActive
import kotlin.coroutines.CoroutineContext

abstract class Node: CoroutineScope {
    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = job

    fun getInputs(): List<Input> {
        return emptyList()
    }

    fun getOutputs(): List<Output> {
        return emptyList()
    }

//    abstract fun <T> input(config: Connection.Config<T>): ReceiveChannel<T>
//    abstract fun <T> output(config: Connection.Config<T>): SendChannel<T>

    fun getChannel(which: Int) {

    }

    fun connectTo() {

    }

    abstract fun start()
    abstract fun stop()
    abstract fun release()

    fun CoroutineScope.connect(input: ReceiveChannel<Int>): ReceiveChannel<Int> = produce {
        while (isActive) {
            val i = input.receive()

        }
    }

    companion object {
        private val TAG = Node::class.java.simpleName
    }
}
