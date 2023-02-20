package io.github.aecsocket.ignacio.paper

import io.github.aecsocket.glossa.core.Message
import io.github.aecsocket.glossa.core.MessageKey
import io.github.aecsocket.glossa.core.SectionKey

interface IgnacioMessages {
    @SectionKey
    val command: Command
    interface Command {
        @SectionKey
        val primitives: Primitives
        interface Primitives {
            @SectionKey
            val create: Create
            interface Create {
                @MessageKey
                fun box(
                    count: Int,
                    mass: Float,
                    locationX: Double,
                    locationY: Double,
                    locationZ: Double,
                ): Message

                @MessageKey
                fun sphere(
                    count: Int,
                    mass: Float,
                    locationX: Double,
                    locationY: Double,
                    locationZ: Double,
                ): Message
            }

            @MessageKey
            fun remove(
                count: Int
            ): Message
        }
    }
}
