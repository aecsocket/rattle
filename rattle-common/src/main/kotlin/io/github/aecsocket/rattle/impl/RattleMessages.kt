package io.github.aecsocket.rattle.impl

import io.github.aecsocket.glossa.Message
import net.kyori.adventure.text.Component

interface RattleMessages {
    val error: Error
    interface Error {
        fun taskTimedOut(): Message

        val space: Space
        interface Space {
            fun alreadyExists(
                world: String,
            ): Message

            fun doesNotExist(
                world: String,
            ): Message
        }
    }

    val command: Command
    interface Command {
        val space: Space
        interface Space {
            fun create(
                world: String,
            ): Message

            fun destroy(
                world: String,
            ): Message
        }

        val body: Body
        interface Body {
            val create: Create
            interface Create {
                val fixed: Shapes
                val moving: Shapes
                interface Shapes {
                    fun sphere(
                        count: Int,
                        positionX: Double, positionY: Double, positionZ: Double,
                    ): Message

                    fun box(
                        count: Int,
                        positionX: Double, positionY: Double, positionZ: Double,
                    ): Message
                }
            }

            val destroy: Destroy
            interface Destroy {
                fun all(
                    world: String,
                    count: Int,
                ): Message
            }
        }

        val stats: Stats
        interface Stats {
            fun timingsHeader(): Message

            fun timing(
                buffer: Double,
                median: Component,
                best5: Component,
                worst5: Component,
            ): Message

            fun spacesHeader(
                count: Int,
            ): Message

            fun space(
                world: String,
                colliders: Int,
                rigidBodies: Int,
                activeRigidBodies: Int,
            ): Message
        }

        val launcher: Launcher
        interface Launcher {
            fun disable(): Message

            fun sphere(): Message

            fun box(): Message
        }
    }

    fun timing(
        time: Double,
    ): Message

    val statsBar: StatsBar
    interface StatsBar {
        fun none(
            world: String,
            median: Component,
            best5: Component,
            worst5: Component,
        ): Message

        fun some(
            world: String,
            rigidBodies: Int,
            activeRigidBodies: Int,
            median: Component,
            best5: Component,
            worst5: Component,
        ): Message
    }
}
