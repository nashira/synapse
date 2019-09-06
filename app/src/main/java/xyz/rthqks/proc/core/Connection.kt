package xyz.rthqks.proc.core

import kotlinx.coroutines.channels.Channel

class Connection<T>(bufferSize: Int = BUFFER_SIZE) {
    private val produceChannel: Channel<T> = Channel(bufferSize)
    private val returnChannel: Channel<T> = Channel(bufferSize)

    suspend fun send(item: T) = produceChannel.send(item)

    suspend fun receive(): T = produceChannel.receive()

    suspend fun giveBack(item: T) = returnChannel.send(item)

    suspend fun takeBack(): T = returnChannel.receive()

    companion object {
        const val BUFFER_SIZE = 3
//        const val TYPE_VIDEO = 1
//        const val TYPE_AUDIO = 2
    }
}
