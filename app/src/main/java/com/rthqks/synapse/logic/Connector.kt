package com.rthqks.synapse.logic

import com.rthqks.flow.logic.Link
import com.rthqks.flow.logic.Node
import com.rthqks.flow.logic.Port

data class Connector(
    val node: Node,
    val port: Port,
    val link: Link? = null
)