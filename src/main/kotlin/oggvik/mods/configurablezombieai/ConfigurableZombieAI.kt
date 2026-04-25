package oggvik.mods.configurablezombieai

import java.util.function.BiPredicate
import java.util.function.Supplier
import net.minecraft.entity.monster.ZombieEntity
import net.minecraftforge.event.RegisterCommandsEvent
import net.minecraftforge.event.entity.EntityJoinWorldEvent
import net.minecraftforge.fml.ExtensionPoint
import net.minecraftforge.fml.ModLoadingContext
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.event.server.FMLServerStoppedEvent
import net.minecraftforge.fml.network.FMLNetworkConstants
import oggvik.mods.configurablezombieai.command.ZombieAiCommands
import oggvik.mods.configurablezombieai.config.ZombieAiSavedData
import oggvik.mods.configurablezombieai.runtime.ZombieAiRuntime
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.apache.commons.lang3.tuple.Pair
import thedarkcolour.kotlinforforge.forge.FORGE_BUS

@Mod(ConfigurableZombieAI.ID)
/**
 * Forge entrypoint for the mod.
 *
 * The object itself stays intentionally small: it only wires Forge lifecycle
 * events into the command layer, the persisted settings store, and the runtime
 * AI logic used by the mixins.
 */
object ConfigurableZombieAI {
    const val ID: String = "configurablezombieai"

    val LOGGER: Logger = LogManager.getLogger()

    init {
        // This mod is server-authoritative. Registering the display test tells
        // Forge that clients may connect even when they do not have the mod
        // installed locally, as long as the server has it.
        ModLoadingContext.get().registerExtensionPoint(ExtensionPoint.DISPLAYTEST) {
            Pair.of(
                Supplier { FMLNetworkConstants.IGNORESERVERONLY },
                BiPredicate<String, Boolean> { _, _ -> true }
            )
        }

        // Commands, zombie setup, and cache cleanup are all server-side concerns.
        FORGE_BUS.addListener(::onRegisterCommands)
        FORGE_BUS.addListener(::onEntityJoinWorld)
        FORGE_BUS.addListener(::onServerStopped)
    }

    private fun onRegisterCommands(event: RegisterCommandsEvent) {
        ZombieAiCommands.register(event.dispatcher)
    }

    private fun onEntityJoinWorld(event: EntityJoinWorldEvent) {
        if (!event.world.isClientSide && event.entity is ZombieEntity) {
            // Newly spawned or newly loaded zombies immediately pick up the
            // current server settings without waiting for a later refresh.
            ZombieAiRuntime.applyCurrentSettings(event.entity as ZombieEntity)
        }
    }

    private fun onServerStopped(@Suppress("UNUSED_PARAMETER") event: FMLServerStoppedEvent) {
        // Saved data is cached per server instance, so it must be cleared when
        // the integrated or dedicated server shuts down.
        ZombieAiSavedData.clearCache()
    }
}
