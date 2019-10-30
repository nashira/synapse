package xyz.rthqks.synapse.core.edge

data class SurfaceEvent(
    var count: Long = 0,
    var timestamp: Long = 0,
    var eos: Boolean = false
)