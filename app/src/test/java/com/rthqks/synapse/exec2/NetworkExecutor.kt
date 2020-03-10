package com.rthqks.synapse.exec2

import android.util.Log
import com.rthqks.synapse.exec.ExecutionContext
import com.rthqks.synapse.exec.Executor
import com.rthqks.synapse.logic.Link
import com.rthqks.synapse.logic.Network
import com.rthqks.synapse.logic.Node
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

open class NetworkExecutor(context: ExecutionContext) : Executor(context) {
    private val nodes = ConcurrentHashMap<Int, NodeExecutor>()
    var network: Network? = null

    suspend fun setup() = async {
//        context.setup()
    }

    suspend fun addNode(node: Node) {
        val executor = node.executor()
        nodes[node.id] = executor
        network?.getLinks(node.id)?.forEach {
            val key = if (it.fromNodeId == node.id) {
                Connection.Key<Event>(it.fromPortId)
            } else {
                Connection.Key<Event>(it.toPortId)
            }
            executor.setLinked(key)
            if (it.inCycle) {
                executor.setCycle(key)
            }
        }
        executor.setup().await()
    }

    suspend fun removeNode(node: Node) {
        network?.getLinks(node.id)?.map {
            scope.launch { removeLink(it) }
        }?.joinAll()

        nodes.remove(node.id)?.release()
    }

    suspend fun addLink(link: Link) {
        val fromNode = nodes[link.fromNodeId] ?: error("missing node ${link.fromNodeId}")
        val toNode = nodes[link.toNodeId] ?: error("missing node ${link.toNodeId}")
        val fromKey = Connection.Key<Event>(link.fromPortId)
        val toKey = Connection.Key<Event>(link.toPortId)

//        val connectionKey = "${link.fromNodeId}:${link.fromPortId}"
//        val connection = getConnection(connectionKey)

        val channel = fromNode.connectProducer(fromKey).await()
        val consume = toNode.connectConsumer(toKey, channel)

        consume.await()
    }

//    private fun getConnection(connectionKey: String): Connection<Event> =
//        connections.getOrPut(connectionKey) { Connection(context) }

    suspend fun removeLink(link: Link) {
        val fromNode = nodes[link.fromNodeId] ?: error("missing node ${link.fromNodeId}")
        val toNode = nodes[link.toNodeId] ?: error("missing node ${link.toNodeId}")
        val fromKey = Connection.Key<Event>(link.fromPortId)
        val toKey = Connection.Key<Event>(link.toPortId)

        val linked = network?.getLinks(link.fromNodeId)?.any { it.fromPortId == link.fromPortId } == true

        val channel = toNode.channel(toKey)!!

        fromNode.setLinked(fromKey, linked)
        fromNode.setCycle(fromKey, link.inCycle)
        fromNode.disconnectProducer(fromKey, channel).await()

        toNode.setLinked(toKey, false)
        toNode.setCycle(toKey, link.inCycle)
        toNode.disconnectConsumer(toKey).await()
    }

    suspend fun addAllLinks() {
        network?.getLinks()?.map {
            scope.launch { addLink(it) }
        }?.joinAll()
    }

    suspend fun removeAllLinks() {
        network?.getLinks()?.map {
            scope.launch { removeLink(it) }
        }?.joinAll()
    }

    suspend fun addAllNodes() {
        network?.getNodes()?.map {
            scope.launch { addNode(it) }
        }?.joinAll()
    }

    suspend fun removeAllNodes() {
        nodes.values.map {
            scope.launch { it.release() }
        }.joinAll()
    }

    override suspend fun release() {
        exec { context.release() }
        super.release()
    }

    companion object {
        const val TAG = "NetworkExecutor"
    }

    private fun Node.executor(): NodeExecutor {

        return when (type) {
//            NodeType.Camera -> CameraNode(context, properties)
//            NodeType.Microphone -> AudioSourceNode(context, node.properties)
//            NodeType.Screen -> SurfaceViewNode(context, node.properties + network.properties)
//            NodeType.MediaEncoder -> EncoderNode(context, node.properties)
//            NodeType.Properties,
//            NodeType.Creation,
//            NodeType.Connection -> error("not an executable node type: ${node.type}")
            else -> object : NodeExecutor(context) {
                private val jobs = ConcurrentHashMap<String, Job>()
                private val running = ConcurrentHashMap<String, Boolean>()
                override suspend fun onSetup() {
                    Log.d(TAG, "onSetup $id")
                }

                override suspend fun <E : Event> onConnect(
                    key: Connection.Key<E>,
                    producer: Boolean
                ) {
                    Log.d(TAG, "onConnect $id ${key.id}")
                    if (producer) {
                        jobs.getOrPut(key.id) {
                            scope.launch {
                                val connection = connection(key) ?: error("missing connection $id ${key.id}")
                                running[key.id] = true
                                connection.prime(Event() as E,Event() as E,Event() as E)
                                val channel = channel(key)!!
                                while (running[key.id] == true) {
                                    val event = channel.receive()
                                    event.queue()
                                    println("sending $id ${key.id} ${event.count}")
                                    delay(50L + 5 * id)
                                }
                            }
                        }
                    } else {
                        jobs.getOrPut(key.id) {
                            scope.launch {
                                val channel = channel(key)!!
                                for (event in channel) {
                                    println("receiving $id ${key.id} ${event.count}")
                                    event.release()
                                }
                            }
                        }
                    }
                }

                override suspend fun <E : Event> onDisconnect(
                    key: Connection.Key<E>,
                    producer: Boolean
                ) {
                    Log.d(TAG, "onDisconnect $id ${key.id}")

                    if (producer) {
                        if (!linked(key)) {
                            running[key.id] = false
                            jobs.remove(key.id)?.join()
                        }
                    } else {
                        jobs.remove(key.id)?.join()
                    }
                }

                override suspend fun onRelease() {
                    Log.d(TAG, "onRelease $id")
                }
            }
        }
    }
}
