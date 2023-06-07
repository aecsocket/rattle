package io.github.aecsocket.rattle.rapier

import io.github.aecsocket.rattle.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TestSpace {
    @Test
    fun testRefCount() {
        val engine = RapierEngine()
        val physics = engine.createSpace()
        val keys = HashSet<ColliderKey>()

        val shape = engine.createShape(Sphere(0.5))

        repeat(256) {
            val coll = engine.createCollider(shape.acquire())
                .material(PhysicsMaterial())
            val key = physics.colliders.add(coll)
            keys += key
        }

        assertEquals(257, shape.refCount)

        keys.forEach { key ->
            val coll = physics.colliders.remove(key)
            assertNotNull(coll)
            assertNull(physics.colliders.read(key))
            coll.destroy()
        }

        assertEquals(1, shape.refCount)
        shape.release()
        physics.destroy()
    }
}
