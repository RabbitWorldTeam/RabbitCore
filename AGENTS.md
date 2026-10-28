# Repository Guidelines

## Project Structure & Module Organization
RabbitCore is a Java 17 mod for Minecraft 1.20.1 and Forge 47.4.0, with Architectury and FTB Quests integrations. Sources live under `src/main/java/com/tr4nce/rabbitcore/`. Keep block behavior in `block/`, block entities in `block/entity/`, registrations in `registry/`, commands in `command/`, event handlers in `event/`, quest integration in `quest/`, and persistent world data in `world/storage/`. Client rendering belongs in `client/renderer/`.

Mod metadata and mixin configuration live in `src/main/resources/`. Models, blockstates, and English/Chinese translations belong in `assets/rabbitcore/` beneath that directory. Data generation targets `src/generated/resources/`.

## Build, Test, and Development Commands
Use the checked-in Gradle wrapper with Java 17. Windows PowerShell examples:

- `.\gradlew.bat build`: compile, run configured checks, and package the mod into `build/libs/`; the JAR task also runs reobfuscation.
- `.\gradlew.bat runClient`: launch the development client.
- `.\gradlew.bat runServer`: launch the development server without a GUI.
- `.\gradlew.bat runData`: generate resources into `src/generated/resources/`.
- `.\gradlew.bat runGameTestServer`: run registered Forge GameTests; the configuration warns that it can fail when none exist.

On Unix-like systems, use `./gradlew` instead.

## Coding Style & Naming Conventions
Follow the existing Java style: four-space indentation, opening braces on the declaration line, `PascalCase` classes, `camelCase` methods and fields, and `UPPER_SNAKE_CASE` constants. Use lowercase snake_case registry IDs and asset names, such as `rabbit_chest`. Reference `Rabbitcore.MODID` for the namespace and use the existing deferred registration pattern. Preserve UTF-8 encoding. No formatter or lint plugin is currently configured.

## Testing Guidelines
No automated test sources, dedicated test framework dependency, or coverage threshold are currently defined. Build before submitting, then exercise affected behavior in the development client and server. For chest, statistics, or quest changes, verify persistence across a world restart and the relevant FTB integration. Document reproduction steps and results; do not treat an empty test run as behavioral coverage.

## Commit & Pull Request Guidelines
The available history contains one commit, `feat: :rocket: Init Commit 1.0.0`; use concise, type-prefixed subjects such as `fix: preserve chest registry data`. Keep changes focused. Pull requests should explain behavior changes, link relevant issues, and list build and manual verification results. Include screenshots for visible changes and call out dependency, configuration, or saved-data compatibility changes.
