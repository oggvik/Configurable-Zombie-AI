package oggvik.mods.configurablezombieai.runtime.targeting

import net.minecraft.entity.LivingEntity
import net.minecraft.entity.merchant.villager.AbstractVillagerEntity
import net.minecraft.entity.passive.IronGolemEntity
import net.minecraft.entity.passive.TurtleEntity
import net.minecraft.entity.passive.horse.HorseEntity
import net.minecraft.entity.player.PlayerEntity
import java.util.function.Predicate

enum class ZombieTargetType(
    val id: String,
    val displayName: String,
    val targetClass: Class<out LivingEntity>,
    val defaultEnabled: Boolean,
    val selector: Predicate<LivingEntity>? = null
) {
    PLAYERS("players", "players", PlayerEntity::class.java, true),
    VILLAGERS("villagers", "villagers", AbstractVillagerEntity::class.java, true),
    IRON_GOLEMS("iron_golems", "iron golems", IronGolemEntity::class.java, true),
    TURTLES("turtles", "baby turtles on land", TurtleEntity::class.java, true, TurtleEntity.BABY_ON_LAND_SELECTOR),
    HORSES("horses", "horses", HorseEntity::class.java, false);

    companion object {
        fun fromTargetClass(targetClass: Class<out LivingEntity>): ZombieTargetType? {
            return values().firstOrNull { targetType ->
                targetType.targetClass.isAssignableFrom(targetClass)
            }
        }

        fun fromTarget(target: LivingEntity): ZombieTargetType? {
            return values().firstOrNull { targetType ->
                targetType.targetClass.isInstance(target)
            }
        }
    }
}
