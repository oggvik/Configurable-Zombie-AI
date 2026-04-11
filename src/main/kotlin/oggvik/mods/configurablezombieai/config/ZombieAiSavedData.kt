package oggvik.mods.configurablezombieai.config

import net.minecraft.nbt.CompoundNBT
import net.minecraft.server.MinecraftServer
import net.minecraft.world.World
import net.minecraft.world.server.ServerWorld
import net.minecraft.world.storage.WorldSavedData
import oggvik.mods.configurablezombieai.ConfigurableZombieAI

/**
 * Server-owned settings for every feature in the mod.
 *
 * This is stored once in overworld data storage so all loaded dimensions share
 * the same live configuration, and so command changes persist with the world.
 */
class ZombieAiSavedData : WorldSavedData(DATA_NAME) {
    var modEnabled: Boolean = true
        set(value) {
            field = value
            setDirty()
        }

    var ignoreLineOfSight: Boolean = false
        set(value) {
            field = value
            setDirty()
        }

    var disableZombieDespawn: Boolean = false
        set(value) {
            field = value
            setDirty()
        }

    var viewDistance: Double = VANILLA_FOLLOW_RANGE
        set(value) {
            field = value
            setDirty()
        }

    var acquisitionDistanceVariability: Double = 0.0
        set(value) {
            field = value
            setDirty()
        }

    var acquisitionClosestChancePercent: Double = 100.0
        set(value) {
            field = value.coerceIn(0.0, 100.0)
            setDirty()
        }

    var switchEnabled: Boolean = false
        set(value) {
            field = value
            setDirty()
        }

    var switchIntervalTicks: Int = 40
        set(value) {
            field = value
            setDirty()
        }

    var switchSearchRadius: Double = 16.0
        set(value) {
            field = value
            setDirty()
        }

    var switchTargetBias: Double = 0.0
        set(value) {
            field = value
            setDirty()
        }

    var switchCurrentTargetBias: Double = 1.0
        set(value) {
            field = value
            setDirty()
        }

    var switchCloserThanCurrentBias: Double = 1.5
        set(value) {
            field = value
            setDirty()
        }

    override fun load(nbt: CompoundNBT) {
        if (nbt.contains("modEnabled")) {
            modEnabled = nbt.getBoolean("modEnabled")
        }
        if (nbt.contains("ignoreLineOfSight")) {
            ignoreLineOfSight = nbt.getBoolean("ignoreLineOfSight")
        }
        if (nbt.contains("disableZombieDespawn")) {
            disableZombieDespawn = nbt.getBoolean("disableZombieDespawn")
        }
        if (nbt.contains("viewDistance")) {
            viewDistance = nbt.getDouble("viewDistance")
        }
        if (nbt.contains("acquisitionDistanceVariability")) {
            acquisitionDistanceVariability = nbt.getDouble("acquisitionDistanceVariability")
        }
        if (nbt.contains(ACQUISITION_CLOSEST_CHANCE_PERCENT_KEY)) {
            acquisitionClosestChancePercent = nbt.getDouble(ACQUISITION_CLOSEST_CHANCE_PERCENT_KEY)
        }
        if (nbt.contains("switchEnabled")) {
            switchEnabled = nbt.getBoolean("switchEnabled")
        }
        if (nbt.contains("switchIntervalTicks")) {
            switchIntervalTicks = nbt.getInt("switchIntervalTicks")
        }
        if (nbt.contains("switchSearchRadius")) {
            switchSearchRadius = nbt.getDouble("switchSearchRadius")
        }
        if (nbt.contains("switchTargetBias")) {
            switchTargetBias = nbt.getDouble("switchTargetBias")
        }
        if (nbt.contains("switchCurrentTargetBias")) {
            switchCurrentTargetBias = nbt.getDouble("switchCurrentTargetBias")
        }
        if (nbt.contains("switchCloserThanCurrentBias")) {
            switchCloserThanCurrentBias = nbt.getDouble("switchCloserThanCurrentBias")
        }
    }

    override fun save(compound: CompoundNBT): CompoundNBT {
        compound.putBoolean("modEnabled", modEnabled)
        compound.putBoolean("ignoreLineOfSight", ignoreLineOfSight)
        compound.putBoolean("disableZombieDespawn", disableZombieDespawn)
        compound.putDouble("viewDistance", viewDistance)
        compound.putDouble("acquisitionDistanceVariability", acquisitionDistanceVariability)
        compound.putDouble(ACQUISITION_CLOSEST_CHANCE_PERCENT_KEY, acquisitionClosestChancePercent)
        compound.putBoolean("switchEnabled", switchEnabled)
        compound.putInt("switchIntervalTicks", switchIntervalTicks)
        compound.putDouble("switchSearchRadius", switchSearchRadius)
        compound.putDouble("switchTargetBias", switchTargetBias)
        compound.putDouble("switchCurrentTargetBias", switchCurrentTargetBias)
        compound.putDouble("switchCloserThanCurrentBias", switchCloserThanCurrentBias)
        return compound
    }

    companion object {
        private const val DATA_NAME: String = "${ConfigurableZombieAI.ID}_settings"
        private const val ACQUISITION_CLOSEST_CHANCE_PERCENT_KEY: String = "acquisitionClosestChancePercent"
        const val VANILLA_FOLLOW_RANGE: Double = 35.0

        // Commands and mixins ask for settings frequently, so the current
        // server's data object is cached until the server shuts down.
        private var cachedServer: MinecraftServer? = null
        private var cachedData: ZombieAiSavedData? = null

        @JvmStatic
        fun clearCache() {
            cachedServer = null
            cachedData = null
        }

        @JvmStatic
        fun get(world: World?): ZombieAiSavedData? {
            if (world !is ServerWorld) {
                return null
            }

            val server = world.server
            if (cachedServer === server && cachedData != null) {
                return cachedData
            }

            // Overworld data storage is the shared persistent store for the
            // whole server, even when commands are executed in another dimension.
            val data = server.overworld().dataStorage.computeIfAbsent(::ZombieAiSavedData, DATA_NAME)
            cachedServer = server
            cachedData = data
            return data
        }
    }
}
