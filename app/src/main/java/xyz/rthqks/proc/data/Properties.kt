package xyz.rthqks.proc.data

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes


abstract class Property<T>(
    val default: T,
    @StringRes val name: Int,
    @DrawableRes val icon: Int
)

class DiscreteProperty<T>(
    default: T,
    name: Int,
    icon: Int,
    val values: List<T>,
    val labels: List<Int>
) : Property<T>(default, name, icon)