package xyz.rthqks.synapse.core

import android.media.AudioFormat
import android.media.AudioRecord

class AudioSourceNode(
    private val sampleRate: Int,
    private val channelMask: Int,
    private val audioEncoding: Int,
    private val source: Int
) : Node() {

    private lateinit var recorder: AudioRecord

    override fun initialize() {
        val minBuffSize = AudioRecord.getMinBufferSize(
            sampleRate,
            channelMask,
            audioEncoding
        )

        recorder = AudioRecord.Builder()
            .setAudioSource(source)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(audioEncoding)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelMask)
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