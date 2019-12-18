package xyz.rthqks.synapse.logic

import xyz.rthqks.synapse.data.GraphData
import xyz.rthqks.synapse.data.NodeData

fun GraphData.toGraph(): Graph {
    return Graph(id, name)
}

fun NodeData.toNode(): Node = type.node().copy(graphId, id)