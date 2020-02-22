package com.rthqks.synapse.util

import android.os.SystemClock
import android.view.View

fun throttleClick(timeout: Int = 1000, block: (View) -> Unit): (View) -> Unit {
    var lastTime = 0L
    return {
        val time = SystemClock.elapsedRealtime()
        if (time - lastTime > timeout) {
            lastTime = time
            block(it)
        }
    }
}