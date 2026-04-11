package oggvik.mods.configurablezombieai

import net.minecraft.entity.monster.ZombieEntity
import net.minecraftforge.event.RegisterCommandsEvent
import net.minecraftforge.event.entity.EntityJoinWorldEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.event.server.FMLServerStoppedEvent
import oggvik.mods.configurablezombieai.command.ZombieAiCommands
import oggvik.mods.configurablezombieai.config.ZombieAiSavedData
import oggvik.mods.configurablezombieai.runtime.ZombieAiRuntime
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import thedarkcolour.kotlinforforge.forge.FORGE_BUS

@Mod(ConfigurableZombieAI.ID)
object ConfigurableZombieAI {
    const val ID: String = "configurablezombieai"

    val LOGGER: Logger = LogManager.getLogger()

    init {
        FORGE_BUS.addListener(::onRegisterCommands)
        FORGE_BUS.addListener(::onEntityJoinWorld)
        FORGE_BUS.addListener(::onServerStopped)
    }

    private fun onRegisterCommands(event: RegisterCommandsEvent) {
        ZombieAiCommands.register(event.dispatcher)
    }

    private fun onEntityJoinWorld(event: EntityJoinWorldEvent) {
        if (!event.world.isClientSide && event.entity is ZombieEntity) {
            ZombieAiRuntime.applyCurrentSettings(event.entity as ZombieEntity)
        }
    }

    private fun onServerStopped(@Suppress("UNUSED_PARAMETER") event: FMLServerStoppedEvent) {
        ZombieAiSavedData.clearCache()
    }
}
