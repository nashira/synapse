package com.rthqks.synapse.build

import android.content.Context
import android.util.Log
import android.view.SurfaceView
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rthqks.synapse.assets.AssetManager
import com.rthqks.synapse.assets.VideoStorage
import com.rthqks.synapse.data.NetworkData
import com.rthqks.synapse.data.SynapseDao
import com.rthqks.synapse.exec.ExecutionContext
import com.rthqks.synapse.logic.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import javax.inject.Inject

class BuilderViewModel @Inject constructor(
    private val contxt: Context,
    private val videoStorage: VideoStorage,
    private val assetManager: AssetManager,
    private val logic: SyncLogic,
    private val dao: SynapseDao
) : ViewModel() {
    private var network: Network? = null
    private val context = ExecutionContext(contxt, videoStorage, assetManager)
    private val executor = BuildExecutor(context)

    val networkChannel = MutableLiveData<Network>()

    //    val connectionChannel = MutableLiveData<Connector>()
    val nodeChannel = MutableLiveData<Node>()
    val titleChannel = MutableLiveData<Int>()
    val menuChannel = MutableLiveData<Int>()

    fun setNetworkId(networkId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                network = logic.getNetwork(networkId)
                Log.d(TAG, "loaded: $network")
            }

            executor.setup()
            executor.initialize()
            executor.setBuildNetwork(network!!)

            networkChannel.postValue(network)
        }
    }

    fun setNodeId(nodeId: Int) {
        network?.getNode(nodeId)?.let {
            nodeChannel.value = it
        }
    }

    fun setSurfaceView(surfaceView: SurfaceView) {
        viewModelScope.launch {
            executor.setSurfaceView(surfaceView)
        }
    }

    fun setOutputPort(nodeId: Int, key: String) = viewModelScope.launch {
        executor.setOutputPort(nodeId, key)
    }

    fun startExecution() {
        Log.d(TAG, "resume")
        viewModelScope.launch {
            executor.resume()
        }
    }

    fun stopExecution() {
        Log.d(TAG, "pause")
        viewModelScope.launch {
            executor.pause()
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
            executor.removeAll()
            executor.release()
        }
        super.onCleared()
    }

    fun getConnectors(nodeId: Int): List<Connector> {
        return network?.getConnectors(nodeId) ?: emptyList()
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