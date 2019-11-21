package xyz.rthqks.synapse.ui.build

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import androidx.viewpager.widget.ViewPager


class LockableViewPager : ViewPager {
    var swipeable = false

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    override fun onTouchEvent(event: MotionEvent): Boolean {
        Log.d("VP", "onTouch $event")
        return if (swipeable) {
            super.onTouchEvent(event)
        } else false
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
//        Log.d("VP", "onIntercept $event")
        return if (swipeable) {
            super.onInterceptTouchEvent(event)
        } else false
    }
}