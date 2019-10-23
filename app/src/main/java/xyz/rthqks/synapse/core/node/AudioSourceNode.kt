package xyz.rthqks.synapse.core.node

import android.media.AudioFormat
import android.media.AudioRecord
import android.util.Log
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import xyz.rthqks.synapse.core.Connection
import xyz.rthqks.synapse.core.Node
import xyz.rthqks.synapse.core.edge.AudioConnection
import xyz.rthqks.synapse.data.PortType

class AudioSourceNode(
    private val sampleRate: Int,
    private val channelMask: Int,
    private val audioEncoding: Int,
    private val source: Int
) : Node() {
    private lateinit var recorder: AudioRecord
    private lateinit var audioFormat: AudioFormat
    private var bufferSize = 0
    private var connection: AudioConnection? = null
    private var recordJob: Job? = null
    private var running = false


    override suspend fun initialize() {
        bufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            channelMask,
            audioEncoding
        ) * 1

        audioFormat = AudioFormat.Builder()
            .setEncoding(audioEncoding)
            .setSampleRate(sampleRate)
            .setChannelMask(channelMask)
            .build()
        recorder = AudioRecord.Builder()
            .setAudioSource(source)
            .setAudioFormat(
                audioFormat
            )
            .setBufferSizeInBytes(bufferSize)
            .build()
    }

    override suspend fun start() = coroutineScope {
        recordJob = launch {
            recorder.startRecording()
            running = true
            var numFrames = 0
            while (running) {
                connection?.dequeue()?.let {
                    it.eos = false
                    it.frame = numFrames
                    it.buffer.position(0)
                    val read =
                        recorder.read(it.buffer, it.buffer.capacity(), AudioRecord.READ_BLOCKING)
                    it.buffer.limit(read)
                    connection?.queue(it)
                    numFrames++
//                Log.d(TAG, "read $read frames $numFrames")
                }
//                yield()
            }
            Log.d(TAG, "read frames $numFrames")
        }
    }

    override suspend fun stop() {
        running = false
        recordJob?.join()
        recorder.stop()
        connection?.dequeue()?.let {
            Log.d(TAG, "sending EOS")
            it.eos = true
            connection?.queue(it)
        }
    }

    override suspend fun release() {
        recorder.release()
    }

    override suspend fun output(key: String): Connection<*>? {
        return if (key == PortType.AUDIO_1) AudioConnection().also {
            it.configure(audioFormat, bufferSize)
            connection = it
        } else null
    }

    override suspend fun <T> input(key: String, connection: Connection<T>) {
        throw IllegalStateException("no inputs: $this")
    }

    companion object {
        private val TAG = AudioSourceNode::class.java.simpleName
    }
}