package com.rthqks.synapse.gl

import android.opengl.GLES30
import android.util.Log

class Program {
    private val uniforms = mutableMapOf<String, Uniform<*>>()
    private val uniformList = mutableListOf<Uniform<*>>()
    var programId: Int = 0
        private set

    fun initialize(vertex: String, fragment: String) {
        val vertexShader: Int = createShader(GLES30.GL_VERTEX_SHADER, vertex)
        val fragmentShader: Int = createShader(GLES30.GL_FRAGMENT_SHADER, fragment)

        programId = GLES30.glCreateProgram().also {
            GLES30.glAttachShader(it, vertexShader)
            GLES30.glAttachShader(it, fragmentShader)
            GLES30.glLinkProgram(it)
            val linkStatus = IntArray(1)
            GLES30.glGetProgramiv(it, GLES30.GL_LINK_STATUS, linkStatus, 0)
            GLES30.glDetachShader(it, vertexShader)
            GLES30.glDetachShader(it, fragmentShader)
            GLES30.glDeleteShader(vertexShader)
            GLES30.glDeleteShader(fragmentShader)
            if (linkStatus[0] != GLES30.GL_TRUE) {
                Log.e(TAG, "Could not link program: ")
                Log.e(TAG, GLES30.glGetProgramInfoLog(it))
                throw RuntimeException("Error creating program")
            }
        }

//        Log.d(TAG, "created program: $programId")
    }

    fun <T> addUniform(type: Uniform.Type<T>, name: String, data: T) {
        val location = GLES30.glGetUniformLocation(programId, name)
//        Log.d(TAG, "add uniform $name $location")
        val uniform = Uniform(type, name, location, data)
        uniforms[name] = uniform
        uniformList.add(uniform)
    }

    fun <T> getUniform(type: Uniform.Type<T>, name: String): Uniform<T> {
        @Suppress("UNCHECKED_CAST")
        return uniforms[name] as Uniform<T>
    }

    fun <T> getUniformOrNull(type: Uniform.Type<T>, name: String): Uniform<T>? {
        @Suppress("UNCHECKED_CAST")
        return uniforms[name] as? Uniform<T>
    }

    fun bindUniforms() {
        uniformList.forEach { uniform ->
            if (uniform.dirty) {
//                Log.d(TAG, "binding uniform $programId ${uniform.name} ${uniform.data}")
                uniform.dirty = false
                when (uniform.type) {
                    Uniform.Type.Int -> GLES30.glUniform1i(
                        uniform.location,
                        uniform.data as Int
                    )
                    Uniform.Type.Float -> GLES30.glUniform1f(
                        uniform.location,
                        uniform.data as Float
                    )
                    Uniform.Type.Mat4 -> GLES30.glUniformMatrix4fv(
                        uniform.location,
                        1,
                        false,
                        uniform.data as FloatArray,
                        0
                    )
                    Uniform.Type.Vec2 -> GLES30.glUniform2fv(
                        uniform.location,
                        1,
                        uniform.data as FloatArray,
                        0
                    )
                    Uniform.Type.Vec3 -> GLES30.glUniform3fv(
                        uniform.location,
                        1,
                        uniform.data as FloatArray,
                        0
                    )
                }

            }
        }
    }

    fun release() {
        GLES30.glDeleteProgram(programId)
        uniforms.clear()
        uniformList.clear()
//        Log.d(TAG, "release program: $programId")
    }

    private fun createShader(type: Int, source: String): Int =
        GLES30.glCreateShader(type).also { shader ->
//            Log.d(TAG, "created shader $shader")
            GLES30.glShaderSource(shader, source)
            GLES30.glCompileShader(shader)
            Log.d(TAG, GLES30.glGetShaderInfoLog(shader))
        }

    companion object {
        private val TAG = Program::class.java.simpleName
    }
}
