package com.rthqks.synapse.exec2

import android.util.Log
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import java.util.concurrent.atomic.AtomicInteger

class Connection<T>(
    private val capacity: Int = CAPACITY
) {
    private var consumers = listOf<Channel<Message<T>>>()
    private val producer = Channel<Message<T>>(capacity)
    private var messageCount = 0
    val consumerCount: Int get() = consumers.size

    fun producer(): ReceiveChannel<Message<T>> = producer

    fun consumer(): ReceiveChannel<Message<T>> {
        val channel = Channel<Message<T>>(capacity)
        consumers = consumers + channel
        return channel
    }

    fun removeConsumer(channel: ReceiveChannel<Message<T>>) {
        Log.d(TAG, "remove cons")
        channel as Channel<Message<T>>
        consumers = consumers - channel
        channel.close()
    }

    suspend fun queue(message: Message<T>) {
        message.count = ++messageCount
        val consumers = consumers
        if (consumers.isEmpty()) {
            Log.e(TAG, "consumers.isEmpty")
            producer.send(message)
        } else {
            message.consumers.set(consumers.size)
            consumers.forEach {
                if (it.isClosedForSend) {
                    message.consumers.decrementAndGet()
                    Log.e(TAG, "isClosedForSend ${message.consumers.get()}")
                } else {
                    it.send(message)
                }
            }
        }
    }

    suspend fun release(message: Message<T>) {
        if (message.consumers.decrementAndGet() <= 0) {
            producer.send(message)
        }
    }

    suspend fun prime(vararg data: T) {
        data.forEach {
            release(Message(it, this))
        }
    }

    data class Key<T>(val id: String)

    companion object {
        const val TAG = "Connection"
        const val CAPACITY = 20
    }
}

interface Config

class Message<T>(
    var data: T,
    var connection: Connection<T>
) {
    val consumers = AtomicInteger()
    var count: Int = 0
    var timestamp: Long = 0
    suspend inline fun queue() = connection.queue(this)
    suspend inline fun release() = connection.release(this)
}