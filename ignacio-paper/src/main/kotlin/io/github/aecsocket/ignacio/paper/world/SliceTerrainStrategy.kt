package io.github.aecsocket.ignacio.paper.world

import io.github.aecsocket.ignacio.core.*
import io.github.aecsocket.ignacio.core.math.*
import io.github.aecsocket.ignacio.paper.ignacioBodyName
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import org.bukkit.Chunk
import org.bukkit.ChunkSnapshot
import org.bukkit.Material
import org.bukkit.World
import java.util.concurrent.ConcurrentHashMap

// note: negative slice Y coordinates are possible
typealias SlicePos = Point3

sealed interface TerrainLayer {
    val collidable: Boolean

    fun name(): String

    object Solid : TerrainLayer {
        override val collidable get() = true

        override fun name() = "solid"
    }

    data class Fluid(
        val name: String,
        val density: Float,
        val settings: FluidSettings,
    ) : TerrainLayer {
        override val collidable get() = false

        override fun name() = "fluid-$name"
    }
}

val solidLayer = TerrainLayer.Solid
val waterFluidLayer = TerrainLayer.Fluid("water", 997.0f, FluidSettings(0.5f, 0.01f))
val lavaFluidLater = TerrainLayer.Fluid("lava", 3100.0f, FluidSettings(0.5f, 0.01f))

