package io.github.aecsocket.ignacio.paper

import io.github.aecsocket.glossa.Message
import net.kyori.adventure.text.Component

interface IgnacioMessages {
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

        val render: Render
        interface Render {
            fun doesNotExist(
                id: Int,
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

        val render: Render
        interface Render {
            val create: Create
            interface Create {
                fun model(
                    id: Int,
                    locationX: Double, locationY: Double, locationZ: Double,
                ): Message

                fun text(
                    id: Int,
                    locationX: Double, locationY: Double, locationZ: Double,
                ): Message
            }

            val destroy: Destroy
            interface Destroy {
                fun one(
                    id: Int,
                ): Message

                fun all(
                    count: Int,
                ): Message
            }
        }

        val body: Body
        interface Body {
            val create: Create
            interface Create {
                val static: Static
                interface Static {
                    fun box(
                        count: Int,
                        locationX: Double, locationY: Double, locationZ: Double,
                    ): Message

                    fun sphere(
                        count: Int,
                        locationX: Double, locationY: Double, locationZ: Double,
                    ): Message
                }

                val moving: Moving
                interface Moving {
                    fun box(
                        count: Int,
                        locationX: Double, locationY: Double, locationZ: Double,
                    ): Message

                    fun sphere(
                        count: Int,
                        locationX: Double, locationY: Double, locationZ: Double,
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

        val timings: Timings
        interface Timings {
            fun timingsHeader(): Message

            fun timing(
                buffer: Double,
                median: Component,
                best5: Component,
                worst5: Component,
            ): Message

            fun spacesHeader(
                numWorldPhysicsSpaces: Int,
            ): Message

            fun space(
                worldName: String,
                numBodies: Int,
                numActiveBodies: Int,
            ): Message
        }
    }

    fun timing(
        time: Double,
    ): Message

    val timingsBar: TimingsBar
    interface TimingsBar {
        fun none(
            worldName: String,
        ): Message

        fun some(
            worldName: String,
            numBodies: Int,
            numActiveBodies: Int,
            median: Component,
            best5: Component,
            worst5: Component,
        ): Message
    }
}
