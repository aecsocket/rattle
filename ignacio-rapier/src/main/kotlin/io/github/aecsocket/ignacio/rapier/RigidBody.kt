package io.github.aecsocket.ignacio.rapier

import io.github.aecsocket.ignacio.DestroyFlag
import io.github.aecsocket.ignacio.Iso

//@JvmInline
//value class RigidBodyHandle(val id: Long)
//
//class RapierRigidBody internal constructor(
//    val space: RapierSpace,
//    val handle: RigidBodyHandle,
//) : FixedBody, MovingBody {
//    private val destroyed = DestroyFlag()
//
//    override fun destroy() {
//        destroyed()
//        val body = space.rigidBodySet.remove(
//            handle.id,
//            space.islands,
//            space.colliderSet,
//            space.impulseJointSet,
//            space.multibodyJointSet,
//            true,
//        ) ?: return
//        body.drop()
//    }
//
//    override var position: Iso
//        get() = pushArena { arena ->
//            space.rigidBodySet.index(handle.id).getPosition(arena).asIso()
//        }
//        set(value) = pushArena { arena ->
//            space.rigidBodySet.indexMut(handle.id).setPosition(value.asIsometry(arena), false)
//        }
//}
