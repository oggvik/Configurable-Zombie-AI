package oggvik.mods.configurablezombieai.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.ai.goal.NearestAttackableTargetGoal;
import net.minecraft.entity.monster.ZombieEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
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

    @Inject(method = "findTarget()V", at = @At("HEAD"), cancellable = true)
    private void configurablezombieai$findTarget(CallbackInfo ci) {
        MobEntity mob = ((TargetGoalAccessor) this).configurablezombieai$getMob();
        if (!(mob instanceof ZombieEntity)) {
            return;
        }

        if (this.targetType != PlayerEntity.class && this.targetType != ServerPlayerEntity.class) {
            return;
        }

        if (!ZombieAiRuntime.isModEnabled(mob)) {
            return;
        }

        this.target = ZombieAiRuntime.selectInitialPlayerTarget(mob);
        ci.cancel();
    }
}
