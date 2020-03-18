package com.rthqks.synapse

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.rthqks.synapse.exec.ExecutionContext
import com.rthqks.synapse.exec2.NetworkExecutor
import com.rthqks.synapse.polish.Effects
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.util.concurrent.Executors

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {

    @Test
    fun testNodeExec() {
        val c = mock<ExecutionContext>() {
            on { dispatcher } doReturn Executors.newFixedThreadPool(4).asCoroutineDispatcher()
        }

        val n = NetworkExecutor(c)

        runBlocking {
            n.network = Effects.none.network
            println("add nodes")
            n.addAllNodes()
            println("add links")
            n.addAllLinks()
            delay(1000)
            println("remove links")
            n.removeAllLinks()
            println("remove nodes")
            n.removeAllNodes()
            println("done")
        }

        println(n)
    }
}
