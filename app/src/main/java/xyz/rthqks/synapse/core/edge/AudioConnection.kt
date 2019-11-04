package xyz.rthqks.synapse.core.edge

import android.media.AudioFormat
import android.util.Log
import xyz.rthqks.synapse.core.Connection

class AudioConnection(bufferSize: Int = CAPACITY) : Connection<AudioEvent>(bufferSize) {
    lateinit var audioFormat: AudioFormat
    var audioBufferSize = 0

    override suspend fun createItem(): AudioEvent = AudioEvent(audioBufferSize)

    fun configure(audioFormat: AudioFormat, bufferSize: Int) {
        this.audioFormat = audioFormat
        this.audioBufferSize = bufferSize
        Log.d(TAG, "configure size $bufferSize")
    }

    companion object {
        private val TAG = AudioConnection::class.java.simpleName
    }
}
