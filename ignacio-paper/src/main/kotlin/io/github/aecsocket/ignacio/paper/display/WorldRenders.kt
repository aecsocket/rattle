package io.github.aecsocket.ignacio.paper.display

import io.github.aecsocket.ignacio.core.math.Transform
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

fun interface PlayerTracker {
    fun trackedPlayers(): Collection<Player>
}

fun Entity.playerTracker() = PlayerTracker { trackedPlayers }

sealed interface WorldRender {
    var playerTracker: PlayerTracker
    var transform: Transform

    fun spawn(players: Iterable<Player>)

    fun despawn(players: Iterable<Player>)
}

fun WorldRender.trackedPlayers() = playerTracker.trackedPlayers()

fun WorldRender.spawn(player: Player) = spawn(setOf(player))
fun WorldRender.spawn() = spawn(trackedPlayers())

fun WorldRender.despawn(player: Player) = despawn(setOf(player))
fun WorldRender.despawn() = despawn(trackedPlayers())

interface WorldModel : WorldRender {
    var glowingColor: NamedTextColor
    var model: ItemStack
}

interface WorldText : WorldRender {
    fun text(text: Component, players: Iterable<Player>)
}

fun WorldText.text(text: Component, player: Player) = text(text, setOf(player))
fun WorldText.text(text: Component) = text(text, trackedPlayers())

interface WorldRenders {
    fun createModel(playerTracker: PlayerTracker, transform: Transform, model: ItemStack): WorldModel

    fun createText(playerTracker: PlayerTracker, transform: Transform): WorldText
}
