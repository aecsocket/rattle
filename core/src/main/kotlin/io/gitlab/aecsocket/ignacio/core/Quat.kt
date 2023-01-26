package io.gitlab.aecsocket.ignacio.core

data class Quat(val x: IgScalar, val y: IgScalar, val z: IgScalar, val w: IgScalar) {
    companion object {
        val Identity = Quat(0.0, 0.0, 0.0, 1.0)
    }

    fun asString(fmt: String = "%f") = "($fmt + ${fmt}i + ${fmt}j + ${fmt}k)".format(w, x, y, z)

    override fun toString() = asString("%.3f")
}
