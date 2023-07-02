package io.github.aecsocket.rattle.paper

import io.github.aecsocket.alexandria.ItemRender
import io.github.aecsocket.alexandria.hook.AlexandriaHook
import io.github.aecsocket.alexandria.paper.AlexandriaPlugin
import io.github.aecsocket.alexandria.paper.ItemDisplayRender
import io.github.aecsocket.alexandria.paper.create
import io.github.aecsocket.alexandria.paper.seralizer.paperSerializers
import io.github.aecsocket.glossa.Glossa
import io.github.aecsocket.glossa.MessageProxy
import io.github.aecsocket.rattle.*
import io.github.aecsocket.rattle.impl.RattleHook
import io.github.aecsocket.rattle.impl.RattleMessages
import io.github.aecsocket.rattle.impl.rattleManifest
import io.github.aecsocket.rattle.serializer.rattleSerializers
import io.github.oshai.kotlinlogging.KLogger
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.ConfigurationOptions
import org.spongepowered.configurate.kotlin.dataClassFieldDiscoverer
import org.spongepowered.configurate.kotlin.extensions.get
import org.spongepowered.configurate.objectmapping.ObjectMapper

/**
 * The entry point for Rattle on the Paper platform.
 */
lateinit var Rattle: PaperRattle
    private set

/**
 * The Paper implementation of Rattle, handling startup, events, teardown, etc.
 */
class PaperRattle : AlexandriaPlugin<RattleHook.Settings>(
    manifest = rattleManifest,
    configOptions = ConfigurationOptions.defaults()
        .serializers { it
            .registerAll(paperSerializers)
            .registerAll(rattleSerializers)
            .registerAnnotatedObjects(ObjectMapper.factoryBuilder()
                .addDiscoverer(dataClassFieldDiscoverer())
                .build()
            )
        },
    savedResources = listOf()
) {
    companion object {
        @JvmStatic
        fun api() = Rattle
    }

    private lateinit var lineItem: ItemStack

    internal val rattle = object : RattleHook() {
        override val ax: AlexandriaHook<*>
            get() = this@PaperRattle.ax

        override val log: KLogger
            get() = this@PaperRattle.log

        override val settings: Settings
            get() = this@PaperRattle.settings

        override val glossa: Glossa
            get() = this@PaperRattle.glossa

        override val draw = object : Draw {
            override fun lineItem(render: ItemRender) {
                (render as ItemDisplayRender).item(lineItem)
            }
        }
    }

    /**
     * Gets the physics engine that this implementation uses.
     */
    val engine: PhysicsEngine
        get() = rattle.engine

    val messages: MessageProxy<RattleMessages>
        get() = rattle.messages

    fun runTask(task: Runnable) =
        rattle.runTask(task)

    lateinit var platform: PaperRattlePlatform
        private set

    init {
        Rattle = this
    }

    override fun loadSettings(node: ConfigurationNode) = node.get() ?: RattleHook.Settings()

    override fun onPreInit() {
        platform = PaperRattlePlatform(this)
    }

    override fun onInit() {
        rattle.init()
    }

    override fun onPostEnable() {
        PaperRattleCommand(this)
        platform.onPostEnable()
    }

    override fun onLoadData() {
        rattle.load(platform)
        lineItem = settings.draw.lineItem.create()
    }

    override fun onReloadData() {
        rattle.reload()
    }

    override fun onDestroy() {
        rattle.destroy(platform)
    }
}

/**
 * @see PaperRattlePlayer
 */
fun Player.rattle() = Rattle.platform.playerData(this)

/**
 * @see PaperRattlePlatform.physicsOrNull
 */
fun World.physicsOrNull() = Rattle.platform.physicsOrNull(this)

/**
 * @see PaperRattlePlatform.physicsOrCreate
 */
fun World.physicsOrCreate() = Rattle.platform.physicsOrCreate(this)

/**
 * @see PaperRattlePlatform.hasPhysics
 */
fun World.hasPhysics() = physicsOrNull() != null
