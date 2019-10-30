package xyz.rthqks.synapse.core.node

import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import kotlinx.coroutines.*
import xyz.rthqks.synapse.R
import xyz.rthqks.synapse.core.Connection
import xyz.rthqks.synapse.core.Node
import xyz.rthqks.synapse.core.edge.SurfaceConnection
import xyz.rthqks.synapse.data.PortType

class SurfaceViewNode(
    private val surfaceView: SurfaceView
) : Node() {
    private var connection: SurfaceConnection? = null
    private var surface: Surface? = null
    private var running: Boolean = false
    private var playJob: Job? = null

    override suspend fun initialize() {
        Log.d(TAG, "setting format")
//        surfaceView.holder.setFormat(PixelFormat.RGB_888)

        Log.d(TAG, "adding callback ${surfaceView.holder.surface}")

        setSurface(surfaceView.holder.surface)

        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceChanged(
                holder: SurfaceHolder?,
                format: Int,
                width: Int,
                height: Int
            ) {
                Log.d(TAG, "surfaceChanged: $holder $format $width $height")
                setSurface(holder!!.surface)
            }

            override fun surfaceDestroyed(holder: SurfaceHolder?) {
                Log.d(TAG, "surfaceDestroyed: $holder")
                setSurface(null)
            }

            override fun surfaceCreated(holder: SurfaceHolder?) {
                Log.d(TAG, "surfaceCreated: $holder")
            }
        })
    }

    override suspend fun start() = coroutineScope {
        val connection = connection ?: return@coroutineScope

        playJob = launch {
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
                val size = connection.getSize()
                val rotation = connection.getRotation()
                withContext(Dispatchers.Main) {
                    surfaceView.holder.setFixedSize(size.width, size.height)
                    ConstraintSet().also {
                        val constraintLayout = surfaceView.parent as ConstraintLayout
                        it.clone(constraintLayout)
                        val outSize = if (rotation == 90 || rotation == 270) Size(size.height, size.width) else size
                        it.setDimensionRatio(R.id.surface_view, "${outSize.width}:${outSize.height}")
                        it.applyTo(constraintLayout)
                    }
                }
                setSurface(surface)
            }
        }
    }

    private fun setSurface(surface: Surface?) {
        Log.d(TAG, "setSurface $surface")
        this.surface = surface
        connection?.setSurface(surface)
    }

    companion object {
        private val TAG = SurfaceViewNode::class.java.simpleName
    }
}

/*
pass surface into execVM -> graph -> surfaceviewnode
listen to surfaceholder events

 */