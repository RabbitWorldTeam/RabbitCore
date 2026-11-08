# RabbitCore 2.0：Minecraft 1.21.1 / NeoForge

此分支仅面向 Minecraft 1.21.1、NeoForge 21.1.265 或更新的 21.1.x，以及 Java 21。旧 Forge 版 JAR 不能和新版同时放在 `mods/`。未验证 1.20.1 世界直接升级；测试请使用新世界或存档副本。

开发构建固定使用以下 NeoForge 依赖；安装到游戏时也需在客户端与服务端安装：

- Architectury API 13.0.8
- [FTB Library 2101.1.37](https://www.curseforge.com/minecraft/mc-mods/ftb-library-forge/files/9008089)
- [FTB Teams 2101.1.11](https://www.curseforge.com/minecraft/mc-mods/ftb-teams-forge/files/8724782)
- [FTB Quests 2101.1.26](https://www.curseforge.com/minecraft/mc-mods/ftb-quests-forge/files/8216578)
- [FTB XMod Compat 21.1.7](https://www.curseforge.com/minecraft/mc-mods/ftb-xmod-compat/files/7429014)

运行 `.\gradlew.bat build` 生成 `build/libs/rabbitcore-2.0.0.jar`。开发客户端和服务端分别使用 `run/neoforge-client` 和 `run/neoforge-server`，不会复用旧开发实例。桥接配置见 [bridge.md](bridge.md)；WebSocket 协议仍为 v1。
