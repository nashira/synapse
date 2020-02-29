package com.rthqks.synapse.exec.link

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import java.util.concurrent.atomic.AtomicInteger

interface Connection<C : Config, E : Event> {
    val config: C
    fun producer(): ReceiveChannel<E>
    fun consumer(): ReceiveChannel<E>
    suspend fun prime(vararg item: E)
    data class Key<C : Config, E : Event>(val id: String)
}

class Connection2<C : Config, E : Event>(
    override val config: C
): Connection<C, E> {
    private val consumers = mutableListOf<Channel<E>>()
    private val producer = Channel<E>(CAPACITY)

    override fun producer(): ReceiveChannel<E> = producer

    override fun consumer(): ReceiveChannel<E> {
        val channel = Channel<E>(CAPACITY)
        consumers.add(channel)
        return channel
    }

    private suspend fun queue(event: E) {
        event.inFlight.set(consumers.size)
        consumers.forEach {
            it.send(event)
        }
    }

    private suspend fun release(event: E) {
        if (event.inFlight.decrementAndGet() <= 0) {
            producer.send(event)
        }
    }

    override suspend fun prime(vararg items: E) {
        items.forEach {
            it.blockQueue = { queue(it) }
            it.blockRelease = { release(it) }
            release(it)
        }
    }

    companion object {
        const val CAPACITY = 20
    }
}

interface Config

abstract class Event {
    val inFlight = AtomicInteger()
    var eos: Boolean = false
    var count: Int = 0
    var timestamp: Long = 0
    var blockQueue: (suspend () -> Unit)? = null
    var blockRelease: (suspend () -> Unit)? = null
    suspend fun queue() = blockQueue?.invoke()
    suspend fun release() = blockRelease?.invoke()
}