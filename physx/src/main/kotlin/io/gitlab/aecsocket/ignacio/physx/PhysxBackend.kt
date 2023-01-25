package io.gitlab.aecsocket.ignacio.physx

import io.gitlab.aecsocket.ignacio.core.IgnacioBackend
import org.spongepowered.configurate.objectmapping.ConfigSerializable
import physx.PxTopLevelFunctions.*
import physx.common.PxCpuDispatcher
import physx.common.PxDefaultAllocator
import physx.common.PxDefaultErrorCallback
import physx.common.PxFoundation
import physx.common.PxTolerancesScale
import physx.physics.PxPhysics
import java.io.File
import java.util.logging.Logger

class PhysxBackend(root: File, settings: Settings, logger: Logger) : IgnacioBackend {
    @ConfigSerializable
    data class Settings(
        val numThreads: Int = -1
    )

    val numThreads: Int

    val foundation: PxFoundation
    val tolerances: PxTolerancesScale
    val physics: PxPhysics
    val dispatcher: PxCpuDispatcher

    init {
        numThreads = if (settings.numThreads < 0) Runtime.getRuntime().availableProcessors() else settings.numThreads

        val version = getPHYSICS_VERSION()
        val allocator = PxDefaultAllocator()
        val errorCb = PxDefaultErrorCallback()
        foundation = CreateFoundation(version, allocator, errorCb)

        tolerances = PxTolerancesScale()
        physics = CreatePhysics(version, foundation, tolerances)

        dispatcher = DefaultCpuDispatcherCreate(numThreads)

        val versionMajor = version shr 24
        val versionMinor = (version shr 16) and 0xff
        val versionBuild = (version shr 8) and 0xff
        logger.info("Initialized PhysX v$versionMajor.$versionMinor.$versionBuild backend")
    }
}
