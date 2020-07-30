package com.rthqks.flow.exec_dep.link

import com.rthqks.flow.gl.GlesManager

class MatrixEvent : Event() {
    val matrix = com.rthqks.flow.gl.GlesManager.identityMat()
}
