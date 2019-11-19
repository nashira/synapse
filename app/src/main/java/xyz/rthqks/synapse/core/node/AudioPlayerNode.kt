package xyz.rthqks.synapse.core.node

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import xyz.rthqks.synapse.core.Node
import xyz.rthqks.synapse.core.edge.*
import xyz.rthqks.synapse.data.PortType

class AudioPlayerNode : Node() {
    private var audioTrack: AudioTrack? = null
    private var audioFormat: AudioFormat? = null
    private var connection: Connection<AudioConfig, AudioEvent>? = null
    private var channel: Channel<AudioEvent>? = null
    private var playJob: Job? = null
    private var running = false

    override suspend fun create() {
    }

    override suspend fun initialize() {
        audioFormat?.let {
            val bufferSize = AudioRecord.getMinBufferSize(
                it.sampleRate,
                AudioFormat.CHANNEL_IN_STEREO,
                it.encoding
            )
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .build()
                ).setAudioFormat(it)
                .setBufferSizeInBytes(bufferSize * 2)
                .build()
        }
    }

    override suspend fun start() = coroutineScope {
        val channel = channel ?: return@coroutineScope

        playJob = launch {
            running = true
            audioTrack?.play()
            var numFrames = 0
            while (running) {
                val audioBuffer = channel.receive()
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
                channel.send(audioBuffer)
            }
            Log.d(TAG, "wrote frames $numFrames")
        }
    }

    override suspend fun stop() {
        playJob?.join()
        audioTrack?.stop()
    }

    override suspend fun release() {
        audioTrack?.release()
    }

    override suspend fun output(key: String): Connection<*, *> {
        throw IllegalStateException("no outputs: $this")
    }

    override suspend fun <C : Config, T : Event> input(key: String, connection: Connection<C, T>) {
        if (key == PortType.AUDIO_1) {
            this.connection = connection as Connection<AudioConfig, AudioEvent>
            channel = connection.consumer()
            audioFormat = connection.config.audioFormat
        }
    }

    companion object {
        private val TAG = AudioPlayerNode::class.java.simpleName
    }
}