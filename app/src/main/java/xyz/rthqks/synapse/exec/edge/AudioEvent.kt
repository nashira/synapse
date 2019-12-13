package xyz.rthqks.synapse.exec.edge

import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioEvent : Event() {
    var index = -1
    var session = -1
    lateinit var buffer: ByteBuffer
}