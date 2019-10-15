package xyz.rthqks.synapse.core

import android.content.Context
import android.util.Log
import xyz.rthqks.synapse.data.GraphConfig
import xyz.rthqks.synapse.data.NodeType
import xyz.rthqks.synapse.data.PortType
import xyz.rthqks.synapse.data.PropertyType

class Graph(
    private val context: Context,
    private val graphConfig: GraphConfig
) {

    private val nodes = mutableMapOf<Int, Node>()
    private val connections = mutableListOf<Connection<Any>>()

    fun initialize() {
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

        graphConfig.edges.forEach { edge ->
            Log.d(TAG, "edge: $edge")
            val fromConfig = graphConfig.nodes.first { it.id == edge.fromNodeId }
            val toConfig = graphConfig.nodes.first { it.id == edge.toNodeId }
            val dataType = fromConfig.type.inputs.first { it.key == edge.fromKey }

            val from = nodes[edge.fromNodeId]!!
            val to = nodes[edge.toNodeId]!!

            Log.d(TAG, "from: $from to $to")

            when (dataType) {
                is PortType.Surface -> TODO()
                is PortType.Texture -> TODO()
                is PortType.AudioBuffer -> {
//                    val connection = AudioBufferConnection()
                }
            }
        }
    }

    companion object {
        private val TAG = Graph::class.java.simpleName
    }
}
