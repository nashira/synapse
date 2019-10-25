package xyz.rthqks.synapse.core

import android.util.Log
import kotlinx.coroutines.channels.Channel

abstract class Connection<T>(
    private val bufferSize: Int = BUFFER_SIZE
) {
    private val produceChannel: Channel<T> = Channel(bufferSize)
    private val returnChannel: Channel<T> = Channel(bufferSize)
    private var bufferCount = 0
    private var lastDequeueTime = 0L
    private var lastAcquireTime = 0L
    private var fps = 0f

    suspend fun queue(item: T) {
//        val time = (SystemClock.elapsedRealtimeNanos() - lastDequeueTime) / 1000
//        Log.d(TAG, "produce time: $time")
        produceChannel.send(item)
    }

    suspend fun dequeue(): T {
        val buffer = returnChannel.poll()
            ?: if (bufferCount < bufferSize) {
                Log.d(TAG, "createBuffer $bufferCount")
                bufferCount += 1
                createBuffer()
            } else returnChannel.receive()

//        val elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
//        val time = (elapsedRealtimeNanos - lastDequeueTime) / 1000
//        lastDequeueTime = elapsedRealtimeNanos
//
//        val newFps = 1_000_000f / time
//        val beta = 0.9f
//        fps = fps * beta + (1f - beta) * newFps
//        Log.d(TAG, "round trip time: $time, fps: $fps")
        return buffer
    }

    suspend fun acquire(): T {
        val receive = produceChannel.receive()
//        lastAcquireTime = SystemClock.elapsedRealtimeNanos()
        return receive
    }

    suspend fun release(item: T) {
//        val time = (SystemClock.elapsedRealtimeNanos() - lastAcquireTime) / 1000
//        Log.d(TAG, "consume time: $time")
        returnChannel.send(item)
    }

    protected abstract suspend fun createBuffer(): T

    companion object {
        private val TAG = Connection::class.java.simpleName
        const val BUFFER_SIZE = 3
    }
}
