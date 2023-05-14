package io.github.aecsocket.ignacio.physx

import io.github.aecsocket.ignacio.*
import physx.NativeObject
import physx.common.PxBaseTask
import physx.physics.PxScene
import java.lang.foreign.Arena
import kotlin.reflect.jvm.isAccessible

class PhysxSpace internal constructor(
    val engine: PhysxEngine,
    val scene: PxScene,
    settings: PhysicsSpace.Settings,
) : PhysicsSpace {
    private val destroyed = DestroyFlag()
    val arena = Arena.openShared()
    // scratch buffer's size must be a multiple of 16K, and it must be 16-byte aligned
    val scratchSize = engine.settings.scratchBlocks * 16 * 1024
    val scratch = NativeObject.wrapPointer(arena.allocate(scratchSize.toLong(), 16).address())

    // stupid hack to make a PxBaseTask pointing to 0x0
    // since we can't pass a null by ourselves ffs
    private val nullTask = PxBaseTask::class.constructors.first().apply {
        isAccessible = true
    }.call(0L)

    override var settings = settings
        set(value) = pushArena { arena ->
            field = value
            scene.gravity = value.gravity.asPx(arena)
        }

    override fun destroy() {
        destroyed()
        scene.release()
        arena.close()
    }

    override fun startStep(dt: Real) {
        scene.simulate(dt.toFloat(), nullTask, scratch, scratchSize)
    }

    override fun finishStep() {
        scene.fetchResults(true)
    }

    override val bodies: PhysicsSpace.Bodies
        get() = TODO("Not yet implemented")
}
