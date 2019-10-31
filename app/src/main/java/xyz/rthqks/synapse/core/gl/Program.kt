package xyz.rthqks.synapse.core.gl

import android.opengl.GLES32
import android.util.Log

class Program(
    private val glesManager: GlesManager,
    val programId: Int
) {
    private val uniforms = mutableMapOf<String, Uniform<*>>()
    private val textures = mutableMapOf<String, Texture>()

    fun initialize() {

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

    fun addTexture(name: String, unit: Int, target: Int, repeat: Int, filter: Int) {
        val id = glesManager.createTexture(target, repeat, filter)
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
        textures.forEach {
            val t = it.value
            glesManager.releaseTexture(t.id)
        }
        glesManager.releaseProgram(programId)

    }

    companion object {
        private val TAG = Program::class.java.simpleName
    }
}
