# 群聊白名单管理协议

RabbitAuth 管理数据库和进服检查；RabbitCore 验证明确的群 ID、操作者 ID 及操作范围后调用可选公开 API。
没有 RabbitAuth 时普通聊天桥接继续工作，管理请求返回 AUTH_UNAVAILABLE。
本次不包含 AstrBot 插件本体；以下是插件接入约定，不能把模拟联调当成真实群聊已经接通。

## 授权配置

在世界 serverconfig/rabbitcore-bridge-server.toml 中合并：

```toml
[bridge.admin.whitelist]
enabled = true
source_ids = ["qq:group:123456"]
sender_ids = ["qq:10001", "qq:10002"]
actions = ["add", "remove", "set", "info", "list", "status"]
```

bridge.enabled、url、token 和 server_id 沿用现有桥接配置。修改后执行 /rabbit_bridge reload 或重启。
管理功能默认关闭；群列表或账号列表为空时不允许任何人。ID 必须与插件上报的完整平台 ID 完全一致。
同一账号在 source_ids 指定的群内可以执行 actions 中的操作；群主、群管理员、昵称都不会额外获得权限。
授权名单只能由能编辑服务器配置的人维护；不提供通过群聊给自己授权的操作。
全局 on/off 默认不授权，确有需要可显式加入 actions。备注、改名、任意控制台命令、import 和修改授权均不在远程接口范围。

管理开关改变会重新握手并更新 hello.capabilities；授权账号/群/操作变更立即用于接收、服务端调度及成功回复过滤。
关闭管理功能时保留原有普通聊天转发行为。
管理权限不依赖普通聊天 sender_ids 过滤，external_to_game=false 时也可以处理明确授权的管理请求。

## 方式一：转发群聊文本

插件可以沿用 chat.external，必须包含 source.id、sender.id 和原始文本。RabbitCore 在管理功能开启时识别：

```text
白名单 添加 Steve
白名单 移除 Steve
白名单 查询 Steve
白名单 列表 1
白名单 暂停 Steve
白名单 启用 Steve
白名单 状态
```

也支持 /白名单、whitelist、/whitelist 前缀及对应英文动词。
明确识别到管理命令后不会广播到游戏。多余参数、非法玩家名或指令拼接会被拒绝。

## 方式二：结构化请求

外层 version=1、id、server_id、timestamp 沿用 docs/bridge.md。id 每次新操作使用 UUID，timestamp 为当前 Unix 毫秒整数。
下面仅展示新增 type/data：

```json
{
  "type": "admin.whitelist",
  "data": {
    "source": {"id": "qq:group:123456"},
    "sender": {"id": "qq:10001"},
    "action": "set",
    "name": "Steve",
    "enabled": false
  }
}
```

action：add/remove/set/info/list/status/on/off。set 的 enabled 必须是 JSON 布尔值。
list 可带正整数 page，默认 1，每页最多 20 条。add/remove/set/info 的 name 为 3–16 位英文字母、数字或下划线。
name 由 RabbitAuth 按现有注册账号规范化大小写，名单按忽略大小写唯一。

插件必须从平台收到的可信事件提取真实 source.id 和 sender.id，不能从用户消息参数取授权身份。
WebSocket token 验证的是桥接端点，RabbitCore 信任该端点提供的平台身份；不要把 token 分发给群成员。

## 定向回复

请求成功排队并执行后，服务器发 admin.whitelist.result：

```json
{
  "type": "admin.whitelist.result",
  "data": {
    "reply_to": "原请求 UUID",
    "source_id": "qq:group:123456",
    "sender_id": "qq:10001",
    "action": "set",
    "ok": true,
    "code": "OK",
    "message": "用于展示的服务端消息",
    "enabled": true,
    "entry": {
      "name": "Steve",
      "enabled": false,
      "created_at": 0,
      "updated_at": 0,
      "actor": "bridge:qq:group:123456:qq:10001"
    }
  }
}
```

enabled 是全局开关；entry.enabled 才是成员状态。时间字段是 Unix 毫秒。
remove 返回 removed=true/name，list 返回 entries/page/pages/total。
插件只回复 source_id 对应的原群，可提及 sender_id；不要将结果当作普通游戏聊天广播，不要广播到所有群。
应核对 reply_to、server_id 与发送请求时保存的路由。服务器在撤权后会丢弃包含成功数据的待发回复。

错误码包括 FORBIDDEN、EXPIRED_REQUEST、DUPLICATE_REQUEST、RATE_LIMITED、BUSY、AUTH_UNAVAILABLE、
NOT_READY、INVALID_NAME、ALREADY_EXISTS、NOT_FOUND、INVALID_PAGE、INVALID_STATE、STORAGE_UNAVAILABLE。
格式或协议错误仍使用现有 error 消息并携带 reply_to；插件需要同时处理它。

## 请求生命周期

- 未完成 ready 握手时不能请求管理。
- 接收与调度时要求 timestamp 与服务端时间相差不超过 30 秒；RabbitAuth 排队执行也检查截止时间。
- 同一进程内保留管理请求 UUID 五分钟，跨 WebSocket 重连仍去重；重复请求不再执行。
- 去重不是跨服务器进程重启的持久化 exactly-once 保证。回执丢失时先查询状态，不要自动换新 ID 重复执行。
- 管理队列最多 64 项，每 tick 最多调度 4 项，复用桥接接收限流。SQLite/MySQL 修改由 RabbitAuth 单线程有界队列执行。
- 在调度时按当前授权接受操作，授权检查通过并提交给 RabbitAuth 的修改可能已经生效；撤权会阻止尚未调度的请求和敏感成功回执。
- 断开旧桥接代际后不向新连接发送旧请求回执。原操作若已提交可能生效，重新连接后查询。

## 测试与范围

RabbitCore 的 bridgeTest 覆盖真实本机 WebSocket 鉴权、过期、重复、队列和回复路由；不依赖安装 RabbitAuth。
RabbitAuth tools/protocol-test/whitelist.mjs 使用模拟群聊 WebSocket 端点、实际桥接源代码和真实服务端验证白名单操作、踢人和重启。
开发夹具排除了 RabbitCore 方块和 FTB 依赖，未验证完整整合包或真实 AstrBot 收发。
