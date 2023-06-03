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

/**
 * A key used to index into a [PhysicsSpace] to gain a reference, mutable or immutable, to an [ImpulseJoint].
 */
interface ImpulseJointKey

/**
 * A key used to index into a [PhysicsSpace] to gain a reference, mutable or immutable, to a [MultibodyJoint].
 */
interface MultibodyJointKey

/*
/**
 * A physical volume in space which can be collided with by other physics structures. This holds a shape
 * and physics properties, and may be attached to a [PhysicsSpace] to simulate it inside that space.
 * A collider may also be attached (parented) to a [RigidBody], which will make the collider determine its
 * position based on its parent body.
 */
object Collider {
    /**
     * Immutable interface for a [Collider].
     */
    interface Read {
        /**
         * The shape.
         */
        val shape: Shape

        /**
         * The physics properties.
         */
        val material: PhysicsMaterial

        /**
         * The **absolute** position of the collider in the physics space, i.e. not relative to its parent body.
         */
        val position: Iso

        /**
         * The physics mode.
         */
        val physicsMode: PhysicsMode

        /**
         * The position of the collider **relative to its parent body**. Even if the collider has no parent, this will
         * keep the last set relative position.
         */
        val relativePosition: Iso

        /**
         * The handle of which body this collider will follow (see [Collider]).
         */
        val parent: RigidBodyKey?

        /**
         * The world-space bounding box of this collider, determined by its shape and position.
         */
        fun bounds(): Aabb
    }

    /**
     * Mutable interface for a [Collider].
     */
    interface Write : Read {
        override var shape: Shape

        override var material: PhysicsMaterial

        override var position: Iso

        override var physicsMode: PhysicsMode

        override var relativePosition: Iso
    }

    /**
     * Mutable owned interface for a [Collider].
     */
    interface Own : Write, Destroyable
}

 */

interface Joint {
    interface Mut : Joint {

    }
}

interface ImpulseJoint : Joint {
    interface Mut : ImpulseJoint, Joint.Mut {

    }

    interface Own : Mut, Destroyable
}

interface MultibodyJoint : Joint {
    interface Mut : MultibodyJoint, Joint.Mut {

    }

    interface Own : Mut, Destroyable
}
