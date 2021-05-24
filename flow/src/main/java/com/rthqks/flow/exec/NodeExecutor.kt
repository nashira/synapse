package com.rthqks.flow.exec

import kotlinx.coroutines.channels.ReceiveChannel

abstract class NodeExecutor(
    context: ExecutionContext
) : Executor(context) {
    private val linkedSet = mutableSetOf<Connection.Key<*>>()
    private val cycleSet = mutableSetOf<Connection.Key<*>>()
    private val connections = mutableMapOf<Connection.Key<*>, Connection<*>>()
    private val channels = mutableMapOf<Connection.Key<*>, ReceiveChannel<Message<*>>>()
    private var state: Int = STATE_PAUSED
    val isResumed: Boolean get() = state == STATE_RESUMED

    abstract suspend fun onSetup()
    abstract suspend fun <T> onConnect(key: Connection.Key<T>, producer: Boolean)
    abstract suspend fun <T> onDisconnect(key: Connection.Key<T>, producer: Boolean)
    abstract suspend fun onRelease()
    open suspend fun onPause() {}
    open suspend fun onResume() {}

    suspend fun setup() = await(this::onSetup)

    suspend fun pause() = await {
        if (state != STATE_PAUSED) {
            state = STATE_PAUSED
            onPause()

        }
    }

    suspend fun resume() = await {
        if (state != STATE_RESUMED) {
            state = STATE_RESUMED
            onResume()
        }
    }

    suspend fun <T> stopConsumer(key: Connection.Key<T>, channel: ReceiveChannel<Message<T>>) =
        await {
            val con = connection(key)
            val linked = con?.consumerCount ?: 0 > 1
            setLinked(key, linked)
            if (!linked) {
                channels.remove(key)
            }

//            Log.d(TAG, "onDisconnect ${key.id}")
            onDisconnect(key, true)
            con?.removeConsumer(channel)
        }

    suspend fun waitForConsumer(key: Connection.Key<*>) = await {
        channels.remove(key)
//        Log.d(TAG, "onDisconnect ${key.id}")
        onDisconnect(key, false)
    }

    override suspend fun release() {
        exec {
//            Log.d(TAG, "onRelease")
            onRelease()
        }
        super.release()
    }

    suspend fun <T> startConsumer(key: Connection.Key<T>, channel: ReceiveChannel<Message<T>>) =
        await {
            channels[key] = channel
//            Log.d(TAG, "onConnect ${key.id}")
            onConnect(key, false)
        }

    suspend fun <T> getConsumer(key: Connection.Key<T>) = await {
        val connection = connections.getOrPut(key) { Connection<T>() }
        channels[key] = connection.producer()
        val consumer = connection.consumer()
//        Log.d(TAG, "onConnect ${key.id}")
        onConnect(key, true)
        consumer
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> connection(key: Connection.Key<T>): Connection<T>? {
        return connections[key] as Connection<T>?
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> channel(key: Connection.Key<T>): ReceiveChannel<Message<T>>? {
        return channels[key] as ReceiveChannel<Message<T>>?
    }

    fun connected(key: Connection.Key<*>) = key in channels

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
        const val STATE_PAUSED = 0
        const val STATE_RESUMED = 1
        const val TAG = "NodeExecutor"
    }
}
