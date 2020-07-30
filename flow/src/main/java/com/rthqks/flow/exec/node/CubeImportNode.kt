package com.rthqks.flow.exec.node

import android.net.Uri
import android.opengl.GLES30
import android.util.Log
import com.rthqks.flow.exec.Connection
import com.rthqks.flow.exec.ExecutionContext
import com.rthqks.flow.exec.NodeExecutor
import com.rthqks.flow.exec.Properties
import com.rthqks.flow.gl.Texture3d
import com.rthqks.flow.logic.NodeDef.CubeImport
import com.rthqks.flow.logic.NodeDef.CubeImport.LutUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.nio.ByteBuffer

class CubeImportNode(
    context: ExecutionContext,
    private val properties: Properties
) : NodeExecutor(context) {
    private val glesManager = context.glesManager
    private var startJob: Job? = null
    private var needsPriming = true

    private val cubeUri: Uri get() = properties[LutUri]
    private var size = Triple(0, 0, 0)

    private var texture: Texture3d? = null

    private fun getInputStream(): InputStream? {
        return when (cubeUri.scheme) {
            "assets" -> context.context.assets.open(cubeUri.pathSegments.joinToString("/"))
            else -> context.context.contentResolver.openInputStream(cubeUri)
        }
    }

    override suspend fun onSetup() {
        val texture = Texture3d()
        this.texture = texture
        glesManager.glContext {
            texture.initialize()
        }
        loadCubeFile()
    }

    suspend fun loadCubeFile() {
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

//                    Log.d(TAG, "rgb($r, $g, $b)")
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

        withContext(Dispatchers.IO) {
            stream.close()
        }

        size = Triple(cube.n, cube.n, cube.n)
        Log.d(TAG, "options $size ${buffer?.position()}")

        buffer?.position(0)

        glesManager.glContext {
            texture?.initData(
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
    }

    override suspend fun <T> onConnect(key: Connection.Key<T>, producer: Boolean) {
        when (key) {
            OUTPUT -> if (startJob == null) {
                if (needsPriming) {
                    needsPriming = false
                    texture?.let { connection(OUTPUT)?.prime(it, it) }
                }
                startJob = scope.launch {
                    sendMessage()
                }
            }
        }
    }

    override suspend fun <T> onDisconnect(key: Connection.Key<T>, producer: Boolean) {
        if (key == OUTPUT && !linked(OUTPUT)) {
            onStop()
        }
    }

    suspend fun sendMessage() {
        Log.d(TAG, "onStart")
        val channel = channel(OUTPUT) ?: error("missing output")
        channel.receive().also {
            Log.d(TAG, "sending event $it")
            it.queue()
        }
    }

    private suspend fun onStop() {
        startJob?.join()
        startJob = null
    }

    override suspend fun onRelease() {
        glesManager.glContext {
            texture?.release()
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
        val OUTPUT = Connection.Key<Texture3d>(CubeImport.OUTPUT.key)
    }
}

private class Cube {
    var n = 0
    var is3d = false
    val min = floatArrayOf(0f, 0f, 0f)
    val max = floatArrayOf(1f, 1f, 1f)

    fun scale(v: Float, i: Int) = (v - min[i]) / (max[i] - min[i])
}