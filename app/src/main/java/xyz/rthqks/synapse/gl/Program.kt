package xyz.rthqks.synapse.gl

import android.opengl.GLES32
import android.util.Log

class Program {
    private val uniforms = mutableMapOf<String, Uniform<*>>()
    private val uniformList = mutableListOf<Uniform<*>>()
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
        val uniform = Uniform(type, name, location, data)
        uniforms[name] = uniform
        uniformList.add(uniform)
    }

    fun <T> getUniform(type: Uniform.Type<T>, name: String): Uniform<T> {
        @Suppress("UNCHECKED_CAST")
        return uniforms[name] as Uniform<T>
    }

    fun bindUniforms() {
        uniformList.forEach { uniform ->
            if (uniform.dirty) {
//                Log.d(TAG, "binding uniform $programId ${uniform.name} ${uniform.data}")
                uniform.dirty = false
                when (uniform.type) {
                    Uniform.Type.Int -> GLES32.glUniform1i(
                        uniform.location,
                        uniform.data as Int
                    )
                    Uniform.Type.Float -> GLES32.glUniform1f(
                        uniform.location,
                        uniform.data as Float
                    )
                    Uniform.Type.Mat4 -> GLES32.glUniformMatrix4fv(
                        uniform.location,
                        1,
                        false,
                        uniform.data as FloatArray,
                        0
                    )
                    Uniform.Type.Vec2 -> GLES32.glUniform2fv(
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
        GLES32.glDeleteProgram(programId)

    }

    private fun createShader(type: Int, source: String): Int =
        GLES32.glCreateShader(type).also { shader ->
            Log.d(TAG, "created shader $shader")
            GLES32.glShaderSource(shader, source)
            GLES32.glCompileShader(shader)
            Log.d(TAG, GLES32.glGetShaderInfoLog(shader))
        }

    companion object {
        private val TAG = Program::class.java.simpleName
    }
}
