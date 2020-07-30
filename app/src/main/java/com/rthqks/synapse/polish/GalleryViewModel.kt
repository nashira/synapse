package com.rthqks.synapse.polish

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rthqks.flow.assets.VideoStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

class GalleryViewModel @Inject constructor(
    private val videoStorage: com.rthqks.flow.assets.VideoStorage
) : ViewModel() {
    val localVideos = MutableLiveData<List<com.rthqks.flow.assets.VideoStorage.Video>>()

    fun loadVideos() {
        viewModelScope.launch(Dispatchers.IO) {
            val videos = videoStorage.getLocalVideos()
            localVideos.postValue(videos)
        }
    }
}
