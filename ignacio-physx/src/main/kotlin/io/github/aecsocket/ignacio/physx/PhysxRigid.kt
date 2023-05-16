package io.github.aecsocket.ignacio.physx

import io.github.aecsocket.ignacio.*
import physx.physics.PxRigidActor
import physx.physics.PxRigidDynamic
import physx.physics.PxRigidStatic

class PhysxCompoundChild internal constructor(
    val collider: PhysxCollider,
) : CompoundChild

private class MappingIterator<T, R>(
    val iter: Iterator<T>,
    val fn: (T) -> R,
) : Iterator<R> {
    override fun hasNext() = iter.hasNext()
    override fun next() = fn(iter.next())
}

private fun <T, R> Iterator<T>.map(fn: (T) -> R): Iterator<R> =
    MappingIterator(this, fn)

fun <VW : VolumeAccess> volumeAccessOf(body: PxRigidActor, volume: Volume<*, VW>): VW {
    val access = when (volume) {
        is Volume.Fixed -> {
            // add all initial colliders
            volume.colliders.forEach { collider ->
                collider as PhysxCollider
                body.attachShape(collider.shape)
            }
            VolumeAccess.Fixed
        }
        is Volume.Mutable -> object : VolumeAccess.Mutable.Write {
            // add initial collider
            var collider: PhysxCollider = (volume.collider as PhysxCollider).also { collider ->
                body.attachShape(collider.shape)
            }

            override fun invoke() = collider

            override fun invoke(collider: Collider) {
                collider as PhysxCollider
                body.detachShape(this.collider.shape)
                this.collider = collider
                body.attachShape(collider.shape)
            }
        }
        is Volume.Compound -> object : VolumeAccess.Compound.Write {
            // add all initial colliders
            val colliders: MutableSet<PhysxCollider> = volume.colliders.map { collider ->
                collider as PhysxCollider
                body.attachShape(collider.shape)
                collider
            }.toMutableSet()

            override fun iterator() = colliders.iterator()
                .map { PhysxCompoundChild(it) }

            override fun attach(collider: Collider): CompoundChild {
                collider as PhysxCollider
                if (colliders.contains(collider))
                    throw IllegalStateException("Volume already contains this collider")
                body.attachShape(collider.shape)
                colliders += collider
                return PhysxCompoundChild(collider)
            }

            override fun detach(child: CompoundChild) {
                child as PhysxCompoundChild
                if (!colliders.contains(child.collider))
                    throw IllegalStateException("Volume does not contain this collider")
                body.detachShape(child.collider.shape)
                colliders -= child.collider
            }
        }
    }
    // SAFETY:
    // Volume is a sealed class, so we know the type bounds explicitly
    // However we cannot reify the VR, VW (which would allow the type system to verify safety)
    // So we have to cast to tell the compiler it's ok
    @Suppress("UNCHECKED_CAST")
    return access as VW
}

sealed class PhysxRigid<AR, AW>(
    open val body: PxRigidActor,
) : RigidBody<AR, AW>
        where AR : PhysxRigid.Read<*>,
              AW : PhysxRigid.Write<*, *> {

    sealed interface Read<VR : VolumeAccess> : RigidBody.Read<VR> {
        val body: PxRigidActor

        override val position: Iso
            get() = body.globalPose.toIso()
    }

    sealed interface Write<VR : VolumeAccess, VW : VR> : Read<VR>, RigidBody.Write<VR, VW> {
        override var position: Iso
            get() = super.position
            set(value) = pushArena { arena ->
                body.globalPose = value.toPx(arena)
            }
    }
}

class PhysxFixed<VR : VolumeAccess, VW : VR>(
    override val body: PxRigidStatic,
    val volume: VW,
) : PhysxRigid<PhysxFixed<VR, VW>.Read, PhysxFixed<VR, VW>.Write>(body),
    FixedBody<PhysxFixed<VR, VW>.Read, PhysxFixed<VR, VW>.Write> {

    inner class Read : PhysxRigid.Read<VR>, FixedBody.Read<VR> {
        override val handle get() = this@PhysxFixed
        override val body get() = this@PhysxFixed.body
        override val volume get() = this@PhysxFixed.volume
    }

    inner class Write : PhysxRigid.Write<VR, VW>, FixedBody.Write<VR, VW> {
        override val handle get() = this@PhysxFixed
        override val body get() = this@PhysxFixed.body
        override val volume get() = this@PhysxFixed.volume
    }

    private val read = Read()
    private val write = Write()

    override fun <R> read(block: (Read) -> R) = block(read)

    override fun <R> write(block: (Write) -> R) = block(write)
}

class PhysxMoving<VR : VolumeAccess, VW : VR>(
    override val body: PxRigidDynamic,
    val volume: VW,
) : PhysxRigid<PhysxMoving<VR, VW>.Read, PhysxMoving<VR, VW>.Write>(body),
    MovingBody<PhysxMoving<VR, VW>.Read, PhysxMoving<VR, VW>.Write> {

    inner class Read : PhysxRigid.Read<VR>, MovingBody.Read<VR> {
        override val handle get() = this@PhysxMoving
        override val body get() = this@PhysxMoving.body
        override val volume get() = this@PhysxMoving.volume

        override val isSleeping: Boolean
            get() = body.isSleeping

        override val linearVelocity: Vec
            get() = body.linearVelocity.toVec()
    }

    inner class Write : PhysxRigid.Write<VR, VW>, MovingBody.Write<VR, VW> {
        override val handle get() = this@PhysxMoving
        override val body get() = this@PhysxMoving.body
        override val volume get() = this@PhysxMoving.volume

        override var isSleeping: Boolean
            get() = body.isSleeping
            set(value) {
                if (value) body.putToSleep()
                else body.wakeUp()
            }

        override var linearVelocity: Vec
            get() = body.linearVelocity.toVec()
            set(value) = pushArena { arena ->
                body.setLinearVelocity(value.toPx(arena), false)
            }
    }

    private val read = Read()
    private val write = Write()

    override fun <R> read(block: (Read) -> R) = block(read)

    override fun <R> write(block: (Write) -> R) = block(write)
}
