

fun go() {
    val net = Network(1)
    net.addNode(1, NodeType.A)
    net.addPort(1, "foo")

    val node = net.getNode(1)
    println(node)
    println(node.ports)
}

go()

data class Node(val networkId: Int, val id: Int, val type: NodeType) {
    val ports = mutableMapOf<String, Port>()
    val properties = mutableMapOf<String, Property>()
}

enum class NodeType { A, B, C }

class Property

data class Port(val nodeId: Int, val key: String, var output: Boolean = false)

class Link

class Network(val id: Int) {
    private val nodes = mutableMapOf<Int, Node>()
    private val properties = mutableMapOf<Int, MutableMap<String, Property>>()
//    private val ports = mutableMapOf<Int, MutableMap<String, Port>>()
    private val links = mutableMapOf<Int, MutableSet<Link>>()

    fun addNode(id: Int, type: NodeType) {
        val node = Node(this.id, id, type)
        nodes[id] = node
//        ports[id] = node.ports
    }

    fun removeNode(id: Int) {
        nodes.remove(id)
    }

    fun getNode(id: Int): Node {
        return nodes[id] ?: error("node not found: $id")
    }

    fun addPort(nodeId: Int, key: String) {
        val port = Port(nodeId, key)
        getNode(nodeId).ports[key] = port
    }

    fun removePort(nodeId: Int, key: String) {
        nodes[nodeId]?.ports?.remove(key)
    }

    fun getPort(nodeId: Int, key: String): Port {
        return nodes[nodeId]?.ports?.get(key) ?: error("port not found $nodeId:$key")
    }

    fun addProperty(nodeId: Int, key: String) {
        val port = Port(nodeId, key)
        getNode(nodeId).ports[key] = port
    }

    fun removeProperty(nodeId: Int, key: String) {
        nodes[nodeId]?.ports?.remove(key)
    }

//    fun getProperty(nodeId: Int, key: String): Property {
//        return nodes[nodeId]?.ports?.get(key) ?: error("port not found $nodeId:$key")
//    }

    fun log() {
        println(nodes.toString())
    }
}