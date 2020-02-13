package com.rthqks.synapse.ui.browse

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rthqks.synapse.data.SynapseDao
import com.rthqks.synapse.logic.Network
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

class NetworkListViewModel @Inject constructor(
    private val dao: SynapseDao
): ViewModel() {
    val networkList = MutableLiveData<List<Network>>()

    init {
        loadNetworks()
    }

    fun loadNetworks() {
        viewModelScope.launch(Dispatchers.IO) {
            networkList.postValue(dao.getNetworks())
        }
    }
}
