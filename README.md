# RabbitCore

为 Minecraft 服务器提供维度管理、世界规则与聊天桥接。

**Minecraft 1.21.1 · NeoForge 21.1.265+（21.1.x） · Java 21**

[发布页](https://github.com/RabbitWorldTeam/RabbitCore/releases) · [Wiki 指南](https://github.com/RabbitWorldTeam/RabbitCore/wiki) · [问题反馈](https://github.com/RabbitWorldTeam/RabbitCore/issues)

## 主要功能

- **维度管理** · 每个维度可应用独立难度、昼夜、种子和结构生成规则。
- **世界规则** · 管理鞘翅飞行、玩家传送和地形保护。
- **聊天桥接** · WebSocket 聊天、通知及可选的 RabbitAuth 白名单管理。

## 安装

将 `rabbitcore-1.21.1-neoforge-2.3.0.jar` 放入客户端与服务端的 `mods/`。Rabbit World 维度需要在服务端世界中安装数据包。[全新安装与迁移](docs/neoforge.md)

商店、任务、玩家任务统计和兔子箱已迁至 [RabbitQuest](https://github.com/RabbitWorldTeam/RabbitQuest)。

## 常用入口

- `/rabbitcore world list` · 查看维度和已应用的规则。
- `/rabbitcore bridge status` · 查看桥接状态。

[聊天桥接](docs/bridge.md) · [群聊白名单](docs/whitelist-admin.md)

## 从源码构建

`./gradlew build`，Windows 使用 `.\gradlew.bat build`。JAR 输出到 `build/libs/`。

MIT，见[模组元数据](src/main/resources/META-INF/neoforge.mods.toml)。
