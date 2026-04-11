package oggvik.mods.configurablezombieai.runtime

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sqrt
import net.minecraft.entity.Entity
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.MobEntity
import net.minecraft.entity.ai.attributes.Attributes
import net.minecraft.entity.monster.ZombieEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.server.MinecraftServer
import oggvik.mods.configurablezombieai.ConfigurableZombieAI
import oggvik.mods.configurablezombieai.config.ZombieAiSavedData

object ZombieAiRuntime {
    private const val ORIGINAL_FOLLOW_RANGE_KEY: String = "${ConfigurableZombieAI.ID}.original_follow_range"
    private const val BIAS_CLAMP: Double = 20.0
    private const val EPSILON: Double = 1.0E-6

    @JvmStatic
    fun isModEnabled(mob: MobEntity): Boolean {
        val settings = ZombieAiSavedData.get(mob.level) ?: return false
        return settings.modEnabled
    }

    @JvmStatic
    fun shouldBypassLineOfSight(mob: MobEntity, entity: Entity): Boolean {
        if (mob !is ZombieEntity || entity !is PlayerEntity) {
            return false
        }

        val settings = ZombieAiSavedData.get(mob.level) ?: return false
        if (!settings.modEnabled || !settings.ignoreLineOfSight) {
            return false
        }

        val range = settings.viewDistance.coerceAtLeast(1.0)
        return mob.distanceToSqr(entity) <= range * range
    }

    @JvmStatic
    fun shouldPreventDespawn(mob: MobEntity): Boolean {
        if (mob !is ZombieEntity) {
            return false
        }

        val settings = ZombieAiSavedData.get(mob.level) ?: return false
        return settings.modEnabled && settings.disableZombieDespawn
    }

    @JvmStatic
    fun selectInitialPlayerTarget(mob: MobEntity): LivingEntity? {
        if (mob !is ZombieEntity) {
            return null
        }

        val settings = ZombieAiSavedData.get(mob.level) ?: return null
        if (!settings.modEnabled) {
            return null
        }

        val followRange = mob.getAttributeValue(Attributes.FOLLOW_RANGE).coerceAtLeast(1.0)
        val candidates = collectPlayerCandidates(mob, followRange, settings)
        if (candidates.isEmpty()) {
            return null
        }

        val closestDistance = candidates.first().distance
        val maxDistance = closestDistance + settings.acquisitionDistanceVariability.coerceAtLeast(0.0)
        val pool = candidates.filter { it.distance <= maxDistance + EPSILON }
        val farthestDistance = pool.maxOf { it.distance }

        return weightedPick(pool, mob.random) { candidate ->
            closenessWeight(
                distance = candidate.distance,
                closestDistance = closestDistance,
                farthestDistance = farthestDistance,
                bias = settings.acquisitionClosestBias
            )
        }?.player
    }

    @JvmStatic
    fun selectSwitchTarget(zombie: ZombieEntity): PlayerEntity? {
        val settings = ZombieAiSavedData.get(zombie.level) ?: return null
        if (!settings.modEnabled || !settings.switchEnabled) {
            return null
        }

        val currentTarget = zombie.target as? PlayerEntity ?: return null
        if (!currentTarget.isAlive) {
            return null
        }

        val interval = settings.switchIntervalTicks.coerceAtLeast(1)
        if ((zombie.tickCount + zombie.id) % interval != 0) {
            return null
        }

        val candidatesById = LinkedHashMap<Int, PlayerCandidate>()
        for (candidate in collectPlayerCandidates(zombie, settings.switchSearchRadius.coerceAtLeast(1.0), settings)) {
            candidatesById[candidate.player.id] = candidate
        }

        val currentCandidate = buildCurrentTargetCandidate(zombie, currentTarget, settings)
        if (currentCandidate != null) {
            candidatesById.putIfAbsent(currentCandidate.player.id, currentCandidate)
        }

        val candidates = candidatesById.values.toList()
        if (candidates.isEmpty()) {
            return null
        }

        val closestDistance = candidates.minOf { it.distance }
        val farthestDistance = candidates.maxOf { it.distance }
        val currentDistance = currentCandidate?.distance ?: sqrt(zombie.distanceToSqr(currentTarget))

        return weightedPick(candidates, zombie.random) { candidate ->
            switchWeight(
                candidate = candidate,
                currentTarget = currentTarget,
                currentDistance = currentDistance,
                closestDistance = closestDistance,
                farthestDistance = farthestDistance,
                settings = settings
            )
        }?.player
    }

    @JvmStatic
    fun applyCurrentSettings(zombie: ZombieEntity) {
        if (zombie.level.isClientSide) {
            return
        }

        val followRange = zombie.getAttribute(Attributes.FOLLOW_RANGE) ?: return
        val persistentData = zombie.persistentData
        if (!persistentData.contains(ORIGINAL_FOLLOW_RANGE_KEY)) {
            persistentData.putDouble(ORIGINAL_FOLLOW_RANGE_KEY, followRange.baseValue)
        }

        val settings = ZombieAiSavedData.get(zombie.level) ?: return
        val restoredValue = persistentData.getDouble(ORIGINAL_FOLLOW_RANGE_KEY).takeIf { it > 0.0 }
            ?: ZombieAiSavedData.VANILLA_FOLLOW_RANGE

        followRange.baseValue = if (settings.modEnabled) {
            settings.viewDistance.coerceAtLeast(1.0)
        } else {
            restoredValue
        }
    }

