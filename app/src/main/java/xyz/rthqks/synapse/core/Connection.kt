package xyz.rthqks.synapse.core

import kotlinx.coroutines.channels.Channel

class Connection<T>(bufferSize: Int = BUFFER_SIZE) {
    private val produceChannel: Channel<T> = Channel(bufferSize)
    private val returnChannel: Channel<T> = Channel(bufferSize)

    suspend fun queue(item: T) = produceChannel.send(item)

    suspend fun dequeue(): T = returnChannel.receive()

    suspend fun acquire(): T = produceChannel.receive()

    suspend fun release(item: T) = returnChannel.send(item)

    companion object {
        const val BUFFER_SIZE = 3
//        const val TYPE_VIDEO = 1
//        const val TYPE_AUDIO = 2
    }
}
