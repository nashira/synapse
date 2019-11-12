package xyz.rthqks.synapse.core

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import java.util.concurrent.atomic.AtomicInteger

class MultiConnection<T : Event>(
    private val capacity: Int = CAPACITY,
    creator: suspend () -> T
) {
    private val consumers = mutableListOf<Duplex<T>>()

    val producerChannel = Channel<T>(Channel.UNLIMITED)
    val producer = ProducerCreator(producerChannel, creator, { item ->

        item._counter.set(consumers.size)
        coroutineScope {
            consumers.forEach {
                launch {
                    it.producer.send(item)
                }
            }
        }
//        val cons = consumers.toMutableSet()
//
//        while (cons.isNotEmpty()) {
//            select<Unit> {
//                cons.forEach { duplex ->
//                    duplex.producer.onSend(item) {
//                        cons.remove(duplex)
//                    }
//                }
//            }
//        }
    }, capacity)

//    { item ->
//        coroutineScope {
//            launch {
//                consumers.forEach {
//                    it.producer.send(item)
//                }
//                consumers.forEach {
//                    it.producer.receive()
//                }
//                producerChannel.send(item)
//            }
//        }
//    }

    fun consumer(): Consumer<T> {
        val duplex =
            Duplex<T>(Channel(Channel.UNLIMITED), Channel(Channel.UNLIMITED))
        consumers.add(duplex)
        return duplex.consumer
    }

    suspend fun start() = coroutineScope {
        while (isActive) {
            select<Unit> {
                consumers.forEach {
                    it.producer.onReceive {
                        if (it._counter.decrementAndGet() == 0) {
                            producerChannel.send(it)
                        }
                    }
                }
            }
        }
    }

//    protected abstract suspend fun createItem(): T

    companion object {
        private val TAG = MultiConnection::class.java.simpleName
        const val CAPACITY = 3
    }
}

abstract class Event {
    val _counter = AtomicInteger()
}

class Duplex<T : Event>(
    produce: Channel<T>,
    consume: Channel<T>
) {
    val consumer = Consumer(produce, consume)
    val producer = Producer(produce, consume)
}

class ProducerCreator<T>(
    private val channel: Channel<T>,
    private val creator: suspend () -> T,
    private val sender: suspend (T) -> Unit,
    private val capacity: Int
) : ReceiveChannel<T> by channel {
    private var itemsCreatedCount = 0

    override suspend fun receive(): T {
        return poll()
            ?: if (itemsCreatedCount < capacity) {
//                Log.d("MultiConnection", "createItem $itemsCreatedCount")
                itemsCreatedCount += 1
                creator()
            } else channel.receive()
    }

    suspend fun send(item: T) = sender(item)
}

open class Producer<T>(
    produce: Channel<T>, consume: Channel<T>
) : Channel<T>, SendChannel<T> by produce, ReceiveChannel<T> by consume

open class Consumer<T>(
    produce: Channel<T>, consume: Channel<T>
) : Channel<T>, SendChannel<T> by consume, ReceiveChannel<T> by produce