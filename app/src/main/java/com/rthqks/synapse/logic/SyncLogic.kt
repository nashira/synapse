package com.rthqks.synapse.logic

import com.rthqks.synapse.data.*
import com.rthqks.synapse.data.SeedData.BaseEffect
import com.rthqks.synapse.data.SeedData.SeedNetworks
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncLogic @Inject constructor(
    val dao: SynapseDao
) {

    suspend fun currentUser(): User {
        val userData = dao.getCurrentUser() ?: run {
            UserData(UUID.randomUUID().toString(), "guest", true).also {
                dao.insertUser(it)
            }
        }

        return User(userData.id)
    }

    suspend fun refreshEffects() {
        SeedNetworks.forEach {
            dao.insertFullNetwork(it.toData())
        }
        dao.insertFullNetwork(BaseEffect.toData())
    }

    suspend fun getNetwork(networkId: String): Network? = dao.getFullNetwork(networkId).toNetwork()
}

fun Network.toData(networkId: String = id): NetworkData {
    val data = NetworkData(networkId, creatorId, name, description)
    data.nodes = getNodes().map { NodeData(it.id, networkId, it.type) }
    data.ports = getPorts().map { e ->
        PortData(
            networkId,
            e.nodeId,
            e.key,
            e.type.ordinal,
            "",
            e.output,
            e.exposed
        )
    }
    data.links = getLinks().map {
        LinkData(
            networkId,
            it.fromNodeId,
            it.fromPortId,
            it.toNodeId,
            it.toPortId
        )
    }
    data.properties = getProperties().map { e ->
        PropertyData(
            networkId,
            e.nodeId,
            e.type,
            e.key.name,
            e.stringValue,
            e.exposed
        )
    }

    return data
}

fun NetworkData.toNetwork(networkId: String = id): Network {
    val network = Network(networkId, creatorId, name, description)

    nodes.forEach {
        network.addNode(Nodes[it.type], it.id)
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