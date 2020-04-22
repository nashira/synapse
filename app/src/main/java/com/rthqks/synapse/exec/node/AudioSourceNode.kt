package com.rthqks.synapse.exec.node

import android.media.AudioFormat
import android.media.AudioRecord
import android.util.Log
import com.rthqks.synapse.exec.Connection
import com.rthqks.synapse.exec.ExecutionContext
import com.rthqks.synapse.exec.NodeExecutor
import com.rthqks.synapse.exec.link.AudioData
import com.rthqks.synapse.logic.NodeDef.Microphone
import com.rthqks.synapse.logic.NodeDef.Microphone.AudioChannel
import com.rthqks.synapse.logic.NodeDef.Microphone.AudioEncoding
import com.rthqks.synapse.logic.NodeDef.Microphone.AudioSampleRate
import com.rthqks.synapse.logic.NodeDef.Microphone.AudioSource
import com.rthqks.synapse.logic.Properties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private var primed = false

    override suspend fun onSetup() {
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

    override suspend fun onPause() {
        onStop()
    }

    override suspend fun onResume() {
        maybeStart()
    }

    override suspend fun <T> onConnect(key: Connection.Key<T>, producer: Boolean) {
        if (!primed) {
            primed = true
            connection(OUTPUT)?.let { con ->
                repeat(8) {
                    val item = AudioData(audioFormat)
                    item.buffer =
                        ByteBuffer.allocateDirect(bufferSize).order(ByteOrder.nativeOrder())
                    con.prime(item)
                }
            }
        }

        maybeStart()
    }

    private fun maybeStart() {
        if (isResumed && recordJob == null && connected(OUTPUT)) {
            recordJob = scope.launch {
                onStart()
            }
        }
    }

    override suspend fun <T> onDisconnect(key: Connection.Key<T>, producer: Boolean) {

    }

    private suspend fun onStart() {
        val output = channel(OUTPUT) ?: return
        running = true
        recorder.startRecording()

        var bytesWritten = 0L
        while (running) {
//                Log.d(TAG, "1")
            val message = output.receive()
            val data = message.data
//                Log.d(TAG, "2")
            data.buffer.position(0)
            withContext(Dispatchers.IO) {
                val read = recorder.read(
                    data.buffer,
                    data.buffer.capacity(),
                    AudioRecord.READ_BLOCKING
                )
//                Log.d(TAG, "3")
                data.buffer.limit(read)
                bytesWritten += read
                message.timestamp = ((bytesWritten / bytesPerFrame) * frameDurationNs) / 1000
            }
            message.queue()
        }
    }

    private suspend fun onStop() {
        Log.d(TAG, "stop")
        running = false
        Log.d(TAG, "stop running = false")
        recordJob?.join()
        recordJob = null
        Log.d(TAG, "stop join")
        recorder.stop()
    }

    override suspend fun onRelease() {
        recorder.release()
    }

    private fun getBytesPerSample(audioFormat: Int): Int {
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
        val OUTPUT = Connection.Key<AudioData>(Microphone.OUTPUT.key)
    }
}