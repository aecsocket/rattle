package io.github.aecsocket.rattle.fabric

import io.github.aecsocket.alexandria.sync.Sync
import io.github.aecsocket.klam.IVec3
import io.github.aecsocket.rattle.PhysicsSpace
import net.minecraft.server.level.ServerLevel

//class FabricDynamicTerrain(
//    rattle: FabricRattle,
//    physics: Sync<PhysicsSpace>,
//    private val world: ServerLevel,
//    settings: Settings = Settings(),
//) : DynamicTerrain(rattle.rattle, physics, settings) {
//    private val toSnapshot = ArrayList<IVec3>()
//
//    override val layers: Array<out Layer>
//        get() = TODO("Not yet implemented")
//
//    override fun scheduleToSnapshot(sectionPos: Iterable<IVec3>) {
//        toSnapshot += sectionPos
//    }
//
//    fun onTick() {
////        toCreate.withLock { toCreate ->
////            toSnapshot.forEach { pos ->
////                world.getChunk(pos.x, pos.y)
////                // TODO
////
////                toCreate += SectionSnapshot(
////                    pos = pos,
////                    blocks = Array(),
////                )
////            }
////        }
//
////        toSnapshot.clear()
//    }
//}
