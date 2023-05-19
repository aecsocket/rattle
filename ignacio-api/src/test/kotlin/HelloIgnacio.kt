import io.github.aecsocket.ignacio.*
import io.github.aecsocket.ignacio.rapier.RapierEngine
import kotlin.test.Test

class HelloIgnacio {
    @Test
    fun helloRapier() {
        runTest(RapierEngine(RapierEngine.Settings()))
    }

    private fun runTest(engine: PhysicsEngine) {
        // set up a space - an independent structure holding simulation data
        val physics: PhysicsSpace = engine.createSpace(PhysicsSpace.Settings())

        // create a non-moving floor body at (0, 0, 0)

        // material: describes physical properties that can be applied to stuff, used in simulation
        val floorMat: PhysicsMaterial = engine.createMaterial(
            friction = 0.25,
            restitution = 0.75,
        )
        val ballMat = engine.createMaterial(
            friction = 0.5,
            restitution = 0.75,
        )

        // geometry: descriptor for a volume in 3D space
        // to define a box, we provide the half-extents - the half-size of the box
        val floorGeom: Geometry = Box(Vec(100.0, 0.5, 100.0))
        // to define a sphere, we provide the radius
        val ballGeom: Geometry = Sphere(0.5)

        // shape: baked form of a geometry, physics-ready
        // shapes are reference-counted, and start with a count of 1
        val floorShape: Shape = engine.createShape(floorGeom)
        val ballShape: Shape = engine.createShape(ballGeom)

        // collider: a volume in 3D space which can produce collision response
        // but *cannot* simulate dynamics (velocity, etc.) on its own
        val floorColl: Collider = engine.createCollider(
            // shapes are reference counted - creating the collider will increment the ref count by 1
            shape = floorShape,
            material = floorMat,
            // an Iso is an isometry - a combination of translation vector + rotation quaternion
            position = Iso(
                // any of the fields of an Iso can be omitted, and they will default to these values:
                translation = Vec(0.0, 0.0, 0.0),
                rotation = Quat.Identity,
                // this is known as the identity isometry
            ),
        )
        // add the collider to the physics space
        floorColl.addTo(physics)

        // `floorColl` is just a handle which can be used to access the collider itself
        // use `.read` to gain immutable read access
        // or `.write` to gain mutable write access
        floorColl.write { coll ->
            // use `Iso()` as a shorthand for the identity isometry
            coll.position = Iso()
        }

        // create a moving ball body starting at (0, 5, 0) travelling at (0, 1, 0)/sec

        val ballBody = engine.createMovingBody(
            position = Iso(Vec(0.0, 5.0, 0.0)),
        )
        ballBody.addTo(physics)

        // `ballBody` is just a handle which can be used to access the body itself
        // same semantics as colliders, but you have to also specify what type of access (moving, fixed)
        ballBody.writeMoving { rb ->
            rb.linearVelocity = Vec(0.0, 1.0, 0.0)
        }

        // no position provided, so it defaults to the identity isometry
        // which doesn't matter, since we will attach this collider to the ball soon
        // so that it will follow the ball
        // ballShape's ref count increased by 1
        val ballCollider = engine.createCollider(ballShape, ballMat)
        ballCollider.addTo(physics)

        ballCollider.write { coll ->
            // attach the collider to the body
            // it will now always be positioned at `ballBody.position` * `ballCollider.relativePosition`
            // relativePosition defaults to the identity isometry
            coll.parent = ballBody
        }

        // simulate
        val dt = 1.0 / 60.0
        repeat(200) { step ->
            // do the simulation step, blocking the current thread until it's complete
            physics.step(dt)

            // use `.read*` to get immutable access to the body's property
            ballBody.readMoving { rb ->
                println("[$step] ball @ ${rb.position.translation}")
            }
        }

        // the physics space owns our bodies and colliders,
        // so when we destroy the physics space these objects will be destroyed as well
        // if these objects were not attached to any space, we would have to destroy them manually
        // note: since colliders own ref-counted shapes, destroying a collider
        //       will reduce the shape's ref count by 1, but *not* destroy it
        physics.destroy()
        // material is not owned by a single space, so must be destroyed manually
        floorMat.destroy()
        // the shapes' current ref counts are 1, so to fully destroy them we bring their ref counts to 0
        floorShape.destroy()
        ballShape.destroy()
        // finally, destroy the engine to tear down any remaining constructs
        engine.destroy()
    }
}
