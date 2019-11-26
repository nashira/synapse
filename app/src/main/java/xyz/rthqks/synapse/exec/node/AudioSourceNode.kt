package xyz.rthqks.synapse.exec.node

import android.media.AudioFormat
import android.media.AudioRecord
import android.util.Log
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import xyz.rthqks.synapse.data.PortType
import xyz.rthqks.synapse.exec.NodeExecutor
import xyz.rthqks.synapse.exec.edge.*

class AudioSourceNode(
    private val sampleRate: Int,
    private val channelMask: Int,
    private val audioEncoding: Int,
    private val source: Int
) : NodeExecutor() {
    private lateinit var recorder: AudioRecord
    private lateinit var audioFormat: AudioFormat
    private var bufferSize = 0
    private var itemsCreated = 0
    private var connection: Connection<AudioConfig, AudioEvent>? = null
    private var recordJob: Job? = null
    private var running = false


    override suspend fun create() {
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

    override suspend fun initialize() {
        connection?.prime(AudioEvent(bufferSize))
        connection?.prime(AudioEvent(bufferSize))
        connection?.prime(AudioEvent(bufferSize))
    }

    override suspend fun start() = coroutineScope {
        val connection = connection ?: return@coroutineScope

        recordJob = launch {
            recorder.startRecording()
            running = true
            var numFrames = 0
            while (running) {
                val audioEvent = connection.dequeue()
                audioEvent.eos = false
                audioEvent.frame = numFrames
                audioEvent.buffer.position(0)
                val read = recorder.read(
                    audioEvent.buffer,
                    audioEvent.buffer.capacity(),
                    AudioRecord.READ_BLOCKING
                )
                audioEvent.buffer.limit(read)
                connection.queue(audioEvent)
                numFrames++
//                Log.d(TAG, "read $read frames $numFrames")
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

    override suspend fun output(key: String): Connection<*, *>? {
        return if (key == PortType.AUDIO_1) {
            SingleConsumer<AudioConfig, AudioEvent>(
                AudioConfig(audioFormat, bufferSize)
            ).also {
                connection = it
            }
        } else null
    }

    override suspend fun <C : Config, T : Event> input(key: String, connection: Connection<C, T>) {
        throw IllegalStateException("no inputs: $this")
    }

    companion object {
        private val TAG = AudioSourceNode::class.java.simpleName
    }
}