# Repository Guidelines

## Project Structure & Module Organization
RabbitCore is a Java 21 mod for Minecraft 1.21.1 and NeoForge 21.1.265, with Architectury and FTB Quests integrations. Sources live under `src/main/java/com/tr4nce/rabbitcore/`. Keep block behavior in `block/`, block entities in `block/entity/`, registrations in `registry/`, commands in `command/`, event handlers in `event/`, quest integration in `quest/`, and persistent world data in `world/storage/`. Client rendering belongs in `client/renderer/`.

NeoForge mod metadata lives in `src/main/resources/`. Models, blockstates, and English/Chinese translations belong in `assets/rabbitcore/` beneath that directory.

## Build, Test, and Development Commands
Use the checked-in Gradle wrapper with Java 21. Windows PowerShell examples:

- `.\gradlew.bat build`: compile, run configured checks, and package the mod into `build/libs/`.
- `.\gradlew.bat runClient`: launch the development client.
- `.\gradlew.bat runServer`: launch the development server without a GUI.
- `.\gradlew.bat bridgeTest`: run bridge protocol, configuration, and socket regression checks.

On Unix-like systems, use `./gradlew` instead.

## Coding Style & Naming Conventions
Follow the existing Java style: four-space indentation, opening braces on the declaration line, `PascalCase` classes, `camelCase` methods and fields, and `UPPER_SNAKE_CASE` constants. Use lowercase snake_case registry IDs and asset names, such as `rabbit_chest`. Reference `Rabbitcore.MODID` for the namespace and use the existing deferred registration pattern. Preserve UTF-8 encoding. No formatter or lint plugin is currently configured.

## Testing Guidelines
The bridge regression suite runs without an additional test framework. No coverage threshold is defined. Build before submitting, then exercise affected behavior in the development client and server. For chest, statistics, or quest changes, verify persistence across a world restart and the relevant FTB integration. Document reproduction steps and results; do not treat an empty test run as behavioral coverage.

## Commit & Pull Request Guidelines
Keep changes focused. Pull requests should explain behavior changes, link relevant issues, and list build and manual verification results. Include screenshots for visible changes and call out dependency, configuration, or saved-data compatibility changes.

## Commit Message Format

Use `<type>: :<gitmoji>: <中文摘要>` for manually authored commit subjects. Use a lowercase type, the literal colon-delimited Gitmoji shortcode, and one space between parts. Keep the Chinese summary concise and specific to the change.

- `feat: :sparkles: 新特性`
- `fix: :bug: 修复登录提示`
- `docs: :memo: 更新安装说明`
- `refactor: :recycle: 整理命令注册`
- `style: :art: 调整物品配色`
- `test: :white_check_mark: 补充权限回归检查`
- `build: :package: 统一产物命名`
- `chore: :wrench: 更新开发配置`
