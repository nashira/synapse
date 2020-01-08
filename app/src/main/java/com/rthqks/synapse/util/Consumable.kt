package com.rthqks.synapse.util

class Consumable<T>(var item: T? = null) {
    fun consume(): T? {
        val item = this.item
        this.item = null
        return item
    }

    override fun toString(): String {
        return "Consumable(item=$item)"
    }


}