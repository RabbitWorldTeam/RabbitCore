# RabbitCore 2.0：Minecraft 1.21.1 / NeoForge

此分支仅面向 Minecraft 1.21.1、NeoForge 21.1.265 或更新的 21.1.x，以及 Java 21。旧 Forge 版 JAR 不能和新版同时放在 `mods/`。未验证 1.20.1 世界直接升级；测试请使用新世界或存档副本。

开发构建固定使用以下 NeoForge 依赖；安装到游戏时也需在客户端与服务端安装：

- Architectury API 13.0.8
- [FTB Library 2101.1.37](https://www.curseforge.com/minecraft/mc-mods/ftb-library-forge/files/9008089)
- [FTB Teams 2101.1.11](https://www.curseforge.com/minecraft/mc-mods/ftb-teams-forge/files/8724782)
- [FTB Quests 2101.1.26](https://www.curseforge.com/minecraft/mc-mods/ftb-quests-forge/files/8216578)
- [FTB XMod Compat 21.1.7](https://www.curseforge.com/minecraft/mc-mods/ftb-xmod-compat/files/7429014)

运行 `.\gradlew.bat build` 生成 `build/libs/rabbitcore-1.21.1-neoforge-2.0.1.jar`。开发客户端和服务端分别使用 `run/neoforge-client` 和 `run/neoforge-server`，不会复用旧开发实例。桥接配置见 [bridge.md](bridge.md)；WebSocket 协议仍为 v1。

## 服务端全新安装

1. 使用 Java 21 安装 Minecraft 1.21.1 的 NeoForge 服务端。服务端与客户端均放入 RabbitCore JAR 和上面的模组依赖。使用 Rabbit World 服务端整合包时，保留与客户端匹配的整合包依赖。
2. 在首次开服前确认 `server.properties` 的 `level-name`。默认是 `world`。将发布附件 `rabbitworld_dimensions.zip` 放入 `<level-name>/datapacks/`，也可以复制仓库中 [数据包目录](../datapacks/rabbitworld_dimensions) 到同一位置。默认路径为 `world/datapacks/rabbitworld_dimensions.zip`。
3. 保持 `generate-structures=true` 和 `spawn-monsters=true`，让困难世界可以按正常规则生成结构和刷怪。开服后执行 `/datapack list enabled` 和 `/rabbitcore world list` 核对加载结果。
4. 如需启用整合包的周常，部署整合包的 FTB Quests 任务配置。RabbitCore 根据下文的标签识别周常。桥接按需另行配置。

维度数据包包含 `rabbitworld:archive`、`rabbitworld:flat_build`、`rabbitworld:hard` 和 `rabbitworld:peaceful`。`archive` 预留旧世界的维度 ID，全新安装时会按数据包生成普通主世界地形。旧世界导入需要另行准备区块数据。部署后如需追加维度数据包，停服放入数据包再启动。

主命令为 `/rabbitcore`。权限等级 2 可使用 `/rabbitcore chests list`、`/rabbitcore stats`、`/rabbitcore stats clear_stats` 和 `/rabbitcore stats toggle`。桥接管理和个人设置位于 `/rabbitcore bridge` 下。

## FTB Quests 周常

在任务（quest）上添加 `rabbit_weekly` 标签，并关闭 FTB Quests 自带的「可重复」选项。RabbitCore 会在香港时间每周一 04:00 重置这些任务的队伍进度和领奖记录，未领的奖励随旧周期到期。首次启用周常时建立当前周期并刷新一次。刷新时服务器离线的，重新开服后会补做刷新。

已有 `quest_timer_24` 的周常可以移除计时子任务。周常下带 `quest_damage` 标签的自定义子任务累计队员对怪物造成的伤害，目标为 6657 点。FTB Quests 继续负责共享进度和奖励发放。

深暗清剿的自定义子任务使用 `quest_damage_deep_dark` 标签。它累计队员对深暗之域内监守者造成的伤害，目标为 500 点。区域按监守者受击时所在的生物群系判定。

工程部订单在任务上同时添加 `rabbit_weekly` 和 `rabbit_engineering_order`，并配置三个消耗物品的交付子任务。首次启用时使用站台翻新清单，之后每周轮换到隧道加固、港口扩建，再回到站台翻新。所有队伍共用本周清单。RabbitCore 保留任务和子任务 ID，刷新时更新材料、数量和中英文说明，并把变化同步到在线玩家。奖励由原任务配置定义。世界存档会记住轮换开始的周期，跨周停机后按经过的周数切换。

## Rabbit World 维度规则

数据包在 `data/rabbitworld/dimension/` 定义 `hard.json` 和 `peaceful.json`。两个维度可使用原版普通主世界生成器。RabbitCore 将 `rabbitworld:hard` 的难度固定为困难，将 `rabbitworld:peaceful` 的难度固定为和平，并跳过和平维度新区块中的结构生成。地形和普通地物继续由原版生成器处理。

管理员用 `/rabbitcore world list` 查看已加载维度、实际难度和结构生成状态。用 `/rabbitcore world tp <维度> <x> <y> <z>` 前往已确认安全的坐标。验证时可调整主世界难度，再查看两个维度是否保留各自的难度。在同一世界种子的两个维度生成同一组区块，比较结构是否只在困难维度出现，并在重启后复查。

### 独立昼夜

管理员可以查询或修改任意已加载维度的时钟。以下用建筑世界举例。

| 命令 | 效果 |
| --- | --- |
| `/rabbitcore world time rabbitworld:flat_build` | 查询昼夜时间和循环状态 |
| `/rabbitcore world time rabbitworld:flat_build set 6000` | 将昼夜时间设为正午 |
| `/rabbitcore world time rabbitworld:flat_build pause` | 暂停昼夜循环 |
| `/rabbitcore world time rabbitworld:flat_build resume` | 恢复昼夜循环 |

首次载入时，其他维度从主世界继承当前时刻和循环开关，之后各自保存在维度的 `data/rabbitcore_dimension_clock.dat` 中。主世界继续使用 `level.dat`。用 RabbitCore 命令单独调时，原版 `/time set` 和 `/time add` 仍遍历所有维度。维度类型的 `fixed_time` 仍决定固定的天空角度。

玩家进入维度时会收到该维度的时间和循环状态。睡觉只会推进所在维度的昼夜，暂停循环时睡觉不会改动时间。要复查，可将两个维度分别设置成夜晚和正午，暂停其中一个，在另一个睡觉，再重启查询。

在 NeoForge 21.1.265 上使用本地测试存档副本验证了独立调时、暂停和恢复、睡觉跳过当前维度的夜晚、进入维度时的原版和 NeoForge 时间包，以及重启后的时钟和循环状态。

在 NeoForge 21.1.265 上用本地测试存档副本验证了独立难度、和平维度敌怪消失，以及同坐标的村庄结构只在困难维度生成。重启后复查了区块。CurseForge 客户端也用实际测试存档验证了维度列表和主世界与和平维度之间的往返传送。

可在测试存档里完成一个周常并保留未领的奖励，同时推进一个普通任务。跨过刷新边界后，周常应回到零进度且无法领取旧奖励，普通任务应保留进度。在同一周期重启服务器时，本周的任务进度应继续保留。
