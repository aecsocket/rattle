package io.github.aecsocket.ignacio

import io.github.aecsocket.klam.DRay3
import io.github.aecsocket.klam.DVec3
import io.github.aecsocket.klam.FVec3
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

        fun createStatic(descriptor: StaticBodyDescriptor, transform: Transform): PhysicsBody

        fun createMoving(descriptor: MovingBodyDescriptor, transform: Transform): PhysicsBody

        fun create(descriptor: BodyDescriptor, transform: Transform) = when (descriptor) {
            is StaticBodyDescriptor -> createStatic(descriptor, transform)
            is MovingBodyDescriptor -> createMoving(descriptor, transform)
        }

        fun destroy(body: PhysicsBody)

        fun destroyAll(bodies: Collection<PhysicsBody>)

        fun add(body: PhysicsBody)

        fun addStatic(descriptor: StaticBodyDescriptor, transform: Transform): PhysicsBody {
            return createStatic(descriptor, transform).also {
                add(it)
            }
        }

        fun addMovingBody(descriptor: MovingBodyDescriptor, transform: Transform): PhysicsBody {
            return createMoving(descriptor, transform).also {
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
