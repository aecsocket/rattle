package io.github.aecsocket.ignacio.paper

import io.github.aecsocket.glossa.core.Message
import net.kyori.adventure.text.Component

interface IgnacioMessages {
    val error: Error
    interface Error {
        val physicsSpace: PhysicsSpace
        interface PhysicsSpace {
            fun alreadyExists(
                worldName: String,
            ): Message

            fun doesNotExist(
                worldName: String,
            ): Message
        }
    }

    val command: Command
    interface Command {
        val primitives: Primitives
        interface Primitives {
            val create: Create
            interface Create {
                val static: Static
                interface Static {
                    fun box(
                        count: Int,
                        locationX: Double,
                        locationY: Double,
                        locationZ: Double,
                    ): Message

                    fun sphere(
                        count: Int,
                        locationX: Double,
                        locationY: Double,
                        locationZ: Double,
                    ): Message
                }

                val dynamic: Dynamic
                interface Dynamic {
                    fun box(
                        count: Int,
                        mass: Float,
                        locationX: Double,
                        locationY: Double,
                        locationZ: Double,
                    ): Message

                    fun sphere(
                        count: Int,
                        mass: Float,
                        locationX: Double,
                        locationY: Double,
                        locationZ: Double,
                    ): Message
                }
            }

            fun remove(
                count: Int,
            ): Message
        }

        val timings: Timings
        interface Timings {
            fun timingHeader(): Message

            fun time(time: Double): Message

            fun timing(
                buffer: Double,
                median: Component,
                best5: Component,
                worst5: Component,
            ): Message

            fun spaceHeader(
                numWorldPhysicsSpaces: Int,
            ): Message

            fun space(
                worldName: String,
                numBodies: Int,
                numActiveBodies: Int,
            ): Message
        }

        val space: Space
        interface Space {
            fun create(
                worldName: String,
            ): Message

            fun destroy(
                worldName: String,
            ): Message

            val terrain: Terrain
            interface Terrain {
                fun enable(
                    worldName: String,
                ): Message

                fun disable(
                    worldName: String,
                ): Message
            }
        }
    }

    val barDisplay: BarDisplay
    interface BarDisplay {
        fun noPhysics(
            worldName: String,
        ): Message

        fun forPhysics(
            worldName: String,
            numBodies: Int,
            numActiveBodies: Int,
            median: Component,
            best5: Component,
            worst5: Component,
        ): Message

        fun forSpaceCreate(
            worldName: String,
            chunksProcessed: Int,
            chunksRemaining: Int,
            chunksTotal: Int,
            chunksProgress: Float,
        ): Message
    }
}
