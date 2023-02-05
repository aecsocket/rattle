package io.github.aecsocket.ignacio.jolt

import io.github.aecsocket.ignacio.core.IgnacioEngine
import io.github.aecsocket.ignacio.core.PhysicsSpace
import jolt.*
import jolt.core.*
import jolt.physics.PhysicsSystem
import jolt.physics.collision.ObjectLayerPairFilter
import jolt.physics.collision.broadphase.BroadPhaseLayer
import jolt.physics.collision.broadphase.BroadPhaseLayerInterface
import jolt.physics.collision.broadphase.ObjectVsBroadPhaseLayerFilter

const val LAYER_NON_MOVING = 0
const val LAYER_MOVING = 1

class JoltEngine : IgnacioEngine {
    override val version get() = "TODO" // todo

    val spaces = HashMap<PhysicsSystem, JtPhysicsSpace>()

    val jobSystem: JobSystem
    val bpLayerNonMoving: BroadPhaseLayer
    val bpLayerMoving: BroadPhaseLayer
    val bpLayers: BroadPhaseLayerInterface
    val objBpLayerFilter: ObjectVsBroadPhaseLayerFilter
    val objPairLayerFilter: ObjectLayerPairFilter

    init {
        JoltNativeLoader.load()

        JoltEnvironment.registerDefaultAllocator()
        RTTIFactory.setInstance(RTTIFactory())
        JoltEnvironment.registerTypes()
        jobSystem = JobSystemThreadPool(2048, 8, Runtime.getRuntime().availableProcessors() - 1)

        bpLayerNonMoving = BroadPhaseLayer.ofValue((0).toByte())
        bpLayerMoving = BroadPhaseLayer.ofValue((1).toByte())
        bpLayers = object : BroadPhaseLayerInterface() {
            override fun getNumBroadPhaseLayers() = 2
            override fun getBroadPhaseLayer(layer: Int) = when (layer) {
                LAYER_NON_MOVING -> bpLayerNonMoving
                LAYER_MOVING -> bpLayerMoving
                else -> throw RuntimeException()
            }
            override fun getBroadPhaseLayerName(layer: BroadPhaseLayer) = when (layer) {
                bpLayerNonMoving -> "NON_MOVING"
                bpLayerMoving -> "MOVING"
                else -> throw RuntimeException()
            }
        }

        objBpLayerFilter = object : ObjectVsBroadPhaseLayerFilter() {
            override fun shouldCollide(layer1: Int, layer2: BroadPhaseLayer) = when (layer1) {
                LAYER_NON_MOVING -> layer2.value == LAYER_MOVING
                LAYER_MOVING -> true
                else -> false
            }
        }

        objPairLayerFilter = object : ObjectLayerPairFilter() {
            override fun shouldCollide(layer1: Int, layer2: Int) = when (layer1) {
                LAYER_NON_MOVING -> layer2 == LAYER_MOVING
                LAYER_MOVING -> true
                else -> false
            }
        }
    }

    override fun destroy() {
        spaces.forEach { (_, space) ->
            space.destroy()
        }

        objPairLayerFilter.delete()
        objBpLayerFilter.delete()
        bpLayers.delete()
        bpLayerMoving.delete()
        bpLayerNonMoving.delete()
        jobSystem.delete()
        RTTIFactory.getInstance()?.delete()
        RTTIFactory.setInstance(null)
    }

    override fun createSpace(settings: PhysicsSpace.Settings): PhysicsSpace {
        val system = PhysicsSystem()
        system.init(
            65536,
            0,
            65536,
            10240,
            bpLayers,
            objBpLayerFilter,
            objPairLayerFilter
        )
        return JtPhysicsSpace(this, system).also {
            spaces[system] = it
        }
    }

    override fun destroySpace(space: PhysicsSpace) {
        space as JtPhysicsSpace
        spaces.remove(space.handle)
        space.destroy()
    }
}
