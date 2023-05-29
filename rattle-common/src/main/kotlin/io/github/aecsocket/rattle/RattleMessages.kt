package io.github.aecsocket.rattle

import io.github.aecsocket.glossa.Message
import net.kyori.adventure.text.Component

interface RattleMessages {
    val error: Error
    interface Error {
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

                    fun capsule(
                        count: Int,
                        positionX: Double, positionY: Double, positionZ: Double,
                    ): Message
                }
            }

            val destroy: Destroy
            interface Destroy {
                fun all(
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
                numColliders: Int,
                numBodies: Int,
                numActiveBodies: Int,
            ): Message
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
            numBodies: Int,
            numActiveBodies: Int,
            median: Component,
            best5: Component,
            worst5: Component,
        ): Message
    }
}
