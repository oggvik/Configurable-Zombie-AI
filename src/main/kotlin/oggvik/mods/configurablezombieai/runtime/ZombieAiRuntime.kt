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
import oggvik.mods.configurablezombieai.runtime.abnormals.targetacquisition.AbnormalTargetAcquisitionCandidate
import oggvik.mods.configurablezombieai.runtime.abnormals.targetacquisition.AbnormalTargetAcquisitionContext
import oggvik.mods.configurablezombieai.runtime.abnormals.targetacquisition.AbnormalTargetAcquisitionRegistry
import java.util.function.Predicate

/**
 * Central gameplay logic for the mod.
 *
 * Mixins stay intentionally thin and delegate to this object so the rules for
 * target acquisition, target switching, LOS bypass, despawn prevention, and
 * follow-range updates live in one place.
 */
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

        // Acquisition still respects vanilla goal families. This logic only
        // replaces how one NearestAttackableTargetGoal picks within its own
        // target class after the vanilla predicate has filtered candidates.
        val closestDistance = candidates.first().distance
        val maxDistance = closestDistance + settings.acquisitionDistanceVariability.coerceAtLeast(0.0)
        val pool = candidates.filter { it.distance <= maxDistance + EPSILON }
        if (pool.isEmpty()) {
            return null
        }

        val closestCandidates = pool.filter { kotlin.math.abs(it.distance - closestDistance) <= EPSILON }
        if (closestCandidates.isEmpty()) {
            return null
        }

        val abnormalTarget = AbnormalTargetAcquisitionRegistry.trySelectTarget(
            AbnormalTargetAcquisitionContext(
                zombie = zombie,
                targetType = targetType,
                targetConditions = targetConditions,
                candidates = candidates.map { it.toAbnormalTargetAcquisitionCandidate() },
                acquisitionPool = pool.map { it.toAbnormalTargetAcquisitionCandidate() },
                closestCandidates = closestCandidates.map { it.toAbnormalTargetAcquisitionCandidate() },
                otherCandidates = pool.filter { it.distance - closestDistance > EPSILON }
                    .map { it.toAbnormalTargetAcquisitionCandidate() },
                closestDistance = closestDistance,
                maxDistance = maxDistance
            ),
            settings
        )
        if (abnormalTarget != null) {
            return abnormalTarget
        }

        // The initial "closest chance" setting is intentionally simple:
        // either roll among the closest-distance ties, or roll among the other
        // valid candidates that survived the distance variability filter.
        val closestChance = settings.acquisitionClosestChancePercent.coerceIn(0.0, 100.0)
        if (closestChance <= EPSILON) {
            return pickRandom(pool, mob.random)?.target
        }

        val otherCandidates = pool.filter { it.distance - closestDistance > EPSILON }
        if (otherCandidates.isEmpty() || closestChance >= 100.0 - EPSILON) {
            return pickRandom(closestCandidates, mob.random)?.target
        }

        val rolledClosest = mob.random.nextDouble() < (closestChance / 100.0)
        val selectionPool = if (rolledClosest) closestCandidates else otherCandidates
        return pickRandom(selectionPool, mob.random)?.target
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

        // Switching runs as a fresh weighted draw on the configured tick, but
        // it is not a completely global re-acquisition. The zombie keeps the
        // current target's family as the switching domain, so a zombie already
        // chasing a villager will only compare villagers during this pass.
        // Switching is deliberately family-local. Once a zombie is already
        // chasing a player, villager, golem, or turtle, the switching pass only
        // evaluates alternatives in that same family.
        val targetFamily = resolveTargetFamily(currentTarget)
        val candidatesById = LinkedHashMap<Int, TargetCandidate>()
        for (candidate in collectSwitchCandidates(zombie, targetFamily, settings)) {
            candidatesById[candidate.target.id] = candidate
        }

        val currentCandidate = buildCurrentTargetCandidate(zombie, currentTarget, targetFamily, settings)
        if (currentCandidate != null) {
            candidatesById.putIfAbsent(currentCandidate.target.id, currentCandidate)
        }

        // The current target is added back separately so "stay on the current
        // target" is represented as one of the weighted outcomes instead of a
        // hardcoded special case outside the lottery.
        val candidates = candidatesById.values.toList()
        if (candidates.isEmpty()) {
            return null
        }

        val closestDistance = candidates.minOf { it.distance }
        val farthestDistance = candidates.maxOf { it.distance }
        val currentDistance = currentCandidate?.distance ?: sqrt(zombie.distanceToSqr(currentTarget))

        // Conceptually this asks:
        // "Given the current target and all same-family alternatives, which one
        // should win right now after combining absolute closeness, improvement
        // over the current target, and current-target stickiness?"
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
            // The first time the mod touches a zombie, preserve vanilla's base
            // follow range so disabling the mod can restore it cleanly.
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
        // Only follow range needs an eager push. Other behaviors pick up the new
        // values naturally because the runtime helpers re-read saved settings.
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
        // Switching uses its own search radius instead of the full follow range
        // so operators can make retargeting stricter or looser than acquisition.
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

        // The current target is validated at follow-range distance instead of
        // switch-search-radius distance so a zombie can keep chasing a target
        // that is still broadly valid even after it moves outside the smaller
        // retargeting search radius.
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
        // Switching has three independent influences:
        // 1. absolute closeness within the candidate pool,
        // 2. whether the candidate is closer than the current target, and
        // 3. an explicit stay-on-current-target bias.
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

        // Multiplying these exponential terms means each slider contributes
        // additively in log-space:
        //
        // log(weight) =
        //   switchTargetBias * absoluteCloseness
        // + switchCloserThanCurrentBias * relativeImprovement
        // + switchCurrentTargetBias * isCurrentTarget
        //
        // So each parameter has a separable conceptual role even though their
        // effects can sometimes point in the same direction.
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

        // 1.0 means "closest in this candidate set", 0.0 means "farthest in
        // this candidate set". Everything else is normalized in between.
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
        // Rebuilding predicates here keeps switching aligned with the current
        // LOS mode and any family-specific selector, such as baby turtles on land.
        val predicate = EntityPredicate()
            .range(maxRange.coerceAtLeast(1.0))
            .selector(selector)
        if (settings.ignoreLineOfSight) {
            predicate.allowUnseeable()
        }
        return predicate
    }

    private fun resolveTargetFamily(target: LivingEntity): TargetFamily {
        // These families mirror the main zombie target-goal buckets in vanilla.
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
        // The world query stays broad and then sorts by true Euclidean distance
        // so both acquisition and switching can reason about nearest/farthest.
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

        // Invalid or non-positive weights are ignored so configuration edge
        // cases degrade into a safe uniform pick instead of crashing.
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

    private fun <T> pickRandom(values: List<T>, random: java.util.Random): T? {
        if (values.isEmpty()) {
            return null
        }

        return values[random.nextInt(values.size)]
    }

    private fun TargetCandidate.toAbnormalTargetAcquisitionCandidate(): AbnormalTargetAcquisitionCandidate {
        return AbnormalTargetAcquisitionCandidate(target, distance)
    }

    // A "family" is the switching scope for an already-acquired target.
    private data class TargetFamily(
        val entityClass: Class<out LivingEntity>,
        val selector: Predicate<LivingEntity>? = null
    )

    // Carrying precomputed distances keeps the higher-level logic readable.
    private data class TargetCandidate(
        val target: LivingEntity,
        val distance: Double
    )
}
