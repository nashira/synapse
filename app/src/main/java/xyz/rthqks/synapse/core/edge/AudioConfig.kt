package xyz.rthqks.synapse.core.edge

import android.media.AudioFormat

data class AudioConfig(
    val audioFormat: AudioFormat,
    val audioBufferSize: Int
) : Config