package xyz.rthqks.synapse.core.edge

import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioEvent(bufferSize: Int) : Event() {
    var frame = 0
    var eos = false
    var buffer: ByteBuffer = ByteBuffer.allocateDirect(bufferSize).order(ByteOrder.nativeOrder())
}