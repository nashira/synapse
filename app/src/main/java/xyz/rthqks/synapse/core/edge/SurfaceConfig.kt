package xyz.rthqks.synapse.core.edge

import android.util.Size
import android.view.Surface
import xyz.rthqks.synapse.util.SuspendableGet

data class SurfaceConfig(
    val size: Size,
    val rotation: Int
) : Config {
    private var surface = SuspendableGet<Surface>()
    fun hasSurface() = surface.has()
    suspend fun setSurface(surface: Surface?) = this.surface.set(surface)
    suspend fun getSurface(): Surface = surface.get()
}