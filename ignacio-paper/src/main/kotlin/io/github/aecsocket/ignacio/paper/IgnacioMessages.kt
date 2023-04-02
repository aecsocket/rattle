package io.github.aecsocket.ignacio.paper

import io.github.aecsocket.glossa.Message

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

        val primitive: Primitive
        interface Primitive {
            val create: Create
            interface Create {
                val static: Static
                interface Static {
                    fun box(
                        count: Int,
                        locationX: Double, locationY: Double, locationZ: Double,
                    ): Message
                }
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
    }
}
