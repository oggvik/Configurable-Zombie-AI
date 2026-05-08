package oggvik.mods.configurablezombieai.runtime.abnormals.targetacquisition

import net.minecraft.entity.LivingEntity
import oggvik.mods.configurablezombieai.ConfigurableZombieAI
import oggvik.mods.configurablezombieai.config.ZombieAiSavedData
import java.util.Random

/**
 * Concrete abnormal acquisition behaviors should live under child packages of
 * `runtime.abnormals.targetacquisition.behaviors` and register themselves here.
 */
object AbnormalTargetAcquisitionRegistry {
    private val behaviors: MutableMap<String, AbnormalTargetAcquisitionBehavior> = linkedMapOf()

    fun register(behavior: AbnormalTargetAcquisitionBehavior) {
        val normalizedId = ZombieAiSavedData.normalizeAbnormalBehaviorId(behavior.id)
            ?: throw IllegalArgumentException("Invalid abnormal target acquisition behavior id: ${behavior.id}")
        require(normalizedId !in behaviors) {
            "Duplicate abnormal target acquisition behavior id: $normalizedId"
        }

        behaviors[normalizedId] = behavior
    }

    fun registeredBehaviorIds(): List<String> {
        return behaviors.keys.toList()
    }

    fun trySelectTarget(
        context: AbnormalTargetAcquisitionContext,
        settings: ZombieAiSavedData
    ): LivingEntity? {
        val abnormalChance = settings.abnormalAcquisitionChancePercent.coerceIn(0.0, 100.0)
        if (abnormalChance <= 0.0 || context.zombie.random.nextDouble() >= abnormalChance / 100.0) {
            return null
        }

        val behavior = pickConfiguredBehavior(settings, context.zombie.random) ?: return null
        val selectedTarget = try {
            behavior.selectTarget(context)
        } catch (exception: RuntimeException) {
            ConfigurableZombieAI.LOGGER.warn(
                "Abnormal target acquisition behavior '${behavior.id}' failed; falling back to normal acquisition.",
                exception
            )
            null
        } ?: return null

        return selectedTarget.takeIf { context.containsTarget(it) }
    }

    private fun pickConfiguredBehavior(
        settings: ZombieAiSavedData,
        random: Random
    ): AbnormalTargetAcquisitionBehavior? {
        if (behaviors.isEmpty()) {
            return null
        }

        val behaviorEntries = behaviors.entries.toList()
        val weights = DoubleArray(behaviorEntries.size)
        var totalWeight = 0.0

        for (index in behaviorEntries.indices) {
            val behaviorId = behaviorEntries[index].key
            val rawWeight = settings.getAbnormalAcquisitionBehaviorChancePercent(behaviorId)
            val weight = rawWeight.takeIf { it.isFinite() && it > 0.0 } ?: 0.0
            weights[index] = weight
            totalWeight += weight
        }

        if (totalWeight <= 0.0) {
            // A positive global abnormal chance is harmless by itself. Without
            // at least one positive behavior weight, normal acquisition should run.
            return null
        }

        val roll = random.nextDouble() * totalWeight
        var cursor = 0.0
        for (index in behaviorEntries.indices) {
            cursor += weights[index]
            if (roll <= cursor) {
                return behaviorEntries[index].value
            }
        }

        return behaviorEntries.last().value
    }
}
