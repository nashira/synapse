package xyz.rthqks.synapse.core.node

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import xyz.rthqks.synapse.core.Connection
import xyz.rthqks.synapse.core.Node
import xyz.rthqks.synapse.core.edge.AudioConnection
import xyz.rthqks.synapse.data.PortType

class AudioPlayerNode : Node() {
    private var audioTrack: AudioTrack? = null
    private lateinit var audioFormat: AudioFormat
    private var connection: AudioConnection? = null
    private var bufferSize = 0
    private var playJob: Job? = null
    private var running = false

    override suspend fun initialize() {
    }

    override suspend fun start() = coroutineScope {
        playJob = launch {
            val connection = connection ?: return@launch

            running = true
            audioTrack?.play()
            var numFrames = 0
            while (running) {
                val audioBuffer = connection.acquire()
                if (audioBuffer.eos) {
                    Log.d(TAG, "got EOS")
                    running = false
                } else {
                    val write = audioTrack?.write(
                        audioBuffer.buffer,
                        audioBuffer.buffer.remaining(),
                        AudioTrack.WRITE_BLOCKING
                    )
                    numFrames++
//                    Log.d(TAG, "written $write frames $numFrames")

                }
                connection.release(audioBuffer)
            }
            Log.d(TAG, "wrote frames $numFrames")
        }
    }

    override suspend fun stop() {
        playJob?.join()
        audioTrack?.stop()
//        TODO: make fail-safe with timeout, maybe on release instead
//        try {
//            withTimeout(1000) {
//                playJob?.join()
//            }
//        } finally {
//            audioTrack?.stop()
//        }
    }

    override suspend fun release() {
        audioTrack?.release()
    }

    override suspend fun output(key: String): Connection<*> {
        throw IllegalStateException("no outputs: $this")
    }

    override suspend fun <T> input(key: String, connection: Connection<T>) {
        if (key == PortType.AUDIO_1) {
            this.connection = connection as AudioConnection
            audioFormat = connection.audioFormat
            bufferSize = connection.bufferSize
            createAudioTrack()
        }
    }

    private fun createAudioTrack() {
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .build()
            ).setAudioFormat(audioFormat)
            .setBufferSizeInBytes(bufferSize * 2)
            .build()
    }

    companion object {
        private val TAG = AudioPlayerNode::class.java.simpleName
    }
}