package xyz.rthqks.synapse.core.gl

import android.opengl.GLES32
import android.util.Log
import java.nio.ByteBuffer

open class Mesh(
    val operation: Int,
    val mode: Int,
    val start: Int,
    val count: Int,
    val type: Int = GLES32.GL_UNSIGNED_SHORT,
    var instances: Int = 0
) {
    var vaoId = 0
        private set
    private val buffers = mutableMapOf<String, Buffer>()
    private val buffersById = mutableMapOf<Int, Buffer>()
    private val attributes = mutableMapOf<String, Attribute>()

    open fun initialize() {
        val vao = IntArray(1)
        GLES32.glGenVertexArrays(1, vao, 0)
        GLES32.glBindVertexArray(vao[0])
        vaoId = vao[0]
        Log.d(TAG, "created vao $vaoId")

        attributes.forEach {
            val attribute = it.value
            val buffer = buffersById[attribute.bufferId]!!

            GLES32.glBindBuffer(buffer.target, buffer.id)
            GLES32.glEnableVertexAttribArray(attribute.index)
            GLES32.glVertexAttribPointer(
                attribute.index,
                attribute.size,
                attribute.type,
                attribute.normalized,
                attribute.stride,
                attribute.offset
            )
        }
        GLES32.glBindVertexArray(0)
    }

    open fun execute() {
        GLES32.glBindVertexArray(vaoId)
        when (operation) {
            DRAW_ARRAYS -> GLES32.glDrawArrays(mode, start, count)
            DRAW_ELEMENTS -> GLES32.glDrawElements(mode, count, type, start)
        }
        GLES32.glBindVertexArray(0)
    }

    open fun release() {
        val ids = buffersById.keys.toIntArray()
        GLES32.glDeleteBuffers(ids.size, ids, 0)
        GLES32.glDeleteVertexArrays(1, intArrayOf(vaoId), 0)
    }

    fun addBuffer(name: String, data: ByteBuffer, target: Int, usage: Int): Buffer {
        val bufferId = IntArray(1)
        GLES32.glGenBuffers(1, bufferId, 0)
        val buffer = Buffer(data, target, usage, bufferId[0])
        buffers[name] = buffer
        buffersById[buffer.id] = buffer
        Log.d(TAG, "addBuffer $buffer")
        bufferData(buffer)
        return buffer
    }

    fun bufferData(buffer: Buffer) {
        buffer.data.position(0)
        GLES32.glBindBuffer(buffer.target, buffer.id)
        GLES32.glBufferData(
            buffer.target,
            buffer.data.capacity(),
            buffer.data,
            buffer.usage
        )
        GLES32.glBindBuffer(buffer.target, 0)
    }

    fun addAttribute(
        name: String,
        bufferId: Int,
        size: Int,
        type: Int,
        stride: Int,
        offset: Int,
        index: Int
    ): Attribute {
        val attribute = Attribute(bufferId, size, type, stride, offset, index)
        attributes[name] = attribute
        Log.d(TAG, "addAttribute $attribute")
        return attribute
    }

    companion object {
        const val DRAW_ARRAYS = 0
        const val DRAW_ELEMENTS = 1
        const val DRAW_ARRAYS_INSTANCED = 2
        const val DRAW_ELEMENTS_INSTANCED = 3
        private val TAG = Mesh::class.java.simpleName
    }
}

data class Buffer(
    val data: ByteBuffer,
    val target: Int,
    val usage: Int,
    val id: Int
)

data class Attribute(
    val bufferId: Int,
    val size: Int,
    val type: Int,
    val stride: Int,
    val offset: Int,
    val index: Int,
    val normalized: Boolean = false,
    val divisor: Int = -1
)