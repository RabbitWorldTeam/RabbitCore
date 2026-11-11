# RabbitCore

为 Minecraft 服务器提供 FTB Quests 联动、玩家统计和聊天桥接。

**Minecraft 1.21.1 · NeoForge 21.1.265+（21.1.x） · Java 21**

[发布页](https://github.com/RabbitWorldTeam/RabbitCore/releases) · [Wiki 指南](https://github.com/RabbitWorldTeam/RabbitCore/wiki) · [问题反馈](https://github.com/RabbitWorldTeam/RabbitCore/issues)

## 主要功能

- **任务联动** · RabbitChest、每周任务和轮换工程订单。
- **玩家统计** · 采集、击杀、伤害和移动距离。
- **聊天桥接** · WebSocket 聊天、通知及可选的 RabbitAuth 白名单管理。

## 安装

将 `rabbitcore-1.21.1-neoforge-2.0.1.jar` 和所需依赖放入客户端与服务端的 `mods/`。Rabbit World 维度需要在服务端世界中安装数据包。[全新安装与任务配置](docs/neoforge.md)

## 常用入口

- `/rabbitcore chests list` · 查看已登记的任务箱。
- `/rabbitcore stats` · 查看玩家统计。
- `/rabbitcore bridge status` · 查看桥接状态。

[聊天桥接](docs/bridge.md) · [群聊白名单](docs/whitelist-admin.md)

## 从源码构建

`./gradlew build`，Windows 使用 `.\gradlew.bat build`。JAR 输出到 `build/libs/`。

MIT，见[模组元数据](src/main/resources/META-INF/neoforge.mods.toml)。
