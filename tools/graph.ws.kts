import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sign
import kotlin.math.sqrt
import kotlin.random.Random


class Graph(builder: Graph.() -> Unit = {}) {
    val nodes = mutableMapOf<String, Node>()
    val links = mutableMapOf<String, MutableSet<Node>>()
    val props = mutableMapOf<String, MutableMap<String, Any>>()

    init {
        builder()
    }

    operator fun invoke(block: Graph.() -> Unit) {
        block()
    }

    fun bfs(node: Node, block: (node: Node) -> Unit) {
        val visited = mutableSetOf<Node>()
        val deque = ArrayDeque<Node>()
        deque += node

        do {
            val n = deque.pollFirst() ?: return
            if (n !in visited) {
                visited += n
                links[n.id]?.let { deque += it }
                block(n)
            }
        } while (deque.isNotEmpty())
    }

    fun dfs(node: Node, block: (node: Node) -> Unit) {
        val visited = mutableSetOf<Node>()
        val deque = ArrayDeque<Node>()
        deque += node

        do {
            val n = deque.pollLast() ?: return
            if (n !in visited) {
                visited += n
                links[n]?.let { deque += it }
                block(n)
            }
        } while (deque.isNotEmpty())
    }

    fun node(id: String): Node {
        return nodes.getOrPut(id) { Node(id) }
    }

    fun node(id: String, block: Node.() -> Unit) {
        node(id).block()
    }


    inner class Node(val id: String) {

        infix fun linkTo(node: Node): Node {
            links.getOrPut(this.id) { mutableSetOf() } += node
            return node
        }

        infix fun linkFrom(node: Node): Node {
            links.getOrPut(node.id) { mutableSetOf() } += this
            return node
        }

//    operator fun String.invoke(block: Node.() -> Unit) {
//        node(this).apply(block)
//    }

        operator inline fun <T> get(key: String): T {
            return props[id]?.get(key) as T
        }

        operator fun set(key: String, value: Any) {
            props.getOrPut(id) { mutableMapOf() }[key] = value
        }

        fun onStart(block: () -> Unit) {
            block()
        }

        fun onStop(block: () -> Unit) {
            block()
        }

        fun on(input: String, block: () -> Unit) {
            block()
        }

        override fun toString(): String {
            return "Node($id)"
        }
    }
}

val g1 = Graph {
    val a = out<Int>("A")

//    onDelay(50, TimeUnit.MILLISECONDS) {
//        a.send(1)
//    }
}

val gg = Graph {
//    input("A")
//    input("B")
//    input("C")
//    output("E")
//    output("F")


}