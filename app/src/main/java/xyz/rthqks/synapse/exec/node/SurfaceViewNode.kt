package xyz.rthqks.synapse.exec.node

import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import xyz.rthqks.synapse.R
import xyz.rthqks.synapse.data.PortType
import xyz.rthqks.synapse.exec.NodeExecutor
import xyz.rthqks.synapse.exec.edge.*

class SurfaceViewNode(
    private var surfaceView: SurfaceView
) : NodeExecutor(), SurfaceHolder.Callback {
    private var channel: Channel<SurfaceEvent>? = null
    private var connection: Connection<SurfaceConfig, SurfaceEvent>? = null
    private var surface: Surface? = null
    private var running: Boolean = false
    private var playJob: Job? = null

    override suspend fun create() {
        Log.d(TAG, "adding callback ${surfaceView.holder.surface}")

        setSurfaceView(surfaceView)
    }

    suspend fun setSurfaceView(surfaceView: SurfaceView) {
        Log.d(TAG, "setSurfaceView $surfaceView")
        this.surfaceView.holder.removeCallback(this)
        this.surfaceView = surfaceView
        surfaceView.holder.addCallback(this)

        connection?.let {
            val size = it.config.size
            val rotation = it.config.rotation
            updateSurfaceViewConfig(size, rotation)
        } ?: run {
            setSurface(surfaceView.holder.surface)
        }

    }

    override fun surfaceChanged(
        holder: SurfaceHolder?,
        format: Int,
        width: Int,
        height: Int
    ) {
        Log.d(TAG, "surfaceChanged: $holder $format $width $height")
        runBlocking { setSurface(holder!!.surface) }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder?) {
        Log.d(TAG, "surfaceDestroyed: $holder")
        runBlocking { setSurface(null) }
    }

    override fun surfaceCreated(holder: SurfaceHolder?) {
        Log.d(TAG, "surfaceCreated: $holder")
    }

    override suspend fun initialize() {

    }

    override suspend fun start() = coroutineScope {
        val connection = channel ?: return@coroutineScope

        playJob = launch {
            running = true
            var numFrames = 0
            while (running) {
                val surfaceEvent = connection.receive()
                if (surfaceEvent.eos) {
                    Log.d(TAG, "got EOS")
                    running = false
                } else {
                    numFrames++
//                    Log.d(TAG, "written frames $numFrames")
                }
                connection.send(surfaceEvent)
            }
            Log.d(TAG, "wrote frames $numFrames")
        }
    }

    override suspend fun stop() {
        playJob?.join()
    }

    override suspend fun release() {

    }

    override suspend fun output(key: String): Connection<*, *>? {
        throw IllegalStateException("$TAG has no outputs")
    }

    override suspend fun <C: Config, T : Event> input(key: String, connection: Connection<C, T>) {
        when (key) {
            PortType.SURFACE_1 -> {
                this.connection = connection as Connection<SurfaceConfig, SurfaceEvent>
                channel = connection.consumer()
                val size = connection.config.size
                val rotation = connection.config.rotation
                updateSurfaceViewConfig(size, rotation)
            }
        }
    }

    private suspend fun updateSurfaceViewConfig(
        size: Size,
        rotation: Int
    ) {
        withContext(Dispatchers.Main) {
            surfaceView.holder.setFixedSize(size.width, size.height)
            ConstraintSet().also {
                val constraintLayout = surfaceView.parent as ConstraintLayout
                it.clone(constraintLayout)
                val outSize =
                    if (rotation == 90 || rotation == 270) Size(size.height, size.width) else size
                it.setDimensionRatio(R.id.surface_view, "${outSize.width}:${outSize.height}")
                it.applyTo(constraintLayout)
            }
        }
        setSurface(surface)
    }

    private suspend fun setSurface(surface: Surface?) {
        Log.d(TAG, "setSurface $surface")
        this.surface = surface
        connection?.config?.setSurface(surface)
    }

    companion object {
        private val TAG = SurfaceViewNode::class.java.simpleName
    }
}

/*
pass surface into execVM -> graph -> surfaceviewnode
listen to surfaceholder events

 */