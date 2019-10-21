package xyz.rthqks.synapse.core.edge

import java.nio.ByteBuffer

class AudioBuffer(bufferSize: Int) {
    var frame = 0
    var eos = false
    var buffer: ByteBuffer = ByteBuffer.allocateDirect(bufferSize)
}