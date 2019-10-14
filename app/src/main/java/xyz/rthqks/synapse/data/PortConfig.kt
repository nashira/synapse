package xyz.rthqks.synapse.data

data class PortConfig(
    val key: PortKey,
    val type: PortType
)

// TODO: find a way to make this fit better
data class PortKey(
    val nodeId: Int,
    val key: String,
    val direction: Int
)