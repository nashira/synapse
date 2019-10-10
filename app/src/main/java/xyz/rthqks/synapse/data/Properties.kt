package xyz.rthqks.synapse.data

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

abstract class Property(
    val key: String,
    val type: ValueType,
    val default: Any,
    @StringRes val name: Int = 0,
    @DrawableRes val icon: Int = 0
)

class DiscreteProperty(
    key: String,
    type: ValueType,
    default: Any,
    val values: List<Any>,
    val labels: List<Int>,
    @StringRes name: Int = 0,
    @DrawableRes icon: Int = 0
) : Property(key, type, default, name, icon)

enum class ValueType {
    Int,
    Long,
    Float,
    Double,
    String
}