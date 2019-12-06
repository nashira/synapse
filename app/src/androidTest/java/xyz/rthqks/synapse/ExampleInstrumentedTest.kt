package xyz.rthqks.synapse

import androidx.test.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getTargetContext()
        assertEquals("xyz.rthqks.synapse", appContext.packageName)
    }
//
//    @Test
//    fun channelBenchmark() {
//        val dispatcher = Executors.newFixedThreadPool(8).asCoroutineDispatcher()
//        val scope = CoroutineScope(SupervisorJob() + dispatcher)
//        val connection = AudioConnection()
//
//        runBlocking {
//            delay(10000)
//        }
//        runTest(scope, connection,10000)
//        runTest(scope, connection,100000)
//        runTest(scope, connection,100000)
//        runTest(scope, connection,100000)
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
}
