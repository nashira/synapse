package com.rthqks.synapse.exec2.node

import android.net.Uri
import android.opengl.GLES30
import android.util.Log
import com.rthqks.synapse.exec.ExecutionContext
import com.rthqks.synapse.exec2.Connection
import com.rthqks.synapse.exec2.NodeExecutor
import com.rthqks.synapse.gl.Texture3d
import com.rthqks.synapse.logic.LutUri
import com.rthqks.synapse.logic.Properties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.InputStream
import java.nio.ByteBuffer

class BCubeImportNode(
    context: ExecutionContext,
    private val properties: Properties
) : NodeExecutor(context) {
    private val glesManager = context.glesManager
    private var startJob: Job? = null
    private var needsPriming = true

    private val cubeUri: Uri get() = properties[LutUri]
    private var size = Triple(0, 0, 0)

    private var texture = Texture3d()

    private fun getInputStream(): InputStream? {
        return when (cubeUri.scheme) {
            "assets" -> context.context.assets.open(cubeUri.pathSegments.joinToString("/"))
            else -> context.context.contentResolver.openInputStream(cubeUri)
        }
    }

    override suspend fun onSetup() {
        glesManager.glContext {
            texture.initialize()
        }
        loadCubeFile()
    }

    suspend fun loadCubeFile() {
        val stream = getInputStream() ?: return
        val reader = DataInputStream(stream)

        val buffer = withContext(Dispatchers.IO) {
            val dimen = reader.readInt()
            size = Triple(dimen, dimen, dimen)
            val bytes = ByteArray(dimen * dimen * dimen * 3)
            reader.readFully(bytes)
            reader.close()
            ByteBuffer.allocateDirect(bytes.size).apply {
                put(bytes)
            }
        }

        Log.d(TAG, "options $size ${buffer.position()}")

        buffer.position(0)

        glesManager.glContext {
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
    }

    override suspend fun <T> onConnect(key: Connection.Key<T>, producer: Boolean) {
        when (key) {
            OUTPUT -> if (startJob == null) {
                if (needsPriming) {
                    needsPriming = false
                    connection(OUTPUT)?.prime(texture, texture)
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
            texture.release()
        }
    }

    companion object {
        const val TAG = "BCubeImportNode"
        val OUTPUT = Connection.Key<Texture3d>("output_lut")
    }
}