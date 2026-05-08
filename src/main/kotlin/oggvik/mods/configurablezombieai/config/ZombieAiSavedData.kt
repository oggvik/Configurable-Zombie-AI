package oggvik.mods.configurablezombieai.config

import net.minecraft.nbt.CompoundNBT
import net.minecraft.server.MinecraftServer
import net.minecraft.world.World
import net.minecraft.world.server.ServerWorld
import net.minecraft.world.storage.WorldSavedData
import oggvik.mods.configurablezombieai.ConfigurableZombieAI
import java.util.Locale

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
            field = coercePercent(value)
            setDirty()
        }

    var abnormalAcquisitionChancePercent: Double = 0.0
        set(value) {
            field = coercePercent(value)
            setDirty()
        }

    private val abnormalAcquisitionBehaviorWeights: MutableMap<String, Double> = linkedMapOf()

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
        if (nbt.contains(ABNORMAL_ACQUISITION_CHANCE_PERCENT_KEY)) {
            abnormalAcquisitionChancePercent = nbt.getDouble(ABNORMAL_ACQUISITION_CHANCE_PERCENT_KEY)
        }
        abnormalAcquisitionBehaviorWeights.clear()
        if (nbt.contains(ABNORMAL_ACQUISITION_BEHAVIOR_WEIGHTS_KEY, NBT_COMPOUND_ID)) {
            val behaviorWeights = nbt.getCompound(ABNORMAL_ACQUISITION_BEHAVIOR_WEIGHTS_KEY)
            for (behaviorId in behaviorWeights.allKeys) {
                setAbnormalAcquisitionBehaviorWeight(behaviorId, behaviorWeights.getDouble(behaviorId))
            }
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
        compound.putDouble(ABNORMAL_ACQUISITION_CHANCE_PERCENT_KEY, abnormalAcquisitionChancePercent)
        val behaviorWeights = CompoundNBT()
        for ((behaviorId, weight) in abnormalAcquisitionBehaviorWeights) {
            behaviorWeights.putDouble(behaviorId, weight)
        }
        compound.put(ABNORMAL_ACQUISITION_BEHAVIOR_WEIGHTS_KEY, behaviorWeights)
        compound.putBoolean("switchEnabled", switchEnabled)
        compound.putInt("switchIntervalTicks", switchIntervalTicks)
        compound.putDouble("switchSearchRadius", switchSearchRadius)
        compound.putDouble("switchTargetBias", switchTargetBias)
        compound.putDouble("switchCurrentTargetBias", switchCurrentTargetBias)
        compound.putDouble("switchCloserThanCurrentBias", switchCloserThanCurrentBias)
        return compound
    }

    fun abnormalAcquisitionBehaviorWeights(): Map<String, Double> {
        return abnormalAcquisitionBehaviorWeights.toMap()
    }

    fun getAbnormalAcquisitionBehaviorWeight(behaviorId: String): Double {
        val normalizedId = normalizeAbnormalBehaviorId(behaviorId) ?: return 0.0
        return abnormalAcquisitionBehaviorWeights[normalizedId] ?: DEFAULT_ABNORMAL_ACQUISITION_BEHAVIOR_WEIGHT
    }

    fun setAbnormalAcquisitionBehaviorWeight(behaviorId: String, weight: Double): Boolean {
        val normalizedId = normalizeAbnormalBehaviorId(behaviorId) ?: return false
        abnormalAcquisitionBehaviorWeights[normalizedId] = coerceWeight(weight)
        setDirty()
        return true
    }

    companion object {
        private const val DATA_NAME: String = "${ConfigurableZombieAI.ID}_settings"
        private const val ACQUISITION_CLOSEST_CHANCE_PERCENT_KEY: String = "acquisitionClosestChancePercent"
        private const val ABNORMAL_ACQUISITION_CHANCE_PERCENT_KEY: String = "abnormalAcquisitionChancePercent"
        private const val ABNORMAL_ACQUISITION_BEHAVIOR_WEIGHTS_KEY: String = "abnormalAcquisitionBehaviorWeights"
        private const val NBT_COMPOUND_ID: Int = 10
        const val VANILLA_FOLLOW_RANGE: Double = 35.0
        const val DEFAULT_ABNORMAL_ACQUISITION_BEHAVIOR_WEIGHT: Double = 1.0
        private val ABNORMAL_BEHAVIOR_ID_PATTERN = Regex("[a-z0-9_.:-]+")

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

        private fun coercePercent(value: Double): Double {
            if (!value.isFinite()) {
                return 0.0
            }

            return value.coerceIn(0.0, 100.0)
        }

        private fun coerceWeight(value: Double): Double {
            if (!value.isFinite()) {
                return 0.0
            }

            return value.coerceAtLeast(0.0)
        }

        fun normalizeAbnormalBehaviorId(behaviorId: String): String? {
            val normalizedId = behaviorId.trim().lowercase(Locale.ROOT)
            if (normalizedId.isEmpty() || !ABNORMAL_BEHAVIOR_ID_PATTERN.matches(normalizedId)) {
                return null
            }

            return normalizedId
        }
    }
}
