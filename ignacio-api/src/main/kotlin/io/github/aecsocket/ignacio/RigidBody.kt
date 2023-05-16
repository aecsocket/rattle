package io.github.aecsocket.ignacio

import java.util.function.Consumer

sealed interface Gravity {
    data object Enabled : Gravity

    data object Disabled : Gravity

    data class Scaled(val scale: Real) : Gravity
}

interface RigidBody<AR, AW>
        where AR : RigidBody.Read<*>,
              AW : RigidBody.Write<*, *> {
    fun <R> read(block: (AR) -> R): R

    fun read(block: Consumer<AR>) = read { block.accept(it) }

    fun <R> write(block: (AW) -> R): R

    fun write(block: Consumer<AW>) = write { block.accept(it) }

    interface Read<VR : VolumeAccess> {
        val handle: RigidBody<out Read<VR>, *>

        val volume: VR

        val position: Iso
    }

    interface Write<VR : VolumeAccess, VW : VR> : Read<VR> {
        override val handle: RigidBody<out Read<VR>, out Write<VR, VW>>

        override val volume: VW

        override var position: Iso
    }
}

interface FixedBody<AR, AW> : RigidBody<AR, AW>
        where AR : FixedBody.Read<*>,
              AW : FixedBody.Write<*, *> {
    interface Read<VR : VolumeAccess> : RigidBody.Read<VR>

    interface Write<VR : VolumeAccess, VW : VR> : Read<VR>, RigidBody.Write<VR, VW>
}

interface MovingBody<AR, AW> : RigidBody<AR, AW>
        where AR : MovingBody.Read<*>,
              AW : MovingBody.Write<*, *> {
    interface Read<VR : VolumeAccess> : RigidBody.Read<VR> {
        val isSleeping: Boolean

        val linearVelocity: Vec
    }

    interface Write<VR : VolumeAccess, VW : VR> : Read<VR>, RigidBody.Write<VR, VW> {
        override var isSleeping: Boolean

        override var linearVelocity: Vec
    }
}
