package com.rthqks.synapse.exec.link

import java.nio.ByteBuffer

class AudioEvent : Event() {
    var index = -1
    var session = -1
    lateinit var buffer: ByteBuffer
}