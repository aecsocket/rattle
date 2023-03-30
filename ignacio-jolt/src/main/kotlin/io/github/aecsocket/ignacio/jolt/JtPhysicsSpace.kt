package io.github.aecsocket.ignacio.jolt

import io.github.aecsocket.ignacio.*
import jolt.core.TempAllocator
import jolt.physics.Activation
import jolt.physics.PhysicsStepListener
import jolt.physics.PhysicsSystem
import jolt.physics.body.Body
import jolt.physics.body.BodyCreationSettings
import jolt.physics.body.MotionType
import java.lang.foreign.MemorySession

class JtPhysicsSpace internal constructor(
    val engine: JoltEngine,
    val handle: PhysicsSystem,
    val tempAllocator: TempAllocator,
    settings: PhysicsSpace.Settings,
) : PhysicsSpace {
    private val destroyed = DestroyFlag()
    private val arena = MemorySession.openShared()

    private fun updateSettings() {
        pushMemory { arena ->
            handle.setGravity(arena.asJolt(settings.gravity))
        }
    }

    override var settings = settings
        set(value) {
            field = value
            updateSettings()
        }

    private val onStep = ArrayList<StepListener>()
    private val bodies = HashMap<Body, JtPhysicsBody>()

    init {
        updateSettings()
        handle.addStepListener(PhysicsStepListener.of(arena) { deltaTime, _ ->
            onStep.forEach { it.onStep(deltaTime) }
        })
    }

    override fun destroy() {
        destroyed.mark()
        handle.delete()
        arena.close()
    }

    private fun createBodySettings(
        mem: MemorySession,
        descriptor: BodyDescriptor,
        transform: Transform,
        motionType: MotionType
    ): BodyCreationSettings {
        val bodySettings = BodyCreationSettings.of(mem,
            (descriptor.shape as JtShape).handle,
            mem.asJolt(transform.position),
            mem.asJolt(transform.rotation),
            motionType,
            (descriptor.objectLayer as JtObjectLayer).id,
        )
        bodySettings.isSensor = descriptor.trigger
        return bodySettings
    }

    override fun createStaticBody(descriptor: StaticBodyDescriptor, transform: Transform): PhysicsBody {
        return pushMemory { arena ->
            val bodySettings = createBodySettings(arena, descriptor, transform, MotionType.STATIC)
            val body = handle.bodyInterface.createBody(bodySettings)
            JtPhysicsBody(handle, descriptor.name, body.id)
        }
    }

    override fun createMovingBody(descriptor: MovingBodyDescriptor, transform: Transform): PhysicsBody {
        return pushMemory { arena ->
            val bodySettings = createBodySettings(
                arena,
                descriptor,
                transform,
                if (descriptor.kinematic) MotionType.KINEMATIC else MotionType.DYNAMIC,
            )

            bodySettings.friction = descriptor.friction
            bodySettings.restitution = descriptor.restitution
            bodySettings.linearDamping = descriptor.linearDamping
            bodySettings.angularDamping = descriptor.angularDamping
            bodySettings.maxLinearVelocity = descriptor.maxLinearVelocity
            bodySettings.maxAngularVelocity = descriptor.maxAngularVelocity
            bodySettings.gravityFactor = descriptor.gravityFactor
            bodySettings.linearVelocity = arena.asJolt(descriptor.linearVelocity)
            bodySettings.angularVelocity = arena.asJolt(descriptor.angularVelocity)
            val body = handle.bodyInterface.createBody(bodySettings)
            JtPhysicsBody(handle, descriptor.name, body.id)
        }
    }

    override fun destroyBody(body: PhysicsBody) {
        body as JtPhysicsBody
        if (body.added)
            throw IllegalStateException("Body $body is still added to physics space")
        if (body.destroyed.getAndSet(true))
            throw IllegalStateException("Body $body is already destroyed")

        handle.bodyInterface.destroyBody(body.id)
    }

    override fun destroyBodies(bodies: Collection<PhysicsBody>) {
        @Suppress("UNCHECKED_CAST")
        bodies as Collection<JtPhysicsBody>
        bodies.forEachIndexed { idx, body ->
            if (body.added)
                throw IllegalStateException("Body $body [$idx] is still added to physics space")
            if (body.destroyed.getAndSet(true))
                throw IllegalStateException("Body $body [$idx] is already destroyed")
        }

        handle.bodyInterface.destroyBodies(bodies.map { it.id })
    }

    override fun addBody(body: PhysicsBody) {
        body as JtPhysicsBody
        if (body.destroyed.get())
            throw IllegalStateException("Body $body is destroyed")
        if (body.added)
            throw IllegalStateException("Body $body is already added to physics space")

        handle.bodyInterface.addBody(body.id, Activation.DONT_ACTIVATE)
    }

    override fun addBodies(bodies: Collection<PhysicsBody>) {
        @Suppress("UNCHECKED_CAST")
        bodies as Collection<JtPhysicsBody>
        bodies.forEachIndexed { idx, body ->
            if (body.destroyed.get())
                throw IllegalStateException("Body $body [$idx] is destroyed")
            if (body.added)
                throw IllegalStateException("Body $body [$idx] is already added to physics space")
        }

        val bulk = handle.bodyInterface.bodyBulk(bodies.map { it.id })
        handle.bodyInterface.addBodiesPrepare(bulk)
        handle.bodyInterface.addBodiesFinalize(bulk, Activation.DONT_ACTIVATE)
    }

    override fun removeBody(body: PhysicsBody) {
        body as JtPhysicsBody
        if (body.destroyed.get())
            throw IllegalStateException("Body $body is destroyed")
        if (!body.added)
            throw IllegalStateException("Body $body is not added to physics space")

        handle.bodyInterface.removeBody(body.id)
    }

    override fun removeBodies(bodies: Collection<PhysicsBody>) {
        @Suppress("UNCHECKED_CAST")
        bodies as Collection<JtPhysicsBody>
        bodies.forEachIndexed { idx, body ->
            if (body.destroyed.get())
                throw IllegalStateException("Body $body [$idx] is destroyed")
            if (body.added)
                throw IllegalStateException("Body $body [$idx] is not added to physics space")
        }

        handle.bodyInterface.removeBodies(bodies.map { it.id })
    }

    override fun onStep(listener: StepListener) {
        onStep += listener
    }

    override fun removeStepListener(listener: StepListener) {
        onStep -= listener
    }

    override fun update(deltaTime: Float) {
        handle.update(
            deltaTime,
            engine.settings.space.collisionSteps,
            engine.settings.space.integrationSubSteps,
            tempAllocator,
            engine.jobSystem,
        )
    }
}
