package com.rthqks.flow.exec_dep.node

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.util.Log
import com.rthqks.flow.exec.ExecutionContext
import com.rthqks.flow.exec_dep.NodeExecutor
import com.rthqks.flow.exec_dep.link.AudioConfig
import com.rthqks.flow.exec_dep.link.AudioEvent
import com.rthqks.flow.exec_dep.link.Connection
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class AudioPlayerNode(context: ExecutionContext) : NodeExecutor(context) {
    private var audioTrack: AudioTrack? = null
    private var audioFormat: AudioFormat? = null
    private var playJob: Job? = null

    override suspend fun onCreate() {
    }

    override suspend fun onInitialize() {
        audioFormat = config(INPUT)?.audioFormat

        audioFormat?.let {
            val bufferSize = AudioRecord.getMinBufferSize(
                it.sampleRate,
                AudioFormat.CHANNEL_IN_STEREO,
                it.encoding
            )
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                ).setAudioFormat(it)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(bufferSize * 2)
                .build()
        }
    }

    override suspend fun onStart() {
        val channel = channel(INPUT) ?: return
        var running = true
        audioTrack?.play()

        playJob = scope.launch {
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
                audioBuffer.release()
            }
            Log.d(TAG, "wrote frames $numFrames")
        }
    }

    override suspend fun onStop() {
        playJob?.join()
        audioTrack?.stop()
    }

    override suspend fun onRelease() {
        audioTrack?.release()
    }

    companion object {
        const val TAG = "AudioPlayerNode"
        val INPUT = Connection.Key<AudioConfig, AudioEvent>("audio_1")
    }
}