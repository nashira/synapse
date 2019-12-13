package xyz.rthqks.synapse.exec.node

import android.media.AudioFormat
import android.media.AudioRecord
import android.util.Log
import kotlinx.coroutines.*
import xyz.rthqks.synapse.exec.NodeExecutor
import xyz.rthqks.synapse.exec.edge.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioSourceNode(
    private val sampleRate: Int,
    private val channelMask: Int,
    private val audioEncoding: Int,
    private val source: Int
) : NodeExecutor() {
    private lateinit var recorder: AudioRecord
    private lateinit var audioFormat: AudioFormat
    private var bufferSize = 0
    private var recordJob: Job? = null

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
        connection(OUTPUT)?.let { con ->
            repeat(3) {
                val item = AudioEvent()
                item.buffer = ByteBuffer.allocateDirect(bufferSize).order(ByteOrder.nativeOrder())
                con.prime(item)
            }
        }
    }

    override suspend fun start() = coroutineScope {

        recordJob = launch {
            val connection = connection(OUTPUT) ?: return@launch
            recorder.startRecording()
            var numFrames = 0
            while (isActive) {
                val audioEvent = connection.dequeue()
                audioEvent.eos = false
                audioEvent.count = numFrames
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
        recordJob?.cancelAndJoin()
        recorder.stop()
        connection(OUTPUT)?.let {
            Log.d(TAG, "sending EOS")
            val e = it.dequeue()
            e.eos = true
            it.queue(e)
        }
    }

    override suspend fun release() {
        recorder.release()
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun <C : Config, E : Event> makeConfig(key: Connection.Key<C, E>): C {
        return when (key) {
            OUTPUT -> AudioConfig(audioFormat, bufferSize) as C
            else -> error("")
        }
    }

    companion object {
        const val TAG = "AudioSourceNode"
        val OUTPUT = Connection.Key<AudioConfig, AudioEvent>("audio_1")
    }
}