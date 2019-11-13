package xyz.rthqks.synapse.core.edge

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
    fun consumer(): Channel<E>
    suspend fun prime(item: E)
    class Key<C : Config, E : Event>(val name: String)
}

class SingleConsumer<C : Config, E : Event>(
    override val config: C
) : Connection<C, E> {
    private val duplex = Duplex<E>(
        Channel(Channel.UNLIMITED),
        Channel(Channel.UNLIMITED)
    )

    override suspend fun queue(item: E) = duplex.host.send(item)

    override suspend fun dequeue(): E = duplex.host.receive()

    override fun poll(): E? = duplex.host.poll()

    override fun consumer(): Channel<E> = duplex.client

    override suspend fun prime(item: E) = duplex.client.send(item)

    companion object {
        private const val TAG = "Connection"
    }
}

class MultiConsumer<C : Config, E : Event>(
    override val config: C
) : Connection<C, E> {
    private val consumers = mutableListOf<Duplex<E>>()

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
            it.host.send(item)
        }
    }

    override suspend fun dequeue(): E {
        while (true) {
            val item = select<E?> {
                consumers.forEach {
                    it.host.onReceive { item ->
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
            it.host.poll()?.let { item ->
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
            it.client.send(item)
        }
    }

    companion object {
        const val TAG = "MultiConsumer"
    }
}

interface Config

abstract class Event {
    val _counter = AtomicInteger()
}

// TODO: use channels directly internally for perf boost?
class Duplex<E : Event>(
    val tx: Channel<E>,
    val rx: Channel<E>
) {
    val host = Simplex(tx, rx)
    val client = Simplex(rx, tx)
}

class Simplex<E>(
    tx: Channel<E>, rx: Channel<E>
) : Channel<E>, SendChannel<E> by tx, ReceiveChannel<E> by rx
