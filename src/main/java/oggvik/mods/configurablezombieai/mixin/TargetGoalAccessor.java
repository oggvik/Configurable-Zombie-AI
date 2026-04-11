package oggvik.mods.configurablezombieai.mixin;

import net.minecraft.entity.MobEntity;
import net.minecraft.entity.ai.goal.TargetGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(TargetGoal.class)
public interface TargetGoalAccessor {
    @Accessor("mob")
    MobEntity configurablezombieai$getMob();
}
