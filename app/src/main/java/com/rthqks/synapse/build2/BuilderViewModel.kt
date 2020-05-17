package com.rthqks.synapse.build2

import android.content.Context
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rthqks.synapse.assets.AssetManager
import com.rthqks.synapse.assets.VideoStorage
import com.rthqks.synapse.data.NetworkData
import com.rthqks.synapse.data.SynapseDao
import com.rthqks.synapse.exec.ExecutionContext
import com.rthqks.synapse.exec.NetworkExecutor
import com.rthqks.synapse.logic.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

class BuilderViewModel @Inject constructor(
    private val contxt: Context,
    private val videoStorage: VideoStorage,
    private val assetManager: AssetManager,
    private val logic: SyncLogic,
    private val dao: SynapseDao
) : ViewModel() {
    private var network: Network? = null
//    private val context = ExecutionContext(contxt, videoStorage, assetManager)
//    private val executor = NetworkExecutor(context)

    val networkChannel = MutableLiveData<Network>()
    val connectionChannel = MutableLiveData<Connector>()
    val nodesChannel = MutableLiveData<AdapterState<Node>>()
    val titleChannel = MutableLiveData<Int>()
    val menuChannel = MutableLiveData<Int>()
    private var nodeAfterCancel: Node? = null

    fun setNetworkId(networkId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            if (networkId == -1) {
                val rowId = dao.insertNetwork(NetworkData(0, ""))

                network = Network(rowId.toInt(), "")
                Log.d(TAG, "created: $network")
            } else {
                network = logic.getNetwork(networkId)
                Log.d(TAG, "loaded: $network")
            }
            networkChannel.postValue(network)

//            nodesChannel.postValue(AdapterState(0, listOf(PROPERTIES_NODE)))
//            executor.setup()
//            executor
        }
    }

    fun showFirstNode() {
        val nextNode = network?.getFirstNode() ?: run {
//            connectionChannel.postValue(Connector(CREATION_NODE, FAKE_PORT))
//            CREATION_NODE
        }
//        nodesChannel.value = AdapterState(0, listOf(PROPERTIES_NODE, nextNode))
    }

    fun swipeToNode(node: Node) {
//        nodesChannel.value = AdapterState(0, listOf(PROPERTIES_NODE, node))
    }

    fun swipeEvent(event: SwipeEvent) {
    }

    fun preparePortSwipe(connector: Connector) {
        val port = connector.port
        val network = network ?: return
        connector.link?.let {
            val leftNode = network.getNode(it.fromNodeId)!!
            val rightNode = network.getNode(it.toNodeId)!!
            val current = if (port.output) 0 else 1
            nodesChannel.value = AdapterState(current, listOf(leftNode, rightNode))
        } ?: run {

            connectionChannel.value = connector
            if (connector.port.output) {
//                nodesChannel.value = AdapterState(0, listOf(connector.node, CONNECTION_NODE))
            } else {
//                nodesChannel.value = AdapterState(1, listOf(CONNECTION_NODE, connector.node))
            }
        }
    }

    fun startConnection(connector: Connector) {
        connectionChannel.value = connector
        if (connector.port.output) {
//            nodesChannel.value = AdapterState(1, listOf(connector.node, CONNECTION_NODE), true)
        } else {
//            nodesChannel.value = AdapterState(0, listOf(CONNECTION_NODE, connector.node), true)
        }
    }



    fun onAddNode() {
//        connectionChannel.postValue(Connector(CREATION_NODE, FAKE_PORT))
//        nodesChannel.value?.let {
//            nodeAfterCancel = it.items[it.currentItem]
//        }
//        nodesChannel.postValue(AdapterState(0, listOf(CREATION_NODE)))
    }

    fun deleteLink(link: Link) {

    }

    fun deleteNode() {

    }

    override fun onCleared() {
        Log.d(TAG, "onCleared")
        CoroutineScope(Dispatchers.IO).launch {
//            executor.release()
        }
        super.onCleared()
    }

    companion object {
        const val TAG = "BuilderViewModel"
    }
}

data class AdapterState<T>(
    val currentItem: Int,
    val items: List<T>,
    val animate: Boolean = false
)