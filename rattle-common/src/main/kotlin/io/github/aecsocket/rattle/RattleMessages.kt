package io.github.aecsocket.rattle

import io.github.aecsocket.glossa.Message

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
    }
}
