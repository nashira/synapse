package xyz.rthqks.synapse.logic

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

class Properties {
    private val map = mutableMapOf<Property.Key<*>, Property<*>>()
    val size: Int = map.size
    val keys = map.keys
    val values = map.values

    @Suppress("UNCHECKED_CAST")
    operator fun <T> set(key: Property.Key<T>, value: T) {
        (map[key] as? Property<T>)?.let {
            it.value = value
        }
    }

    @Suppress("UNCHECKED_CAST")
    operator fun <T> get(key: Property.Key<T>): T {
        return map[key]?.value as T
    }

    fun <T> put(key: Property.Key<T>, property: Property<T>) {
        map[key] = property
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> find(key: Property.Key<T>): Property<T>? {
        return map[key] as? Property<T>
    }

    operator fun plus(other: Properties): Properties = Properties().also {
        it += this
        it += other
    }

    operator fun plusAssign(properties: Properties) {
        map.putAll(properties.map)
    }

    fun copyTo(properties: Properties): Properties = properties.also {
        map.mapValuesTo(it.map) { entry ->
            entry.value.copy()
        }
    }
}

data class Property<T>(
    val key: Key<T>,
    val type: Type<T>,
    var value: T,
    val requiresRestart: Boolean = false
) {

    data class Key<T>(val name: String)

    abstract class Type<T>(
        @StringRes val title: Int = 0,
        @DrawableRes val icon: Int = 0
    )

    companion object {
        fun RangeType(
            @StringRes title: Int,
            @DrawableRes icon: Int,
            range: ClosedFloatingPointRange<Float>
        ): FloatRangeType = FloatRangeType(title, icon, range)

        fun RangeType(
            @StringRes title: Int,
            @DrawableRes icon: Int,
            range: IntRange
        ): IntRangeType = IntRangeType(title, icon, range)
    }
}

class ChoiceType<T>(
    @StringRes title: Int,
    @DrawableRes icon: Int,
    vararg val choices: Choice<T>
) : Property.Type<T>(title, icon)

class FloatRangeType(
    @StringRes title: Int,
    @DrawableRes icon: Int,
    val range: ClosedFloatingPointRange<Float>
) : Property.Type<Float>(title, icon)

class IntRangeType(
    @StringRes title: Int,
    @DrawableRes icon: Int,
    val range: IntRange
) : Property.Type<Int>(title, icon)

data class Choice<T>(val item: T, @StringRes val label: Int)