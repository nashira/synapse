package xyz.rthqks.proc.core

import android.media.AudioFormat
import android.media.AudioRecord

class AudioSourceNode(audioSourceConfig: AudioSourceConfig) : Node() {

    private val recorder: AudioRecord

    init {
        val minBuffSize = AudioRecord.getMinBufferSize(
            audioSourceConfig.sampleRate,
            audioSourceConfig.channelMask,
            audioSourceConfig.audioEncoding
        )

        recorder = AudioRecord.Builder()
            .setAudioSource(audioSourceConfig.source)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(audioSourceConfig.audioEncoding)
                    .setSampleRate(audioSourceConfig.sampleRate)
                    .setChannelMask(audioSourceConfig.channelMask)
                    .build()
            )
            .setBufferSizeInBytes(2 * minBuffSize)
            .build()
    }

    override fun start() {

    }

    override fun stop() {

    }

    override fun release() {
    }
}