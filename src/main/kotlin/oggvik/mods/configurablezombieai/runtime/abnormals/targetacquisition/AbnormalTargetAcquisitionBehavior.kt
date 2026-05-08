package oggvik.mods.configurablezombieai.runtime.abnormals.targetacquisition

import net.minecraft.entity.LivingEntity

interface AbnormalTargetAcquisitionBehavior {
    val id: String
    val displayName: String

    fun selectTarget(context: AbnormalTargetAcquisitionContext): LivingEntity?
}
