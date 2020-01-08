package com.rthqks.synapse.util

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SuspendableGet<T> {
    private var item: T? = null
    private val mutex = Mutex(true)

    suspend fun set(item: T?) {
        if (!mutex.isLocked) {
            mutex.lock()
        }
        this.item = item

        item?.let {
            mutex.unlock()
        }
    }

    fun has(): Boolean = item != null

    suspend fun get(): T = mutex.withLock {
        item!!
    }
}