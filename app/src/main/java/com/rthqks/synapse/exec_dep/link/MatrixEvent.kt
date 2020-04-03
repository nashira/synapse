package com.rthqks.synapse.exec_dep.link

import com.rthqks.synapse.gl.GlesManager

class MatrixEvent : Event() {
    val matrix = GlesManager.identityMat()
}
