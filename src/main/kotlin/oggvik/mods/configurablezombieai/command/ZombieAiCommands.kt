package oggvik.mods.configurablezombieai.command

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import java.util.Locale
import net.minecraft.command.CommandSource
import net.minecraft.command.Commands
import net.minecraft.entity.monster.ZombieEntity
import net.minecraft.entity.player.ServerPlayerEntity
import net.minecraft.util.Util
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.text.IFormattableTextComponent
import net.minecraft.util.text.StringTextComponent
import net.minecraft.util.text.TextFormatting
import oggvik.mods.configurablezombieai.config.ZombieAiSavedData
import oggvik.mods.configurablezombieai.runtime.abnormals.targetacquisition.AbnormalTargetAcquisitionRegistry
import oggvik.mods.configurablezombieai.runtime.ZombieAiRuntime
import oggvik.mods.configurablezombieai.runtime.targeting.ZombieTargetType

/**
 * OP-only runtime control surface for the mod.
 *
 * Every command mutates the live server-side settings object, then asks the
 * runtime layer to refresh already-loaded zombies so the change takes effect
 * immediately.
 */
object ZombieAiCommands {
    fun register(dispatcher: CommandDispatcher<CommandSource>) {
        dispatcher.register(buildRoot("czai"))
        dispatcher.register(buildRoot("configurablezombieai"))
    }

