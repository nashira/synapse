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
    private val channels = mutableMapOf<Connection.Key<*>, ReceiveChannel<*>>()

    abstract suspend fun onSetup()
    abstract suspend fun <E : Event> onConnect(key: Connection.Key<E>, producer: Boolean)
    abstract suspend fun <E : Event> onDisconnect(key: Connection.Key<E>, producer: Boolean)
    abstract suspend fun onRelease()

    suspend fun setup() = async(this::onSetup)

    suspend fun <E : Event> disconnectProducer(key: Connection.Key<E>, channel: ReceiveChannel<E>) = async {
        val con = connection(key)
        con?.removeConsumer(channel)
        onDisconnect(key, true)
    }

    suspend fun <E : Event> disconnectConsumer(key: Connection.Key<E>) = async {
        onDisconnect(key, false)
    }

    override suspend fun release() {
        exec { onRelease() }
        super.release()
    }

    suspend fun <E : Event> connectConsumer(key: Connection.Key<E>, channel: ReceiveChannel<E>) = async {
        channels[key] = channel
        onConnect(key, false)
    }

    suspend fun <E : Event> connectProducer(key: Connection.Key<E>) = async {
        val connection = connections.getOrPut(key) {
            Connection<E>().also {
                channels[key] = it.producer()
            }
        }

        onConnect(key, true)
        return@async connection.consumer()
    }

    @Suppress("UNCHECKED_CAST")
    fun <E : Event> connection(key: Connection.Key<E>): Connection<E>? {
        return connections[key] as Connection<E>?
    }

    @Suppress("UNCHECKED_CAST")
    fun <E : Event> channel(key: Connection.Key<E>): ReceiveChannel<E>? {
        return channels[key] as ReceiveChannel<E>?
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
