package io.github.aecsocket.rattle

sealed interface AxisState {
    /* TODO: Kotlin 1.9 data */ object Free : AxisState

    data class Limited(
        val min: Real,
        val max: Real,
    ) : AxisState

    /* TODO: Kotlin 1.9 data */ object Locked : AxisState
}

enum class JointAxis {
    X,
    Y,
    Z,
    ANG_X,
    ANG_Y,
    ANG_Z,
}

data class JointAxes(
    val x: AxisState = AxisState.Free,
    val y: AxisState = AxisState.Free,
    val z: AxisState = AxisState.Free,
    val angX: AxisState = AxisState.Free,
    val angY: AxisState = AxisState.Free,
    val angZ: AxisState = AxisState.Free,
) : Iterable<JointAxes.Entry> {
    data class Entry(
        val axis: JointAxis,
        val state: AxisState,
    )

    override fun iterator() = object : Iterator<Entry> {
        var cursor = 0

        override fun hasNext() = cursor < 5

        override fun next(): Entry {
            return when (cursor) {
                0 -> Entry(JointAxis.X, x)
                1 -> Entry(JointAxis.Y , y)
                2 -> Entry(JointAxis.Z , z)
                3 -> Entry(JointAxis.ANG_X , angX)
                4 -> Entry(JointAxis.ANG_Y , angY)
                5 -> Entry(JointAxis.ANG_Z , angZ)
                else -> throw NoSuchElementException()
            }.also { cursor += 1 }
        }
    }
}

interface Joint : Destroyable {

}

interface ImpulseJoint : Joint

interface MultibodyJoint : Joint
