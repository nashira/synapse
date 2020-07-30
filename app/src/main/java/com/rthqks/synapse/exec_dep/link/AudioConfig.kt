package com.rthqks.flow.exec_dep.link

import android.media.AudioFormat

data class AudioConfig(
    val audioFormat: AudioFormat,
    val audioBufferSize: Int
) : Config