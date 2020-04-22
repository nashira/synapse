package com.rthqks.synapse.logic

class Port(
    val type: PortType,
    val id: String,
    val output: Boolean,
    var exposed: Boolean = false
)