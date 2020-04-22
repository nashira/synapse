package com.rthqks.synapse.logic

sealed class PropertyDef<T>(
    val key: Property.Key<T>,
    val default: T
) {
    class Simple<T>(key: Property.Key<T>, default: T) : PropertyDef<T>(key, default)
    class IntRange(
        key: Property.Key<Int>,
        default: Int,
        val range: kotlin.ranges.IntRange
    ) : PropertyDef<Int>(key, default)

    class FloatRange(
        key: Property.Key<Float>,
        default: Float,
        val range: ClosedFloatingPointRange<Float>
    ) : PropertyDef<Float>(key, default)

    class List<T>(
        key: Property.Key<T>,
        default: T,
        val list: kotlin.collections.List<T>
    ) : PropertyDef<T>(key, default)
}
