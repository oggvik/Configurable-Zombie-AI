package oggvik.mods.configurablezombieai.command

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import java.util.Locale
import net.minecraft.command.CommandSource
import net.minecraft.command.Commands
import net.minecraft.util.text.IFormattableTextComponent
import net.minecraft.util.text.StringTextComponent
import net.minecraft.util.text.TextFormatting
import oggvik.mods.configurablezombieai.config.ZombieAiSavedData
import oggvik.mods.configurablezombieai.runtime.ZombieAiRuntime

object ZombieAiCommands {
    fun register(dispatcher: CommandDispatcher<CommandSource>) {
        dispatcher.register(buildRoot("czai"))
        dispatcher.register(buildRoot("configurablezombieai"))
    }

    private fun buildRoot(name: String): LiteralArgumentBuilder<CommandSource> {
        val acquisition = Commands.literal("acquisition")
            .then(Commands.literal("variability")
                .then(Commands.argument("blocks", DoubleArgumentType.doubleArg(0.0, 4096.0))
                    .executes { context ->
                        val blocks = DoubleArgumentType.getDouble(context, "blocks")
                        updateSettings(context.source) { data ->
                            data.acquisitionDistanceVariability = blocks
                            "Initial target variability set to ${formatDecimal(blocks)} blocks."
                        }
                    }))
            .then(Commands.literal("bias")
                .then(buildAcquisitionClosestChanceArgument()))
            .then(Commands.literal("chance")
                .then(buildAcquisitionClosestChanceArgument()))

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
            .then(Commands.literal("radius")
                .then(Commands.argument("blocks", DoubleArgumentType.doubleArg(1.0, 4096.0))
                    .executes { context ->
                        val blocks = DoubleArgumentType.getDouble(context, "blocks")
                        updateSettings(context.source) { data ->
                            data.switchSearchRadius = blocks
                            "Target switching search radius set to ${formatDecimal(blocks)} blocks."
                        }
                    }))
            .then(Commands.literal("target_bias")
                .then(Commands.argument("value", DoubleArgumentType.doubleArg())
                    .executes { context ->
                        val value = DoubleArgumentType.getDouble(context, "value")
                        updateSettings(context.source) { data ->
                            data.switchTargetBias = value
                            "Target switching closest-bias set to ${formatDecimal(value)}."
                        }
                    }))
            .then(Commands.literal("current_bias")
                .then(Commands.argument("value", DoubleArgumentType.doubleArg())
                    .executes { context ->
                        val value = DoubleArgumentType.getDouble(context, "value")
                        updateSettings(context.source) { data ->
                            data.switchCurrentTargetBias = value
                            "Target switching current-target bias set to ${formatDecimal(value)}."
                        }
                    }))
            .then(Commands.literal("closer_bias")
                .then(Commands.argument("value", DoubleArgumentType.doubleArg())
                    .executes { context ->
                        val value = DoubleArgumentType.getDouble(context, "value")
                        updateSettings(context.source) { data ->
                            data.switchCloserThanCurrentBias = value
                            "Target switching closer-than-current bias set to ${formatDecimal(value)}."
                        }
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
                    context.source.sendSuccess(buildStatusMessage(data), false)
                    1
                })
            .then(Commands.literal("los")
                .then(Commands.argument("enabled", BoolArgumentType.bool())
                    .executes { context ->
                        val enabled = BoolArgumentType.getBool(context, "enabled")
                        updateSettings(context.source) { data ->
                            data.ignoreLineOfSight = enabled
                            "Zombie line-of-sight override ${formatToggle(enabled)}."
                        }
                    }))
            .then(Commands.literal("despawn")
                .then(Commands.argument("disabled", BoolArgumentType.bool())
                    .executes { context ->
                        val disabled = BoolArgumentType.getBool(context, "disabled")
                        updateSettings(context.source) { data ->
                            data.disableZombieDespawn = disabled
                            "Zombie despawn prevention ${formatToggle(disabled)}."
                        }
                    }))
            .then(Commands.literal("range")
                .then(Commands.argument("blocks", DoubleArgumentType.doubleArg(1.0, 4096.0))
                    .executes { context ->
                        val blocks = DoubleArgumentType.getDouble(context, "blocks")
                        updateSettings(context.source) { data ->
                            data.viewDistance = blocks
                            "Zombie view distance set to ${formatDecimal(blocks)} blocks."
                        }
                    }))
            .then(acquisition)
            .then(switching)
    }

    private fun updateSettings(
        source: CommandSource,
        updater: (ZombieAiSavedData) -> String
    ): Int {
        val data = ZombieAiSavedData.get(source.level) ?: return 0
        val response = updater(data)
        ZombieAiRuntime.onSettingsChanged(source.server)
        source.sendSuccess(StringTextComponent(response).withStyle(TextFormatting.GREEN), true)
        return 1
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

    private fun buildStatusMessage(data: ZombieAiSavedData): IFormattableTextComponent {
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
            .append(StringTextComponent("\n"))
            .append(label("Closest Chance"))
            .append(value(formatPercent(data.acquisitionClosestChancePercent)))
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
            .append(label("Closest Bias"))
            .append(value(formatDecimal(data.switchTargetBias)))
            .append(StringTextComponent("\n"))
            .append(label("Current Target Bias"))
            .append(value(formatDecimal(data.switchCurrentTargetBias)))
            .append(StringTextComponent("\n"))
            .append(label("Closer Than Current Bias"))
            .append(value(formatDecimal(data.switchCloserThanCurrentBias)))
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
}
