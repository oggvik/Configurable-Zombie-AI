package oggvik.mods.configurablezombieai.mixin;

import net.minecraft.entity.MobEntity;
import oggvik.mods.configurablezombieai.runtime.ZombieAiRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MobEntity.class)
public abstract class ZombiePersistenceMixin {
    @Inject(method = "isPersistenceRequired()Z", at = @At("HEAD"), cancellable = true)
    private void configurablezombieai$isPersistenceRequired(CallbackInfoReturnable<Boolean> cir) {
        if (ZombieAiRuntime.shouldPreventDespawn((MobEntity) (Object) this)) {
            cir.setReturnValue(true);
        }
    }
}
