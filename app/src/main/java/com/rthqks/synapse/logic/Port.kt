package com.rthqks.synapse.logic

class Port(
    val networkId: Int,
    val nodeId: Int,
    val key: String,
    val type: PortType,
    val output: Boolean,
    var exposed: Boolean = false
) {
    val input: Boolean get() = !output
}