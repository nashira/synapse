package xyz.rthqks.synapse.ui.build

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import javax.inject.Inject

class BuilderViewModel @Inject constructor() : ViewModel() {
    private val consumable = Consumable<SwipeEvent>()
    val onSwipeEvent = MutableLiveData<Consumable<SwipeEvent>>()

    fun swipeEvent(event: SwipeEvent) {
        consumable.item = event
        onSwipeEvent.value = consumable
    }
}

class Consumable<T>(var item: T? = null) {
    fun consume(): T? {
        val item = this.item
        this.item = null
        return item
    }

    override fun toString(): String {
        return "Consumable(item=$item)"
    }


}