package xyz.rthqks.synapse.core

import xyz.rthqks.synapse.data.PortConfig

interface Port

class Input(
    val config: PortConfig
) : Port

class Output(
    val config: PortConfig
) : Port