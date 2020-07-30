package com.rthqks.flow.logic

data class Link(
    val fromNodeId: Int,
    val fromPortId: String,
    val toNodeId: Int,
    val toPortId: String
) {
    var inCycle: Boolean = false
}