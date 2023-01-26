package io.gitlab.aecsocket.ignacio.core.math

data class Mat3(
    val n00: Double, val n01: Double, val n02: Double,
    val n10: Double, val n11: Double, val n12: Double,
    val n20: Double, val n21: Double, val n22: Double,
) {
    companion object {
        val Identity = Mat3(
            1.0, 0.0, 0.0,
            0.0, 1.0, 0.0,
            0.0, 0.0, 1.0
        )

        val Zero = Mat3(
            0.0, 0.0, 0.0,
            0.0, 0.0, 0.0,
            0.0, 0.0, 0.0
        )
    }

    @JvmName("mul")
    operator fun times(m: Mat3) = Mat3(
        n00*m.n00 + n01*m.n10 + n02*m.n20,  n00*m.n01 + n01*m.n11 + n02*m.n21,  n00*m.n02 + n01*m.n12 + n02*m.n22,
        n10*m.n00 + n11*m.n10 + n12*m.n20,  n10*m.n01 + n11*m.n11 + n12*m.n21,  n10*m.n02 + n11*m.n12 + n12*m.n22,
        n20*m.n00 + n21*m.n10 + n22*m.n20,  n20*m.n01 + n21*m.n11 + n22*m.n21,  n20*m.n02 + n21*m.n12 + n22*m.n22,
    )
    @JvmName("mul")
    operator fun times(v: Vec3) = Vec3(
        n00*v.x + n01*v.y + n02*v.z,
        n10*v.x + n11*v.y + n12*v.z,
        n20*v.x + n21*v.y + n22*v.z,
    )
    @JvmName("mul")
    operator fun times(s: Double) = Mat3(
        n00*s, n01*s, n02*s,
        n10*s, n11*s, n12*s,
        n20*s, n21*s, n22*s,
    )

    fun asString(fmt: String = "%f") = """Mat3(
  $fmt $fmt $fmt
  $fmt $fmt $fmt
  $fmt $fmt $fmt
)""".format(
        n00, n01, n02,
        n10, n11, n12,
        n20, n21, n22,
    )

    override fun toString() = asString(DECIMAL_FORMAT)
}
