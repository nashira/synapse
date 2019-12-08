package xyz.rthqks.synapse.exec.node

import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import kotlinx.coroutines.*
import xyz.rthqks.synapse.R
import xyz.rthqks.synapse.exec.NodeExecutor
import xyz.rthqks.synapse.exec.edge.*

class SurfaceViewNode(
    private var surfaceView: SurfaceView
) : NodeExecutor(), SurfaceHolder.Callback {
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

        connection(INPUT)?.let {
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
        val connection = channel(INPUT) ?: return@coroutineScope

        playJob = launch {
            running = true
            while (running) {
                val surfaceEvent = connection.receive()
                if (surfaceEvent.eos) {
                    Log.d(TAG, "got EOS")
                    running = false
                }
                connection.send(surfaceEvent)
            }
        }
    }

    override suspend fun stop() {
        playJob?.join()
    }

    override suspend fun release() {

    }

    override suspend fun <C : Config, E : Event> setConfig(key: Connection.Key<C, E>, config: C) {
        super.setConfig(key, config)
        if (key == INPUT) {
            config as VideoConfig
            config.requiresSurface = true
            val size = config.size
            val rotation = config.rotation
            updateSurfaceViewConfig(size, rotation)
        }
    }

    private suspend fun updateSurfaceViewConfig(
        size: Size,
        rotation: Int
    ) {
        withContext(Dispatchers.Main) {
            val outSize =
                if (rotation == 90 || rotation == 270) Size(size.height, size.width) else size
            surfaceView.holder.setFixedSize(outSize.width, outSize.height)
            ConstraintSet().also {
                val constraintLayout = surfaceView.parent as ConstraintLayout
                it.clone(constraintLayout)
                it.setDimensionRatio(R.id.surface_view, "${size.width}:${size.height}")
                it.applyTo(constraintLayout)
            }
        }
        setSurface(surface)
    }

    private suspend fun setSurface(surface: Surface?) {
        Log.d(TAG, "setSurface $surface")
        this.surface = surface
        config(INPUT)?.surface?.set(surface)
    }

    companion object {
        const val TAG = "SurfaceViewNode"
        val INPUT = Connection.Key<VideoConfig, VideoEvent>("video_1")
    }
}

/*
pass surface into execVM -> graph -> surfaceviewnode
listen to surfaceholder events

 */