    @JvmStatic
    fun onSettingsChanged(server: MinecraftServer) {
        for (world in server.allLevels) {
            for (entity in world.allEntities) {
                if (entity is ZombieEntity) {
                    applyCurrentSettings(entity)
                }
            }
        }
    }

    private fun collectPlayerCandidates(
        zombie: ZombieEntity,
        maxRange: Double,
        settings: ZombieAiSavedData
    ): List<PlayerCandidate> {
        val clampedRange = maxRange.coerceAtLeast(1.0)
        val searchBox = zombie.boundingBox.inflate(clampedRange, clampedRange, clampedRange)
        return zombie.level.getEntitiesOfClass(PlayerEntity::class.java, searchBox) { player ->
            isEligiblePlayerTarget(zombie, player, clampedRange, settings)
        }.map { player ->
            PlayerCandidate(player, sqrt(zombie.distanceToSqr(player)))
        }.sortedBy { it.distance }
    }

    private fun isEligiblePlayerTarget(
        zombie: ZombieEntity,
        player: PlayerEntity,
        maxRange: Double,
        settings: ZombieAiSavedData
    ): Boolean {
        if (!player.isAlive || player.isSpectator) {
            return false
        }

        if (player.abilities.invulnerable || player.isInvulnerable) {
            return false
        }

        if (!zombie.canAttack(player) || !zombie.canAttackType(player.type) || zombie.isAlliedTo(player)) {
            return false
        }

        val visibilityMultiplier = player.getVisibilityPercent(zombie)
        val effectiveRange = max(maxRange * visibilityMultiplier, 2.0)
        if (zombie.distanceToSqr(player) > effectiveRange * effectiveRange) {
            return false
        }

        if (!settings.ignoreLineOfSight && !zombie.sensing.canSee(player)) {
            return false
        }

        return true
    }

    private fun buildCurrentTargetCandidate(
        zombie: ZombieEntity,
        currentTarget: PlayerEntity,
        settings: ZombieAiSavedData
    ): PlayerCandidate? {
        val followRange = zombie.getAttributeValue(Attributes.FOLLOW_RANGE).coerceAtLeast(1.0)
        if (!isEligiblePlayerTarget(zombie, currentTarget, followRange, settings)) {
            return null
        }

        return PlayerCandidate(currentTarget, sqrt(zombie.distanceToSqr(currentTarget)))
    }

    private fun switchWeight(
        candidate: PlayerCandidate,
        currentTarget: PlayerEntity,
        currentDistance: Double,
        closestDistance: Double,
        farthestDistance: Double,
        settings: ZombieAiSavedData
    ): Double {
        val distanceWeight = closenessWeight(
            distance = candidate.distance,
            closestDistance = closestDistance,
            farthestDistance = farthestDistance,
            bias = settings.switchTargetBias
        )

        val relativeDelta = ((currentDistance - candidate.distance) / max(currentDistance, 1.0)).coerceIn(-1.0, 1.0)
        val relativeWeight = exp(clampBias(settings.switchCloserThanCurrentBias) * relativeDelta)

        val sameTargetWeight = if (candidate.player.id == currentTarget.id) {
            exp(clampBias(settings.switchCurrentTargetBias))
        } else {
            1.0
        }

        return distanceWeight * relativeWeight * sameTargetWeight
    }

    private fun closenessWeight(
        distance: Double,
        closestDistance: Double,
        farthestDistance: Double,
        bias: Double
    ): Double {
        if (farthestDistance - closestDistance <= EPSILON) {
            return 1.0
        }

        val normalizedCloseness = 1.0 - ((distance - closestDistance) / (farthestDistance - closestDistance)).coerceIn(0.0, 1.0)
        return exp(clampBias(bias) * normalizedCloseness)
    }

    private fun clampBias(value: Double): Double {
        return value.coerceIn(-BIAS_CLAMP, BIAS_CLAMP)
    }

    private fun <T> weightedPick(values: List<T>, random: java.util.Random, weightSelector: (T) -> Double): T? {
        if (values.isEmpty()) {
            return null
        }

        val weights = DoubleArray(values.size)
        var totalWeight = 0.0

        for (index in values.indices) {
            val rawWeight = weightSelector(values[index])
            val weight = rawWeight.takeIf { it.isFinite() && it > 0.0 } ?: 0.0
            weights[index] = weight
            totalWeight += weight
        }

        if (totalWeight <= 0.0) {
            return values[random.nextInt(values.size)]
        }

        val roll = random.nextDouble() * totalWeight
        var cursor = 0.0
        for (index in values.indices) {
            cursor += weights[index]
            if (roll <= cursor) {
                return values[index]
            }
        }

        return values.last()
    }

    private data class PlayerCandidate(
        val player: PlayerEntity,
        val distance: Double
    )
}
