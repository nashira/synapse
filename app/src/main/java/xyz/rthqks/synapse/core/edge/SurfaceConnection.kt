package xyz.rthqks.synapse.core.edge

import android.util.Size
import android.view.Surface
import xyz.rthqks.synapse.core.Connection

class SurfaceConnection(bufferSize: Int = BUFFER_SIZE) : Connection<SurfaceEvent>(bufferSize) {
    private lateinit var size: Size
    private lateinit var onSurface: (Surface) -> Unit
    override suspend fun createBuffer(): SurfaceEvent = SurfaceEvent()

    fun configure(size: Size, onSurface: (Surface) -> Unit) {
        this.size = size
        this.onSurface = onSurface
    }

    fun setSurface(surface: Surface) {
        onSurface(surface)
    }
}