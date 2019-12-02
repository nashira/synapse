package xyz.rthqks.synapse.ui.browse

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import xyz.rthqks.synapse.data.SynapseDao
import xyz.rthqks.synapse.logic.Graph
import javax.inject.Inject

class GraphListViewModel @Inject constructor(
    private val dao: SynapseDao
): ViewModel() {
    val graphList = MutableLiveData<List<Graph>>()

    init {
        loadGraphs()
    }

    fun loadGraphs() {
        viewModelScope.launch(Dispatchers.IO) {
            graphList.postValue(dao.getGraphs())
        }
    }
}
