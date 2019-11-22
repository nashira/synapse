package xyz.rthqks.synapse.ui.build

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs

class TouchMediator(
    context: Context,
    private var callback: ((SwipeEvent) -> Unit)
) : View.OnTouchListener {
    private val handler = Handler(Looper.getMainLooper())
    private var startX = 0f
    private var x = -1f
    private var didLongClick = false
    private var touchSlop: Int = 8
    private var longPressTimeout: Long = 0
    private val swipeEvent = SwipeEvent(0)

    init {
        touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()
    }

    override fun onTouch(view: View, event: MotionEvent): Boolean {
//        Log.d("TouchMediator", "onTouch $event")
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                swipeEvent.action = MotionEvent.ACTION_DOWN
                callback(swipeEvent)

                x = event.rawX
                startX = x
                didLongClick = false
                handler.postDelayed(
                    {
                        if (x != -1f && abs(x - startX) < touchSlop) {
                            didLongClick = view.performLongClick()
                        }
                    },
                    longPressTimeout
                )
            }
            MotionEvent.ACTION_MOVE -> {
                swipeEvent.action = MotionEvent.ACTION_MOVE
                swipeEvent.x = event.rawX - x
                callback(swipeEvent)

                x = event.rawX
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                swipeEvent.action = MotionEvent.ACTION_UP
                callback(swipeEvent)

                x = -1f
                Log.d(NodeFragment.TAG, "total move ${abs(event.rawX - startX)}")
                if (!didLongClick && abs(event.rawX - startX) < touchSlop) {
                    view.performClick()
                }
            }
        }
        return !didLongClick
    }
}

class SwipeEvent(
    var action: Int,
    var x: Float = 0f
)