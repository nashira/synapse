package com.rthqks.synapse.exec.node

import android.media.AudioFormat
import android.media.AudioRecord
import android.util.Log
import com.rthqks.synapse.exec.ExecutionContext
import com.rthqks.synapse.exec.NodeExecutor
import com.rthqks.synapse.exec.link.*
import com.rthqks.synapse.logic.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioSourceNode(
    context: ExecutionContext,
    private val properties: Properties
) : NodeExecutor(context) {
    private lateinit var recorder: AudioRecord
    private lateinit var audioFormat: AudioFormat
    private var bufferSize = 0
    private var recordJob: Job? = null

    private val sampleRate: Int get() = properties[AudioSampleRate]
    private val channelMask: Int get() = properties[AudioChannel]
    private val audioEncoding: Int get() = properties[AudioEncoding]
    private val source: Int get() = properties[AudioSource]
    private val frameDurationNs = 1_000_000_000 / sampleRate
    private var bytesPerFrame = 0
    private var running = false

    override suspend fun onCreate() {
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

        val channelCount = audioFormat.channelCount
        val bytesPerSample = getBytesPerSample(audioEncoding)
        bytesPerFrame = channelCount * bytesPerSample
    }

    override suspend fun onInitialize() {
        connection(OUTPUT)?.let { con ->
            repeat(8) {
                val item = AudioEvent()
                item.buffer = ByteBuffer.allocateDirect(bufferSize).order(ByteOrder.nativeOrder())
                con.prime(item)
            }
        }
    }

    override suspend fun onStart() {
        val output = channel(OUTPUT) ?: return
        running = true
        recorder.startRecording()

        recordJob = scope.launch {
            var bytesWritten = 0L
            var numFrames = 0
            while (running) {
                val audioEvent = output.receive()
                audioEvent.eos = false
                audioEvent.count = numFrames
//                audioEvent.timestamp = (SystemClock.elapsedRealtimeNanos() - start) / 1000
//                audioEvent.timestamp = SystemClock.elapsedRealtimeNanos() / 1000
                audioEvent.buffer.position(0)
                val read = recorder.read(
                    audioEvent.buffer,
                    audioEvent.buffer.capacity(),
                    AudioRecord.READ_BLOCKING
                )
                audioEvent.buffer.limit(read)
                bytesWritten += read
                audioEvent.timestamp = ((bytesWritten / bytesPerFrame) * frameDurationNs) / 1000

                output.send(audioEvent)
                numFrames++
//                Log.d(TAG, "read $read frames $numFrames")
            }
            Log.d(TAG, "read frames $numFrames")
        }
    }

    override suspend fun onStop() {
        Log.d(TAG, "stop")
        running = false
        Log.d(TAG, "stop running = false")
        recordJob?.join()
        Log.d(TAG, "stop join")
        recorder.stop()
        Log.d(TAG, "stop recorder stop")
        channel(OUTPUT)?.let {
            Log.d(TAG, "sending EOS")
            val e = it.receive()
            Log.d(TAG, "sending receive")
            e.eos = true
            it.send(e)
            Log.d(TAG, "sending send")
        }
    }

    override suspend fun onRelease() {
        recorder.release()
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun <C : Config, E : Event> makeConfig(key: Connection.Key<C, E>): C {
        return when (key) {
            OUTPUT -> AudioConfig(audioFormat, bufferSize) as C
            else -> error("")
        }
    }

    fun getBytesPerSample(audioFormat: Int): Int {
        return when (audioFormat) {
            AudioFormat.ENCODING_PCM_8BIT -> 1
            AudioFormat.ENCODING_PCM_16BIT,
            AudioFormat.ENCODING_IEC61937,
            AudioFormat.ENCODING_DEFAULT -> 2
            AudioFormat.ENCODING_PCM_FLOAT -> 4
            AudioFormat.ENCODING_INVALID -> throw IllegalArgumentException("Bad audio format $audioFormat")
            else -> throw IllegalArgumentException("Bad audio format $audioFormat")
        }
    }

    companion object {
        const val TAG = "AudioSourceNode"
        val OUTPUT = Connection.Key<AudioConfig, AudioEvent>("audio_1")
    }
}