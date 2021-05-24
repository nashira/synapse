package com.rthqks.synapse.build

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.rthqks.flow.logic.Connector
import kotlin.math.abs

class TouchMediator(
    context: Context,
    private val callback: ((SwipeEvent) -> Unit)
) {
    private val handler = Handler(Looper.getMainLooper())
    private val swipeEvent = SwipeEvent(0)
    private var startX = 0f
    private var x = -1f
    private var didLongClick = false
    private var touchSlop: Int = 8
    private var longPressTimeout: Long = 0

    init {
        touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()
    }

    fun onTouch(view: View, event: MotionEvent, portConfig: Connector? = null): Boolean {
//        Log.d("TouchMediator", "onTouch $event")
        swipeEvent.portConfig = portConfig
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                view.drawableHotspotChanged(event.x, event.y)
                view.isPressed = true
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
                view.drawableHotspotChanged(event.x, event.y)
                swipeEvent.action = MotionEvent.ACTION_MOVE
                swipeEvent.x = event.rawX - x
                callback(swipeEvent)

                x = event.rawX
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                view.isPressed = false
                swipeEvent.action = MotionEvent.ACTION_UP
                callback(swipeEvent)

                x = -1f
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
    var x: Float = 0f,
    var portConfig: Connector? = null
)