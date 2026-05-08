# Configurable Zombie AI

Configurable Zombie AI is a Minecraft Forge mod for `1.16.5` that makes vanilla zombies easier to tune for harder survival servers and modpacks.

Instead of hardcoding one zombie behavior profile, the mod exposes runtime commands for server operators so they can change how zombies detect, acquire, keep, and switch targets without restarting the world. Settings are stored with the world and apply to loaded zombies immediately.

## What It Does

- Changes zombie follow range.
- Optionally lets zombies ignore line of sight checks.
- Optionally prevents zombies from despawning naturally.
- Replaces vanilla "pick the nearest valid target" behavior with configurable target acquisition.
- Adds a framework for weighted abnormal target-acquisition behaviors.
- Optionally lets zombies re-evaluate and switch targets over time using weighted rules.
- Adds easy-to-use admin commands to kill zombies globally or within a radius.

The mod keeps vanilla target families intact. A zombie can still only target the kinds of entities vanilla would normally allow, but the selection logic inside those target pools becomes configurable.

## Compatibility

- Minecraft: `1.16.5`
- Forge: `36.x` or newer in the `1.16.5` line
- Language/runtime: Kotlin via KotlinForForge
- Implementation details: Sponge Mixin injections plus Kotlin runtime logic
- Multiplayer clients do not need the mod installed to join a server running it.

## How Configuration Works

- All settings are changed in-game through commands.
- Command roots: `czai` and `configurablezombieai`
- Permission level: operator level `2`
- Settings are saved in world data, so they persist across restarts.
- When settings change, loaded zombies are refreshed immediately.

## Command Reference

Examples below use the short root command, but every command also works with `configurablezombieai` instead of `czai`.

### Core Commands

| Command | Description |
| --- | --- |
| `czai enable` | Enables all mod behavior. |
| `czai disable` | Disables all mod behavior and restores vanilla follow range for loaded zombies. |
| `czai status` | Prints the current live configuration. |
| `czai ignore_LOS <true\|false>` | Makes zombies ignore line-of-sight checks when choosing valid targets. |
| `czai despawn_prevention <true\|false>` | Prevents natural zombie despawning when enabled. |
| `czai visibility_range <blocks>` | Sets the zombie follow range / detection distance. |

### Acquisition Tuning

These settings affect how a zombie picks an initial target after vanilla has already decided that a target goal may run.

| Command | Description |
| --- | --- |
| `czai acquisition distance_variability_from_closest_target <blocks>` | Expands the initial candidate pool beyond the closest target by the given distance. |
| `czai acquisition chance_to_auto-select_closest_target <percent>` | Chance to guarantee the closest available target is chosen. At `100`, the closest target always wins. At `0`, any candidate in the acquisition pool may be chosen. |
| `czai acquisition abnormals chance <percent>` | Chance that an initial target acquisition roll tries to use an abnormal behavior before falling back to normal acquisition. |
| `czai acquisition abnormals behavior <behavior_id> weight <value>` | Sets the relative weight for a specific abnormal behavior id. A weight of `1.0` is baseline, `2.0` is twice as likely as baseline, `0.5` is half as likely, and `0.0` disables that behavior. |
| `czai acquisition abnormals reset_behavior_weights` | Resets all registered abnormal behavior weights to `1.0`. |
| `czai acquisition abnormals list` | Lists registered abnormal acquisition behaviors. |

Registered abnormal acquisition behaviors:

| Behavior ID | Description |
| --- | --- |
| `furthest` | Selects the farthest valid target inside the zombie's configured acquisition range. |

### Switching Tuning

Switching is off by default. When enabled, zombies periodically re-roll their target inside the same target family as the current one.

| Command | Description |
| --- | --- |
| `czai switching enabled <true\|false>` | Turns weighted target switching on or off. |
| `czai switching interval <ticks>` | How often each zombie evaluates target switching. |
| `czai switching search_radius <blocks>` | Radius used to collect alternative targets for switching. |
| `czai switching proximity_bias <value>` | Positive values favor nearer candidates; negative values favor farther ones. |
| `czai switching current_target_bias <value>` | Positive values make the zombie more likely to stay on its current target. |
| `czai switching closer_than_current_target_bias <value>` | Positive values reward candidates that are closer than the current target. |

### Utility Commands

| Command | Description |
| --- | --- |
| `czai kill all` | Kills all loaded zombies across every loaded dimension. |
| `czai kill radius <blocks>` | Kills zombies within a radius of the executing player. |

## Default Values

| Setting | Default |
| --- | --- |
| Mod enabled | `true` |
| Ignore line of sight | `false` |
| Prevent despawn | `false` |
| Visibility range | `35.0` blocks |
| Acquisition distance variability | `0.0` blocks |
| Chance to auto-select closest target | `100.0%` |
| Abnormal acquisition chance | `0.0%` |
| Abnormal behavior weights | `1.0` per registered behavior |
| Switching enabled | `false` |
| Switching interval | `40` ticks |
| Switching search radius | `16.0` blocks |
| Switching proximity bias | `0.0` |
| Switching current target bias | `1.0` |
| Switching closer-than-current-target bias | `1.5` |

## Targeting Notes

- Acquisition still respects vanilla target categories such as players, villagers, iron golems, and baby turtles on land.
- Abnormal acquisition behavior is attempted only after vanilla-compatible candidates have been collected. If no abnormal behavior is registered, no behavior has a positive configured weight, or the selected behavior cannot produce a valid target, normal acquisition runs instead.
- Switching does not jump between target families. If a zombie is currently chasing a villager, it will only compare that villager against other villager candidates during a switching pass.
- Line-of-sight bypass still respects attack validity checks and the configured visibility range.

## Building From Source

### Requirements

- JDK `17` for Gradle dev runs and builds
- A working Gradle environment through the included wrapper

### Common Tasks

```bash
./gradlew build
./gradlew runClient
./gradlew runServer
```

Build output is written to `build/libs/`.

- `configurablezombieai-<version>.jar`: reobfuscated release jar for normal Forge launchers
- `configurablezombieai-<version>-dev.jar`: intermediate jar kept as the `reobfJar` input

## Project Layout

- `src/main/kotlin`: mod entrypoint, saved settings, commands, and runtime AI logic
- `src/main/java`: mixins and mixin accessors
- `src/main/resources`: mod metadata, mixin config, and pack metadata

## License

This project is licensed under `GPL-3.0-or-later`. See `LICENSE.md`.
