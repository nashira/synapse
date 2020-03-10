package com.rthqks.synapse.exec2

import android.util.Log
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import java.util.concurrent.atomic.AtomicInteger

class Connection<E : Event>() {
    var consumers = listOf<Channel<E>>()
    val producer = Channel<E>(CAPACITY)
    var eventCount = 0

    val consumerCount: Int get() = consumers.size

    fun producer(): ReceiveChannel<E> = producer

    suspend fun consumer(): ReceiveChannel<E> {
        val channel = Channel<E>(CAPACITY)
        consumers = consumers + channel
        return channel
    }

    suspend fun removeConsumer(channel: ReceiveChannel<E>) {
        Log.d(TAG, "remove cons")
        channel as Channel<E>
        consumers = consumers - channel
        channel.close()
    }

    private suspend fun queue(event: E) {
        event.count = ++eventCount
        val consumers = consumers

        event.inFlight.set(consumers.size)
        consumers.forEach {
            if (it.isClosedForSend) {
                event.inFlight.decrementAndGet()
            } else {
                it.send(event)
            }
        }
    }

    private suspend fun release(event: E) {
        if (event.inFlight.decrementAndGet() <= 0 && !producer.isClosedForSend) {
            producer.send(event)
        }
    }

    suspend fun prime(vararg items: E) {
        items.forEach {
            it.blockQueue = { queue(it) }
            it.blockRelease = { release(it) }
            release(it)
        }
    }

    suspend fun closeProducer() {
        Log.d(TAG, "remove pro")
    }

    data class Key<E : Event>(val id: String)

    companion object {
        const val TAG = "Connection"
        const val CAPACITY = 20
    }
}

interface Config

open class Event {
    val inFlight = AtomicInteger()
    var eos: Boolean = false
    var count: Int = 0
    var timestamp: Long = 0
    var blockQueue: (suspend () -> Unit)? = null
    var blockRelease: (suspend () -> Unit)? = null
    suspend fun queue() = blockQueue?.invoke()
    suspend fun release() = blockRelease?.invoke()
}