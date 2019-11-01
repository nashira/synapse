package xyz.rthqks.synapse.core.gl

import android.opengl.GLES32
import android.util.Log
import java.nio.ByteBuffer

class Program {
    private val uniforms = mutableMapOf<String, Uniform<*>>()
    private val textures = mutableMapOf<String, Texture>()
    var programId: Int = 0
        private set

    fun initialize(vertex: String, fragment: String) {
        val vertexShader: Int = createShader(GLES32.GL_VERTEX_SHADER, vertex)
        val fragmentShader: Int = createShader(GLES32.GL_FRAGMENT_SHADER, fragment)

        programId = GLES32.glCreateProgram().also {
            GLES32.glAttachShader(it, vertexShader)
            GLES32.glAttachShader(it, fragmentShader)
            GLES32.glLinkProgram(it)
            val linkStatus = IntArray(1)
            GLES32.glGetProgramiv(it, GLES32.GL_LINK_STATUS, linkStatus, 0)
            if (linkStatus[0] != GLES32.GL_TRUE) {
                Log.e(TAG, "Could not link program: ")
                Log.e(TAG, GLES32.glGetProgramInfoLog(it))
            }
            GLES32.glDeleteShader(vertexShader)
            GLES32.glDeleteShader(fragmentShader)
        }

        Log.d(TAG, "created program: $programId")
    }

    fun <T> addUniform(type: Uniform.Type<T>, name: String, data: T) {
        val location = GLES32.glGetUniformLocation(programId, name)
        Log.d(TAG, "add uniform $name $location")
        uniforms[name] = Uniform(type, name, location, data)
    }

    fun <T> getUniform(type: Uniform.Type<T>, name: String): Uniform<T> {
        @Suppress("UNCHECKED_CAST")
        return uniforms[name] as Uniform<T>
    }

    fun bindUniforms() {
        uniforms.forEach {
            val uniform = it.value
            if (uniform.dirty) {
                Log.d(TAG, "binding uniform $programId ${uniform.name} ${uniform.data}")
                uniform.dirty = false
                when (uniform.type) {
                    Uniform.Type.Integer -> GLES32.glUniform1i(
                        uniform.location,
                        uniform.data as Int
                    )
                    Uniform.Type.Float -> TODO()
                    Uniform.Type.Mat4 -> GLES32.glUniformMatrix4fv(
                        uniform.location,
                        1,
                        false,
                        uniform.data as FloatArray,
                        0
                    )
                }

            }
        }
    }

    fun addTexture(name: String, unit: Int, target: Int, repeat: Int, filter: Int) {
        val id = createTexture(target, repeat, filter)
        val texture = Texture(id, target, unit)
        textures[name] = texture
        addUniform(Uniform.Type.Integer, name, GLES32.GL_TEXTURE0 - unit)
    }

    fun getTexture(name: String) = textures[name]!!

    fun bindTextures() {
        textures.forEach {
            val texture = it.value
            GLES32.glActiveTexture(texture.unit)
            GLES32.glBindTexture(texture.target, texture.id)
        }
    }

    fun release() {
        val ts = textures.map { it.value.id }.toIntArray()
        GLES32.glDeleteTextures(ts.size, ts, 0)
        GLES32.glDeleteProgram(programId)

    }

    private fun createTexture(
        target: Int = GLES32.GL_TEXTURE_2D,
        repeat: Int = GLES32.GL_REPEAT,
        filter: Int = GLES32.GL_LINEAR
    ): Int {
        val textureHandle = IntArray(1)

        GLES32.glGenTextures(1, textureHandle, 0)

        if (textureHandle[0] != 0) {

            GLES32.glBindTexture(target, textureHandle[0])

            GLES32.glTexParameteri(target, GLES32.GL_TEXTURE_MIN_FILTER, filter)
            GLES32.glTexParameteri(target, GLES32.GL_TEXTURE_MAG_FILTER, filter)

            GLES32.glTexParameterf(target, GLES32.GL_TEXTURE_WRAP_S, repeat.toFloat())
            GLES32.glTexParameterf(target, GLES32.GL_TEXTURE_WRAP_T, repeat.toFloat())

            GLES32.glBindTexture(target, 0)
        } else {
            throw RuntimeException("Error creating texture.")
        }

        return textureHandle[0]
    }

    private fun createShader(type: Int, source: String): Int =
        GLES32.glCreateShader(type).also { shader ->
            Log.d(TAG, "created shader $shader")
            GLES32.glShaderSource(shader, source)
            GLES32.glCompileShader(shader)
            Log.d(TAG, GLES32.glGetShaderInfoLog(shader))
        }

    fun initTextureData(
        name: String,
        level: Int,
        internalFormat: Int,
        width: Int,
        height: Int,
        format: Int,
        type: Int,
        buffer: ByteBuffer?
    ) {

        val texture = textures[name]!!
        GLES32.glActiveTexture(texture.unit)
        GLES32.glBindTexture(texture.target, texture.id)
        GLES32.glTexImage2D(
            texture.target,
            level,
            internalFormat,
            width,
            height,
            0,
            format,
            type,
            buffer
        )
        GLES32.glBindTexture(texture.target, 0)
    }

    fun updateTextureData(
        name: String,
        level: Int,
        xoffset: Int,
        yoffset: Int,
        width: Int,
        height: Int,
        format: Int,
        type: Int,
        buffer: ByteBuffer
    ) {
        val texture = textures[name]!!
        GLES32.glActiveTexture(texture.unit)
        GLES32.glBindTexture(texture.target, texture.id)
        GLES32.glTexSubImage2D(
            texture.target,
            level,
            xoffset,
            yoffset,
            width,
            height,
            format,
            type,
            buffer
        )
        GLES32.glBindTexture(texture.target, 0)
    }

    companion object {
        private val TAG = Program::class.java.simpleName
    }
}
