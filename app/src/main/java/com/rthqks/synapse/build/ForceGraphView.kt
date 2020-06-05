package com.rthqks.synapse.build

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.rthqks.synapse.R
import com.rthqks.synapse.logic.Network
import com.rthqks.synapse.logic.Node
import java.lang.Math.random
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sign
import kotlin.math.sqrt

class ForceGraphView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var network: Network? = null
    private val nodes1 = mutableListOf<Vertex>()
    private val nodes2 = mutableListOf<Vertex>()
    private var nodes0 = nodes1
    private val paint = Paint()
    private val diameter: Float = resources.getDimension(R.dimen.graph_diameter)
    private val radius = diameter / 2f

    init {
        paint.color = resources.getColor(R.color.colorAccent, context.theme)
        paint.style = Paint.Style.FILL
        paint.isAntiAlias = true
    }

    fun setData(network: Network) {
        this.network = network
        val nodes = network.getNodes()
        nodes1.clear()
        nodes1.addAll(nodes.map { Vertex(it) })
        nodes2.clear()
        nodes2.addAll(nodes.map { Vertex(it) })

        repeat(100) {
            iterate()
        }
        invalidate()
    }

    fun iterate() {
//        iterate(nodes1, nodes2)
        if (nodes0 == nodes1) {
            iterate(nodes1, nodes2)
            nodes0 = nodes2
        } else {
            iterate(nodes2, nodes1)
            nodes0 = nodes1
        }
    }

    private fun iterate(from: List<Vertex>, to: List<Vertex>) {
        from.forEachIndexed { index, n1 ->
            var fx = 0f
            var fy = 0f
            from.forEach { n2 ->
                if (n1.node != n2.node) {
                    val dx = (n1.x - n2.x)
                    val dy = (n1.y - n2.y)
                    val d2 = max(dx * dx, 1f) + max(dy * dy, 1f)
                    val dist = sqrt(d2).let { it * it }
                    fx += dx.sign / dist
                    fy += dy.sign / dist
                }
            }

//            Log.d("ForceGraph", "$index x $fx y $fy")
            val K = 2000.0f
            val n3 = to[index]
            n3.x = n1.x + fx * K
            n3.y = n1.y + fy * K
        }

        val fromMap = from.associateBy { it.node.id }
        val toMap = to.associateBy { it.node.id}

        network?.getLinks()?.forEach {
            val n1 = fromMap[it.fromNodeId]!!
            val n2 = fromMap[it.toNodeId]!!

            val fx = n1.x - n2.x
            val fy = n1.y - n2.y

            val K = -0.001f
            val n3 = toMap[it.fromNodeId]!!
            n3.x += fx * K
            n3.y += fy * K

            val n4 = toMap[it.toNodeId]!!
            n4.x -= fx * K
            n4.y -= fy * K
        }
    }

    override fun onDraw(canvas: Canvas?) {
        canvas ?: return
        canvas.save()
        canvas.translate(width / 2f, height / 2f)

        nodes0.forEach {
            canvas.drawArc(
                it.x - radius,
                it.y - radius,
                it.x + radius,
                it.y + radius,
                0f,
                360f,
                false,
                paint
            )

            canvas.drawText(it.node.type, it.x, it.y, paint)
        }

        canvas.restore()
    }
}

private class Vertex(
    val node: Node,
    var x: Float = random().toFloat() * 100,
    var y: Float = random().toFloat() * 100
)