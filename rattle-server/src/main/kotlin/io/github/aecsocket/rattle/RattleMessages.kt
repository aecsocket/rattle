package io.github.aecsocket.rattle

import io.github.aecsocket.glossa.Message

interface RattleMessages {
    val command: Command
    interface Command {
        val bodies: Bodies
        interface Bodies {
            val create: Create
            interface Create {
                val fixed: BodyType
                val moving: BodyType
                interface BodyType {
                    fun sphere(
                        count: Int,
                        locationX: Double, locationY: Double, locationZ: Double,
                    ): Message

                    fun box(
                        count: Int,
                        locationX: Double, locationY: Double, locationZ: Double,
                    ): Message

                    fun capsule(
                        count: Int,
                        locationX: Double, locationY: Double, locationZ: Double,
                    ): Message
                }
            }

            val destroy: Destroy
            interface Destroy {
                fun all(): Message
            }
        }
    }
}
