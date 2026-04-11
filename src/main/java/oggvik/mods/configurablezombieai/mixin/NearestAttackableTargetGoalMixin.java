package oggvik.mods.configurablezombieai.mixin;

import net.minecraft.entity.EntityPredicate;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.ai.goal.NearestAttackableTargetGoal;
import net.minecraft.entity.monster.ZombieEntity;
import oggvik.mods.configurablezombieai.runtime.ZombieAiRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NearestAttackableTargetGoal.class)
public abstract class NearestAttackableTargetGoalMixin {
    @Shadow protected LivingEntity target;
    @Shadow protected Class<? extends LivingEntity> targetType;
    @Shadow protected EntityPredicate targetConditions;

    @Inject(method = "findTarget()V", at = @At("HEAD"), cancellable = true)
    private void configurablezombieai$findTarget(CallbackInfo ci) {
        MobEntity mob = ((TargetGoalAccessor) this).configurablezombieai$getMob();
        if (!(mob instanceof ZombieEntity)) {
            return;
        }

        if (!ZombieAiRuntime.isModEnabled(mob)) {
            return;
        }

        // Vanilla still decides whether this target goal is allowed to run at
        // all. Once it does, the runtime owns the actual choice of target.
        this.target = ZombieAiRuntime.selectInitialTarget(mob, this.targetType, this.targetConditions);
        ci.cancel();
    }
}
