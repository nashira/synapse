package xyz.rthqks.synapse.core.node

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.*
import xyz.rthqks.synapse.core.Connection
import xyz.rthqks.synapse.core.Node
import xyz.rthqks.synapse.core.edge.AudioBufferConnection
import xyz.rthqks.synapse.data.PortType
import java.lang.IllegalStateException
import java.util.concurrent.atomic.AtomicBoolean

class AudioSourceNode(
    private val sampleRate: Int,
    private val channelMask: Int,
    private val audioEncoding: Int,
    private val source: Int
) : Node() {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private lateinit var recorder: AudioRecord
    private lateinit var audioFormat: AudioFormat
    private var bufferSize = 0
    private var connection: AudioBufferConnection? = null
    private var recordJob: Job? = null
    private var running = false


    override fun initialize() {
        bufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            channelMask,
            audioEncoding
        ) * 2

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

    override fun start() {
        recordJob = scope.launch {
            recorder.startRecording()
            running = true
            var numFrames = 0
            while (running) {
                connection?.dequeue()?.let {
                    it.buffer.position(0)
                    val read = recorder.read(it.buffer, it.buffer.capacity(), AudioRecord.READ_BLOCKING)
                    connection?.queue(it)
                    numFrames++
                    Log.d(TAG, "read $read frames $numFrames")
                }
            }
        }
    }

    override fun stop() {
        scope.launch {
            running = false
            recordJob?.join()
            recorder.stop()
            connection?.dequeue()?.let {
                it.eos = true
                connection?.queue(it)
            }
        }
    }

    override fun release() {
        recorder.release()
    }

    override fun <T> output(key: String, connection: Connection<T>) {
        if (key == PortType.AUDIO_1) {
            this.connection = connection as AudioBufferConnection
            this.connection?.configure(audioFormat, bufferSize)
        }
    }

    override fun <T> input(key: String, connection: Connection<T>) {
        throw IllegalStateException("no inputs: $this")
    }

    companion object {
        private val TAG = AudioSourceNode::class.java.simpleName
    }
}