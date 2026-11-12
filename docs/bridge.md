# RabbitCore 聊天桥接

RabbitCore 主动连接 AstrBot 插件提供的 WebSocket 服务，支持双向文本聊天、彩色显示、游戏 ID 提及音效和玩家上下线通知。插件需适配 RabbitCore Bridge v1 自定义协议。

## 外部服务主动连接 RabbitCore

在服务端存档的 `serverconfig/rabbitcore-bridge-server.toml` 添加或修改：

```toml
[bridge.inbound]
enabled = true
bind = "0.0.0.0"
port = 6701
token = "YOUR_RANDOM_ASCII_TOKEN"
```

将示例令牌替换为独立的随机字符串，使用可打印 ASCII 字符且不含空格。修改监听设置、入站令牌、`bridge.server_id` 或入站队列及速率限制后重启服务器。用 `/rabbitcore bridge status` 查询监听状态。入站可以独立启用，`bridge.enabled` 控制的是 RabbitCore 主动连接 AstrBot。

连接 URL 为 `ws://<服务器内网IP>:6701/rabbitcore`，升级请求带 `Authorization: Bearer <token>`。这是明文连接，令牌和消息都经内网明文传输，防火墙应只向需要接入的主机开放该端口。

```sh
wscat -c ws://192.168.1.20:6701/rabbitcore -H "Authorization: Bearer YOUR_TOKEN"
```

连接后 10 秒内发送 `hello`。每条消息使用新的 UUID，`timestamp` 填当前 Unix 毫秒时间，`server_id` 必须与 `bridge.server_id` 一致。以下示例使用默认 ID。

```json
{"version":1,"id":"8dfda147-e551-4d9f-9404-17cd0d5df34c","server_id":"rabbit-house","timestamp":0,"type":"hello","data":{"events":["chat.game","player.join","player.leave","player.death"]}}
```

收到 `ready` 后可以发聊天：

```json
{"version":1,"id":"210081fe-b443-46ae-92de-269e2acc835d","server_id":"rabbit-house","timestamp":0,"type":"chat.external","data":{"source":{"id":"intranet","name":"内网"},"sender":{"id":"bot:1","name":"机器人"},"text":"你好"}}
```

- `ready.data.reply_to` 对应 `hello.id`。`chat.result` 的 `data.reply_to` 对应聊天 ID，`ok` 表示游戏线程是否已处理消息，失败时 `code` 给出原因。入站聊天向游戏内接收外部聊天的玩家广播。开启 `bridge.game_to_external` 且出站桥接就绪时，RabbitCore 同时按 `chat.game` 转发给 RabbitChat，使用入站消息的发送者名称。回执只确认游戏侧处理。
- `subscribe` 使用与 `hello` 相同的 `data.events`，替换当前订阅。空数组取消所有订阅。断线后重新发送 `hello`，只收到新的实时事件。
- 游戏聊天、上下线和死亡事件沿用下文的桥接开关和过滤规则。入站聊天遵守 `bridge.external_to_game` 和外部发送者 ID 过滤，玩家 UUID 和名称名单用于游戏玩家聊天。RabbitChat 发来的群消息继续只进游戏，入站订阅者只收到游戏内原生聊天事件。

## 配置

服务器首次加载世界后生成 `<世界目录>/serverconfig/rabbitcore-bridge-server.toml`。配置由服务端独立读取，不通过 NeoForge 配置同步发送给客户端。功能默认关闭。设置 `[bridge]` 下的 `enabled = true`、完整 `url`、非空 `token` 和唯一 `server_id`，再执行 `/rabbitcore bridge reload`。公网连接使用 `wss://`。不要提交含令牌的配置。

连接请求头为 `Authorization: Bearer <token>`。模组发送 `hello`，插件 10 秒内回复关联 `hello.id` 的 `ready`，之后开始转发；断线自动重连，不补发断线期间消息。

主要配置：

