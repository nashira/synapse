package xyz.rthqks.synapse.util

import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class SuspendableGet<T> {
    private var continuation: Continuation<T>? = null
    private var item: T? = null

    fun set(item: T?) {
        this.item = item
        item?.let {
            continuation?.resume(it)
            continuation = null
        }
    }

    suspend fun get(): T = item ?: suspendCoroutine {
        continuation = it
    }
}