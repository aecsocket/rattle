package io.gitlab.aecsocket.ignacio.core

data class Quat(val x: IgScalar, val y: IgScalar, val z: IgScalar, val w: IgScalar) {
    companion object {
        val Identity = Quat(0.0, 0.0, 0.0, 1.0)
    }
}
