# RabbitCore 聊天桥接

RabbitCore 主动连接 AstrBot 插件提供的 WebSocket 服务，支持双向文本聊天、彩色显示、游戏 ID 提及音效和玩家上下线通知。插件需适配 RabbitCore Bridge v1 自定义协议。

## 配置

服务器首次加载世界后生成 `<世界目录>/serverconfig/rabbitcore-bridge-server.toml`。配置由服务端独立读取，不通过 Forge 配置同步发送给客户端。功能默认关闭。设置 `[bridge]` 下的 `enabled = true`、完整 `url`、非空 `token` 和唯一 `server_id`，再执行 `/rabbit_bridge reload`。公网连接使用 `wss://`。不要提交含令牌的配置。

连接请求头为 `Authorization: Bearer <token>`。模组发送 `hello`，插件 10 秒内回复关联 `hello.id` 的 `ready`，之后开始转发；断线自动重连，不补发断线期间消息。

主要配置：

- `bridge.game_to_external` / `external_to_game`：聊天转发方向开关。
- `bridge.chat.game_to_external`：`filter_mode`、`player_uuids`、`player_names`。
- `bridge.chat.external_to_game`：`filter_mode`、`sender_ids`，例如 `qq:987654`。
- `bridge.presence`：独立的上下线开关、文字模板与 UUID/账户名名单。
- `bridge.colors`：来源、发送者、分隔符、正文和提及的 Minecraft 命名颜色。
- `bridge.mentions`：提及开关、音效开关、声音 ID、音量、音高和冷却。
- `bridge.limits`：入站令牌桶速率/容量与出站/待广播队列容量。

名单模式为 `all`、`blacklist` 或 `whitelist`。空白名单拒绝全部，空黑名单允许全部。游戏账户名完整匹配并忽略大小写；外部用户 ID 精确匹配。三个名单独立，不控制登录权限。无效重载保留旧配置。

外部正文中完整出现在线游戏 ID 即可提示，无需 `@`。例如 Steve 在线时，`Steve你好` 可识别，`Steve123` 不会误匹配。提示音仅发送给被提及且开启接收与音效的玩家，默认每人冷却 3 秒。外部内容按纯文本显示，不执行命令。

## 命令

| 命令 | 权限与效果 |
| --- | --- |
| `/rabbit_bridge status` | 权限 2；连接、计数和脱敏错误 |
| `/rabbit_bridge reload` | 权限 2；校验并重载，连接参数变化时重连 |
| `/rabbit_bridge reconnect` | 权限 2；手动重连 |
| `/rabbit_bridge sound on/off` | 玩家；开关个人提示音 |
| `/rabbit_bridge sound` | 玩家；查看个人音效状态 |
| `/rabbit_bridge sound test` | 玩家；测试自己的音效，遵守开关和冷却 |
| `/rabbit_bridge receive on/off` | 玩家；开关个人群聊接收 |
| `/rabbit_bridge receive` | 玩家；查看个人接收状态 |

个人偏好按 UUID 保存到世界的 `rabbit_bridge_preferences` SavedData，重启后保留。管理命令支持控制台。

## 验证

`./gradlew build` 包含 `bridgeTest`，可用 `./gradlew bridgeTest` 单独运行配置、协议和本地 WebSocket 回归检查。Windows 使用 `.\gradlew.bat`。

游戏内验收：两位玩家登录，与适配插件互发消息；分别测试无 @ 的游戏 ID 提及、关闭音效、关闭接收、黑白名单、重连与无效配置重载。确认只有被提及者听到声音，并在重启后确认个人偏好保留。插件尚未适配时，独立协议测试不等同于真实 AstrBot 联调。
