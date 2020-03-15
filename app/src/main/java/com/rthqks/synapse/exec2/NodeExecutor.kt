package com.rthqks.synapse.exec2

import com.rthqks.synapse.exec.ExecutionContext
import com.rthqks.synapse.exec.Executor
import kotlinx.coroutines.channels.ReceiveChannel

abstract class NodeExecutor(
    context: ExecutionContext
) : Executor(context) {
    private val linkedSet = mutableSetOf<Connection.Key<*>>()
    private val cycleSet = mutableSetOf<Connection.Key<*>>()
    private val connections = mutableMapOf<Connection.Key<*>, Connection<*>>()
    private val channels = mutableMapOf<Connection.Key<*>, ReceiveChannel<Message<*>>>()

    abstract suspend fun onSetup()
    abstract suspend fun <T> onConnect(key: Connection.Key<T>, producer: Boolean)
    abstract suspend fun <T> onDisconnect(key: Connection.Key<T>, producer: Boolean)
    abstract suspend fun onRelease()

    suspend fun setup() = async(this::onSetup)

    suspend fun <T> stopConsumer(key: Connection.Key<T>, channel: ReceiveChannel<Message<T>>) = async {
        val con = connection(key)
        con?.removeConsumer(channel)
        val linked = con?.consumerCount ?: 0 > 1
        setLinked(key, linked)
        onDisconnect(key, true)
    }

    suspend fun waitForConsumer(key: Connection.Key<*>) = async {
        onDisconnect(key, false)
    }

    override suspend fun release() {
        exec { onRelease() }
        super.release()
    }

    suspend fun <T> startConsumer(key: Connection.Key<T>, channel: ReceiveChannel<Message<T>>) = async {
        channels[key] = channel
        onConnect(key, false)
    }

    suspend fun <T> getConsumer(key: Connection.Key<T>) = async {
        val connection = connections.getOrPut(key) {
            Connection<T>().also {
                channels[key] = it.producer()
            }
        }

        val consumer = connection.consumer()
        onConnect(key, true)
        return@async consumer
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> connection(key: Connection.Key<T>): Connection<T>? {
        return connections[key] as Connection<T>?
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> channel(key: Connection.Key<T>): ReceiveChannel<Message<T>>? {
        return channels[key] as ReceiveChannel<Message<T>>?
    }

    fun setLinked(key: Connection.Key<*>, linked: Boolean = true) {
        if (linked) {
            linkedSet += key
        } else {
            linkedSet -= key
        }
    }

    fun linked(key: Connection.Key<*>): Boolean = key in linkedSet

    fun setCycle(key: Connection.Key<*>, inCycle: Boolean = true) {
        if (inCycle) {
            cycleSet += key
        } else {
            cycleSet -= key
        }
    }

    fun cycle(key: Connection.Key<*>): Boolean = key in cycleSet

    companion object {
        const val TAG = "NodeExecutor"
    }
}
