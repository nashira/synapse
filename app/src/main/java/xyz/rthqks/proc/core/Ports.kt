package xyz.rthqks.proc.core

import xyz.rthqks.proc.data.PortConfig

interface Port

class Input(
    val config: PortConfig
) : Port

class Output(
    val config: PortConfig
) : Port