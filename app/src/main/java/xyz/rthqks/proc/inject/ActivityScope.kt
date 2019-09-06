package xyz.rthqks.proc.inject

import javax.inject.Scope

import kotlin.annotation.MustBeDocumented
import kotlin.annotation.Retention

@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Scope
internal annotation class ActivityScope
