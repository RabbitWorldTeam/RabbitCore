package com.tr4nce.rabbitcore.bridge;

import com.google.gson.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.LongAdder;

/** One serialized network state machine; game threads only offer bounded queue entries. */
public final class BridgeConnection implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger("RabbitCore-Bridge");
    private final ScheduledExecutorService loop = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "rabbitcore-bridge"); thread.setDaemon(true); return thread;
    });
    private final ExecutorService httpExecutor = Executors.newFixedThreadPool(2, r -> {
        Thread thread = new Thread(r, "rabbitcore-bridge-http"); thread.setDaemon(true); return thread;
    });
    private final HttpClient http = HttpClient.newBuilder().executor(httpExecutor)
            .connectTimeout(Duration.ofSeconds(10)).build();
    private final String modVersion;
    private volatile BridgeConfig config;
    private volatile BridgeConfig requestedConfig;
    private volatile String state = "DISCONNECTED", lastError = "无";
    private volatile long generation, readyAt;
    private volatile boolean closed;
    private WebSocket socket;
    private CompletableFuture<WebSocket> connecting;
    private long retryAt, deadline, pingAt, pongDeadline, lastWarning;
    private int attempts;
    private boolean writing;
    private UUID hello;
    private double tokens;
    private long tokenAt = System.nanoTime(), lastErrorReply;
    private final LinkedHashMap<UUID, Long> seen = new LinkedHashMap<>();
    private final Queue<Outgoing> outgoing = new ArrayDeque<>();
    private final Queue<Incoming> incoming = new ArrayDeque<>();
    private final Queue<AdminIncoming> admins = new ArrayDeque<>();
    private final LinkedHashMap<UUID, Long> adminSeen = new LinkedHashMap<>();
    public record AdminIncoming(long generation, UUID id, long timestamp, BridgeAdmin.Request request) {}
    public AdminIncoming pollAdmin() { synchronized (admins) { return admins.poll(); } }
    public void adminResult(AdminIncoming incoming, Map<String, Object> result) {
        if (!current(incoming.generation())) return;
        JsonObject data = BridgeProtocol.GSON.toJsonTree(result).getAsJsonObject();
        data.addProperty("reply_to", incoming.id().toString());
        data.addProperty("source_id", incoming.request().sourceId());
        data.addProperty("sender_id", incoming.request().senderId());
        data.addProperty("action", incoming.request().action());
        send("admin.whitelist.result", null, "", data);
    }
    private void enqueueAdmin(UUID id, long timestamp, BridgeAdmin.Request request) {
        AdminIncoming message = new AdminIncoming(generation, id, timestamp, request);
        if (!config.allowsWhitelistAdmin(request)) {
            adminResult(message, Map.of("ok", false, "code", "FORBIDDEN", "message", "该群或账号未获白名单管理授权")); return;
        }
        if (!BridgeAdmin.fresh(timestamp, System.currentTimeMillis())) {
            adminResult(message, Map.of("ok", false, "code", "EXPIRED_REQUEST", "message", "请求已过期")); return;
        }
        long now = System.nanoTime();
        adminSeen.entrySet().removeIf(e -> now - e.getValue() >= TimeUnit.MINUTES.toNanos(5));
        if (adminSeen.containsKey(id)) {
            adminResult(message, Map.of("ok", false, "code", "DUPLICATE_REQUEST", "message", "重复请求未再次执行")); return;
        }
        tokens = Math.min(config.number("limits.incoming_burst"), tokens
                + (now - tokenAt) / 1_000_000_000.0 * config.number("limits.incoming_messages_per_second"));
        tokenAt = now;
        if (tokens < 1 || adminSeen.size() >= 4096) {
            adminResult(message, Map.of("ok", false, "code", "RATE_LIMITED", "message", "管理请求过多")); return;
        }
        tokens--;
        synchronized (admins) {
            if (admins.size() >= 64) {
                adminResult(message, Map.of("ok", false, "code", "BUSY", "message", "管理队列已满")); return;
            }
            adminSeen.put(id, now);
            admins.add(message);
        }
    }
    private final Queue<Control> controls = new ArrayDeque<>();
    public final LongAdder sent = new LongAdder(), received = new LongAdder(), filtered = new LongAdder();
    public final LongAdder rateDropped = new LongAdder(), queueDropped = new LongAdder();
    public record Incoming(long generation, BridgeProtocol.External message) {}
    private record Outgoing(long generation, String type, UUID player, String name, String json) {}
    private record Control(String json, long generation, Runnable after) {}

    public BridgeConnection(BridgeConfig config, String modVersion) {
        this.config = config; this.requestedConfig = config;
        this.modVersion = modVersion; tokens = config.number("limits.incoming_burst");
        loop.scheduleAtFixedRate(this::tickSafely, 0, 50, TimeUnit.MILLISECONDS);
    }
    public void configure(BridgeConfig next) {
        requestedConfig = next;
        execute(() -> {
            BridgeConfig old = config;
            tokens = Math.min(tokens, next.number("limits.incoming_burst"));
            if (old.flag("enabled") != next.flag("enabled") || old.flag("admin.whitelist.enabled") != next.flag("admin.whitelist.enabled") || !old.text("url").equals(next.text("url"))
                    || !old.text("token").equals(next.text("token")) || !old.text("server_id").equals(next.text("server_id"))) {
                disconnect(); retryAt = 0;
            }
            config = next;
        });
    }
    public void reconnect() { execute(() -> { disconnect(); retryAt = 0; }); }
    private void execute(Runnable task) {
        if (!closed) try { loop.execute(task); } catch (RejectedExecutionException ignored) { }
    }
    public boolean ready() { return !closed && requestedConfig == config && state.equals("READY") && config.flag("enabled"); }
    public boolean current(long value) { return ready() && value == generation; }
    public String status() {
        long seconds = ready() ? TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - readyAt) : 0;
        return "状态=" + state + " | 就绪=" + seconds + "s | 发送=" + sent.sum() + " 接收=" + received.sum()
                + " 过滤=" + filtered.sum() + " 限流丢弃=" + rateDropped.sum() + " 队列丢弃=" + queueDropped.sum()
                + " | 最近错误=" + lastError;
    }
    public void send(String type, UUID player, String name, JsonObject data) {
        if (!ready()) return;
        long currentGeneration = generation;
        String json = BridgeProtocol.message(config.text("server_id"), type, data).toString();
        if (json.getBytes(StandardCharsets.UTF_8).length > BridgeProtocol.MAX_BYTES) { queueDropped.increment(); return; }
        synchronized (outgoing) {
            if (!current(currentGeneration)) return;
            if (outgoing.size() >= config.number("limits.outgoing_queue_capacity")) { queueDropped.increment(); return; }
            outgoing.add(new Outgoing(currentGeneration, type, player, name, json));
        }
    }
    public Incoming poll() { synchronized (incoming) { return incoming.poll(); } }
    private void tickSafely() {
        try { tick(); } catch (RuntimeException e) { fail("内部连接错误"); }
    }
    private void tick() {
        if (closed || !config.flag("enabled")) return;
        long now = System.nanoTime();
        if (state.equals("DISCONNECTED") && now >= retryAt) connect();
        if ((state.equals("CONNECTING") || state.equals("WAITING_READY")) && now > deadline) fail("连接或握手超时");
        if (state.equals("WAITING_READY")) { flush(); return; }
        if (!ready()) return;
        if (now - readyAt >= TimeUnit.SECONDS.toNanos(60)) attempts = 0;
        if (pongDeadline != 0 && now >= pongDeadline) { fail("心跳超时"); return; }
        if (now >= pingAt && pongDeadline == 0) {
            pongDeadline = now + TimeUnit.SECONDS.toNanos(15);
            long session = generation;
            socket.sendPing(ByteBuffer.wrap(new byte[]{1})).whenComplete((value, error) -> {
                if (error != null) execute(() -> { if (session == generation) fail("心跳发送失败"); });
            });
            pingAt = now + TimeUnit.SECONDS.toNanos(30);
        }
        if (queueDropped.sum() + rateDropped.sum() > 0 && now - lastWarning > TimeUnit.SECONDS.toNanos(30)) {
            lastWarning = now; LOGGER.warn("Bridge dropped messages: rate={}, queue={}", rateDropped.sum(), queueDropped.sum());
        }
        flush();
    }
    private void flush() {
        if (writing || socket == null) return;
        Control control = controls.poll();
        if (control != null) {
            write(control.json, control.generation, false, control.after); return;
        }
        if (!ready()) return;
        Outgoing entry;
        synchronized (outgoing) { entry = outgoing.poll(); }
        if (entry == null) return;
        if (!current(entry.generation) || !allows(entry)) { filtered.increment(); return; }
        write(entry.json, entry.generation, true, null);
    }
    private void write(String json, long session, boolean business, Runnable after) {
        if (session != generation) return;
        writing = true;
        socket.sendText(json, true).orTimeout(10, TimeUnit.SECONDS).whenComplete((value, error) -> execute(() -> {
            if (session != generation) return;
            writing = false;
            if (error != null) fail("消息发送失败");
            else { if (business) sent.increment(); if (after != null) after.run(); }
        }));
    }
    private boolean allows(Outgoing entry) {
        if (entry.type.equals("admin.whitelist.result")) {
            JsonObject data = BridgeProtocol.parse(entry.json).getAsJsonObject("data");
            if (!data.get("ok").getAsBoolean()) return true;
            return config.allowsWhitelistAdmin(new BridgeAdmin.Request(data.get("source_id").getAsString(),
                    data.get("sender_id").getAsString(), data.get("action").getAsString(), "", true, 1));
        }
        if (entry.type.equals("chat.game")) return config.flag("game_to_external")
                && config.allowsPlayer("chat.game_to_external", entry.player, entry.name);
        if (entry.type.equals("player.death")) return config.flag("death.enabled")
                && config.allowsPlayer("death", entry.player, entry.name);
        boolean join = entry.type.equals("player.join");
        return config.flag("presence.enabled") && config.flag(join ? "presence.join_enabled" : "presence.leave_enabled")
                && config.allowsPlayer("presence", entry.player, entry.name);
    }
    private void connect() {
        state = "CONNECTING"; LOGGER.info("Bridge state: CONNECTING");
        long session = ++generation;
        deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        try {
            connecting = http.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + config.text("token"))
                    .buildAsync(URI.create(config.text("url")), new Listener(session));
            connecting.whenComplete((value, error) -> execute(() -> {
                if (session != generation) { if (value != null) value.abort(); return; }
                if (error != null) fail("连接失败（检查地址、服务端和鉴权）");
            }));
        } catch (RuntimeException e) { fail("连接配置无效"); }
    }
    private void opened(long session, WebSocket ws) {
        if (closed || session != generation) { ws.abort(); return; }
        socket = ws; state = "WAITING_READY";
        deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        JsonObject data = new JsonObject();
        data.addProperty("mod_version", modVersion); data.addProperty("minecraft_version", net.minecraft.SharedConstants.getCurrentVersion().getName());
        List<String> capabilities = new ArrayList<>(List.of("chat.send", "chat.receive", "chat.mentions", "player.presence", "player.death"));
        if (config.flag("admin.whitelist.enabled")) capabilities.add("admin.whitelist");
        data.add("capabilities", BridgeProtocol.GSON.toJsonTree(capabilities));
        JsonObject root = BridgeProtocol.message(config.text("server_id"), "hello", data);
        hello = UUID.fromString(root.get("id").getAsString());
        control(root, session, null);
    }
    private void control(JsonObject root, long session, Runnable after) {
        if (socket == null || session != generation) return;
        if (controls.size() >= 16) { fail("控制消息过多"); return; }
        controls.add(new Control(root.toString(), session, after));
        flush();
    }
    private void message(long session, String text) {
        if (session != generation || closed) return;
        JsonObject root;
        try {
            root = BridgeProtocol.parse(text);
        } catch (RuntimeException e) { closeProtocol(1007); return; }
        UUID id;
        try { id = BridgeProtocol.uuid(BridgeProtocol.string(root, "id", 36)); }
        catch (RuntimeException e) { closeProtocol(1008); return; }
        boolean errorMessage = root.has("type") && root.get("type").isJsonPrimitive()
                && root.get("type").getAsString().equals("error");
        try {
            BridgeProtocol.Envelope envelope = BridgeProtocol.envelope(root, config.text("server_id"));
            if (envelope.type().equals("error")) { lastError = "插件报告协议错误"; return; }
            if (envelope.type().equals("ready")) {
                String reply = BridgeProtocol.string(envelope.data(), "reply_to", 36);
                if (!state.equals("WAITING_READY") || !hello.toString().equals(reply))
                    throw new BridgeProtocol.Invalid("INVALID_MESSAGE", "ready.reply_to");
                state = "READY"; readyAt = System.nanoTime(); pongDeadline = 0;
                pingAt = readyAt + TimeUnit.SECONDS.toNanos(30);
                LOGGER.info("Bridge state: READY"); return;
            }
            if (!Set.of("chat.external", "admin.whitelist").contains(envelope.type())) throw new BridgeProtocol.Invalid("UNSUPPORTED_TYPE", "type");
            if (!ready()) throw new BridgeProtocol.Invalid("NOT_READY", "handshake");
            if (envelope.type().equals("admin.whitelist")) {
                enqueueAdmin(id, BridgeProtocol.integer(root, "timestamp"), BridgeAdmin.structured(envelope.data())); return;
            }
            BridgeProtocol.External external = BridgeProtocol.external(envelope.data());
            if (config.flag("admin.whitelist.enabled") && BridgeAdmin.textCommand(external.text())) {
                enqueueAdmin(id, BridgeProtocol.integer(root, "timestamp"), BridgeAdmin.text(envelope.data(), external.text())); return;
            }
            long now = System.nanoTime();
            seen.entrySet().removeIf(entry -> now - entry.getValue() >= TimeUnit.MINUTES.toNanos(5));
            if (seen.containsKey(id)) return;
            seen.put(id, now);
            if (seen.size() > 4096) seen.remove(seen.keySet().iterator().next());
            received.increment();
            tokens = Math.min(config.number("limits.incoming_burst"), tokens
                    + (now - tokenAt) / 1_000_000_000.0 * config.number("limits.incoming_messages_per_second"));
            tokenAt = now;
            if (tokens < 1) { rateDropped.increment(); return; }
            tokens--;
            if (!config.flag("external_to_game") || !config.allowsSender(external.senderId()) || external.text().isBlank()) {
                filtered.increment(); return;
            }
            synchronized (incoming) {
                if (incoming.size() >= config.number("limits.broadcast_queue_capacity")) queueDropped.increment();
                else incoming.add(new Incoming(session, external));
            }
        } catch (BridgeProtocol.Invalid e) {
            boolean fatal = e.code.equals("UNSUPPORTED_VERSION") || e.code.equals("SERVER_ID_MISMATCH");
            if (errorMessage) { if (fatal) closeProtocol(1008); return; }
            long now = System.nanoTime();
            if (fatal || now - lastErrorReply > TimeUnit.SECONDS.toNanos(1)) {
                lastErrorReply = now;
                JsonObject data = new JsonObject(); data.addProperty("reply_to", id.toString());
                data.addProperty("code", e.code); data.addProperty("message", e.getMessage());
                control(BridgeProtocol.message(config.text("server_id"), "error", data), session,
                        fatal ? () -> closeProtocol(1008) : null);
            }
        }
    }
    private void closeProtocol(int code) {
        // JDK WebSocket clients reserve 1003/1007/1009; use documented application codes.
        int wireCode = switch (code) { case 1003 -> 4003; case 1007 -> 4007; case 1009 -> 4009; default -> code; };
        WebSocket closing = socket;
        socket = null;
        if (closing != null) {
            closing.sendClose(wireCode, "Bridge protocol violation").orTimeout(1, TimeUnit.SECONDS)
                    .whenComplete((value, error) -> closing.abort());
        }
        fail("协议连接已关闭: " + code);
    }
    private void fail(String reason) {
        lastError = reason;
        if (System.nanoTime() - lastWarning > TimeUnit.SECONDS.toNanos(30)) {
            lastWarning = System.nanoTime(); LOGGER.warn("Bridge: {}", reason);
        }
        disconnect();
        long seconds = Math.min(60, 1L << Math.min(attempts++, 6));
        retryAt = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Math.min(60000,
                (long) (seconds * 1000 * ThreadLocalRandom.current().nextDouble(1, 1.2))));
    }
    private void disconnect() {
        ++generation;
        if (connecting != null) { connecting.cancel(true); connecting = null; }
        if (socket != null) { socket.abort(); socket = null; }
        synchronized (outgoing) { outgoing.clear(); }
        synchronized (incoming) { incoming.clear(); }
        synchronized (admins) { admins.clear(); }
        controls.clear(); writing = false; pongDeadline = 0; state = "DISCONNECTED";
    }
    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        loop.execute(() -> { disconnect(); state = "STOPPING"; seen.clear(); });
        loop.shutdown(); httpExecutor.shutdownNow();
    }
    private final class Listener implements WebSocket.Listener {
        private final long session;
        private final StringBuilder text = new StringBuilder();
        private boolean rejected;
        Listener(long session) { this.session = session; }
        @Override public void onOpen(WebSocket ws) { execute(() -> opened(session, ws)); ws.request(1); }
        @Override public CompletionStage<?> onText(WebSocket ws, CharSequence part, boolean last) {
            if (rejected || closed || session != generation) { ws.abort(); return null; }
            text.append(part);
            if (text.length() > BridgeProtocol.MAX_BYTES || text.toString().getBytes(StandardCharsets.UTF_8).length > BridgeProtocol.MAX_BYTES) {
                rejected = true; text.setLength(0); execute(() -> { if (session == generation) closeProtocol(1009); });
                return null;
            }
            if (!last) { ws.request(1); return null; }
            String complete = text.toString(); text.setLength(0);
            // Backpressure: only request the next message after this one was processed.
            execute(() -> {
                try { if (session == generation) message(session, complete); }
                catch (RuntimeException e) { if (session == generation) fail("消息处理失败"); }
                finally { ws.request(1); }
            });
            return null;
        }
        @Override public CompletionStage<?> onBinary(WebSocket ws, ByteBuffer data, boolean last) {
            execute(() -> { if (session == generation) closeProtocol(1003); }); return null;
        }
        @Override public CompletionStage<?> onPing(WebSocket ws, ByteBuffer data) { ws.request(1); return ws.sendPong(data); }
        @Override public CompletionStage<?> onPong(WebSocket ws, ByteBuffer data) {
            execute(() -> { if (session == generation) pongDeadline = 0; }); ws.request(1); return null;
        }
        @Override public CompletionStage<?> onClose(WebSocket ws, int code, String reason) {
            execute(() -> { if (session == generation) fail("远端关闭连接: " + code); }); return null;
        }
        @Override public void onError(WebSocket ws, Throwable error) {
            execute(() -> { if (session == generation) fail("网络连接异常"); });
        }
    }
}
