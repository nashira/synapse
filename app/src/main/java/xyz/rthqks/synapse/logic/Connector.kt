package xyz.rthqks.synapse.logic

data class Connector(
    val node: Node,
    val port: Port,
    val edge: Edge? = null
)