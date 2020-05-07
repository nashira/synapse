package com.rthqks.synapse.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

sealed class PropertyUi<T>(
    @StringRes val title: Int,
    @DrawableRes val icon: Int,
    val type: PropertyType
)
enum class PropertyType {
    VALUE,
    EXPANDED,
    TOGGLE,
    MENU,
    FLOAT_RANGE,
    INT_RANGE
}

data class Choice<T>(val item: T, @StringRes val label: Int, @DrawableRes val icon: Int)

//class ValueHolder<T>(
//    @StringRes title: Int,
//    @DrawableRes icon: Int
//) : PropertyHolder<T>(title, icon)

//class UriHolder(
//    @StringRes title: Int,
//    @DrawableRes icon: Int,
//    val mime: String
//) : PropertyHolder<Uri>(title, icon)
//
//class TextHolder(
//    @StringRes title: Int,
//    @DrawableRes icon: Int
//) : PropertyHolder<String>(title, icon)

class ChoiceUi<T>(
    @StringRes title: Int,
    @DrawableRes icon: Int,
    type: PropertyType,
    val choices: List<Choice<T>>
) : PropertyUi<T>(title, icon, type) {
}

class FloatRangeUi(
    @StringRes title: Int,
    @DrawableRes icon: Int,
    val range: ClosedFloatingPointRange<Float>
) : PropertyUi<Float>(title, icon, PropertyType.FLOAT_RANGE)

class IntRangeUi(
    @StringRes title: Int,
    @DrawableRes icon: Int,
    val range: IntRange
) : PropertyUi<Int>(title, icon, PropertyType.INT_RANGE)

fun <T> ExpandedUi(
    @StringRes title: Int,
    @DrawableRes icon: Int,
    vararg choices: Choice<T>
) = ChoiceUi(title, icon, PropertyType.EXPANDED, choices.toList())

fun <T> ToggleUi(
    @StringRes title: Int,
    @DrawableRes icon: Int,
    vararg choices: Choice<T>
) = ChoiceUi(title, icon, PropertyType.TOGGLE, choices.toList())

fun <T> MenuUi(
    @StringRes title: Int,
    @DrawableRes icon: Int,
    vararg choices: Choice<T>
) = ChoiceUi(title, icon, PropertyType.MENU, choices.toList())

fun RangeUi(
    @StringRes title: Int,
    @DrawableRes icon: Int,
    range: ClosedFloatingPointRange<Float>
): FloatRangeUi =
    FloatRangeUi(title, icon, range)

fun RangeUi(
    @StringRes title: Int,
    @DrawableRes icon: Int,
    range: IntRange
): IntRangeUi =
    IntRangeUi(title, icon, range)