package oggvik.mods.configurablezombieai.runtime.abnormals.targetacquisition

import net.minecraft.entity.EntityPredicate
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.monster.ZombieEntity

data class AbnormalTargetAcquisitionContext(
    val zombie: ZombieEntity,
    val targetType: Class<out LivingEntity>,
    val targetConditions: EntityPredicate,
    val candidates: List<AbnormalTargetAcquisitionCandidate>,
    val acquisitionPool: List<AbnormalTargetAcquisitionCandidate>,
    val closestCandidates: List<AbnormalTargetAcquisitionCandidate>,
    val otherCandidates: List<AbnormalTargetAcquisitionCandidate>,
    val closestDistance: Double,
    val maxDistance: Double
) {
    fun containsTarget(target: LivingEntity): Boolean {
        return candidates.any { candidate -> candidate.target.id == target.id }
    }
}
