package oggvik.mods.configurablezombieai.runtime

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sqrt
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityPredicate
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.MobEntity
import net.minecraft.entity.ai.attributes.Attributes
import net.minecraft.entity.merchant.villager.AbstractVillagerEntity
import net.minecraft.entity.monster.ZombieEntity
import net.minecraft.entity.passive.IronGolemEntity
import net.minecraft.entity.passive.TurtleEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.server.MinecraftServer
import oggvik.mods.configurablezombieai.ConfigurableZombieAI
import oggvik.mods.configurablezombieai.config.ZombieAiSavedData
import java.util.function.Predicate

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
        val zombie = mob as? ZombieEntity ?: return false
        val livingEntity = entity as? LivingEntity ?: return false
        val settings = ZombieAiSavedData.get(mob.level) ?: return false
        if (!settings.modEnabled || !settings.ignoreLineOfSight) {
            return false
        }

        if (!zombie.canAttack(livingEntity) || !zombie.canAttackType(livingEntity.type) || zombie.isAlliedTo(livingEntity)) {
            return false
        }

        val range = settings.viewDistance.coerceAtLeast(1.0)
        return zombie.distanceToSqr(entity) <= range * range
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
    fun selectInitialTarget(
        mob: MobEntity,
        targetType: Class<out LivingEntity>,
        targetConditions: EntityPredicate
    ): LivingEntity? {
        val zombie = mob as? ZombieEntity ?: return null
        val settings = ZombieAiSavedData.get(mob.level) ?: return null
        if (!settings.modEnabled) {
            return null
        }

        val followRange = mob.getAttributeValue(Attributes.FOLLOW_RANGE).coerceAtLeast(1.0)
        targetConditions.range(followRange)
        val candidates = collectCandidates(zombie, targetType, followRange) { candidate ->
            targetConditions.test(zombie, candidate)
        }
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
        }?.target
    }

    @JvmStatic
    fun selectSwitchTarget(zombie: ZombieEntity): LivingEntity? {
        val settings = ZombieAiSavedData.get(zombie.level) ?: return null
        if (!settings.modEnabled || !settings.switchEnabled) {
            return null
        }

        val currentTarget = zombie.target ?: return null
        if (!currentTarget.isAlive) {
            return null
        }

        val interval = settings.switchIntervalTicks.coerceAtLeast(1)
        if ((zombie.tickCount + zombie.id) % interval != 0) {
            return null
        }

        val targetFamily = resolveTargetFamily(currentTarget)
        val candidatesById = LinkedHashMap<Int, TargetCandidate>()
        for (candidate in collectSwitchCandidates(zombie, targetFamily, settings)) {
            candidatesById[candidate.target.id] = candidate
        }

        val currentCandidate = buildCurrentTargetCandidate(zombie, currentTarget, targetFamily, settings)
        if (currentCandidate != null) {
            candidatesById.putIfAbsent(currentCandidate.target.id, currentCandidate)
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
        }?.target
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

    private fun collectSwitchCandidates(
        zombie: ZombieEntity,
        targetFamily: TargetFamily,
        settings: ZombieAiSavedData
    ): List<TargetCandidate> {
        val predicate = buildEntityPredicate(settings.switchSearchRadius.coerceAtLeast(1.0), settings, targetFamily.selector)
        return collectCandidates(zombie, targetFamily.entityClass, settings.switchSearchRadius.coerceAtLeast(1.0)) { candidate ->
            predicate.test(zombie, candidate)
        }
    }

    private fun buildCurrentTargetCandidate(
        zombie: ZombieEntity,
        currentTarget: LivingEntity,
        targetFamily: TargetFamily,
        settings: ZombieAiSavedData
    ): TargetCandidate? {
        val followRange = zombie.getAttributeValue(Attributes.FOLLOW_RANGE).coerceAtLeast(1.0)
        val predicate = buildEntityPredicate(followRange, settings, targetFamily.selector)
        if (!predicate.test(zombie, currentTarget)) {
            return null
        }

        return TargetCandidate(currentTarget, sqrt(zombie.distanceToSqr(currentTarget)))
    }

    private fun switchWeight(
        candidate: TargetCandidate,
        currentTarget: LivingEntity,
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

        val sameTargetWeight = if (candidate.target.id == currentTarget.id) {
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

    private fun buildEntityPredicate(
        maxRange: Double,
        settings: ZombieAiSavedData,
        selector: Predicate<LivingEntity>?
    ): EntityPredicate {
        val predicate = EntityPredicate()
            .range(maxRange.coerceAtLeast(1.0))
            .selector(selector)
        if (settings.ignoreLineOfSight) {
            predicate.allowUnseeable()
        }
        return predicate
    }

    private fun resolveTargetFamily(target: LivingEntity): TargetFamily {
        return when (target) {
            is PlayerEntity -> TargetFamily(PlayerEntity::class.java)
            is AbstractVillagerEntity -> TargetFamily(AbstractVillagerEntity::class.java)
            is IronGolemEntity -> TargetFamily(IronGolemEntity::class.java)
            is TurtleEntity -> TargetFamily(TurtleEntity::class.java, TurtleEntity.BABY_ON_LAND_SELECTOR)
            else -> TargetFamily(target.javaClass.asSubclass(LivingEntity::class.java))
        }
    }

    private fun collectCandidates(
        zombie: ZombieEntity,
        targetClass: Class<out LivingEntity>,
        maxRange: Double,
        predicate: (LivingEntity) -> Boolean
    ): List<TargetCandidate> {
        val clampedRange = maxRange.coerceAtLeast(1.0)
        val searchBox = zombie.boundingBox.inflate(clampedRange, clampedRange, clampedRange)
        @Suppress("UNCHECKED_CAST")
        val typedClass = targetClass as Class<LivingEntity>
        return zombie.level.getEntitiesOfClass(typedClass, searchBox) { candidate ->
            predicate(candidate)
        }.map { candidate ->
            TargetCandidate(candidate, sqrt(zombie.distanceToSqr(candidate)))
        }.sortedBy { it.distance }
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

    private data class TargetFamily(
        val entityClass: Class<out LivingEntity>,
        val selector: Predicate<LivingEntity>? = null
    )

    private data class TargetCandidate(
        val target: LivingEntity,
        val distance: Double
    )
}
