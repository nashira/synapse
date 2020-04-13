package com.rthqks.synapse.exec

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.actor

abstract class Executor(
    protected val context: ExecutionContext
) {
    protected val scope = CoroutineScope(Job() + context.dispatcher)
    private val actor = scope.actor<suspend () -> Unit> {
        for (cmd in channel) {
            cmd()
        }
//        Log.d(TAG, "actor closed")
    }

    protected suspend fun <T> async(block: suspend () -> T): Deferred<T> {
        val deferred = CompletableDeferred<T>()
        actor.send {
            deferred.complete(block())
        }
        return deferred
    }

    protected suspend fun exec(block: suspend () -> Unit) {
        actor.send(block)
    }

    protected suspend fun <T> await(block: suspend () -> T): T {
        val deferred = CompletableDeferred<T>()
        actor.send {
            deferred.complete(block())
        }
        return deferred.await()
    }

    open suspend fun release() {
        val done = async {  }
        actor.close()
        done.await()
        scope.cancel()
        scope.coroutineContext[Job]?.join()
    }

    companion object {
        const val TAG = "Executor"
    }
}