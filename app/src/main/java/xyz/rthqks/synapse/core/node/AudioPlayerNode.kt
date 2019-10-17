package xyz.rthqks.synapse.core.node

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.*
import xyz.rthqks.synapse.core.Connection
import xyz.rthqks.synapse.core.Node
import xyz.rthqks.synapse.core.edge.AudioBufferConnection
import xyz.rthqks.synapse.data.PortType

class AudioPlayerNode: Node() {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var audioTrack: AudioTrack? = null
    private lateinit var audioFormat: AudioFormat
    private var connection: AudioBufferConnection? = null
    private var bufferSize = 0
    private var playJob: Job? = null

    override fun initialize() {
    }

    override fun start() {
        audioTrack?.play()
        playJob = scope.launch {
            var numFrames = 0
            while (true) {
                connection?.acquire()?.let {
                    if (it.eos) {
                        return@launch
                    }
                    it.buffer.position(0)
                    val write = audioTrack?.write(it.buffer, it.buffer.capacity(), AudioTrack.WRITE_BLOCKING)
                    connection?.release(it)
                    numFrames++
                    Log.d(TAG, "written $write frames $numFrames")
                }
            }
        }
    }

    override fun stop() {
        scope.launch {
            playJob?.join()
            audioTrack?.stop()
        }
    }

    override fun release() {
        audioTrack?.release()
    }

    override fun <T> output(key: String, connection: Connection<T>) {
        throw IllegalStateException("no outputs: $this")
    }

    override fun <T> input(key: String, connection: Connection<T>) {
        if (key == PortType.AUDIO_1) {
            this.connection = connection as AudioBufferConnection
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