package xyz.rthqks.synapse.core

import kotlinx.coroutines.channels.Channel

abstract class Connection<T>(
    private val bufferSize: Int = BUFFER_SIZE
) {
    private val produceChannel: Channel<T> = Channel(bufferSize)
    private val returnChannel: Channel<T> = Channel(bufferSize)
    private var bufferCount = 0

    suspend fun queue(item: T) = produceChannel.send(item)

    suspend fun dequeue(): T {
        val buffer = returnChannel.poll()
        return when {
            buffer != null -> return buffer
            bufferCount < bufferSize -> createBuffer()
            else -> returnChannel.receive()
        }
    }

    suspend fun acquire(): T = produceChannel.receive()

    suspend fun release(item: T) = returnChannel.send(item)

    protected abstract suspend fun createBuffer(): T

    companion object {
        const val BUFFER_SIZE = 3
    }
}
