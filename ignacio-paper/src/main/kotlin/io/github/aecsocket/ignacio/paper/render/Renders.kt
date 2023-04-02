package io.github.aecsocket.ignacio.paper.render

import io.github.aecsocket.ignacio.Transform
import io.github.aecsocket.klam.*
import net.kyori.adventure.text.Component
import org.bukkit.entity.Display.Billboard
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

fun interface PlayerTracker {
    fun trackedPlayers(): Collection<Player>
}

fun Entity.playerTracker() = PlayerTracker { trackedPlayers }


sealed interface RenderDescriptor {
    val scale: FVec3
    val billboard: Billboard
}

data class ModelDescriptor(
    override val scale: FVec3 = FVec3(1.0f),
    override val billboard: Billboard = Billboard.FIXED,
    val item: ItemStack,
) : RenderDescriptor

enum class TextAlignment {
    LEFT,
    RIGHT,
    CENTER,
}

data class TextDescriptor(
    override val scale: FVec3 = FVec3(1.0f),
    override val billboard: Billboard = Billboard.CENTER,
    val text: Component,
    val lineWidth: Int = 200,
    val backgroundColor: RGBA = fromARGB(0x40000000),
    val hasShadow: Boolean = false,
    val isSeeThrough: Boolean = false,
    val alignment: TextAlignment = TextAlignment.CENTER,
) : RenderDescriptor

sealed interface Render {
    var tracker: PlayerTracker

    var transform: Transform

    var scale: FVec3

    fun spawn(players: Iterable<Player>)

    fun despawn(players: Iterable<Player>)
}

fun Render.trackedPlayers() = tracker.trackedPlayers()

fun Render.spawn(player: Player) = spawn(setOf(player))

fun Render.spawn() = spawn(trackedPlayers())

fun Render.despawn(player: Player) = despawn(setOf(player))

fun Render.despawn() = despawn(trackedPlayers())

interface ModelRender : Render {
    var item: ItemStack
}

interface TextRender : Render {
    var text: Component
}

interface Renders {
    fun createModel(descriptor: ModelDescriptor, tracker: PlayerTracker, transform: Transform): ModelRender

    fun createText(descriptor: TextDescriptor, tracker: PlayerTracker, transform: Transform): TextRender

    fun create(descriptor: RenderDescriptor, tracker: PlayerTracker, transform: Transform): Render {
        return when (descriptor) {
            is ModelDescriptor -> createModel(descriptor, tracker, transform)
            is TextDescriptor -> createText(descriptor, tracker, transform)
        }
    }
}
