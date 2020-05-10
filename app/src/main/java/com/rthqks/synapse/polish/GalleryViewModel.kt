package com.rthqks.synapse.polish

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rthqks.synapse.assets.VideoStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

class GalleryViewModel @Inject constructor(
    private val videoStorage: VideoStorage
) : ViewModel() {
    val localVideos = MutableLiveData<List<VideoStorage.Video>>()

    fun loadVideos() {
        viewModelScope.launch(Dispatchers.IO) {
            val videos = videoStorage.getLocalVideos()
            localVideos.postValue(videos)
        }
    }
}
