package xyz.rthqks.synapse.exec.edge

import android.media.AudioFormat

data class AudioConfig(
    val audioFormat: AudioFormat,
    val audioBufferSize: Int
) : Config