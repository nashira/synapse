package com.rthqks.synapse.logic

data class Link(
    val fromNodeId: Int,
    val fromPortId: String,
    val toNodeId: Int,
    val toPortId: String
)