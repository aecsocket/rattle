package io.github.aecsocket.ignacio

import org.spongepowered.configurate.objectmapping.ConfigSerializable

fun interface StepListener {
    fun onStep(deltaTime: Float)
}

interface PhysicsSpace : Destroyable {
    @ConfigSerializable
    data class Settings(
        val gravity: Vec3 = Vec3(0.0f, -9.81f, 0.0f),
    )

    var settings: Settings

    fun createStaticBody(descriptor: StaticBodyDescriptor, transform: Transform): PhysicsBody

    fun createMovingBody(descriptor: MovingBodyDescriptor, transform: Transform): PhysicsBody

    fun createBody(descriptor: BodyDescriptor, transform: Transform) = when (descriptor) {
        is StaticBodyDescriptor -> createStaticBody(descriptor, transform)
        is MovingBodyDescriptor -> createMovingBody(descriptor, transform)
    }

    fun destroyBody(body: PhysicsBody)

    fun destroyBodies(bodies: Collection<PhysicsBody>)

    fun addBody(body: PhysicsBody)

    fun addStaticBody(descriptor: StaticBodyDescriptor, transform: Transform): PhysicsBody {
        return createStaticBody(descriptor, transform).also {
            addBody(it)
        }
    }

    fun addMovingBody(descriptor: MovingBodyDescriptor, transform: Transform): PhysicsBody {
        return createMovingBody(descriptor, transform).also {
            addBody(it)
        }
    }

    fun addBodies(bodies: Collection<PhysicsBody>)

    fun removeBody(body: PhysicsBody)

    fun removeBodies(bodies: Collection<PhysicsBody>)

    fun onStep(listener: StepListener)

    fun removeStepListener(listener: StepListener)

    fun update(deltaTime: Float)
}
