package oggvik.mods.configurablezombieai.mixin;

import net.minecraft.entity.ai.goal.NearestAttackableTargetGoal;
import net.minecraft.entity.monster.ZombieEntity;
import net.minecraft.entity.passive.horse.HorseEntity;
import oggvik.mods.configurablezombieai.runtime.ZombieAiRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ZombieEntity.class)
public abstract class ZombieHorseTargetGoalMixin {
    @Inject(method = "addBehaviourGoals()V", at = @At("TAIL"))
    private void configurablezombieai$addHorseTargetGoal(CallbackInfo ci) {
        ZombieEntity zombie = (ZombieEntity) (Object) this;
        zombie.targetSelector.addGoal(
            4,
            new NearestAttackableTargetGoal<>(
                zombie,
                HorseEntity.class,
                10,
                true,
                false,
                target -> ZombieAiRuntime.canUseConfiguredTarget(zombie, target)
            )
        );
    }
}