- `bridge.game_to_external` / `external_to_game`：聊天转发方向开关。
- `bridge.chat.game_to_external`：`filter_mode`、`player_uuids`、`player_names`。
- `bridge.chat.external_to_game`：`filter_mode`、`sender_ids`，例如 `qq:987654`。
- `bridge.presence`：独立的上下线开关、文字模板与 UUID/账户名名单。
- `bridge.death`：独立的死亡通知开关、文字模板与 UUID/账户名名单。
- `bridge.colors`：来源、发送者、分隔符、正文和提及的 Minecraft 命名颜色。
- `bridge.mentions`：提及开关、音效开关、声音 ID、音量、音高和冷却。
- `bridge.limits`：入站令牌桶速率/容量与出站/待广播队列容量。

名单模式为 `all`、`blacklist` 或 `whitelist`。空白名单拒绝全部，空黑名单允许全部。游戏账户名完整匹配并忽略大小写；外部用户 ID 精确匹配。各名单独立，不控制登录权限。无效重载保留旧配置。

死亡通知使用独立的 `player.death` 事件，AstrBot 适配端需将其 `data.text` 转发到目标群。它不依赖聊天和上下线通知开关，也不改变原版游戏内死亡消息。示例配置：

```toml
[bridge.death]
enabled = true
format = "[死亡] {reason}"
filter_mode = "blacklist"
player_uuids = []
player_names = ["BotAccount"]
```

`{reason}` 是游戏生成的完整死亡文本，使用原版简体中文翻译，例如「Steve 被僵尸杀死了」或「Steve 从高处摔了下来」。模组自定义死因没有对应翻译时保留服务端原文。`{player}` 是账户名。默认模板为 `{reason}`，默认名单模式为 `all`。

外部正文中完整出现在线游戏 ID 即可提示，无需 `@`。例如 Steve 在线时，`Steve你好` 可识别，`Steve123` 不会误匹配。提示音仅发送给被提及且开启接收与音效的玩家，默认每人冷却 3 秒。外部内容按纯文本显示，不执行命令。

## 命令

| 命令 | 权限与效果 |
| --- | --- |
| `/rabbitcore bridge status` | 权限 2；连接、计数和脱敏错误 |
| `/rabbitcore bridge reload` | 权限 2；校验并重载，连接参数变化时重连 |
| `/rabbitcore bridge reconnect` | 权限 2；手动重连 |
| `/rabbitcore bridge sound on/off` | 玩家；开关个人提示音 |
| `/rabbitcore bridge sound` | 玩家；查看个人音效状态 |
| `/rabbitcore bridge sound test` | 玩家；测试自己的音效，遵守开关和冷却 |
| `/rabbitcore bridge receive on/off` | 玩家；开关个人群聊接收 |
| `/rabbitcore bridge receive` | 玩家；查看个人接收状态 |

个人偏好按 UUID 保存到世界的 `rabbit_bridge_preferences` SavedData，重启后保留。管理命令支持控制台。

## 验证

`./gradlew build` 包含 `bridgeTest`，可用 `./gradlew bridgeTest` 单独运行配置、协议和本地 WebSocket 回归检查。Windows 使用 `.\gradlew.bat`。

游戏内验收：两位玩家登录，与适配插件互发消息；分别测试无 @ 的游戏 ID 提及、关闭音效、关闭接收、黑白名单、重连与无效配置重载。确认只有被提及者听到声音，并在重启后确认个人偏好保留。插件尚未适配时，独立协议测试不等同于真实 AstrBot 联调。

## 可选白名单管理

RabbitCore 可对接 RabbitAuth 的自定义白名单，按明确的群 ID 和发送者 ID 授权，不继承群管理员昵称或角色。
默认关闭，支持结构化 admin.whitelist 和管理功能开启后的白名单文本命令；结果通过 admin.whitelist.result 定向回复。
完整配置和 AstrBot 接口见 [群聊白名单协议](whitelist-admin.md)。AstrBot 插件本体尚未接通。
