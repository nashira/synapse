package com.rthqks.synapse.exec.link

import com.rthqks.synapse.gl.GlesManager

class MatrixEvent : Event() {
    val matrix = GlesManager.identityMat()
}
