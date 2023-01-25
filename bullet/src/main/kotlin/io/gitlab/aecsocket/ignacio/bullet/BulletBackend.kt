package io.gitlab.aecsocket.ignacio.bullet

import com.jme3.bullet.joints.New6Dof
import com.jme3.bullet.objects.PhysicsRigidBody
import com.jme3.system.JmeSystem
import com.jme3.system.Platform
import io.gitlab.aecsocket.ignacio.core.IgnacioBackend
import org.spongepowered.configurate.objectmapping.ConfigSerializable
import java.io.File
import java.net.URL
import java.nio.file.Files
import java.util.logging.Level
import java.util.logging.Logger

const val JME_VERSION = "17.5.2"

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

class BulletBackend(root: File, settings: Settings, logger: Logger) : IgnacioBackend {
    @ConfigSerializable
    data class Settings(
        val buildType: JmeBuildType = JmeBuildType.RELEASE,
        val flavor: JmeFlavor = JmeFlavor.DP_MT
    )

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
}
