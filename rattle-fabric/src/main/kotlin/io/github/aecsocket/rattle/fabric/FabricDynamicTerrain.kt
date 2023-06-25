package io.github.aecsocket.rattle.fabric

import io.github.aecsocket.alexandria.extension.swapList
import io.github.aecsocket.alexandria.fabric.ItemDisplayRender
import io.github.aecsocket.alexandria.fabric.extension.nextEntityId
import io.github.aecsocket.alexandria.sync.Locked
import io.github.aecsocket.klam.IVec3
import io.github.aecsocket.rattle.PhysicsSpace
import io.github.aecsocket.rattle.world.DynamicTerrain
import io.github.aecsocket.rattle.world.TILES_IN_SLICE
import io.github.aecsocket.rattle.world.posInChunk
import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.chunk.ChunkAccess
import net.minecraft.world.level.chunk.ChunkStatus

class FabricDynamicTerrain(
    world: ServerLevel,
    platform: FabricRattlePlatform,
    physics: PhysicsSpace,
    settings: Settings = Settings(),
) : DynamicTerrain<ServerLevel>(world, platform, physics, settings) {
    private val toSnapshot = Locked(HashSet<IVec3>())

    override fun createRender(pos: IVec3) = ItemDisplayRender(nextEntityId()) { packet ->
        PlayerLookup
            .tracking(world, ChunkPos(pos.x, pos.z))
            .filter { it.rattle().draw.terrain }
            .forEach { it.connection.send(packet) }
    }

    override fun scheduleSnapshot(pos: IVec3) {
        toSnapshot.withLock { it += pos }
    }

    fun onTick() {
        toSnapshot.withLock { it.swapList() }.forEach { pos ->
            val snapshot = createSnapshot(world.getChunk(pos.x, pos.z, ChunkStatus.FULL), pos)
            setSliceSnapshot(pos, snapshot)
        }
    }

    private fun createSnapshot(chunk: ChunkAccess, pos: IVec3): SliceState.Snapshot {
        if (pos.y < -world.minBuildHeight / 16 || pos.y >= world.maxBuildHeight / 16) {
            return SliceState.Snapshot.Empty
        }
        if (chunk.getSection(pos.y - world.minBuildHeight / 16).hasOnlyAir()) {
            return SliceState.Snapshot.Empty
        }

        val tiles: Array<out Tile?> = Array<Tile?>(TILES_IN_SLICE) { i ->
            val (lx, ly, lz) = posInChunk(i)
            val gy = pos.y * 16 + ly
            // guaranteed to be in range because of the Y check at the start
            null
//            chunk.getBlockState()
//            val block = chunk.getBlock(lx, gy, lz)
//            wrapBlock(block)
        }

        return SliceState.Snapshot(
            tiles = tiles,
        )
    }
}
