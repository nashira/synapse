package xyz.rthqks.synapse.core.node

import android.graphics.PixelFormat
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlinx.coroutines.*
import xyz.rthqks.synapse.core.Connection
import xyz.rthqks.synapse.core.Node
import xyz.rthqks.synapse.core.edge.SurfaceConnection
import xyz.rthqks.synapse.data.PortType
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class SurfaceViewNode(
    private val surfaceView: SurfaceView
) : Node() {
    private lateinit var connection: SurfaceConnection
    private var surface: Surface? = null
    private var surfaceContinuation: Continuation<Surface>? = null
    private var running: Boolean = false
    private var playJob: Job? = null

    override suspend fun initialize() {
        Log.d(TAG, "setting format")
        surfaceView.holder.setFormat(PixelFormat.RGB_888)

        Log.d(TAG, "adding callback ${surfaceView.holder.surface}")
        surface = surfaceView.holder.surface

        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceChanged(
                holder: SurfaceHolder?,
                format: Int,
                width: Int,
                height: Int
            ) {
                Log.d(TAG, "surfaceChanged: $holder $format $width $height")
                surface = holder!!.surface
                surfaceContinuation?.resume(surface!!)
                surfaceContinuation = null
            }

            override fun surfaceDestroyed(holder: SurfaceHolder?) {
                surface = null
                Log.d(TAG, "surfaceDestroyed: $holder")
            }

            override fun surfaceCreated(holder: SurfaceHolder?) {
                Log.d(TAG, "surfaceCreated: $holder")
            }
        })
    }

    override suspend fun start() = coroutineScope {
        playJob = launch {
            val connection = connection

            running = true
            var numFrames = 0
            while (running) {
                val surfaceEvent = connection.acquire()
                if (surfaceEvent.eos) {
                    Log.d(TAG, "got EOS")
                    running = false
                } else {
                    numFrames++
//                    Log.d(TAG, "written $write frames $numFrames")

                }
                connection.release(surfaceEvent)
            }
            Log.d(TAG, "wrote frames $numFrames")
        }
    }

    override suspend fun stop() {
        playJob?.join()
    }

    override suspend fun release() {

    }

    override suspend fun output(key: String): Connection<*>? {
        throw IllegalStateException("$TAG has no outputs")
    }

    override suspend fun <T> input(key: String, connection: Connection<T>) {
        when (key) {
            PortType.SURFACE_1 -> {
                this.connection = connection as SurfaceConnection
                withContext(Dispatchers.Main) {
                    surfaceView.holder.setFixedSize(connection.size.width, connection.size.height)
                }
                connection.setSurface(ensureSurface())
            }
        }
    }

    private suspend fun ensureSurface(): Surface {
        Log.d(TAG, "ensureSurface $surface")
        return surface?.let { it } ?: suspendCoroutine {
            Log.d(TAG, "ensureSurface $surface")
            surfaceContinuation = it
        }
    }

    companion object {
        private val TAG = SurfaceViewNode::class.java.simpleName
    }
}

/*
pass surface into execVM -> graph -> surfaceviewnode
listen to surfaceholder events

 */