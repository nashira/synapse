package com.rthqks.synapse.ui.edit

import android.net.Uri
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.rthqks.synapse.data.GraphData
import com.rthqks.synapse.data.NodeData
import com.rthqks.synapse.data.PropertyData
import com.rthqks.synapse.data.SynapseDao
import com.rthqks.synapse.util.Consumable
import javax.inject.Inject

class EditGraphViewModel @Inject constructor(
    private val dao: SynapseDao
) : ViewModel() {

    val onAddNodeClicked = MutableLiveData<Unit>()
    lateinit var graph: GraphData
    val onNodeAdded = MutableLiveData<NodeData>()
    val onPortSelected = MutableLiveData<Unit>()
    val onSelectFile = MutableLiveData<Consumable<PropertyData>>()

    fun setGraphName(name: String) {
        graph.name = name
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertGraph(graph)
            Log.d(TAG, "saved: $graph")
        }
    }

    fun saveProperty(property: PropertyData) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertProperty(property)
            Log.d(TAG, "saved: $property")
        }
    }

    fun selectFileFor(data: PropertyData) {
        onSelectFile.value = Consumable(data)
    }

    fun onFileSelected(data: Uri?, config: PropertyData) {
        data?.let {
            config.value = it.toString()
            saveProperty(config)
        }
    }

    companion object {
        private val TAG = EditGraphViewModel::class.java.simpleName
    }
}
