# Changelog

All notable changes to this project will be documented in this file.

The format is inspired by Keep a Changelog. The `0.1.0` entry below is a baseline reconstructed from the repository history up to April 12, 2026.

## [0.1.1]

### Added

- Added `CHANGELOG.md` to track future project history in one place.
- Added logo.

### Changed

- Rewrote `README.md` with project overview, feature summary, command reference, defaults, build instructions, and licensing details.

## [0.1.0] - 2026-04-12

### Added

- Added the initial Forge `1.16.5` implementation of Configurable Zombie AI in Kotlin.
- Added persistent world-level settings so command changes survive server restarts.
- Added operator commands under `czai` and `configurablezombieai` for enabling, disabling, and inspecting the mod at runtime.
- Added controls for line-of-sight bypass, zombie despawn prevention, and configurable visibility range.
- Added configurable target acquisition logic so zombies can choose from a pool near the closest valid target instead of always taking the single nearest one.
- Added weighted target switching so zombies can periodically reconsider their current target.
- Added admin commands to kill all loaded zombies or zombies within a radius.
- Added support for configurable targeting behavior across vanilla zombie target families, including players, villagers, iron golems, and baby turtles on land.

### Changed

- Changed acquisition tuning from a simple nearest/random approach to a percentage-based "auto-select closest target" model.
- Improved command labels and status output to make the runtime configuration easier to understand in game.
- Updated build configuration for more reliable Forge `1.16.5` development and packaging, including JDK `17` dev tooling with Java `8` bytecode output.
- Updated mod metadata and credits.

### Fixed

- Fixed packaging and build configuration details so generated mixin metadata and distributable jars align more reliably with Forge runtime expectations.

### Documentation

- Updated the project README during early project setup.
