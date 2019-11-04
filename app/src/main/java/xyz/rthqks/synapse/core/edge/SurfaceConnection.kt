package xyz.rthqks.synapse.core.edge

import android.util.Size
import android.view.Surface
import xyz.rthqks.synapse.core.Connection
import xyz.rthqks.synapse.util.SuspendableGet

class SurfaceConnection(capacity: Int = CAPACITY) : Connection<SurfaceEvent>(capacity) {
    private var size = SuspendableGet<Size>()
    private var rotation = SuspendableGet<Int>()
    private var surface = SuspendableGet<Surface>()

    override suspend fun createItem(): SurfaceEvent = SurfaceEvent()

    suspend fun configure(size: Size, rotation: Int) {
        this.size.set(size)
        this.rotation.set(rotation)
    }

    suspend fun setSurface(surface: Surface?) = this.surface.set(surface)

    fun hasSurface() = surface.has()

    suspend fun getSize(): Size = size.get()

    suspend fun getRotation(): Int = rotation.get()

    suspend fun getSurface(): Surface = surface.get()
}