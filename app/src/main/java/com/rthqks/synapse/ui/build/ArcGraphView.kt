package com.rthqks.synapse.ui.build

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.core.graphics.transform
import androidx.recyclerview.widget.RecyclerView
import com.rthqks.synapse.R
import com.rthqks.synapse.logic.Link
import com.rthqks.synapse.logic.Node
import kotlin.math.abs


class ArcGraphView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    private val nodes = mutableMapOf<Int, Node>()
    private var links = setOf<Link>()
    private val paint = Paint()

    init {
        paint.color = 0x77000000 or (0x00ffffff and context.getColor(R.color.grey))
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 6f
        paint.isAntiAlias = true
    }

    override fun onInterceptTouchEvent(e: MotionEvent?): Boolean {
        return false
    }

    fun setData(
        sorted: List<Node>,
        links: Set<Link>
    ) {
        nodes.clear()
        sorted.forEach {
            nodes[it.id] = it
        }
        this.links = links
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        canvas ?: return
        val dy = getChildAt(0)?.height ?: return
        val scroll = computeVerticalScrollOffset()
        val x = width - dy
        val yOff = 0.5f * dy - scroll
        links.forEach {
            val from = nodes[it.fromNodeId]!!.position * dy
            val to = nodes[it.toNodeId]!!.position * dy
            val y = (from + to) / 2f
            val radius = abs(y - from)

//            if (from < to) {
//                canvas.drawArc(
//                    x - radius,
//                    y - radius + yOff,
//                    x + radius,
//                    y + radius + yOff,
//                    270f,
//                    180f,
//                    false,
//                    paint
//                )
//            } else {
                canvas.drawArc(
                    x - radius,
                    y - radius + yOff,
                    x + radius,
                    y + radius + yOff,
                    90f,
                    180f,
                    false,
                    paint
                )
//            }
        }
    }
}