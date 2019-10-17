package xyz.rthqks.synapse.core.edge

import java.nio.ByteBuffer

class AudioBuffer(bufferSize: Int) {
    var eos = false
    var buffer = ByteBuffer.allocateDirect(bufferSize)
}