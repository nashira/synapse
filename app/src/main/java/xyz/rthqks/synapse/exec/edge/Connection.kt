package xyz.rthqks.synapse.exec.edge

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.selects.select
import java.util.concurrent.atomic.AtomicInteger

interface Connection<C : Config, E : Event> {
    val config: C
    suspend fun queue(item: E)
    suspend fun dequeue(): E
    fun poll(): E?
    fun producer(): Channel<E>
    fun consumer(): Channel<E>
    suspend fun prime(item: E)
    data class Key<C : Config, E : Event>(val id: String)
}

class SingleConsumer<C : Config, E : Event>(
    override val config: C
) : Connection<C, E> {
    internal val duplex = Duplex<E>(
        Channel(Channel.UNLIMITED),
        Channel(Channel.UNLIMITED)
    )

    override suspend fun queue(item: E) = duplex.tx.send(item)

    override suspend fun dequeue(): E = duplex.rx.receive()

    override fun poll(): E? = duplex.rx.poll()

    override fun producer(): Channel<E> = duplex.host
    override fun consumer(): Channel<E> = duplex.client

    override suspend fun prime(item: E) = duplex.rx.send(item)

    companion object {
        const val TAG = "Connection"
    }
}

class MultiConsumer<C : Config, E : Event>(
    override val config: C
) : Connection<C, E> {
    private val consumers = mutableListOf<Duplex<E>>()
    private val producer = object : Channel<E> by Channel() {
        override suspend fun send(element: E) {
            element._counter.set(consumers.size)
            consumers.forEach {
                it.tx.send(element)
            }
        }

        override suspend fun receive(): E {
            while (true) {
                val item = select<E?> {
                    consumers.forEach {
                        it.rx.onReceive { item ->
                            if (item._counter.decrementAndGet() == 0) {
                                item
                            } else {
                                null
                            }
                        }
                    }
                }
                item?.let {
                    return it
                }
            }
        }
    }

    fun consumer(duplex: Duplex<E>) {
        consumers.add(duplex)
    }

    override fun producer(): Channel<E> = producer

    override fun consumer(): Channel<E> {
        val duplex = Duplex<E>(
            Channel(Channel.UNLIMITED),
            Channel(Channel.UNLIMITED)
        )
        consumers.add(duplex)
        return duplex.client
    }

    override suspend fun queue(item: E) {
        item._counter.set(consumers.size)
        consumers.forEach {
            it.tx.send(item)
        }
    }

    override suspend fun dequeue(): E {
        while (true) {
            val item = select<E?> {
                consumers.forEach {
                    it.rx.onReceive { item ->
                        if (item._counter.decrementAndGet() == 0) {
                            item
                        } else {
                            null
                        }
                    }
                }
            }
            item?.let {
                return it
            }
        }
    }

    override fun poll(): E? {
        consumers.forEach {
            it.rx.poll()?.let { item ->
                if (item._counter.decrementAndGet() == 0) {
                    return item
                }
            }
        }
        return null
    }

    override suspend fun prime(item: E) {
        item._counter.set(consumers.size)
        consumers.forEach {
            it.rx.send(item)
        }
    }

    companion object {
        const val TAG = "MultiConsumer"
    }
}

interface Config

abstract class Event {
    val _counter = AtomicInteger()
    var eos: Boolean = false
    var count: Int = 0
    var timestamp: Long = 0
}

class Duplex<E : Event>(
    internal val tx: Channel<E>,
    internal val rx: Channel<E>
) {
    val host = Simplex(tx, rx)
    val client = Simplex(rx, tx)
}

class Simplex<E>(
    tx: Channel<E>,
    rx: Channel<E>
) : Channel<E>, SendChannel<E> by tx, ReceiveChannel<E> by rx