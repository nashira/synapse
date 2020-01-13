package com.rthqks.synapse.ui.build

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class TouchRecyclerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    override fun onInterceptTouchEvent(e: MotionEvent?): Boolean {
        return false
    }

    override fun onDraw(c: Canvas?) {
        super.onDraw(c)

        val l = layoutManager as LinearLayoutManager
    }
}