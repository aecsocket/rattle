package io.gitlab.aecsocket.ignacio.bullet

import com.jme3.bullet.PhysicsSpace
import com.jme3.bullet.collision.shapes.BoxCollisionShape
import com.jme3.bullet.collision.shapes.CapsuleCollisionShape
import com.jme3.bullet.collision.shapes.CollisionShape
import com.jme3.bullet.collision.shapes.EmptyShape
import com.jme3.bullet.collision.shapes.PlaneCollisionShape
import com.jme3.bullet.collision.shapes.SphereCollisionShape
import com.jme3.bullet.joints.New6Dof
import com.jme3.bullet.objects.PhysicsRigidBody
import com.jme3.math.Plane
import com.jme3.math.Vector3f
import com.jme3.system.JmeSystem
import com.jme3.system.Platform
import io.gitlab.aecsocket.ignacio.core.*
import io.gitlab.aecsocket.ignacio.core.math.Transform
import io.gitlab.aecsocket.ignacio.core.math.Vec3
import org.spongepowered.configurate.objectmapping.ConfigSerializable
import java.io.File
import java.net.URL
import java.nio.file.Files
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.collections.HashMap

const val JME_VERSION = "17.5.4"

enum class JmeBuildType(val key: String) {
    RELEASE ("Release"),
    DEBUG   ("Debug")
}

enum class JmeFlavor(val key: String) {
    SP      ("Sp"),
    SP_MT   ("SpMt"),
    DP      ("Dp"),
    DP_MT   ("DpMt")
}

fun Platform.nativePathSuffix(): String {
    return when (this) {
        Platform.Windows32, Platform.Windows64 -> "bulletjme.dll"
        Platform.Linux_ARM32, Platform.Linux_ARM64, Platform.Linux32, Platform.Linux64 -> "libbulletjme.so"
        Platform.MacOSX32, Platform.MacOSX64, Platform.MacOSX_ARM64 -> "libbulletjme.dylib"
        else -> throw IllegalArgumentException("Platform $this has no native path suffix")
    }
}

class BulletBackend(
    settings: Settings,
    val physicsThread: IgPhysicsThread,
    root: File,
    logger: Logger
) : IgBackend<BulletBackend.Settings> {
    @ConfigSerializable
    data class Settings(
        val buildType: JmeBuildType = JmeBuildType.RELEASE,
        val flavor: JmeFlavor = JmeFlavor.DP_MT,
        val maxSubSteps: Int = 4,
    )

    var settings: Settings = settings
        private set

    init {
        val platform = JmeSystem.getPlatform()
        val buildType = settings.buildType.key
        val flavor = settings.flavor.key
        val suffix = platform.nativePathSuffix()
        // for downloading from Libbulletjme repo
        val remoteFileName = "${platform}${buildType}${flavor}_${suffix}"
        // for loading from pre-downloaded native
        val localFileName = "${platform}${buildType}${flavor}_${JME_VERSION}_${suffix}"

        val nativeFile = root.resolve(localFileName)

        if (!root.resolve(localFileName).exists()) {
            // auto-download
            val urlAddr = "https://github.com/stephengold/Libbulletjme/releases/download/$JME_VERSION/$remoteFileName"
            val url = URL(urlAddr)
            logger.info("Downloading natives from $urlAddr to $localFileName")
            url.openStream().use { stream ->
                root.mkdirs()
                Files.copy(stream, nativeFile.toPath())
            }
            logger.info("Downloaded")
        }

        try {
            System.load(nativeFile.absolutePath)
        } catch (ex: Exception) {
            throw RuntimeException("Could not load native library", ex)
        }

        // avoid excess logging
        PhysicsRigidBody.logger2.level = Level.WARNING
        New6Dof.logger2.level = Level.WARNING

        logger.info("Initialized Bullet v$JME_VERSION backend")
    }

    val spaces = HashMap<Long, BltPhysicsSpace>()

    val emptyShape = EmptyShape(false)
    val planeShape = PlaneCollisionShape(Plane(Vector3f.UNIT_X, 0f))

    override fun reload(settings: Settings) {
        this.settings = settings
    }

    internal inline fun assertThread() = physicsThread.assertThread()

    fun btShapeOf(geometry: IgGeometry): CollisionShape {
        return when (geometry) {
            is IgPlaneGeometry -> planeShape
            is IgSphereGeometry -> SphereCollisionShape(geometry.radius.toFloat())
            is IgBoxGeometry -> BoxCollisionShape(geometry.halfExtent.btSp())
            is IgCapsuleGeometry -> CapsuleCollisionShape(geometry.radius.toFloat(), (geometry.halfHeight * 2).toFloat())
        }
    }

    override fun createShape(geometry: IgGeometry, transform: Transform): IgShape {
        val handle = btShapeOf(geometry)
        return BltShape(handle, geometry, transform)
    }

    private fun createBody(transform: Transform, mass: Float): BltRigidBody {
        val handle = PhysicsRigidBody(emptyShape, mass)
        handle.transform = transform
        return BltRigidBody(this, handle)
    }

    override fun createStaticBody(transform: Transform): BltRigidBody {
        return createBody(transform, PhysicsRigidBody.massForStatic)
    }

    override fun createDynamicBody(transform: Transform, dynamics: IgBodyDynamics): BltRigidBody {
        val body = createBody(transform, dynamics.mass.toFloat())
        return body
    }

    override fun createSpace(settings: IgPhysicsSpace.Settings): BltPhysicsSpace {
        assertThread()
        val handle = PhysicsSpace(PhysicsSpace.BroadphaseType.DBVT)
        handle.gravity = settings.gravity

        val ground = createStaticBody(
            Transform(Vec3(0.0, settings.groundPlaneY, 0.0), groundPlaneQuat)
        )
        ground.setGeometry(IgPlaneGeometry)
        val space = BltPhysicsSpace(this, handle, settings, ground)
        space.addBody(ground)

        spaces[handle.nativeId()] = space
        return space
    }

    override fun destroySpace(space: IgPhysicsSpace) {
        assertThread()
        space as BltPhysicsSpace
        spaces.remove(space.handle.nativeId())
        space.handle.destroy()
    }

    override fun step(spaces: Iterable<IgPhysicsSpace>) {
        spaces.forEach { space ->
            space as BltPhysicsSpace
            // TODO multithread this dogwater code
            space.handle.update(space.settings.stepInterval.toFloat(), settings.maxSubSteps)
        }
    }

    override fun destroy() {
        assertThread()
        spaces.forEach { (_, space) ->
            destroySpace(space)
        }
    }
}