    private fun buildRoot(name: String): LiteralArgumentBuilder<CommandSource> {
        val abnormals = Commands.literal("abnormals")
            .then(Commands.literal("chance")
                .then(Commands.argument("percent", DoubleArgumentType.doubleArg(0.0, 100.0))
                    .executes { context ->
                        val percent = DoubleArgumentType.getDouble(context, "percent")
                        updateSettings(context.source) { data ->
                            data.abnormalAcquisitionChancePercent = percent
                            "Abnormal acquisition chance set to ${formatPercent(percent)}."
                        }
                    }))
            .then(Commands.literal("behavior")
                .then(Commands.argument("behavior_id", StringArgumentType.word())
                    .then(Commands.literal("weight")
                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0))
                            .executes { context ->
                                setAbnormalAcquisitionBehaviorWeight(
                                    context.source,
                                    StringArgumentType.getString(context, "behavior_id"),
                                    DoubleArgumentType.getDouble(context, "value")
                                )
                            }))))
            .then(Commands.literal("reset_behavior_weights")
                .executes { context ->
                    resetAbnormalAcquisitionBehaviorWeights(context.source)
                })
            .then(Commands.literal("list")
                .executes { context ->
                    sendAbnormalBehaviorList(context.source)
                    1
                })

        // Acquisition settings control how zombies choose an initial target when
        // a vanilla target goal first decides it wants to acquire something.
        val acquisition = Commands.literal("acquisition")
            .then(Commands.literal("distance_variability_from_closest_target")
                .then(Commands.argument("blocks", DoubleArgumentType.doubleArg(0.0, 4096.0))
                    .executes { context ->
                        val blocks = DoubleArgumentType.getDouble(context, "blocks")
                        updateSettings(context.source) { data ->
                            data.acquisitionDistanceVariability = blocks
                            "Initial target variability set to ${formatDecimal(blocks)} blocks."
                        }
                    }))
            .then(Commands.literal("chance_to_auto-select_closest_target")
                .then(buildAcquisitionClosestChanceArgument()))
            .then(abnormals)

        // Switching settings are separate from acquisition settings because the
        // zombie's "stay or swap" decision uses a different algorithm.
        val switching = Commands.literal("switching")
            .then(Commands.literal("enabled")
                .then(Commands.argument("value", BoolArgumentType.bool())
                    .executes { context ->
                        val value = BoolArgumentType.getBool(context, "value")
                        updateSettings(context.source) { data ->
                            data.switchEnabled = value
                            "Target switching ${formatToggle(value)}."
                        }
                    }))
            .then(Commands.literal("interval")
                .then(Commands.argument("ticks", IntegerArgumentType.integer(1, 12000))
                    .executes { context ->
                        val ticks = IntegerArgumentType.getInteger(context, "ticks")
                        updateSettings(context.source) { data ->
                            data.switchIntervalTicks = ticks
                            "Target switching interval set to $ticks ticks."
                        }
                    }))
            .then(Commands.literal("search_radius")
                .then(Commands.argument("blocks", DoubleArgumentType.doubleArg(1.0, 4096.0))
                    .executes { context ->
                        val blocks = DoubleArgumentType.getDouble(context, "blocks")
                        updateSettings(context.source) { data ->
                            data.switchSearchRadius = blocks
                            "Target switching search radius set to ${formatDecimal(blocks)} blocks."
                        }
                    }))
            .then(Commands.literal("proximity_bias")
                .then(buildSwitchAbsoluteProximityBiasArgument()))
            .then(Commands.literal("current_target_bias")
                .then(buildSwitchCurrentTargetStickinessArgument()))
            .then(Commands.literal("closer_than_current_target_bias")
                .then(buildSwitchRelativeImprovementBiasArgument()))

        val targetsRadius = Commands.argument("blocks", DoubleArgumentType.doubleArg(0.0, 4096.0))
        for (targetType in ZombieTargetType.values()) {
            targetsRadius.then(
                Commands.literal(targetType.id)
                    .then(Commands.argument("enabled", BoolArgumentType.bool())
                        .executes { context ->
                            setTargetTypeForZombiesInRadius(
                                context.source,
                                DoubleArgumentType.getDouble(context, "blocks"),
                                targetType,
                                BoolArgumentType.getBool(context, "enabled")
                            )
                        })
            )
        }
        targetsRadius
            .then(Commands.literal("all")
                .then(Commands.argument("enabled", BoolArgumentType.bool())
                    .executes { context ->
                        setAllTargetTypesForZombiesInRadius(
                            context.source,
                            DoubleArgumentType.getDouble(context, "blocks"),
                            BoolArgumentType.getBool(context, "enabled")
                        )
                    }))
            .then(Commands.literal("reset")
                .executes { context ->
                    resetTargetTypesForZombiesInRadius(
                        context.source,
                        DoubleArgumentType.getDouble(context, "blocks")
                    )
                })

        val targets = Commands.literal("targets")
            .then(Commands.literal("radius")
                .then(targetsRadius))

        // Utility commands are kept under the same root so server operators only
        // need one namespace for both AI tuning and admin actions.
        val kill = Commands.literal("kill")
            .then(Commands.literal("all")
                .executes { context ->
                    killAllZombies(context.source)
                })
            .then(Commands.literal("radius")
                .then(Commands.argument("blocks", DoubleArgumentType.doubleArg(0.0, 4096.0))
                    .executes { context ->
                        val blocks = DoubleArgumentType.getDouble(context, "blocks")
                        killZombiesInRadius(context.source, blocks)
                    }))

        return Commands.literal(name)
            .requires { source -> source.hasPermission(2) }
            .then(Commands.literal("enable")
                .executes { context ->
                    updateSettings(context.source) { data ->
                        data.modEnabled = true
                        "Configurable Zombie AI enabled."
                    }
                })
            .then(Commands.literal("disable")
                .executes { context ->
                    updateSettings(context.source) { data ->
                        data.modEnabled = false
                        "Configurable Zombie AI disabled."
                    }
                })
            .then(Commands.literal("status")
                .executes { context ->
                    val data = ZombieAiSavedData.get(context.source.level) ?: return@executes 0
                    sendStatusMessage(context.source, buildStatusMessage(data))
                    1
                })
            .then(Commands.literal("ignore_LOS")
                .then(Commands.argument("enabled", BoolArgumentType.bool())
                    .executes { context ->
                        val enabled = BoolArgumentType.getBool(context, "enabled")
                        updateSettings(context.source) { data ->
                            data.ignoreLineOfSight = enabled
                            "Zombie line-of-sight override ${formatToggle(enabled)}."
                        }
                    }))
            .then(Commands.literal("despawn_prevention")
                .then(Commands.argument("disabled", BoolArgumentType.bool())
                    .executes { context ->
                        val disabled = BoolArgumentType.getBool(context, "disabled")
                        updateSettings(context.source) { data ->
                            data.disableZombieDespawn = disabled
                            "Zombie despawn prevention ${formatToggle(disabled)}."
                        }
                    }))
            .then(Commands.literal("visibility_range")
                .then(Commands.argument("blocks", DoubleArgumentType.doubleArg(1.0, 4096.0))
                    .executes { context ->
                        val blocks = DoubleArgumentType.getDouble(context, "blocks")
                        updateSettings(context.source) { data ->
                            data.viewDistance = blocks
                            "Zombie view distance set to ${formatDecimal(blocks)} blocks."
                        }
                    }))
            .then(kill)
            .then(acquisition)
            .then(targets)
            .then(switching)
    }

    private fun updateSettings(
        source: CommandSource,
        updater: (ZombieAiSavedData) -> String
    ): Int {
        val data = ZombieAiSavedData.get(source.level) ?: return 0
        val response = updater(data)
        // Follow range is cached in entity attributes, so loaded zombies need an
        // explicit refresh after settings change. Other features re-read settings
        // live from mixin hooks and runtime helpers.
        ZombieAiRuntime.onSettingsChanged(source.server)
        source.sendSuccess(StringTextComponent(response).withStyle(TextFormatting.GREEN), true)
        return 1
    }

    private fun sendStatusMessage(source: CommandSource, message: IFormattableTextComponent) {
        val player = source.entity as? ServerPlayerEntity
        if (player != null) {
            // Direct player chat bypasses the normal command-feedback gamerule,
            // so status stays visible even when servers suppress command spam.
            player.sendMessage(message, Util.NIL_UUID)
            return
        }

        source.sendSuccess(message, false)
    }

    private fun buildAcquisitionClosestChanceArgument() =
        Commands.argument("percent", DoubleArgumentType.doubleArg(0.0, 100.0))
            .executes { context ->
                val percent = DoubleArgumentType.getDouble(context, "percent")
                updateSettings(context.source) { data ->
                    data.acquisitionClosestChancePercent = percent
                    "Initial target closest-chance set to ${formatPercent(percent)}."
                }
            }

    private fun buildSwitchAbsoluteProximityBiasArgument() =
        Commands.argument("value", DoubleArgumentType.doubleArg())
            .executes { context ->
                val value = DoubleArgumentType.getDouble(context, "value")
                updateSettings(context.source) { data ->
                    data.switchTargetBias = value
                    "Target switching absolute-proximity bias set to ${formatDecimal(value)}."
                }
            }

    private fun buildSwitchCurrentTargetStickinessArgument() =
        Commands.argument("value", DoubleArgumentType.doubleArg())
            .executes { context ->
                val value = DoubleArgumentType.getDouble(context, "value")
                updateSettings(context.source) { data ->
                    data.switchCurrentTargetBias = value
                    "Target switching current-target stickiness set to ${formatDecimal(value)}."
                }
            }

    private fun buildSwitchRelativeImprovementBiasArgument() =
        Commands.argument("value", DoubleArgumentType.doubleArg())
            .executes { context ->
                val value = DoubleArgumentType.getDouble(context, "value")
                updateSettings(context.source) { data ->
                    data.switchCloserThanCurrentBias = value
                    "Target switching relative-improvement bias set to ${formatDecimal(value)}."
                }
            }

    private fun setAbnormalAcquisitionBehaviorWeight(
        source: CommandSource,
        behaviorId: String,
        weight: Double
    ): Int {
        val normalizedId = ZombieAiSavedData.normalizeAbnormalBehaviorId(behaviorId)
        if (normalizedId == null) {
            source.sendFailure(
                StringTextComponent("Invalid abnormal acquisition behavior id: $behaviorId")
                    .withStyle(TextFormatting.RED)
            )
            return 0
        }

        val registeredIds = AbnormalTargetAcquisitionRegistry.registeredBehaviorIds()
        return updateSettings(source) { data ->
            data.setAbnormalAcquisitionBehaviorWeight(normalizedId, weight)
            val registrationNote = if (normalizedId in registeredIds) {
                ""
            } else {
                " No behavior with this id is registered yet."
            }
            "Abnormal acquisition behavior '$normalizedId' weight set to ${formatDecimal(weight)}.$registrationNote"
        }
    }

    private fun resetAbnormalAcquisitionBehaviorWeights(source: CommandSource): Int {
        val registeredIds = AbnormalTargetAcquisitionRegistry.registeredBehaviorIds()
        if (registeredIds.isEmpty()) {
            source.sendFailure(
                StringTextComponent("No abnormal acquisition behaviors are registered yet.")
                    .withStyle(TextFormatting.RED)
            )
            return 0
        }

        return updateSettings(source) { data ->
            for (behaviorId in registeredIds) {
                data.setAbnormalAcquisitionBehaviorWeight(
                    behaviorId,
                    ZombieAiSavedData.DEFAULT_ABNORMAL_ACQUISITION_BEHAVIOR_WEIGHT
                )
            }
            "Reset ${registeredIds.size} abnormal acquisition behavior weight${pluralize(registeredIds.size)} to ${formatDecimal(ZombieAiSavedData.DEFAULT_ABNORMAL_ACQUISITION_BEHAVIOR_WEIGHT)}."
        }
    }

    private fun sendAbnormalBehaviorList(source: CommandSource) {
        val registeredIds = AbnormalTargetAcquisitionRegistry.registeredBehaviorIds()
        val message = if (registeredIds.isEmpty()) {
            "No abnormal acquisition behaviors are registered yet."
        } else {
            "Registered abnormal acquisition behaviors: ${registeredIds.joinToString(", ")}"
        }

        source.sendSuccess(StringTextComponent(message).withStyle(TextFormatting.YELLOW), false)
    }

    private fun setTargetTypeForZombiesInRadius(
        source: CommandSource,
        radius: Double,
        targetType: ZombieTargetType,
        enabled: Boolean
    ): Int {
        val zombies = findZombiesInRadius(source, radius)
        for (zombie in zombies) {
            ZombieAiRuntime.setTargetTypeEnabled(zombie, targetType, enabled)
        }

        source.sendSuccess(
            StringTextComponent(
                "Set ${targetType.displayName} targeting ${formatToggle(enabled)} for ${zombies.size} zombie${pluralize(zombies.size)} within ${formatDecimal(radius)} blocks of ${source.textName}."
            ).withStyle(TextFormatting.GREEN),
            true
        )
        return zombies.size
    }

    private fun setAllTargetTypesForZombiesInRadius(
        source: CommandSource,
        radius: Double,
        enabled: Boolean
    ): Int {
        val zombies = findZombiesInRadius(source, radius)
        for (zombie in zombies) {
            ZombieAiRuntime.setAllTargetTypesEnabled(zombie, enabled)
        }

        source.sendSuccess(
            StringTextComponent(
                "Set all configurable target types ${formatToggle(enabled)} for ${zombies.size} zombie${pluralize(zombies.size)} within ${formatDecimal(radius)} blocks of ${source.textName}."
            ).withStyle(TextFormatting.GREEN),
            true
        )
        return zombies.size
    }

    private fun resetTargetTypesForZombiesInRadius(source: CommandSource, radius: Double): Int {
        val zombies = findZombiesInRadius(source, radius)
        for (zombie in zombies) {
            ZombieAiRuntime.resetTargetTypes(zombie)
        }

        source.sendSuccess(
            StringTextComponent(
                "Reset configurable target types to defaults for ${zombies.size} zombie${pluralize(zombies.size)} within ${formatDecimal(radius)} blocks of ${source.textName}."
            ).withStyle(TextFormatting.GREEN),
            true
        )
        return zombies.size
    }

    private fun findZombiesInRadius(source: CommandSource, radius: Double): List<ZombieEntity> {
        val origin = source.position
        val searchBox = AxisAlignedBB(
            origin.x - radius,
            origin.y - radius,
            origin.z - radius,
            origin.x + radius,
            origin.y + radius,
            origin.z + radius
        )
        val radiusSqr = radius * radius
        return source.level.getEntitiesOfClass(ZombieEntity::class.java, searchBox) { zombie ->
            zombie.distanceToSqr(origin) <= radiusSqr
        }
    }

    private fun killAllZombies(source: CommandSource): Int {
        var killed = 0
        for (world in source.server.allLevels) {
            // "all" intentionally means every loaded dimension, not just the
            // executor's current world.
            val zombies = world.allEntities.filterIsInstance<ZombieEntity>()
            for (zombie in zombies) {
                zombie.kill()
            }
            killed += zombies.size
        }

        source.sendSuccess(
            StringTextComponent("Killed $killed zombie${pluralize(killed)} across all loaded worlds.")
                .withStyle(TextFormatting.RED),
            true
        )
        return killed
    }

    private fun killZombiesInRadius(source: CommandSource, radius: Double): Int {
        val player = source.getPlayerOrException()
        val radiusSqr = radius * radius
        val zombies = player.level.getEntitiesOfClass(
            ZombieEntity::class.java,
            player.boundingBox.inflate(radius, radius, radius)
        ) { zombie ->
            // The inflated box is only a coarse search shape; this keeps the
            // final selection circular/spherical around the executing player.
            zombie.distanceToSqr(player) <= radiusSqr
        }

        for (zombie in zombies) {
            zombie.kill()
        }

        source.sendSuccess(
            StringTextComponent(
                "Killed ${zombies.size} zombie${pluralize(zombies.size)} within ${formatDecimal(radius)} blocks of ${player.name.string}."
            ).withStyle(TextFormatting.RED),
            true
        )
        return zombies.size
    }

    private fun buildStatusMessage(data: ZombieAiSavedData): IFormattableTextComponent {
        // Status output is assembled manually so the command can present a
        // compact operator dashboard instead of raw key/value spam.
        return StringTextComponent("")
            .append(StringTextComponent("========== ").withStyle(TextFormatting.DARK_GRAY))
            .append(StringTextComponent("Configurable Zombie AI").withStyle(TextFormatting.GOLD, TextFormatting.BOLD))
            .append(StringTextComponent(" ==========\n").withStyle(TextFormatting.DARK_GRAY))
            .append(label("Mod Status"))
            .append(flag(data.modEnabled, "ACTIVE", "DISABLED"))
            .append(StringTextComponent("\n"))
            .append(label("Line of Sight"))
            .append(flag(data.ignoreLineOfSight, "IGNORE WALLS", "VANILLA"))
            .append(StringTextComponent("\n"))
            .append(label("Despawn"))
            .append(flag(data.disableZombieDespawn, "DISABLED", "VANILLA"))
            .append(StringTextComponent("\n"))
            .append(label("View Distance"))
            .append(value("${formatDecimal(data.viewDistance)} blocks"))
            .append(StringTextComponent("\n"))
            .append(StringTextComponent("Acquisition\n").withStyle(TextFormatting.YELLOW, TextFormatting.BOLD))
            .append(label("Distance Variability"))
            .append(value("${formatDecimal(data.acquisitionDistanceVariability)} blocks"))
            .append(" (from closest possible target)")
            .append(StringTextComponent("\n"))
            .append(label("Closest Chance"))
            .append(value(formatPercent(data.acquisitionClosestChancePercent)))
            .append(" (i.e. the chance to GUARANTEE the closest possible target will be selected)")
            .append(StringTextComponent("\n"))
            .append(StringTextComponent("Abnormals\n").withStyle(TextFormatting.YELLOW, TextFormatting.BOLD))
            .append(label("Abnormal Chance"))
            .append(value(formatPercent(data.abnormalAcquisitionChancePercent)))
            .append(" (chance to roll an abnormal acquisition behavior)")
            .append(StringTextComponent("\n"))
            .append(label("Registered Behaviors"))
            .append(value(formatBehaviorIds(AbnormalTargetAcquisitionRegistry.registeredBehaviorIds())))
            .append(StringTextComponent("\n"))
            .append(label("Behavior Weights"))
            .append(value(formatBehaviorWeights(data.abnormalAcquisitionBehaviorWeights())))
            .append(StringTextComponent("\n"))
            .append(StringTextComponent("Switching\n").withStyle(TextFormatting.YELLOW, TextFormatting.BOLD))
            .append(label("Enabled"))
            .append(flag(data.switchEnabled, "YES", "NO"))
            .append(StringTextComponent("\n"))
            .append(label("Interval"))
            .append(value("${data.switchIntervalTicks} ticks"))
            .append(StringTextComponent("\n"))
            .append(label("Search Radius"))
            .append(value("${formatDecimal(data.switchSearchRadius)} blocks"))
            .append(StringTextComponent("\n"))
            .append(label("Absolute Proximity Bias"))
            .append(value(formatDecimal(data.switchTargetBias)))
            .append(" (How strongly should the zombie prefer nearer candidates in the whole switching pool?)")
            .append(StringTextComponent("\n"))
            .append(label("Current Target Stickiness"))
            .append(value(formatDecimal(data.switchCurrentTargetBias)))
            .append(" (How strongly should the zombie stay committed to the current target?)")
            .append(StringTextComponent("\n"))
            .append(label("Relative Improvement Bias"))
            .append(value(formatDecimal(data.switchCloserThanCurrentBias)))
            .append(" (How strongly should the zombie reward candidates that are closer THAN the current target?)")
    }

    private fun label(text: String): IFormattableTextComponent {
        return StringTextComponent("$text: ").withStyle(TextFormatting.GRAY)
    }

    private fun value(text: String): IFormattableTextComponent {
        return StringTextComponent(text).withStyle(TextFormatting.AQUA)
    }

    private fun flag(enabled: Boolean, enabledText: String, disabledText: String): IFormattableTextComponent {
        return StringTextComponent(if (enabled) enabledText else disabledText)
            .withStyle(if (enabled) TextFormatting.GREEN else TextFormatting.RED, TextFormatting.BOLD)
    }

    private fun formatToggle(value: Boolean): String {
        return if (value) "enabled" else "disabled"
    }

    private fun formatDecimal(value: Double): String {
        return String.format(Locale.ROOT, "%.2f", value)
    }

    private fun formatPercent(value: Double): String {
        return "${formatDecimal(value)}%"
    }

    private fun formatBehaviorIds(behaviorIds: List<String>): String {
        if (behaviorIds.isEmpty()) {
            return "none"
        }

        return behaviorIds.joinToString(", ")
    }

    private fun formatBehaviorWeights(configuredWeights: Map<String, Double>): String {
        val registeredIds = AbnormalTargetAcquisitionRegistry.registeredBehaviorIds()
        if (registeredIds.isEmpty() && configuredWeights.isEmpty()) {
            return "none"
        }

        val behaviorWeights = linkedMapOf<String, Double>()
        for (behaviorId in registeredIds) {
            behaviorWeights[behaviorId] = configuredWeights[behaviorId]
                ?: ZombieAiSavedData.DEFAULT_ABNORMAL_ACQUISITION_BEHAVIOR_WEIGHT
        }
        for ((behaviorId, weight) in configuredWeights.entries.sortedBy { it.key }) {
            behaviorWeights.putIfAbsent(behaviorId, weight)
        }

        return behaviorWeights.entries
            .joinToString(", ") { (behaviorId, weight) -> "$behaviorId=${formatDecimal(weight)}" }
    }

    private fun pluralize(count: Int): String {
        return if (count == 1) "" else "s"
    }
}
