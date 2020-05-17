package com.rthqks.synapse.logic

import android.util.Log
import com.rthqks.synapse.data.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncLogic @Inject constructor(
    val dao: SynapseDao
) {

    suspend fun refreshEffects() {
        SeedNetworks.forEach {
            dao.insertFullNetwork(it.toData())
        }
    }

    suspend fun getNetwork(networkId: Int): Network? = dao.getFullNetwork(networkId).toNetwork()
}



fun Network.toData(): NetworkData {
    val data = NetworkData(id, name)
    data.nodes = nodes.values.map { NodeData(it.id, id, it.type) }
    data.ports = ports.map { e -> e.value.map { PortData(id, e.key, it.id, it.type.ordinal, "", it.output, it.exposed) } }.flatten()
    data.links = getLinks().map { LinkData(id, it.fromNodeId, it.fromPortId, it.toNodeId, it.toPortId) }
    data.properties = properties.map { e -> e.value.map { PropertyData(id, e.key, it.getType(), it.key.name, it.getString(), it.exposed) } }.flatten()

    return data
}

fun NetworkData.toNetwork(): Network {
    val network = Network(id, name)

    nodes.forEach {
        network.addNode(NodeDef[it.type].toNode(it.id))
    }

    links.map { Link(it.fromNodeId, it.fromKey, it.toNodeId, it.toKey) }.let(network::addLinks)

    properties.forEach {
        val key = network.getNode(it.nodeId)?.properties?.getKey(it.type, it.key)
        key?.let {  key ->
            network.getNode(it.nodeId)?.properties?.putString(key, it.value!!)
            network.setExposed(it.nodeId, key, it.exposed)
        }
    }

    ports.forEach {
        network.setExposed(it.nodeId, it.id, it.exposed)
        Log.d("toNetwork", "port $it")
    }

    return network
}