class SliceTerrainStrategy(
    private val engine: IgnacioEngine,
    private val world: World,
    private val physics: PhysicsSpace,
) : TerrainStrategy {
    data class SliceSnapshot(
        val pos: SlicePos,
        val blocks: ChunkSnapshot,
    )

    data class SliceSettings(
        val layers: Map<TerrainLayer, Geometry>,
    )

    data class SliceBodies(
        val layers: Map<TerrainLayer, PhysicsBody>,
    ) {
        fun allBodies() = layers.map { (_, body) -> body }
    }

    data class FluidContact(
        val fluidBody: PhysicsBody,
        val fluid: TerrainLayer.Fluid,
    )

    private val bodyKey = ignacioBodyName("SliceTerrainStrategy-${world.name}")
    private val cube = engine.createShape(BoxGeometry(Vec3f(0.5f)))
    private val startY = world.minHeight
    private val numSlices = (world.maxHeight - startY) / 16
    private val negativeYSlices = -startY / 16

    private val chunkSnapshots = HashMap<Long, ChunkSnapshot>()
    private var toRemove: MutableSet<SlicePos> = HashSet()
    private var toCreate: MutableSet<SlicePos> = HashSet()
    private var toSnapshot: MutableMap<SlicePos, SliceSnapshot> = HashMap()

    private val sliceBodies = HashMap<SlicePos, SliceBodies>()
    private val bodyToLayer = HashMap<PhysicsBody, TerrainLayer>()
    private val inFluid = ConcurrentHashMap<PhysicsBody, FluidContact>()

    private val onStep = StepListener { deltaTime ->
        inFluid.forEach { (access, contact) ->
            access.writeUnlockedOf<PhysicsBody.MovingWrite> { body ->
                val bodyDensity = body.shape.density ?: return@writeUnlockedOf
                val fluid = contact.fluid

                body.applyBuoyancy(
                    deltaTime = deltaTime,
                    buoyancy = fluid.density / bodyDensity,
                    fluidSurface = Vec3d(0.0, 63.0, 0.0), // todo
                    fluidNormal = Vec3f.Up,
                    fluidVelocity = Vec3f.Zero, // todo
                    fluid = fluid.settings,
                )
            }
        }
    }
    private val onContact = object : ContactListener {
        override fun onAdded(body1: PhysicsBody.Read, body2: PhysicsBody.Read, manifold: ContactManifold) {
            fun tryAdd(target: PhysicsBody, fluidBody: PhysicsBody) {
                val fluidLayer = bodyToLayer[fluidBody] as? TerrainLayer.Fluid ?: return
                inFluid[target] = FluidContact(fluidBody, fluidLayer)
            }

            tryAdd(body1.body, body2.body)
            tryAdd(body2.body, body1.body)
        }

        override fun onRemoved(body1: PhysicsBody, body2: PhysicsBody) {
            fun tryRemove(target: PhysicsBody, fluidBody: PhysicsBody) {
                val contact = inFluid[target] ?: return
                if (contact.fluidBody != fluidBody) return
                inFluid.remove(target)
            }

            tryRemove(body1, body2)
            tryRemove(body2, body1)
        }
    }

    var enabled = true
        private set

    override val numInFluid: Int
        get() = inFluid.size

    init {
        physics.onStep(onStep)
        physics.onContact(onContact)
    }

    override fun destroy() {
        cube.destroy()
        physics.removeStepListener(onStep)
        physics.removeContactListener(onContact)
    }

    override fun enable() {
        enabled = true
    }

    override fun disable() {
        enabled = false
    }

    private fun createSliceSettings(slice: SliceSnapshot): SliceSettings {
        val layers = HashMap<TerrainLayer, MutableList<CompoundChild>>()

        fun add(layer: TerrainLayer, child: CompoundChild) {
            layers.computeIfAbsent(layer) { ArrayList() } += child
        }

        fun processBlock(lx: Int, ly: Int, lz: Int) {
            val gy = slice.pos.y * 16 + ly
            val block = slice.blocks.getBlockData(lx, gy, lz)
            val material = block.material
            when (material) {
                Material.WATER -> {
                    add(waterFluidLayer, CompoundChild(
                        shape = cube, // todo lower fluid levels
                        position = Vec3f(lx + 0.5f, ly + 0.5f, lz + 0.5f),
                        rotation = Quat.Identity,
                    ))
                    return
                }
                Material.LAVA -> {
                    add(lavaFluidLater, CompoundChild(
                        shape = cube,
                        position = Vec3f(lx + 0.5f, ly + 0.5f, lz + 0.5f),
                        rotation = Quat.Identity,
                    ))
                    return
                }
                else -> {}
            }

            when {
                material.isCollidable -> add(solidLayer, CompoundChild(
                    shape = cube,
                    position = Vec3f(lx + 0.5f, ly + 0.5f, lz + 0.5f),
                    rotation = Quat.Identity,
                ))
            }
        }

        repeat(16) { lx ->
            repeat(16) { ly ->
                repeat(16) { lz ->
                    processBlock(lx, ly, lz)
                }
            }
        }

        return SliceSettings(
            layers = layers
                .map { (layer, children) -> layer to StaticCompoundGeometry(children) }
                .associate { it },
        )
    }

    private fun createSliceBodies(slice: SliceSnapshot): SliceBodies {
        val settings = createSliceSettings(slice)
        val sliceTransform = Transform(slice.pos.toVec3d() * 16.0)

        val layers = settings.layers.map { (layer, geometry) ->
            layer to physics.bodies.createStatic(
                StaticBodySettings(
                    name = "$bodyKey-${slice.pos}-${layer.name()}",
                    shape = engine.createShape(geometry),
                    layer = engine.layers.ofObject.terrain,
                    isSensor = !layer.collidable,
                ), sliceTransform
            ).body
        }.associate { it }

        return SliceBodies(
            layers = layers,
        )
    }

    override fun tickUpdate() {
        if (!enabled) return

        // create snapshots for positions
        val toSnapshot = HashMap<SlicePos, SliceSnapshot>()
        toCreate.forEach { pos ->
            // chunk not loaded, don't load it
            if (!world.isChunkLoaded(pos.x, pos.z)) return@forEach
            // already created body, don't remake it
            if (sliceBodies.contains(pos)) return@forEach
            val sy = pos.y + negativeYSlices
            // out of range, we can't make a body
            if (sy < 0 || sy >= numSlices) return@forEach

            val chunkKey = Chunk.getChunkKey(pos.x, pos.z)
            val snapshot = chunkSnapshots.computeIfAbsent(chunkKey) {
                world.getChunkAt(pos.x, pos.z).getChunkSnapshot(false, false, false)
            }
            // empty slices aren't even passed to toCreate
            if (snapshot.isSectionEmpty(sy)) return@forEach

            toSnapshot[pos] = SliceSnapshot(
                pos = pos,
                blocks = snapshot,
            )
        }
        chunkSnapshots.clear()
        // even though we're double buffering, we clear it afterwards anyway
        // in case we go through a 2nd tick and the buffer hasn't updated yet
        toCreate.clear()
        this.toSnapshot = toSnapshot

        engine.launchTask {
            // clear all the bodies we've marked as unused last tick
            val bodiesToRemove = synchronized(sliceBodies) {
                synchronized(bodyToLayer) {
                    toRemove.flatMap { pos ->
                        sliceBodies.remove(pos)?.allBodies() ?: emptyList()
                    }.toSet()
                }
            }
            toRemove.clear()
            bodyToLayer -= bodiesToRemove
            physics.bodies {
                removeAll(bodiesToRemove)
                destroyAll(bodiesToRemove)
            }

            // create all the bodies we've created snapshots for last tick
            val slices = toSnapshot.map { (pos, slice) ->
                async { pos to createSliceBodies(slice) }
            }.awaitAll()
            toSnapshot.clear()

            val bodiesToAdd = ArrayList<PhysicsBody>()
            synchronized(sliceBodies) {
                synchronized(bodyToLayer) {
                    slices.forEach { (pos, bodies) ->
                        sliceBodies[pos] = bodies
                        bodies.layers.forEach { (layer, body) ->
                            bodyToLayer[body] = layer
                            bodiesToAdd += body
                        }
                    }
                }
            }
            physics.bodies.addAll(bodiesToAdd, false)
        }
    }

    override fun physicsUpdate(deltaTime: Float) {
        if (!enabled) return

        val toRemove = synchronized(sliceBodies) { HashSet(sliceBodies.keys) }
        val toCreate = HashSet<SlicePos>()
        physics.bodies.active().forEach { body ->
            body.readUnlocked { access ->
                // only create for moving objects (TODO custom object layer support: expand this)
                if (access.objectLayer != engine.layers.ofObject.moving) return@readUnlocked
                // next tick, we will create snapshots of the chunk slices this body covers
                val overlappingSlices = (access.boundingBox / 16.0).points().toSet()
                toCreate += overlappingSlices
                toRemove -= overlappingSlices
            }
        }
        this.toRemove = toRemove
        this.toCreate = toCreate
    }

    override fun isTerrain(body: PhysicsBody) = synchronized(bodyToLayer) { bodyToLayer.contains(body) }

    override fun onChunksLoad(chunks: Collection<Chunk>) {}

    override fun onChunksUnload(chunks: Collection<Chunk>) {
        if (!enabled) return

    }

    override fun onSlicesUpdate(slices: Collection<SlicePos>) {
        if (!enabled) return

    }
}
