# RabbitCore 2.0：Minecraft 1.21.1 / NeoForge

此分支仅面向 Minecraft 1.21.1、NeoForge 21.1.265 或更新的 21.1.x，以及 Java 21。旧 Forge 版 JAR 不能和新版同时放在 `mods/`。未验证 1.20.1 世界直接升级；测试请使用新世界或存档副本。

开发构建固定使用以下 NeoForge 依赖；安装到游戏时也需在客户端与服务端安装：

- Architectury API 13.0.8
- [FTB Library 2101.1.37](https://www.curseforge.com/minecraft/mc-mods/ftb-library-forge/files/9008089)
- [FTB Teams 2101.1.11](https://www.curseforge.com/minecraft/mc-mods/ftb-teams-forge/files/8724782)
- [FTB Quests 2101.1.26](https://www.curseforge.com/minecraft/mc-mods/ftb-quests-forge/files/8216578)
- [FTB XMod Compat 21.1.7](https://www.curseforge.com/minecraft/mc-mods/ftb-xmod-compat/files/7429014)

运行 `.\gradlew.bat build` 生成 `build/libs/rabbitcore-1.21.1-neoforge-2.0.0.jar`。开发客户端和服务端分别使用 `run/neoforge-client` 和 `run/neoforge-server`，不会复用旧开发实例。桥接配置见 [bridge.md](bridge.md)；WebSocket 协议仍为 v1。

主命令为 `/rabbitcore`。权限等级 2 可使用 `/rabbitcore chests list`、`/rabbitcore stats`、`/rabbitcore stats clear_stats` 和 `/rabbitcore stats toggle`。桥接管理和个人设置位于 `/rabbitcore bridge` 下。

## FTB Quests 周常

在任务（quest）上添加 `rabbit_weekly` 标签，并关闭 FTB Quests 自带的「可重复」选项。RabbitCore 会在香港时间每周一 04:00 重置这些任务的队伍进度和领奖记录，未领的奖励随旧周期到期。首次启用周常时建立当前周期并刷新一次。刷新时服务器离线的，重新开服后会补做刷新。

已有 `quest_timer_24` 的周常可以移除计时子任务。周常下带 `quest_damage` 标签的自定义子任务累计队员对怪物造成的伤害，目标为 6657 点。FTB Quests 继续负责共享进度和奖励发放。

深暗清剿的自定义子任务使用 `quest_damage_deep_dark` 标签。它累计队员对深暗之域内监守者造成的伤害，目标为 500 点。区域按监守者受击时所在的生物群系判定。

工程部订单在任务上同时添加 `rabbit_weekly` 和 `rabbit_engineering_order`，并配置三个消耗物品的交付子任务。首次启用时使用站台翻新清单，之后每周轮换到隧道加固、港口扩建，再回到站台翻新。所有队伍共用本周清单。RabbitCore 保留任务和子任务 ID，刷新时更新材料、数量和中英文说明，并把变化同步到在线玩家。奖励由原任务配置定义。世界存档会记住轮换开始的周期，跨周停机后按经过的周数切换。

可在测试存档里完成一个周常并保留未领的奖励，同时推进一个普通任务。跨过刷新边界后，周常应回到零进度且无法领取旧奖励，普通任务应保留进度。在同一周期重启服务器时，本周的任务进度应继续保留。
