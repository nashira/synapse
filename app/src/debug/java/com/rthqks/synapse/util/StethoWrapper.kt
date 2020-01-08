package com.rthqks.synapse.util

import android.content.Context
import com.facebook.stetho.Stetho

object StethoWrapper {
    fun init(context: Context) {
        Stetho.initializeWithDefaults(context)
    }
}