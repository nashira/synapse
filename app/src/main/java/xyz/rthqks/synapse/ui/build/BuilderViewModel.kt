package xyz.rthqks.synapse.ui.build

import android.view.MotionEvent
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import javax.inject.Inject

class BuilderViewModel @Inject constructor() : ViewModel() {
    val onSwipeEnable = MutableLiveData<Consumable<MotionEvent>>()

    fun enableSwipe(event: MotionEvent) {
        onSwipeEnable.value = Consumable(event)
    }
}

class Consumable<T>(item: T) {
    private var item: T? = item

    fun consume(): T? {
        val item = this.item
        this.item = null
        return item
    }

    override fun toString(): String {
        return "Consumable(item=$item)"
    }


}