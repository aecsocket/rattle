package io.github.aecsocket.rattle.fabric

import io.github.aecsocket.klam.IVec3
import io.github.aecsocket.rattle.PhysicsSpace
import io.github.aecsocket.rattle.world.DynamicTerrain
import net.minecraft.server.level.ServerLevel

class FabricDynamicTerrain(
    rattle: FabricRattle,
    physics: PhysicsSpace,
    private val world: ServerLevel,
) : DynamicTerrain(rattle.rattle, physics) {
    private val toSnapshot = ArrayList<IVec3>()

    override fun scheduleToSnapshot(sectionPos: Iterable<IVec3>) {
        toSnapshot += sectionPos
    }

    fun onTick() {
        toCreate.withLock { toCreate ->
            toSnapshot.forEach { pos ->
                world.getChunk(pos.x, pos.y)
                // TODO
//
//                toCreate += SectionSnapshot(
//                    pos = pos,
//                    blocks = Array(),
//                )
            }
        }

        toSnapshot.clear()
    }
}
