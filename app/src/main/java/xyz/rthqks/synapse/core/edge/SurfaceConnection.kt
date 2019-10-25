package xyz.rthqks.synapse.core.edge

import android.util.Size
import android.view.Surface
import xyz.rthqks.synapse.core.Connection
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class SurfaceConnection(bufferSize: Int = BUFFER_SIZE) : Connection<SurfaceEvent>(bufferSize) {
    lateinit var size: Size
    private var surface: Surface? = null
    private var surfaceContinuation: Continuation<Surface>? = null

    override suspend fun createBuffer(): SurfaceEvent = SurfaceEvent()

    fun configure(size: Size) {
        this.size = size
    }

    fun setSurface(surface: Surface?) {
        this.surface = surface

        surface?.let {
            surfaceContinuation?.resume(surface)
            surfaceContinuation = null
        }
    }

    suspend fun getSurface(): Surface {
        return surface ?: suspendCoroutine {
            surfaceContinuation = it
        }
    }
}