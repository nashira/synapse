package xyz.rthqks.synapse.core.edge

import android.util.Size
import android.view.Surface
import xyz.rthqks.synapse.core.Connection
import xyz.rthqks.synapse.util.SuspendableGet

class SurfaceConnection(bufferSize: Int = BUFFER_SIZE) : Connection<SurfaceEvent>(bufferSize) {
    private var size = SuspendableGet<Size>()
    private var surface = SuspendableGet<Surface>()

    override suspend fun createItem(): SurfaceEvent = SurfaceEvent()

    fun configure(size: Size) {
        this.size.set(size)
    }

    fun setSurface(surface: Surface?) {
        this.surface.set(surface)
    }

    suspend fun getSize(): Size {
        return size.get()
    }

    suspend fun getSurface(): Surface {
        return surface.get()
    }
}