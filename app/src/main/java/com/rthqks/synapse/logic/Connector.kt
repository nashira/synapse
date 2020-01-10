package com.rthqks.synapse.logic

data class Connector(
    val node: Node,
    val port: Port,
    val link: Link? = null
)