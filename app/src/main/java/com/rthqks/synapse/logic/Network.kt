package com.rthqks.synapse.logic

import com.rthqks.synapse.R

class Network(
    val id: Int
) {
    private val nodes = mutableMapOf<Int, Node>()
    private val links = mutableSetOf<Link>()
    private val linkIndex = mutableMapOf<Int, MutableSet<Link>>()
    val properties = Properties()
    private var maxNodeId = 0

    val name: String get() = properties[NetworkName]

    init {
        properties.put(
            Property(
                NetworkName,
                TextType(
                    R.string.property_name_network_name,
                    R.drawable.ic_text_fields
                ), "Network"
            ), TextConverter
        )
        properties.put(
            Property(
                CropToFit,
                ToggleType(
                    R.string.property_title_crop_to_fit, R.drawable.ic_crop,
                    R.string.property_subtitle_crop_to_fit_enabled,
                    R.string.property_subtitle_crop_to_fit_disabled
                ), value = true, requiresRestart = true
            ), BooleanConverter
        )
    }

    fun addNode(node: Node) {
        if (node.id == -1) {
            node.id = ++maxNodeId
        }
        nodes[node.id] = node
        maxNodeId = max(maxNodeId, node.id)
    }

    fun nodeCount() = nodes.size

    fun removeNode(nodeId: Int): Node? {
        return nodes.remove(nodeId)
    }

    fun getFirstNode(): Node? = nodes.values.firstOrNull()

    fun getNode(nodeId: Int): Node? {
//        Log.d(TAG, "getNode $nodeId")
        return nodes[nodeId]
    }

    fun getNodes(): List<Node> {
        return nodes.values.toList()
    }

    fun getLinks(): Set<Link> = links

    fun getLinks(nodeId: Int): Set<Link> = linkIndex[nodeId] ?: emptySet()

    fun addLink(
        fromNodeId: Int,
        fromKey: String,
        toNodeId: Int,
        toKey: String
    ): Link {
        val link = Link(fromNodeId, fromKey, toNodeId, toKey)
        links.add(link)
        linkIndex.getOrPut(fromNodeId) { mutableSetOf() } += link
        linkIndex.getOrPut(toNodeId) { mutableSetOf() } += link
        return link
    }

    fun removeLink(link: Link) {
        links.remove(link)
        linkIndex[link.fromNodeId]?.remove(link)
        linkIndex[link.toNodeId]?.remove(link)
    }

    fun removeLinks(nodeId: Int): Set<Link> {
        val removed = linkIndex.remove(nodeId) ?: emptySet<Link>()
        removed.forEach(this::removeLink)
        return removed
    }

    fun getConnectors(nodeId: Int): List<Connector> {
        val node = getNode(nodeId) ?: return emptyList()
        val ports = node.getPortIds().toMutableSet()
        val nodeLinks = getLinks(nodeId)

        return (nodeLinks.map {
            if (it.fromNodeId == nodeId) {
                ports.remove(it.fromPortId)
                Connector(node, node.getPort(it.fromPortId), it)
            } else {
                ports.remove(it.toPortId)
                Connector(node, node.getPort(it.toPortId), it)
            }
        } + ports.map { Connector(node, node.getPort(it)) }).sortedBy { it.port.id }
    }

    fun getOpenConnectors(connector: Connector): List<Connector> {
        val node = connector.node
        val port = connector.port
        val connectors = mutableListOf<Connector>()
        nodes.forEach {
            val n = it.value
            if (n.id != node.id) {
                connectors += n.ports.filter {
                    it.value.type == port.type
                            && it.value.output != port.output
                            && (it.value.output || !isConnected(n, it.value))
                }
                    .map { Connector(n, it.value) }
            }
        }
        return connectors
    }

    fun getPotentialConnectors(connector: Connector): List<Connector> {
        val port = connector.port
        val connectors = mutableListOf<Connector>()
        Nodes.filter { it.type != Node.Type.OverlayFilter }.forEach { n ->
            connectors += n.ports.filter {
                it.value.type == port.type
                        && it.value.output != port.output
            }
                .map { Connector(n.copy(id), it.value) }
        }
        return connectors
    }

    fun getCreationConnectors(): List<Connector> {
        val connectors = mutableListOf<Connector>()
        Nodes.forEach { n ->
            connectors += n.ports.filter { it.value.output }
                .map { Connector(n.copy(id), it.value) }
        }
        return connectors
    }

    private fun isConnected(node: Node, port: Port): Boolean {
        return linkIndex[node.id]?.any {
            it.fromNodeId == node.id && it.fromPortId == port.id
                    || it.toNodeId == node.id && it.toPortId == port.id
        } ?: false
    }

    fun copy(): Network {
        return Network(id).also {
            it.nodes += nodes
            it.links += links
            it.properties += properties
            val ei = mutableMapOf<Int, MutableSet<Link>>()
            linkIndex.forEach { ei[it.key] = it.value.toMutableSet() }
            it.linkIndex += ei
            it.maxNodeId = maxNodeId + COPY_ID_SKIP
        }
    }

    private fun max(i: Int, j: Int) = if (i > j) i else j

    companion object {
        const val TAG = "Network"
        const val COPY_ID_SKIP = 10_000
    }
}