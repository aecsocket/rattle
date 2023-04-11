package io.github.aecsocket.ignacio

import io.github.aecsocket.klam.*
import org.spongepowered.configurate.objectmapping.ConfigSerializable

fun interface StepListener {
    fun onStep(deltaTime: Float)
}

interface PhysicsSpace : Destroyable {
    @ConfigSerializable
    data class Settings(
        val gravity: FVec3 = FVec3(0.0f, -9.81f, 0.0f),
    )

    var settings: Settings

    val bodies: Bodies
    interface Bodies {
        val count: Int

        val activeCount: Int

        fun all(): Collection<PhysicsBody>

        fun active(): Collection<PhysicsBody>

        fun createStatic(descriptor: StaticBodyDescriptor, position: DVec3, rotation: FQuat): PhysicsBody

        fun createMoving(descriptor: MovingBodyDescriptor, position: DVec3, rotation: FQuat): PhysicsBody

        fun create(descriptor: BodyDescriptor, position: DVec3, rotation: FQuat) = when (descriptor) {
            is StaticBodyDescriptor -> createStatic(descriptor, position, rotation)
            is MovingBodyDescriptor -> createMoving(descriptor, position, rotation)
        }

        fun destroy(body: PhysicsBody)

        fun destroyAll(bodies: Collection<PhysicsBody>)

        fun add(body: PhysicsBody)

        fun addStatic(descriptor: StaticBodyDescriptor, position: DVec3, rotation: FQuat): PhysicsBody {
            return createStatic(descriptor, position, rotation).also {
                add(it)
            }
        }

        fun addMovingBody(descriptor: MovingBodyDescriptor, position: DVec3, rotation: FQuat): PhysicsBody {
            return createMoving(descriptor, position, rotation).also {
                add(it)
            }
        }

        fun addAll(bodies: Collection<PhysicsBody>)

        fun remove(body: PhysicsBody)

        fun removeAll(bodies: Collection<PhysicsBody>)
    }

    data class RayCast(
        val body: PhysicsBody,
        val hitFraction: Float,
    )

    val broadQuery: BroadQuery
    interface BroadQuery {
        fun rayCastBody(ray: DRay3, distance: Float, layerFilter: LayerFilter): RayCast?

        fun rayCastBodies(ray: DRay3, distance: Float, layerFilter: LayerFilter): Collection<RayCast>

        fun contactBox(box: DAabb3, layerFilter: LayerFilter): Collection<PhysicsBody>

        fun contactSphere(position: DVec3, radius: Float, layerFilter: LayerFilter): Collection<PhysicsBody>
    }

    val narrowQuery: NarrowQuery
    interface NarrowQuery {
        fun rayCastBody(ray: DRay3, distance: Float, layerFilter: LayerFilter, bodyFilter: BodyFilter): RayCast?

        fun rayCastBodies(ray: DRay3, distance: Float, layerFilter: LayerFilter, bodyFilter: BodyFilter, shapeFilter: ShapeFilter): Collection<RayCast>
    }

    fun onStep(listener: StepListener)

    fun removeStepListener(listener: StepListener)

    fun update(deltaTime: Float)
}
