package xyz.rthqks.synapse.data

data class PortConfig(
    val key: PortKey,
    val type: PortType
)

data class PortKey(
    val nodeId: Int,
    val key: String,
    val direction: Int
)