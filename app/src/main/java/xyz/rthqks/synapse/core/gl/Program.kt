package xyz.rthqks.synapse.core.gl

import android.opengl.GLES32
import android.util.Log

class Program(
    val programId: Int
) {
    private val uniforms = mutableMapOf<String, Uniform<*>>()

    fun initialize() {

    }

    fun <T> addUniform(type: Uniform.Type<T>, name: String, data: T) {
        val location = GLES32.glGetUniformLocation(programId, name)
        Log.d(TAG, "add uniform $name $location")
        uniforms[name] = Uniform(type, name, location, data)
    }

    fun <T> setUniform(type: Uniform.Type<T>, name: String, data: T?) {
        @Suppress("UNCHECKED_CAST")
        val uniform = uniforms[name] as Uniform<T>
        uniform.data = data
        uniform.dirty = true
    }

    fun markDirty(name: String) {
        uniforms[name]?.dirty = true
    }

    fun <T> getUniform(type: Uniform.Type<T>, name: String): T? {
        @Suppress("UNCHECKED_CAST")
        val uniform = uniforms[name] as Uniform<T>
        return uniform.data
    }

    fun bindUniforms() {
        uniforms.forEach {
            val uniform = it.value
            if (uniform.dirty) {
//                Log.d(TAG, "binding uniform $programId ${uniform.name} ${uniform.data}")
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

    companion object {
        private val TAG = Program::class.java.simpleName
    }
}
