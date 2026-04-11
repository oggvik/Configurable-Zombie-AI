package oggvik.mods.configurablezombieai.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.monster.ZombieEntity;
import oggvik.mods.configurablezombieai.runtime.ZombieAiRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ZombieEntity.class)
public abstract class ZombieTargetSwitchMixin {
    @Inject(method = "aiStep()V", at = @At("TAIL"))
    private void configurablezombieai$aiStep(CallbackInfo ci) {
        ZombieEntity zombie = (ZombieEntity) (Object) this;
        if (zombie.level.isClientSide) {
            return;
        }

        LivingEntity selectedTarget = ZombieAiRuntime.selectSwitchTarget(zombie);
        if (selectedTarget != null && selectedTarget != zombie.getTarget()) {
            zombie.setTarget(selectedTarget);
        }
    }
}
