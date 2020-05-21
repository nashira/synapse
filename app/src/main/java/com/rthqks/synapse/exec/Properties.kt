package com.rthqks.synapse.exec

import com.rthqks.synapse.logic.Property

class Properties(
    private val map: Map<String, Property>
) {
    @Suppress("UNCHECKED_CAST")
    operator fun <T: Any> get(key: Property.Key<T>): T = map[key.name]?.value as T
}