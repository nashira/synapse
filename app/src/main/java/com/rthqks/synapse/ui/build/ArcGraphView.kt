package com.rthqks.synapse.ui.build

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.rthqks.synapse.logic.Link
import com.rthqks.synapse.logic.Node
import kotlin.math.abs

class ArcGraphView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val nodes = mutableMapOf<Int, Node>()
    private var links = setOf<Link>()
    private val paint = Paint()

    init {
        paint.color = Color.BLACK
        paint.style = Paint.Style.STROKE
        paint.isAntiAlias = true
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
        canvas ?: return
        val x = width / 4f
        val dy = height / nodes.size
        val yOff = 0.5f * dy
        links.forEach {
            val from = nodes[it.fromNodeId]!!.position * dy
            val to = nodes[it.toNodeId]!!.position * dy
            val y = (from + to) / 2f
            val radius = abs(y - from)
            if (from < to) {
                canvas.drawArc(
                    x - radius,
                    y - radius + yOff,
                    x + radius,
                    y + radius + yOff,
                    270f,
                    180f,
                    false,
                    paint
                )
            } else {
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
            }
        }
    }
}