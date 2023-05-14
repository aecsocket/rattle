package io.github.aecsocket.ignacio.physx

import io.github.aecsocket.ignacio.*
import org.spongepowered.configurate.objectmapping.ConfigSerializable
import physx.common.PxDefaultAllocator
import physx.common.PxErrorCallbackImpl
import physx.common.PxErrorCodeEnum
import java.util.logging.Level
import java.util.logging.Logger

import physx.PxTopLevelFunctions.*
import physx.common.PxCpuDispatcher
import physx.common.PxErrorCallback
import physx.common.PxFoundation
import physx.common.PxTolerancesScale
import physx.geometry.PxBoxGeometry
import physx.geometry.PxCapsuleGeometry
import physx.geometry.PxSphereGeometry
import physx.physics.*

class PhysxEngine(
    val settings: Settings,
    logger: Logger,
) : PhysicsEngine {
    enum class BroadPhaseType {
        SAP,
        // MBP, // requires defining regions
        ABP,
        PABP,
        // TODO GPU,
    }

    @ConfigSerializable
    data class Settings(
        val numThreads: Int = 0,
        val broadPhaseType: BroadPhaseType = BroadPhaseType.ABP,
        val scratchBlocks: Int = 4, // 64KB
    )

    private val destroyed = DestroyFlag()
    val allocator: PxDefaultAllocator
    val errorCallback: PxErrorCallback
    val foundation: PxFoundation
    val tolerances: PxTolerancesScale
    val physics: PxPhysics
    val cpuDispatcher: PxCpuDispatcher

    val defaultFilterShader = DefaultFilterShader()
    val defaultMaterial: PxMaterial
    val defaultShapeFlags: PxShapeFlags
    val defaultFilterData: PxFilterData

    override lateinit var version: String
        private set

    init {
        val versionNum = getPHYSICS_VERSION()
        version = "${versionNum shr 24}.${(versionNum shr 16) and 0xff}.${(versionNum shr 8) and 0xff}"

        allocator = PxDefaultAllocator()
        errorCallback = object : PxErrorCallbackImpl() {
            override fun reportError(code: PxErrorCodeEnum, message: String, file: String, line: Int) {
                val level = when (code) {
                    PxErrorCodeEnum.eNO_ERROR, PxErrorCodeEnum.eMASK_ALL -> Level.INFO

                    PxErrorCodeEnum.eDEBUG_INFO -> Level.FINE

                    PxErrorCodeEnum.eDEBUG_WARNING, PxErrorCodeEnum.ePERF_WARNING -> Level.WARNING

                    PxErrorCodeEnum.eINVALID_PARAMETER,
                    PxErrorCodeEnum.eINVALID_OPERATION,
                    PxErrorCodeEnum.eOUT_OF_MEMORY,
                    PxErrorCodeEnum.eINTERNAL_ERROR,
                    PxErrorCodeEnum.eABORT -> Level.SEVERE
                }
                logger.log(level, "$message ($file:$line)")
            }
        }
        foundation = CreateFoundation(versionNum, allocator, errorCallback)

        tolerances = PxTolerancesScale()
        physics = CreatePhysics(versionNum, foundation, tolerances)

        cpuDispatcher = DefaultCpuDispatcherCreate(numThreads(settings.numThreads))

        // TODO
        defaultMaterial = physics.createMaterial(0.5f, 0.5f, 0.5f)
        defaultShapeFlags = PxShapeFlags(
            (PxShapeFlagEnum.eSCENE_QUERY_SHAPE.value or PxShapeFlagEnum.eSIMULATION_SHAPE.value).toByte()
        )
        defaultFilterData = PxFilterData(1, 1, 0, 0)
    }

    override fun destroy() {
        destroyed()

        defaultFilterData.destroy()
        defaultShapeFlags.destroy()
        defaultMaterial.release()

        physics.release()
        foundation.release()
        errorCallback.destroy()
        allocator.destroy()
    }

    override fun createMaterial(desc: PhysicsMaterialDesc): PhysicsMaterial {
        fun CoeffCombineRule.asPx() = when (this) {
            CoeffCombineRule.AVERAGE -> PxCombineModeEnum.eAVERAGE
            CoeffCombineRule.MIN -> PxCombineModeEnum.eMIN
            CoeffCombineRule.MULTIPLY -> PxCombineModeEnum.eMULTIPLY
            CoeffCombineRule.MAX -> PxCombineModeEnum.eMAX
        }

        val material = physics.createMaterial(
            desc.friction.toFloat(),
            desc.friction.toFloat(),
            desc.restitution.toFloat(),
        )
        material.frictionCombineMode = desc.frictionCombine.asPx()
        material.restitutionCombineMode = desc.restitutionCombine.asPx()
        return PhysxMaterial(material)
    }

    override fun createShape(geom: Geometry): Shape {
        val shape = pushArena { arena ->
            val pxGeom = when (geom) {
                is Sphere -> PxSphereGeometry.createAt(
                    arena,
                    allocFn,
                    geom.radius.toFloat(),
                )
                is Cuboid -> PxBoxGeometry.createAt(
                    arena,
                    allocFn,
                    geom.halfExtent.x.toFloat(),
                    geom.halfExtent.y.toFloat(),
                    geom.halfExtent.z.toFloat(),
                )
                is Capsule -> PxCapsuleGeometry.createAt(
                    arena,
                    allocFn,
                    geom.radius.toFloat(),
                    geom.halfHeight.toFloat(),
                )
            }
            physics.createShape(pxGeom, defaultMaterial) // TODO
        }
        shape.simulationFilterData = defaultFilterData
        return PhysxShape(shape)
    }

    override fun createSpace(settings: PhysicsSpace.Settings): PhysicsSpace {
        val scene = pushArena { arena ->
            val scene = PxSceneDesc.createAt(arena, allocFn, tolerances)
            scene.gravity = settings.gravity.asPx(arena)
            scene.cpuDispatcher = cpuDispatcher
            scene.filterShader = defaultFilterShader
            scene.broadPhaseType = when (this.settings.broadPhaseType) {
                BroadPhaseType.SAP -> PxBroadPhaseTypeEnum.eSAP
                BroadPhaseType.ABP -> PxBroadPhaseTypeEnum.eABP
                BroadPhaseType.PABP -> PxBroadPhaseTypeEnum.ePABP
                //BroadPhaseType.GPU -> PxBroadPhaseTypeEnum.eGPU
            }
            physics.createScene(scene)
        }
        return PhysxSpace(this, scene, settings)
    }
}