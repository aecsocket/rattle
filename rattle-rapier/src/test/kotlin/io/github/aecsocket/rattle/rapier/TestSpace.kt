package io.github.aecsocket.rattle.rapier

import io.github.aecsocket.alexandria.sync.Locked
import io.github.aecsocket.klam.*
import io.github.aecsocket.rattle.*
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
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
            val coll = engine.createCollider(shape.acquire(), Collider.Start.Relative())
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

    @Test
    fun testLock() {
        val engine = RapierEngine()
        val physics = engine.createSpace()
        val lock = ReentrantLock()
        physics.lock = lock

        assertThrows<IllegalStateException> {
            physics.colliders.count
        }

        lock.lock()
        val shape = engine.createShape(Sphere(0.5))
        val collKey = engine.createCollider(shape, Collider.Start.Absolute(DIso3.identity))
            .let { physics.colliders.add(it) }
        val coll = physics.colliders.read(collKey)!!
        lock.unlock()

        assertThrows<IllegalStateException> {
            coll.position
        }
    }

    @Test
    fun testAddRemove() {
        val engine = RapierEngine()
        val lock = ReentrantLock(true)
        val physics = Locked(engine.createSpace(), lock)
        physics.withLock { it.lock = lock }

        val running = AtomicBoolean(true)
        val stepping = Thread {
            val dt = 1.0 / 60.0
            while (running.get()) {
                physics.withLock { physics ->
                    engine.stepSpaces(dt, listOf(physics))
                }
                Thread.sleep((dt * 1000).toLong())
            }
        }.apply { start() }

        val mutating = Thread {
            val shape = engine.createShape(Sphere(0.5))

            repeat(10_000) { i ->
                val keys = (0 until 50).map {
                    physics.withLock { physics ->
                        val collKey = engine.createCollider(shape.acquire(), Collider.Start.Relative())
                            .let { physics.colliders.add(it) }
                        val bodyKey = engine.createBody(RigidBodyType.DYNAMIC, DIso3.identity)
                            .let { physics.rigidBodies.add(it) }
                        physics.colliders.attach(collKey, bodyKey)
                        collKey to bodyKey
                    }
                }


                keys.forEach { (collKey, bodyKey) ->
                    physics.withLock { physics ->
                        physics.colliders.remove(collKey)?.destroy()
                        physics.rigidBodies.remove(bodyKey)?.destroy()
                    }
                }
            }

            running.set(false)
        }.apply { start() }

        stepping.join()
        mutating.join()
    }
}
