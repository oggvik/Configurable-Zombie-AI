package oggvik.mods.configurablezombieai.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.monster.ZombieEntity;
import oggvik.mods.configurablezombieai.runtime.ZombieAiRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MobEntity.class)
public abstract class ZombieSetTargetMixin {
    @Inject(method = "setTarget(Lnet/minecraft/entity/LivingEntity;)V", at = @At("HEAD"), cancellable = true)
    private void configurablezombieai$setTarget(LivingEntity target, CallbackInfo ci) {
        MobEntity mob = (MobEntity) (Object) this;
        if (mob instanceof ZombieEntity && !ZombieAiRuntime.canSetConfiguredTarget((ZombieEntity) mob, target)) {
            ci.cancel();
        }
    }

    @Inject(method = "setTarget(Lnet/minecraft/entity/LivingEntity;)V", at = @At("RETURN"))
    private void configurablezombieai$afterSetTarget(LivingEntity target, CallbackInfo ci) {
        MobEntity mob = (MobEntity) (Object) this;
        if (mob instanceof ZombieEntity) {
            ZombieAiRuntime.onTargetAssigned((ZombieEntity) mob, target);
        }
    }
}
