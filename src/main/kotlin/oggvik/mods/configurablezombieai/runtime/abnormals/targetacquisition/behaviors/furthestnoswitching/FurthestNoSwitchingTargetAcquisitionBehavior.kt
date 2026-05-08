package oggvik.mods.configurablezombieai.runtime.abnormals.targetacquisition.behaviors.furthestnoswitching

import net.minecraft.entity.LivingEntity
import oggvik.mods.configurablezombieai.runtime.ZombieAiRuntime
import oggvik.mods.configurablezombieai.runtime.abnormals.targetacquisition.AbnormalTargetAcquisitionBehavior
import oggvik.mods.configurablezombieai.runtime.abnormals.targetacquisition.AbnormalTargetAcquisitionContext

object FurthestNoSwitchingTargetAcquisitionBehavior : AbnormalTargetAcquisitionBehavior {
    const val ID: String = "furthest_no_switching"

    override val id: String = ID
    override val displayName: String = "Furthest Target Without Switching"

    override fun selectTarget(context: AbnormalTargetAcquisitionContext): LivingEntity? {
        val target = context.candidates.maxByOrNull { candidate -> candidate.distance }?.target ?: return null
        ZombieAiRuntime.lockTargetSwitchingForAbnormalAcquisition(context.zombie, target)
        return target
    }
}
