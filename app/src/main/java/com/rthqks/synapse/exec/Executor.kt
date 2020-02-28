package com.rthqks.synapse.exec

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.actor

abstract class Executor(
    protected val context: ExecutionContext
) {
    protected val scope = CoroutineScope(Job() + context.dispatcher)
    @Suppress("UNCHECKED_CAST")
    private val actor = scope.actor<Cmd<*>> {
        for (cmd in channel) {
            Log.d(TAG, "executing $cmd")
            when (cmd) {
                is Cmd.Async -> {
                    val value = cmd.block()
                    (cmd.deferred as? CompletableDeferred<Any?>)?.complete(value)
                }
                is Cmd.Run -> cmd.block()
            }
        }
        Log.d(TAG, "actor closed")
    }

    protected suspend fun <T> async(block: suspend () -> T): Deferred<T> {
        val deferred = CompletableDeferred<T>()
        actor.send(Cmd.Async(deferred, block))
        return deferred
    }

    protected suspend fun run(block: suspend () -> Unit) {
        actor.send(Cmd.Run(block))
    }

    protected suspend fun <T> await(block: suspend () -> T): T {
        val deferred = CompletableDeferred<T>()
        actor.send(Cmd.Async(deferred, block))
        return deferred.await()
    }

    open suspend fun release() {
        actor.close()
        scope.cancel()
        scope.coroutineContext[Job]?.join()
    }

//    suspend fun init() = await(this::onInit)
//    suspend fun start() = await(this::onStart)
//    suspend fun stop() = await(this::onStop)
//    suspend fun release() = await(this::onRelease)
//    abstract suspend fun onInit()
//    abstract suspend fun onStart()
//    abstract suspend fun onStop()
//    abstract suspend fun onRelease()

    companion object {
        const val TAG = "Executor"
    }
}

private sealed class Cmd<T>(
    val block: suspend () -> T
) {
    class Run(block: suspend () -> Unit): Cmd<Unit>(block)
    class Async<T>(
        val deferred: Deferred<T>,
        block: suspend () -> T
    ) : Cmd<T>(block)
}