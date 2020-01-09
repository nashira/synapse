package com.rthqks.synapse

import com.rthqks.synapse.exec.edge.*
import kotlinx.coroutines.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun keyEquality() {
        val k1 = Connection.Key<AudioConfig, AudioEvent>("foo")
        val k2 = Connection.Key<AudioConfig, AudioEvent>("bar")
        val k3 = Connection.Key<VideoConfig, VideoEvent>("foo")
        val k4 = Connection.Key<VideoConfig, VideoEvent>("bar")
        assertNotEquals(k1, k2)
        assertNotEquals(k3, k4)
        assertEquals(k1, k3)
        assertEquals(k2, k4)
    }

    @Test
    fun completableDeferred() {
        val c = CompletableDeferred<Int>()
        runBlocking {
            launch {
                println("await 1 ${c.await()}")
                println("await 2 ${c.await()}")
            }

            c.complete(1)
        }
    }

    @Test
    fun state() {
        val s = State()
        println("created ${s.created.satisfied}")
        s.create.satisfied = true
        println("created ${s.created.satisfied}")

        val f = Foo()
        val a = f.getId()
        val b = f.getId()
        f.set(a, true)
        f.set(b, true)
        val c = a or b
        println(f.state.toString(2))
        println(f.state and c)
    }

    class Foo {
        private var id = 1
        var state: Int = 0

        fun getId(): Int {
            val old = id
            id = id shl 1
            return old
        }

        fun set(id: Int, sat: Boolean) {
            val mask = if (sat) 1 shl id else 0
            state = state or mask
        }
    }

    class Dep(vararg deps: Dep) {
        private val deps = deps.toList()
        var satisfied = false
            get() = if (deps.isNotEmpty()) deps.all { it.satisfied } else field
    }

    class State {
        val create = Dep()
        val created = Dep(create)
        val surface = Dep()
        val ready = Dep(created, surface)
        val start = Dep()
        val started = Dep(ready, start)
    }

//
//
//    @Test
//    fun testMultiChannel() {
//        var ID = 1
//        val m = MultiConsumer(2) { TestEvent(ID++) }
//        runBlocking {
//            val i = m.dequeue()
//            println("received $i")
//            val c1 = m.consumer()
//            val c2 = m.consumer()
//            val c3 = m.consumer()
//            launch {
//                delay(1000)
//                val d = c1.receive()
//                println("c1 r $d")
//                c1.send(d)
//                println("c1 r done")
//            }
//            launch {
//                val d = c2.receive()
//                c3.receive()
//                c3.send(d)
//                delay(1000)
//                println("c2 r $d")
//                c2.send(d)
//                println("c2 r done")
//            }
//            launch {
//                println("m.p")
//                val d = m.dequeue()
//                println("m.p $d")
//                m.queue(d)
//                println("m.p done")
//            }
//            m.queue(i)
//        }
//    }
//
//    data class TestEvent(val id: Int) : Event()
//
//
//    @Test
//    fun channelBenchmark() {
//        val dispatcher = Executors.newFixedThreadPool(8).asCoroutineDispatcher()
//        val scope = CoroutineScope(SupervisorJob() + dispatcher)
//        val connection = AudioConnection()
//
//        runBlocking {
//            delay(1000)
//        }
//        runTest(scope, connection,10000)
//        runTest(scope, connection,100000)
////        runTest(scope, connection,100000)
////        runTest(scope, connection,100000)
//    }
//
//    private fun runTest(
//        scope: CoroutineScope,
//        connection: AudioConnection,
//        num: Int
//    ) {
//        val receiveJob = receive(scope, connection)
//
//        val sendJob = send(scope, connection, num)
//
//        runBlocking {
//            val time = measureNanoTime {
//                receiveJob.join()
//                sendJob.join()
//            }
//            println("elapsed $time")
//        }
//    }
//
//    private fun receive(
//        scope: CoroutineScope,
//        connection: AudioConnection
//    ): Job {
//        return scope.launch {
//            var event = connection.acquire()
//            while (!event.eos) {
//                connection.release(event)
//                event = connection.acquire()
//            }
//        }
//    }
//
//    private fun send(
//        scope: CoroutineScope,
//        connection: AudioConnection,
//        num: Int
//    ): Job {
//        return scope.launch {
//            repeat(num) {
//                val event = connection.dequeue()
//                event.eos = false
//                connection.queue(event)
//            }
//            val event = connection.dequeue()
//            event.eos = true
//            connection.queue(event)
//        }
//    }

    @Test
    fun joinFromOtherScope() {
        val parentJob = Job()
        val scope1 = CoroutineScope(Job())
        val scope2 = CoroutineScope(parentJob)
        val job1 = scope1.launch {
            foo(scope2)
        }
        runBlocking {
            println("*******")
            job1.join()
            println("^^^^^^^")
            parentJob.join()
        }
    }

    suspend fun foo(scope: CoroutineScope) {
        val job1 = scope.launch { delay(1000); println("scope2 job1") }
        val job2 = scope.launch { delay(3000); println("scope2 job2") }
        job1.join()
    }


    @Test
    fun coroutine() {
        val job1 = Job()
        val scope1 = CoroutineScope(job1)
        val job2 = Job()
        val scope2 = CoroutineScope(job2)

        runBlocking {
            println("start")
            val l1 = scope1.launch {
                println("scope1 launch")
                val l2 = scope2.launch {
                    println("scope2 launch")
                    println("scope2 finish")
                }
                println("scope1 finish")
            }
            println("finish")
            l1.join()
            println("l1 join")
        }
        println("done blocking")
    }

    @Test
    fun graphCoroutines() {
        runBlocking {
            val gTest = GTest()
            gTest.initialize()
            gTest.start()
            delay(2000)
            gTest.stop()
            gTest.release()
//            gTest.initialize()
        }
        println("done blocking")
    }
}

