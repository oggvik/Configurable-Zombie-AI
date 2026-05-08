package oggvik.mods.configurablezombieai.runtime.abnormals.targetacquisition

import net.minecraft.entity.LivingEntity

data class AbnormalTargetAcquisitionCandidate(
    val target: LivingEntity,
    val distance: Double
)
