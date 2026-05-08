package oggvik.mods.configurablezombieai.runtime.abnormals.targetacquisition.behaviors.furthest

import net.minecraft.entity.LivingEntity
import oggvik.mods.configurablezombieai.runtime.abnormals.targetacquisition.AbnormalTargetAcquisitionBehavior
import oggvik.mods.configurablezombieai.runtime.abnormals.targetacquisition.AbnormalTargetAcquisitionContext

object FurthestTargetAcquisitionBehavior : AbnormalTargetAcquisitionBehavior {
    const val ID: String = "furthest"

    override val id: String = ID
    override val displayName: String = "Furthest Target"

    override fun selectTarget(context: AbnormalTargetAcquisitionContext): LivingEntity? {
        return context.candidates.maxByOrNull { candidate -> candidate.distance }?.target
    }
}
