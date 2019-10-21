package xyz.rthqks.synapse.core

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import xyz.rthqks.synapse.core.edge.AudioBufferConnection
import xyz.rthqks.synapse.core.node.AudioPlayerNode
import xyz.rthqks.synapse.core.node.AudioSourceNode
import xyz.rthqks.synapse.data.GraphConfig
import xyz.rthqks.synapse.data.NodeType
import xyz.rthqks.synapse.data.PortType
import xyz.rthqks.synapse.data.PropertyType

class Graph(
    private val context: Context,
    private val graphConfig: GraphConfig
) {
    private val scope = CoroutineScope(SupervisorJob())
    private val glDispatcher = newSingleThreadContext("Dispatchers.GL")
    private val nodes = mutableMapOf<Int, Node>()
    private val connections = mutableListOf<Connection<Any>>()

    suspend fun initialize() {

        graphConfig.nodes.forEach {
            val node = when (it.type) {
                NodeType.Camera -> TODO()
                NodeType.Microphone -> AudioSourceNode(
                    it.getIntProperty(PropertyType.AUDIO_SAMPLE_RATE),
                    it.getIntProperty(PropertyType.AUDIO_CHANNEL),
                    it.getIntProperty(PropertyType.AUDIO_ENCODING),
                    it.getIntProperty(PropertyType.AUDIO_SOURCE)
                )
                NodeType.Image -> TODO()
                NodeType.AudioFile -> TODO()
                NodeType.VideoFile -> TODO()
                NodeType.ColorFilter -> TODO()
                NodeType.ShaderFilter -> TODO()
                NodeType.Speakers -> AudioPlayerNode()
                NodeType.Screen -> TODO()
            }
            nodes[it.id] = node
        }

        parallelJoin {
            Log.d(TAG, "initialize ${it}")
            it.initialize()
            Log.d(TAG, "initialize complete ${it}")
        }

        graphConfig.edges.map { edge ->
            Log.d(TAG, "edge: $edge")
            val fromConfig = graphConfig.nodes.first { it.id == edge.fromNodeId }
            val toConfig = graphConfig.nodes.first { it.id == edge.toNodeId }
            val dataType = fromConfig.type.outputs.first { it.key == edge.fromKey }

            val from = nodes[edge.fromNodeId]!!
            val to = nodes[edge.toNodeId]!!

            Log.d(TAG, "from: $from to $to")

            when (dataType) {
                is PortType.Surface -> TODO()
                is PortType.Texture -> TODO()
                is PortType.AudioBuffer -> {
                    val connection = AudioBufferConnection()
                    from.output(edge.fromKey, connection)
                    to.input(edge.toKey, connection)
                }
            }
        }
        logCoroutineInfo(scope.coroutineContext[Job])
    }

    suspend fun start() {
        parallel {
            Log.d(TAG, "start ${it}")
            it.start()
            Log.d(TAG, "start complete ${it}")
        }

        logCoroutineInfo(scope.coroutineContext[Job])
    }

    suspend fun stop() {
        parallelJoin {
            Log.d(TAG, "stop ${it}")
            it.stop()
            Log.d(TAG, "stop complete ${it}")
        }
        logCoroutineInfo(scope.coroutineContext[Job])
    }

    suspend fun release() {
        parallelJoin {
            Log.d(TAG, "release ${it}")
            it.release()
            Log.d(TAG, "release complete ${it}")
        }
        logCoroutineInfo(scope.coroutineContext[Job])
    }

    private fun logCoroutineInfo(job: Job?, indent: String = "") {
        job?.let {
            Log.d(TAG, "coroutine ${it[CoroutineName]}: $indent${it.children.count()}")
            it.children.forEach {
                logCoroutineInfo(it, "$indent  ")
            }
        }
    }


    private suspend fun parallel(block: suspend (node: Node) -> Unit) {
        nodes.forEach { scope.launch { block(it.value) } }
    }

    private suspend fun parallelJoin(block: suspend (node: Node) -> Unit) {
        nodes.map { scope.launch { block(it.value) } }.forEach { it.join() }
    }

    companion object {
        private val TAG = Graph::class.java.simpleName
    }
}
