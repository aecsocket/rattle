package io.github.aecsocket.ignacio.paper.display

import io.github.aecsocket.ignacio.core.math.Transform
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

fun interface PlayerTracker {
    fun trackedPlayers(): Collection<Player>
}

sealed interface WorldRender {
    var playerTracker: PlayerTracker

    fun spawn(transform: Transform, players: Iterable<Player>)

    fun despawn(players: Iterable<Player>)

    fun transform(transform: Transform, players: Iterable<Player>)
}

fun WorldRender.trackedPlayers() = playerTracker.trackedPlayers()

fun WorldRender.transform(transform: Transform, player: Player) = transform(transform, setOf(player))
fun WorldRender.transform(transform: Transform) = transform(transform, trackedPlayers())

fun WorldRender.spawn(transform: Transform, player: Player) = spawn(transform, setOf(player))
fun WorldRender.spawn(transform: Transform) = spawn(transform, trackedPlayers())

fun WorldRender.despawn(player: Player) = despawn(setOf(player))
fun WorldRender.despawn() = despawn(trackedPlayers())

interface WorldModel : WorldRender {
    var glowingColor: NamedTextColor

    fun model(item: ItemStack, players: Iterable<Player>)
}

fun WorldModel.model(item: ItemStack, player: Player) = model(item, setOf(player))
fun WorldModel.model(item: ItemStack) = model(item, trackedPlayers())

interface WorldText : WorldRender {
    fun text(text: Component, players: Iterable<Player>)
}

fun WorldText.text(text: Component, player: Player) = text(text, setOf(player))
fun WorldText.text(text: Component) = text(text, trackedPlayers())

interface WorldRenders {
    fun createModel(transform: Transform, playerTracker: PlayerTracker): WorldModel

    fun createText(transform: Transform, playerTracker: PlayerTracker): WorldText
}
