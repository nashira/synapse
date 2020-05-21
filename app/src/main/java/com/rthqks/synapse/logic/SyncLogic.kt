package com.rthqks.synapse.logic

import com.rthqks.synapse.data.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncLogic @Inject constructor(
    val dao: SynapseDao
) {
    suspend fun refreshEffects() {
        dao.insertFullNetwork(BaseEffect.toData())
        SeedNetworks.forEach {
            dao.insertFullNetwork(it.toData())
        }
    }

    suspend fun getNetwork(networkId: Int): Network? = dao.getFullNetwork(networkId).toNetwork()
}



fun Network.toData(): NetworkData {
    val data = NetworkData(id, name)
    data.nodes = getNodes().map { NodeData(it.id, id, it.type) }
    data.ports = getPorts().map { e -> PortData(id, e.nodeId, e.key, e.type.ordinal, "", e.output, e.exposed) }
    data.links = getLinks().map { LinkData(id, it.fromNodeId, it.fromPortId, it.toNodeId, it.toPortId) }
    data.properties = getProperties().map { e -> PropertyData(id, e.nodeId, e.type, e.key.name, e.stringValue, e.exposed) }

    return data
}

fun NetworkData.toNetwork(): Network {
    val network = Network(id, name)

    nodes.forEach {
        network.addNode(NodeDef[it.type], it.id)
    }

    links.map { Link(it.fromNodeId, it.fromKey, it.toNodeId, it.toKey) }.let(network::addLinks)

    properties.forEach {
        val key = Property.getKey(it.type, it.key)
        val value = Property.fromString(key.klass, it.value)
        network.setProperty(it.nodeId, key, value)
        network.setExposed(it.nodeId, key, it.exposed)
    }

    ports.forEach {
        network.setExposed(it.nodeId, it.id, it.exposed)
    }

    return network
}