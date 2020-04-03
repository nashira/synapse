package com.rthqks.synapse.exec.link

import android.media.AudioFormat
import java.nio.ByteBuffer

class AudioData(
    var audioFormat: AudioFormat
) {
    var index = -1
    var session = -1
    var buffer: ByteBuffer = EMPTY_BUFFER
    companion object {
        private val EMPTY_BUFFER = ByteBuffer.allocateDirect(0)
    }
}