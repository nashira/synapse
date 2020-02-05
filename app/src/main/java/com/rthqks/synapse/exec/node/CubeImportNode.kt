package com.rthqks.synapse.exec.node

import android.content.Context
import android.net.Uri
import android.opengl.GLES30
import android.util.Log
import com.rthqks.synapse.exec.NodeExecutor
import com.rthqks.synapse.exec.link.*
import com.rthqks.synapse.gl.GlesManager
import com.rthqks.synapse.gl.Texture3d
import com.rthqks.synapse.logic.MediaUri
import com.rthqks.synapse.logic.Properties
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.io.InputStream
import java.nio.ByteBuffer

class CubeImportNode(
    private val context: Context,
    private val glesManager: GlesManager,
    private val properties: Properties
) : NodeExecutor() {

    private var startJob: Job? = null
    private val cubeUri: Uri get() = properties[MediaUri]
    private var size = Triple(0, 0, 0)
    private var frameCount = 0

    private var texture: Texture3d? = null

    private fun getInputStream(): InputStream? {
        return when (cubeUri.scheme) {
            "assets" -> context.assets.open(cubeUri.pathSegments.joinToString("/"))
            else -> context.contentResolver.openInputStream(cubeUri)
        }
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    override suspend fun create() {
        val stream = getInputStream() ?: return
        val reader = stream.bufferedReader()
        val cube = Cube()

        var buffer: ByteBuffer? = null

        reader.forEachLine { line ->
            when {
                line matches TABLE_DATA -> TABLE_DATA.find(line)?.let {
                    if (buffer == null) {
                        buffer = ByteBuffer.allocateDirect(cube.n * cube.n * cube.n * 3)
                    }
                    val r = it.groupValues[1].toFloat()
                    val g = it.groupValues[2].toFloat()
                    val b = it.groupValues[3].toFloat()

                    buffer?.put((cube.scale(r, 0) * 255).toByte())
                    buffer?.put((cube.scale(g, 1) * 255).toByte())
                    buffer?.put((cube.scale(b, 2) * 255).toByte())
                }
                line matches DOMAIN_MIN -> DOMAIN_MIN.find(line)?.let {
                    cube.min[0] = it.groupValues[1].toFloat()
                    cube.min[1] = it.groupValues[2].toFloat()
                    cube.min[2] = it.groupValues[3].toFloat()
                }
                line matches DOMAIN_MAX -> DOMAIN_MAX.find(line)?.let {
                    cube.max[0] = it.groupValues[1].toFloat()
                    cube.max[1] = it.groupValues[2].toFloat()
                    cube.max[2] = it.groupValues[3].toFloat()
                }
                line matches LUT_1D_PATTERN -> LUT_1D_PATTERN.find(line)?.let {
                    Log.d(TAG, "found 1D lut ${it.groupValues[1]}")
                    cube.n = it.groupValues[1].toInt()
                }
                line matches LUT_3D_PATTERN -> LUT_3D_PATTERN.find(line)?.let {
                    Log.d(TAG, "found 3D lut ${it.groupValues[1]}")
                    cube.n = it.groupValues[1].toInt()
                    cube.is3d = true
                }
            }
        }
        stream.close()
        size = Triple(cube.n, cube.n, cube.n)
        Log.d(TAG, "options $size")

        buffer?.position(0)

        val texture = Texture3d()
        glesManager.glContext {
            texture.initialize()
            texture.initData(
                0,
                GLES30.GL_RGB8,
                size.first,
                size.second,
                size.third,
                GLES30.GL_RGB,
                GLES30.GL_UNSIGNED_BYTE,
                buffer
            )
        }
        this.texture = texture
    }

    override suspend fun initialize() {
        val connection = connection(OUTPUT) ?: return
//        val inputStream = getInputStream() ?: return

        texture?.let {
            val item1 = Texture3dEvent(it)
            val item2 = Texture3dEvent(it)
            connection.prime(item1, item2)
        }
    }

    override suspend fun start() = coroutineScope {
        val channel = channel(OUTPUT) ?: return@coroutineScope
        startJob = launch {
            channel.receive().also {
                Log.d(TAG, "sending event $it")
                it.eos = false
                it.count == ++frameCount
                channel.send(it)
            }
        }
    }

    override suspend fun stop() {
        val channel = channel(OUTPUT) ?: return
        startJob?.join()
        channel.receive().also {
            it.eos = true
            it.count = frameCount
            channel.send(it)
        }
    }

    override suspend fun release() {
        glesManager.glContext {
            texture?.release()
        }
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun <C : Config, E : Event> makeConfig(key: Connection.Key<C, E>): C {
        return when (key) {
            OUTPUT -> {
                Texture3dConfig(
                    size.first,
                    size.second,
                    size.third,
                    GLES30.GL_RGB8,
                    GLES30.GL_RGB,
                    GLES30.GL_UNSIGNED_BYTE
                ) as C
            }
            else -> error("unknown key $key")
        }
    }

    companion object {
        const val TAG = "CubeImportNode"
        val LUT_1D_PATTERN = Regex("^LUT_1D_SIZE\\s+(\\d+)")
        val LUT_3D_PATTERN = Regex("^LUT_3D_SIZE\\s+(\\d+)")
        val NUMBER = "[-+]?[0-9]*\\.?[0-9]+(?:[eE][-+]?[0-9]+)?"
        val TABLE_DATA = Regex("^($NUMBER)\\s+($NUMBER)\\s+($NUMBER)")
        val DOMAIN_MIN = Regex("^DOMAIN_MIN\\s+([^\\s]+)\\s+([^\\s]+)\\s+([^\\s]+)")
        val DOMAIN_MAX = Regex("^DOMAIN_MAX\\s+([^\\s]+)\\s+([^\\s]+)\\s+([^\\s]+)")
        val OUTPUT = Connection.Key<Texture3dConfig, Texture3dEvent>("output_lut")
    }
}

private class Cube {
    var n = 0
    var is3d = false
    val min = floatArrayOf(0f, 0f, 0f)
    val max = floatArrayOf(1f, 1f, 1f)

    fun scale(v: Float, i: Int) = (v - min[i]) / (max[i] - min[i])
}