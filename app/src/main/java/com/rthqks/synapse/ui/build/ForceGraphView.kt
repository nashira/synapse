package com.rthqks.synapse.ui.build

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
    }

    fun iterate() {
        iterate(nodes1, nodes2)
//        if (nodes0 == nodes1) {
//            iterate(nodes1, nodes2)
//            nodes0 = nodes2
//        } else {
//            iterate(nodes2, nodes1)
//            nodes0 = nodes1
//        }
        invalidate()
    }

    private fun iterate(from: List<Vertex>, to: MutableList<Vertex>) {
        val map = mutableMapOf<Int, Vertex>()

        from.forEachIndexed { index, n1 ->
            var fx = 0f
            var fy = 0f
            from.forEach { n2 ->
                if (n1.node != n2.node) {
                    val dx = (n1.x - n2.x).let { it.sign * max(1f, abs(it)) }
                    val dy = (n1.y - n2.y).let { it.sign * max(1f, abs(it)) }
                    val d2 = dx * dx + dy * dy
                    val dist = sqrt(d2)
                    fx += dx / (dist * dist * dist)
                    fy += dy / (dist * dist * dist)
                }
            }

//            Log.d("ForceGraph", "$index x $fx y $fy")
            val K = 100.0f
            val n3 = to[index]
            n3.x = n1.x + fx * K
            n3.y = n1.y + fy * K
            map[n3.node.id] = n3
        }

//
//        nodes.clear()
//        nodes0.forEach {
//            this.nodes[it.node.id] = it
//        }

        to.forEachIndexed { i, vert ->
            var fx = 0f
            var fy = 0f
            val n1 = vert

            network?.getLinks(vert.node.id)?.forEach {
                val n2 = map[it.toNodeId]!!
                val dx = (n1.x - n2.x).let { it.sign * max(1f, abs(it)) }
                val dy = (n1.y - n2.y).let { it.sign * max(1f, abs(it)) }
                fx += dx
                fy += dy
            }

            val K = -0.04f
            val n3 = from[i]
            n3.x = n1.x + fx * K
            n3.y = n1.y + fy * K
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
        }

        canvas.restore()
    }
}

private class Vertex(
    val node: Node,
    var x: Float = random().toFloat(),
    var y: Float = random().toFloat()
)