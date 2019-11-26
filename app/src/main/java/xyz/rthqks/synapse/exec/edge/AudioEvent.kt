package xyz.rthqks.synapse.exec.edge

import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioEvent(bufferSize: Int) : Event() {
    var index = -1
    var session = -1
    var frame = 0
    var eos = false
    var buffer: ByteBuffer = ByteBuffer.allocateDirect(bufferSize).order(ByteOrder.nativeOrder())
}