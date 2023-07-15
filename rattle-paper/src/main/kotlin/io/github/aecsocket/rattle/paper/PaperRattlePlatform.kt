package io.github.aecsocket.rattle.paper

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent
import io.github.aecsocket.alexandria.paper.extension.forWorld
import io.github.aecsocket.alexandria.paper.extension.position
import io.github.aecsocket.alexandria.paper.extension.registerEvents
import io.github.aecsocket.kbeam.sync.Locked
import io.github.aecsocket.kbeam.sync.Sync
import io.github.aecsocket.rattle.CommandSource
import io.github.aecsocket.rattle.PhysicsSpace
import io.github.aecsocket.rattle.impl.RattlePlatform
import io.github.aecsocket.rattle.impl.RattlePlayer
import io.papermc.paper.event.packet.PlayerChunkLoadEvent
import io.papermc.paper.event.packet.PlayerChunkUnloadEvent
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.world.WorldUnloadEvent

class PaperRattlePlatform(
    val plugin: PaperRattle,
) : RattlePlatform(plugin.rattle) {
  private val worldMap = HashMap<World, Sync<PaperWorldPhysics>>()
  private val players = HashMap<UUID, PaperRattlePlayer>()

  private val _drawPlayers = HashMap<Player, RattlePlayer.Draw>()
  val drawPlayers: Map<Player, RattlePlayer.Draw>
    get() = _drawPlayers

  override val worlds: Iterable<RWorld>
    get() = Bukkit.getWorlds().map { it.wrap() }

  override fun asPlayer(sender: CommandSource) =
      (sender.unwrap() as? Player)?.let { playerData(it) }

  override fun key(world: RWorld) = world.unwrap().key()

  /** Gets the physics state of a world if it already exists. */
  fun physicsOrNull(world: World) = worldMap[world]

  override fun physicsOrNull(world: RWorld) = physicsOrNull(world.unwrap())

  /**
   * Gets the physics state of a world if it already exists, or creates a new physics state if it
   * does not exist.
   */
  fun physicsOrCreate(world: World): Sync<PaperWorldPhysics> {
    return worldMap.computeIfAbsent(world) {
      val lock = ReentrantLock()
      val spaceSettings = plugin.settings.worldPhysics.forWorld(world) ?: PhysicsSpace.Settings()
      val physics = plugin.engine.createSpace(spaceSettings)
      physics.lock = lock

      val simpleBodies = PaperSimpleBodies(this, physics, world, plugin.settings.simpleBodies)
      val terrain =
          if (plugin.settings.terrain.enabled) {
            PaperTerrainCollision(this, physics, world, plugin.settings.terrain)
          } else {
            null
          }
      val entities =
          if (plugin.settings.entities.enabled) {
            val entities = PaperEntityCollision(this, physics, plugin.settings.entities)
            // todo
            //world.entities.forEach { entity -> entities.onAdd(entity) }
            entities
          } else {
            null
          }

      Locked(PaperWorldPhysics(this, physics, terrain, entities, simpleBodies, world), lock)
    }
  }

  override fun physicsOrCreate(world: RWorld) = physicsOrCreate(world.unwrap())

  internal fun removePhysics(world: World) {
    worldMap.remove(world)
  }

  fun playerData(player: Player) =
      players.computeIfAbsent(player.uniqueId) { PaperRattlePlayer(this, player) }

  override fun setPlayerDraw(player: RattlePlayer, draw: RattlePlayer.Draw?) {
    val handle = player.unwrap()
    if (draw == null) {
      _drawPlayers -= handle
      plugin.runTask {
        physicsOrNull(handle.world)?.withLock { physics -> physics.terrain?.hideDebug(handle) }
      }
    } else {
      _drawPlayers[handle] = draw
      plugin.runTask {
        physicsOrNull(handle.world)?.withLock { physics -> physics.terrain?.showDebug(handle) }
      }
    }
  }

  override fun onTick() {
    players.forEach { (_, player) -> player.onTick() }
    super.onTick()
  }

  internal fun onPostEnable() {
    plugin.scheduling.onServer().runRepeating { onTick() }
    plugin.registerEvents(
        object : Listener {
          @EventHandler
          fun on(event: WorldUnloadEvent) {
            worldMap[event.world]?.withLock { it.destroy() }
          }

          @EventHandler
          fun on(event: PlayerQuitEvent) {
            players -= event.player.uniqueId
            _drawPlayers -= event.player
          }

          @EventHandler
          fun on(event: PlayerInteractEvent) {
            if (event.action.isLeftClick) {
              players[event.player.uniqueId]?.onClick()
            }
          }

          // terrain

          private fun terrain(world: World, fn: PaperTerrainCollision.() -> Unit) {
            plugin.runTask {
              physicsOrNull(world)?.withLock { physics -> physics.terrain?.let(fn) }
            }
          }

          @EventHandler
          fun on(event: PlayerChunkLoadEvent) {
            terrain(event.world) { onTrackChunk(event.player, event.chunk) }
          }

          @EventHandler
          fun on(event: PlayerChunkUnloadEvent) {
            terrain(event.world) { onUntrackChunk(event.player, event.chunk) }
          }

          private fun terrainUpdate(block: Block) {
            terrain(block.world) { onSliceUpdate(block.position().map { it shr 4 }) }
          }

          @EventHandler
          fun on(event: BlockBreakEvent) {
            terrainUpdate(event.block)
          }

          @EventHandler
          fun on(event: BlockPlaceEvent) {
            terrainUpdate(event.block)
          }

          // entities

          private fun entities(entity: Entity, fn: PaperEntityCollision.() -> Unit) {
            plugin.runTask {
              physicsOrNull(entity.world)?.withLock { physics -> physics.entities?.let(fn) }
            }
          }

          @EventHandler
          fun on(event: EntityAddToWorldEvent) {
            val entity = event.entity
            entities(entity) { onAdd(entity) }
          }

          @EventHandler
          fun on(event: EntityRemoveFromWorldEvent) {
            val entity = event.entity
            entities(entity) { onRemove(entity) }
          }
        })
  }
}
