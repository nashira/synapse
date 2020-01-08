package com.rthqks.synapse.gl

import android.opengl.GLES32.*
import android.util.Log

abstract class Mesh(
    val operation: Int,
    val mode: Int,
    val start: Int,
    val count: Int,
    val elementType: Int = GL_UNSIGNED_SHORT,
    var instances: Int = 0
) {
    var vaoId = 0
        private set
    private val buffers = mutableMapOf<String, Buffer>()
    private val buffersById = mutableMapOf<Int, Buffer>()
    private val attributes = mutableMapOf<String, Attribute>()

    open fun initialize() {
        val vao = IntArray(1)
        glGenVertexArrays(1, vao, 0)
        glBindVertexArray(vao[0])
        vaoId = vao[0]
        Log.d(TAG, "created vao $vaoId")

        attributes.forEach {
            val attribute = it.value
            val buffer = buffersById[attribute.bufferId]!!

            glBindBuffer(buffer.target, buffer.id)
            glEnableVertexAttribArray(attribute.index)
            glVertexAttribPointer(
                attribute.index,
                attribute.size,
                attribute.type,
                attribute.normalized,
                attribute.stride,
                attribute.offset
            )
        }
        glBindVertexArray(0)
    }

    open fun execute() {
        glBindVertexArray(vaoId)
        when (operation) {
            DRAW_ARRAYS -> glDrawArrays(mode, start, count)
            DRAW_ELEMENTS -> glDrawElements(mode, count, elementType, start)
        }
        glBindVertexArray(0)
    }

    open fun release() {
        val ids = buffersById.keys.toIntArray()
        glDeleteBuffers(ids.size, ids, 0)
        glDeleteVertexArrays(1, intArrayOf(vaoId), 0)
    }

    fun addBuffer(name: String, data: java.nio.Buffer, target: Int, usage: Int): Buffer {
        val bufferId = IntArray(1)
        glGenBuffers(1, bufferId, 0)
        val buffer = Buffer(data, target, usage, bufferId[0])
        buffers[name] = buffer
        buffersById[buffer.id] = buffer
        Log.d(TAG, "addBuffer $buffer")
        initBufferData(buffer, data.capacity())
        return buffer
    }

    fun initBufferData(buffer: Buffer, size: Int) {
        glBindBuffer(buffer.target, buffer.id)
        glBufferData(
            buffer.target,
            size,
            buffer.data,
            buffer.usage
        )
        glBindBuffer(buffer.target, 0)
    }

    fun updateBufferData(buffer: Buffer, offset: Int, size: Int) {
        glBindBuffer(buffer.target, buffer.id)
        glBufferSubData(
            buffer.target,
            offset,
            size,
            buffer.data
        )
        glBindBuffer(buffer.target, 0)
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
        const val TAG = "Mesh"
    }
}

data class Buffer(
    val data: java.nio.Buffer,
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