class GTest {
    val graphJob = SupervisorJob()
    val glDispatcher = newSingleThreadContext("GLDispatcher")
    val scope = CoroutineScope(Dispatchers.Default + graphJob)
    val nodes = (1..5).map { NTest(glDispatcher, it) }

    suspend fun initialize() {
        println("initialize")
        val job = Job(graphJob)
        val ex = CoroutineExceptionHandler { coroutineContext, throwable ->
            println("caught exception")
//            throw throwable
        }
        nodes.map { scope.launch(job) { it.init() } }.forEach { it.join() }

        println("initialized")
    }

    suspend fun start() {
        println("start")
        val startJob = Job()
        parallel { it.start() }
        println("started")
    }

    suspend fun stop() {
        println("stop")
//        nodes.forEach { it.stop() }
//        nodes.map { scope.launch { it.stop() } }.forEach { it.join() }
        parallelJoin { it.stop() }
        println("stopped")
    }

    suspend fun release() {
        println("release")
        nodes.forEach { it.release() }
        println("released")
    }

    private suspend fun parallel(block: suspend (node: NTest) -> Unit) {
        nodes.forEach { scope.launch { block(it) } }
    }

    private suspend fun parallelJoin(block: suspend (node: NTest) -> Unit) {
        nodes.map { scope.launch { block(it) } }.forEach { it.join() }
    }
}

class NTest(
    private val dispatch: ExecutorCoroutineDispatcher,
    val id: Int
) {

    private var running: Boolean = false
    private var startJob: Job? = null

    suspend fun init() = coroutineScope {
        println("init $id")
        launch {
            delay((8 - id) * 100L)
            println("other  $id ${Thread.currentThread().name}")
        }
        launch(dispatch) {
            if (id == 3) {
//                throw RuntimeException("kaboom")
            }
            delay((5 - id) * 100L)
            println("flakey $id ${Thread.currentThread().name}")
        }
        println("inited $id")
        Unit
    }


    suspend fun start() {
        println("start $id")
//        startJob = launch {
        running = true
        while (running) {
            delay(400)
            println("run $id ${Thread.currentThread().name}")
            if (Math.random() < 0.3) {
//                    throw RuntimeException("random error $id")
            }
//            }
        }
        println("started $id")
    }

    suspend fun stop() {
        println("stop $id")
        startJob?.cancelAndJoin()
        running = false
        println("stopped $id")
    }

    suspend fun release() {
        println("release $id")
        println("released $id")
    }
}