package com.rthqks.synapse.exec.link

import android.media.AudioFormat

data class AudioConfig(
    val audioFormat: AudioFormat,
    val audioBufferSize: Int
) : Config