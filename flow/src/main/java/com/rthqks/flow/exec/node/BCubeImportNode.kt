package com.rthqks.flow.exec.node

import android.net.Uri
import android.opengl.GLES30
import com.rthqks.flow.exec.Connection
import com.rthqks.flow.exec.ExecutionContext
import com.rthqks.flow.exec.NodeExecutor
import com.rthqks.flow.exec.Properties
import com.rthqks.flow.gl.Texture3d
import com.rthqks.flow.logic.NodeDef.BCubeImport
import com.rthqks.flow.logic.NodeDef.BCubeImport.LutUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap

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

    override suspend fun onResume() {
        if (connected(OUTPUT)) {
            sendMessage()
        }
    }

    private suspend fun getBuffer(): ByteBuffer? {
        val buffer = cache.getOrPut(cubeUri) {
            val stream = getInputStream() ?: return null
            val reader = DataInputStream(stream)
            withContext(Dispatchers.IO) {
                val dimen = reader.readInt()
                size = Triple(dimen, dimen, dimen)
                val bytes = ByteArray(dimen * dimen * dimen * 3)
                reader.readFully(bytes)
                reader.close()
                val buffer = ByteBuffer.allocateDirect(bytes.size).apply {
                    put(bytes)
                }
                Pair(dimen, buffer)
            }
        }

        size = Triple(buffer.first, buffer.first, buffer.first)

//        Log.d(TAG, "options $size ${buffer.second.capacity()}")
        buffer.second.position(0)
        return buffer.second
    }

    suspend fun loadCubeFile() {
        val buffer = getBuffer() ?: return
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
            OUTPUT -> {
                if (needsPriming) {
                    needsPriming = false
                    connection(OUTPUT)?.prime(texture, texture)
                }
                if (startJob == null) {
                    startJob = scope.launch {
                        sendMessage()
                    }
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
//        Log.d(TAG, "onStart")
        val channel = channel(OUTPUT) ?: error("missing output")
        channel.receive().also {
//            Log.d(TAG, "sending event $it")
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
        private val cache = ConcurrentHashMap<Uri, Pair<Int, ByteBuffer>>()
        val OUTPUT = Connection.Key<Texture3d>(BCubeImport.OUTPUT.key)
    }
}