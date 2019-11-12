package xyz.rthqks.synapse.core

import kotlinx.coroutines.channels.Channel

abstract class Connection<T : Event>(
    private val capacity: Int = CAPACITY
) {
    private val produceChannel: Channel<T> = Channel(Channel.UNLIMITED)
    private val returnChannel: Channel<T> = Channel(Channel.UNLIMITED)
    private val duplex = Duplex(produceChannel, returnChannel)
    //    , capacity) {
//        createItem()
//    }
    private var lastDequeueTime = 0L
    private var lastAcquireTime = 0L
    private var fps = 0f

    suspend fun queue(item: T) = duplex.producer.send(item)

    suspend fun dequeue(): T = duplex.producer.receive()

    suspend fun acquire(): T = duplex.consumer.receive()

    suspend fun release(item: T) = duplex.consumer.send(item)

    protected abstract suspend fun createItem(): T

    companion object {
        private val TAG = Connection::class.java.simpleName
        const val CAPACITY = 3
    }
}
