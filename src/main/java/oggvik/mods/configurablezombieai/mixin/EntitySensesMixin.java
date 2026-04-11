package oggvik.mods.configurablezombieai.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.ai.EntitySenses;
import oggvik.mods.configurablezombieai.runtime.ZombieAiRuntime;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntitySenses.class)
public abstract class EntitySensesMixin {
    @Shadow @Final private MobEntity mob;

    @Inject(method = "canSee(Lnet/minecraft/entity/Entity;)Z", at = @At("HEAD"), cancellable = true)
    private void configurablezombieai$canSee(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (ZombieAiRuntime.shouldBypassLineOfSight(this.mob, entity)) {
            cir.setReturnValue(true);
        }
    }
}
