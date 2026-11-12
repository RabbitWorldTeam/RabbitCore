package com.tr4nce.rabbitcore.bridge;

import com.google.gson.*;
import com.tr4nce.rabbitcore.world.storage.BridgePreferencesData;
import net.minecraft.nbt.CompoundTag;
import java.io.*;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

/** Runs with bridgeTest and check; real loopback WebSocket frames test transport boundaries. */
public final class BridgeRegressionTest {
    private static int checks;
    private static void check(boolean value, String description) {
        checks++;
        if (!value) throw new AssertionError(description);
    }
    private static JsonObject external(String text) {
        JsonObject data = new JsonObject();
        JsonObject source = new JsonObject(); source.addProperty("id", "qq:group:1"); source.addProperty("name", "交流群");
        JsonObject sender = new JsonObject(); sender.addProperty("id", "qq:2"); sender.addProperty("name", "小明");
        data.add("source", source); data.add("sender", sender); data.addProperty("text", text); return data;
    }
    private static void invalid(Runnable action, String code) {
        try { action.run(); throw new AssertionError("Expected " + code); }
        catch (BridgeProtocol.Invalid e) { check(e.code.equals(code), "error code " + code); }
    }
    private static Path configFile(String content) throws IOException {
        Path path = Files.createTempFile("bridge-test-", ".toml"); Files.writeString(path, content); return path;
    }
    private static BridgeConfig config(String content) throws IOException {
        Path path = configFile(content);
        try { return BridgeConfig.load(path); } finally { Files.delete(path); }
    }
    private static void await(java.util.function.BooleanSupplier predicate) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (!predicate.getAsBoolean()) {
            if (System.nanoTime() > end) throw new AssertionError("Timed out");
            Thread.sleep(20);
        }
    }
    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        check(DeathText.render(net.minecraft.network.chat.Component.translatable("death.attack.mob", "Steve",
                net.minecraft.network.chat.Component.translatable("entity.minecraft.zombie"))).equals("Steve被僵尸杀死了"),
                "vanilla Chinese death message with translated attacker");
        check(DeathText.render(net.minecraft.network.chat.Component.translatable("death.fell.accident.generic", "Steve"))
                .equals("Steve从高处摔了下来"), "vanilla Chinese environmental death");
        check(DeathText.render(net.minecraft.network.chat.Component.translatable("death.attack.player.item", "Steve", "Alex",
                net.minecraft.network.chat.Component.literal("[霜刃]"))).equals("Steve被Alex用[霜刃]杀死了"),
                "vanilla Chinese death message preserves custom weapon name");
        check(BridgeProtocol.tokens("Steve @STEVE Steve你好 Steve123 mySteve Steve_").equals(
                Set.of("steve", "steve123", "mysteve", "steve_")), "full game IDs without @");
        check(BridgeProtocol.clean("§c红色\n&atext\u0000", false).equals("红色 &atext"), "literal text cleaning");
        try { BridgeProtocol.parse("{unquoted: true}"); throw new AssertionError("lenient JSON accepted"); }
        catch (RuntimeException expected) { checks++; }
        try { BridgeProtocol.parse("[".repeat(33) + "0" + "]".repeat(33)); throw new AssertionError("deep JSON accepted"); }
        catch (RuntimeException expected) { checks++; }
        BridgePreferencesData preferences = new BridgePreferencesData();
        UUID preferenceId = UUID.randomUUID();
        check(preferences.sound(preferenceId) && preferences.receive(preferenceId), "personal defaults enabled");
        preferences.set(preferenceId, true, false); preferences.set(preferenceId, false, false);
        BridgePreferencesData loaded = BridgePreferencesData.load(preferences.save(new CompoundTag(), null), null);
        check(!loaded.sound(preferenceId) && !loaded.receive(preferenceId), "preferences survive NBT round trip");
        loaded.set(preferenceId, true, true);
        check(loaded.sound(preferenceId) && !loaded.receive(preferenceId), "personal switches independent");
        JsonObject root = BridgeProtocol.message("rabbit-house", "chat.external", external("hello"));
        check(BridgeProtocol.envelope(root, "rabbit-house").type().equals("chat.external"), "valid envelope");
        invalid(() -> BridgeProtocol.envelope(root, "other"), "SERVER_ID_MISMATCH");
        root.addProperty("version", 2); invalid(() -> BridgeProtocol.envelope(root, "rabbit-house"), "UNSUPPORTED_VERSION");
        root.addProperty("version", "1"); invalid(() -> BridgeProtocol.envelope(root, "rabbit-house"), "INVALID_MESSAGE");
        JsonObject data = external("x"); data.addProperty("text", "😀".repeat(2001));
        invalid(() -> BridgeProtocol.external(data), "MESSAGE_TOO_LARGE");
        data.addProperty("text", "hello"); data.add("mentions", JsonParser.parseString("[\"Steve\", 1]"));
        invalid(() -> BridgeProtocol.external(data), "INVALID_MESSAGE");
        data.add("mentions", JsonParser.parseString("[\"Steve\", \"STEVE\"]"));
        check(BridgeProtocol.external(data).mentions().size() == 1, "mention deduplication");
        BridgeConfig defaults = config("");
        check(!defaults.flag("enabled"), "disabled by default");
        Path configDirectory = Files.createTempDirectory("bridge-config-");
        Path generated = configDirectory.resolve(BridgeConfig.FILE);
        try {
            BridgeConfig.createIfMissing(generated);
            check(!BridgeConfig.load(generated).flag("enabled"), "generated TOML round trip");
            Files.writeString(generated, "[bridge]\nserver_id='custom'\n");
            BridgeConfig.createIfMissing(generated);
            check(BridgeConfig.load(generated).text("server_id").equals("custom"), "existing config preserved");
        } finally { Files.deleteIfExists(generated); Files.delete(configDirectory); }
        UUID player = UUID.randomUUID();
        BridgeConfig white = config("[bridge.chat.game_to_external]\nfilter_mode='whitelist'\nplayer_names=['Steve']\n");
        check(white.allowsPlayer("chat.game_to_external", player, "STEVE"), "case insensitive whitelist");
        check(!white.allowsPlayer("chat.game_to_external", player, "Steve123"), "no substring whitelist");
        BridgeConfig empty = config("[bridge.chat.external_to_game]\nfilter_mode='whitelist'\n");
        check(!empty.allowsSender("qq:2"), "empty whitelist denies");
        BridgeConfig death = config("[bridge.death]\nfilter_mode='blacklist'\nplayer_uuids=['" + player + "']\nformat='[死亡] {reason}'\n");
        check(!death.allowsPlayer("death", player, "Steve") && death.allowsPlayer("presence", player, "Steve"),
                "death blacklist is independent of presence");
        try { config("[bridge.presence]\nfilter_mode='wrong'\n"); throw new AssertionError("invalid mode accepted"); }
        catch (IllegalArgumentException expected) { checks++; }
        try { config("[bridge]\nincoming_format='{invalid}'\n"); throw new AssertionError("invalid template accepted"); }
        catch (IllegalArgumentException expected) { checks++; }
        whitelistAdmin();
        sockets();
        filteringAndLimits();
        inboundSockets();
        System.out.println("Bridge regression checks passed: " + checks);
    }

    private static void inboundSockets() throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            port = reservation.getLocalPort();
        }
        BridgeConfig settings = config("""
                [bridge.inbound]
                enabled = true
                bind = "127.0.0.1"
                port = %d
                token = "inbound-test-token"
                """.formatted(port));
        URI address = URI.create("ws://127.0.0.1:" + port + "/rabbitcore");
        BlockingQueue<JsonObject> messages = new LinkedBlockingQueue<>();
        WebSocket.Listener receiver = new WebSocket.Listener() {
            private final StringBuilder frame = new StringBuilder();

            @Override public CompletionStage<?> onText(WebSocket socket, CharSequence text, boolean last) {
                frame.append(text);
                if (last) {
                    messages.add(BridgeProtocol.parse(frame.toString()));
                    frame.setLength(0);
                }
                socket.request(1);
                return null;
            }
        };
        try (InboundBridge bridge = new InboundBridge(settings); HttpClient http = HttpClient.newHttpClient()) {
            try {
                http.newWebSocketBuilder().buildAsync(address, receiver).get(5, TimeUnit.SECONDS);
                throw new AssertionError("Inbound socket accepted unauthenticated upgrade");
            } catch (ExecutionException e) {
                check(e.getCause() instanceof WebSocketHandshakeException failure
                        && failure.getResponse().statusCode() == 401, "Inbound upgrade requires token");
            }
            WebSocket socket = http.newWebSocketBuilder().header("Authorization", "Bearer inbound-test-token")
                    .buildAsync(address, receiver).get(5, TimeUnit.SECONDS);
            try {
                JsonObject subscription = new JsonObject();
                JsonArray events = new JsonArray();
                events.add("chat.game");
                subscription.add("events", events);
                sendInbound(socket, "hello", subscription);
                check(inboundResponse(messages).type().equals("ready"), "Inbound hello returns ready");
                check(bridge.subscribed("chat.game") && !bridge.subscribed("player.join"), "Inbound subscriptions are scoped");
                bridge.publish("player.join", new JsonObject());
                bridge.publish("chat.game", new JsonObject());
                check(inboundResponse(messages).type().equals("chat.game"), "Only subscribed event is sent");
                sendInbound(socket, "chat.external", external("hello"));
                InboundBridge.Chat chat = null;
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (chat == null && System.nanoTime() < deadline) {
                    chat = bridge.poll();
                    if (chat == null) Thread.sleep(10);
                }
                check(chat != null && chat.message().text().equals("hello"), "Inbound chat reaches game queue");
                chat.reply(null);
                check(inboundResponse(messages).type().equals("chat.result"), "Inbound chat acknowledgement");
            } finally {
                socket.abort();
            }
        }
    }

    private static void sendInbound(WebSocket socket, String type, JsonObject data) throws Exception {
        socket.sendText(BridgeProtocol.message("rabbit-house", type, data).toString(), true).get(5, TimeUnit.SECONDS);
    }

    private static BridgeProtocol.Envelope inboundResponse(BlockingQueue<JsonObject> messages) throws InterruptedException {
        JsonObject result = messages.poll(5, TimeUnit.SECONDS);
        if (result == null) throw new AssertionError("No inbound bridge response");
        return BridgeProtocol.envelope(result, "rabbit-house");
    }

    private static void whitelistAdmin() throws Exception {
        var add = BridgeAdmin.text(external("白名单 添加 Steve"), "白名单 添加 Steve");
        check(add.action().equals("add") && add.name().equals("Steve"), "group whitelist text parses strict action");
        invalid(() -> BridgeAdmin.text(external("x"), "白名单 添加 Steve extra"), "INVALID_MESSAGE");
        invalid(() -> BridgeAdmin.text(external("x"), "白名单 添加 Steve;op"), "INVALID_MESSAGE");
        check(!BridgeConfig.disabled().allowsWhitelistAdmin(add), "group administration disabled by default");
        check(BridgeAdmin.fresh(100000, 100000) && !BridgeAdmin.fresh(1, 100000), "administrative timestamp window");
        check(RabbitAuthIntegration.execute(add, System.currentTimeMillis() + 30000).toCompletableFuture().get()
                .get("code").equals("AUTH_UNAVAILABLE"), "RabbitCore operates without RabbitAuth installed");
        try (ServerSocket listener = new ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"))) {
            listener.setSoTimeout(10000);
            String settings = "[bridge]\nenabled=true\nexternal_to_game=false\nurl='ws://127.0.0.1:" + listener.getLocalPort()
                    + "/rabbitcore'\ntoken='test-token'\n[bridge.presence]\nenabled=false\n"
                    + "[bridge.admin.whitelist]\nenabled=true\nsource_ids=['qq:group:1']\nsender_ids=['qq:2']\n";
            BridgeConfig config = config(settings);
            check(config.allowsWhitelistAdmin(add), "explicit source and account IDs authorize action");
            check(!config.allowsWhitelistAdmin(new BridgeAdmin.Request("qq:group:1", "qq:other", "add", "Steve", true, 1)), "display names cannot replace administrator IDs");
            check(!config.allowsWhitelistAdmin(new BridgeAdmin.Request("qq:other", "qq:2", "add", "Steve", true, 1)), "administrator cannot operate outside configured groups");
            check(!config.allowsWhitelistAdmin(new BridgeAdmin.Request("qq:group:1", "qq:2", "on", "", true, 1)), "global switches are not granted by default");
            try (BridgeConnection bridge = new BridgeConnection(config, "test"); Peer peer = new Peer(listener.accept())) {
                JsonObject hello = peer.json(), ready = new JsonObject();
                ready.addProperty("reply_to", hello.get("id").getAsString());
                peer.send(BridgeProtocol.message("rabbit-house", "ready", ready).toString()); await(bridge::ready);
                JsonObject request = external(""); request.addProperty("action", "add"); request.addProperty("name", "Steve");
                request.getAsJsonObject("sender").addProperty("id", "qq:other");
                peer.send(BridgeProtocol.message("rabbit-house", "admin.whitelist", request).toString());
                check(peer.json().getAsJsonObject("data").get("code").getAsString().equals("FORBIDDEN") && bridge.pollAdmin() == null, "unauthorized wire request never reaches game");
                request.getAsJsonObject("sender").addProperty("id", "qq:2");
                JsonObject stale = BridgeProtocol.message("rabbit-house", "admin.whitelist", request);
                stale.addProperty("timestamp", System.currentTimeMillis() - 31000);
                peer.send(stale.toString());
                check(peer.json().getAsJsonObject("data").get("code").getAsString().equals("EXPIRED_REQUEST"), "expired wire request rejected");
                JsonObject valid = BridgeProtocol.message("rabbit-house", "admin.whitelist", request);
                peer.send(valid.toString());
                var captured = new java.util.concurrent.atomic.AtomicReference<BridgeConnection.AdminIncoming>();
                await(() -> { var value = bridge.pollAdmin(); if (value != null) captured.set(value); return captured.get() != null; });
                check(captured.get().request().name().equals("Steve") && bridge.poll() == null, "admin queue independent of chat forwarding and filters");
                bridge.adminResult(captured.get(), Map.of("ok", true, "code", "OK", "message", "done"));
                JsonObject result = peer.json();
                check(result.get("type").getAsString().equals("admin.whitelist.result")
                        && result.getAsJsonObject("data").get("reply_to").equals(valid.get("id"))
                        && result.getAsJsonObject("data").get("source_id").getAsString().equals("qq:group:1"),
                        "result correlated and directed to original group despite presence disabled");
                peer.send(valid.toString());
                check(peer.json().getAsJsonObject("data").get("code").getAsString().equals("DUPLICATE_REQUEST")
                        && bridge.pollAdmin() == null, "duplicate admin request never executes twice");
                peer.send(BridgeProtocol.message("rabbit-house", "chat.external", external("白名单 暂停 Steve")).toString());
                captured.set(null);
                await(() -> { var value = bridge.pollAdmin(); if (value != null) captured.set(value); return captured.get() != null; });
                check(captured.get().request().action().equals("set") && !captured.get().request().enabled() && bridge.poll() == null,
                        "legacy chat forwarding supports strict whitelist state command without broadcast");
                bridge.configure(config(settings.replace("sender_ids=['qq:2']", "sender_ids=[]"))); await(bridge::ready);
                long filtered = bridge.filtered.sum();
                bridge.adminResult(captured.get(), Map.of("ok", true, "code", "OK", "message", "private result"));
                await(() -> bridge.filtered.sum() > filtered);
                check(true, "authorization revocation suppresses queued sensitive success replies");
                bridge.configure(config(settings.replace("[bridge.admin.whitelist]\nenabled=true", "[bridge.admin.whitelist]\nenabled=false")));
                try (Peer disabledPeer = new Peer(listener.accept())) {
                    JsonObject disabledHello = disabledPeer.json();
                    ready.addProperty("reply_to", disabledHello.get("id").getAsString());
                    disabledPeer.send(BridgeProtocol.message("rabbit-house", "ready", ready).toString()); await(bridge::ready);
                    // Chat forwarding is explicitly enabled for the compatibility check.
                    bridge.configure(config(settings.replace("external_to_game=false", "external_to_game=true")
                            .replace("[bridge.admin.whitelist]\nenabled=true", "[bridge.admin.whitelist]\nenabled=false"))); await(bridge::ready);
                    disabledPeer.send(BridgeProtocol.message("rabbit-house", "chat.external", external("白名单 查询 Steve")).toString());
                    await(() -> bridge.received.sum() == 1);
                    check(bridge.poll() != null && bridge.pollAdmin() == null, "disabled administration preserves legacy chat routing");
                }
            }
        }
    }

    private static void sockets() throws Exception {
        try (ServerSocket listener = new ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"))) {
            listener.setSoTimeout(10000);
            BridgeConfig enabled = config("[bridge]\nenabled=true\nurl='ws://127.0.0.1:" + listener.getLocalPort()
                    + "/rabbitcore'\ntoken='test-token'\n");
            try (BridgeConnection bridge = new BridgeConnection(enabled, "test"); Peer peer = new Peer(listener.accept())) {
                JsonObject hello = peer.json(); check(hello.get("type").getAsString().equals("hello"), "hello first");
                check(peer.headers.contains("Authorization: Bearer test-token"), "request authentication");
                peer.send(BridgeProtocol.message("rabbit-house", "chat.external", external("before ready")).toString());
                check(peer.json().getAsJsonObject("data").get("code").getAsString().equals("NOT_READY"), "chat before handshake rejected");
                JsonObject ready = new JsonObject(); ready.addProperty("reply_to", hello.get("id").getAsString());
                peer.send(BridgeProtocol.message("rabbit-house", "ready", ready).toString());
                await(bridge::ready); check(bridge.ready(), "ready handshake");
                JsonObject chat = BridgeProtocol.message("rabbit-house", "chat.external", external("Steve你好"));
                String text = chat.toString(); int split = text.indexOf("Steve");
                peer.frame(1, false, text.substring(0, split).getBytes(StandardCharsets.UTF_8));
                peer.frame(0, true, text.substring(split).getBytes(StandardCharsets.UTF_8));
                await(() -> bridge.received.sum() == 1);
                BridgeConnection.Incoming incoming = bridge.poll();
                check(incoming != null && incoming.message().text().equals("Steve你好"), "fragment reassembly");
                peer.send(text);
                JsonObject second = BridgeProtocol.message("rabbit-house", "chat.external", external("next"));
                peer.send(second.toString()); await(() -> bridge.received.sum() == 2);
                check(bridge.poll() != null && bridge.poll() == null, "duplicate not broadcast");
                UUID player = UUID.randomUUID();
                bridge.send("chat.game", player, "Steve", external("outgoing"));
                check(peer.json().get("type").getAsString().equals("chat.game"), "outbound chat");
                await(() -> bridge.sent.sum() == 1);
                bridge.sendInboundChat(BridgeProtocol.external(external("inbound relay")));
                JsonObject relayed = peer.json();
                JsonObject relayData = relayed.getAsJsonObject("data");
                check(relayed.get("type").getAsString().equals("chat.game")
                                && relayData.get("text").getAsString().equals("inbound relay")
                                && relayData.getAsJsonObject("sender").get("id").getAsString().equals("qq:2")
                                && relayData.getAsJsonObject("source").get("name").getAsString().equals("交流群"),
                        "inbound relay uses outbound player chat event with external identity");
                JsonObject death = new JsonObject();
                death.addProperty("reason", "Steve fell from a high place");
                death.addProperty("text", "[死亡] Steve fell from a high place");
                bridge.send("player.death", player, "Steve", death);
                JsonObject deathMessage = peer.json();
                check(deathMessage.get("type").getAsString().equals("player.death")
                        && deathMessage.getAsJsonObject("data").get("reason").getAsString().contains("fell"),
                        "death uses independent event with plain reason");
                Thread.sleep(1050); // Error responses are deliberately limited to one per second.
                peer.send(BridgeProtocol.message("rabbit-house", "ready", ready).toString());
                check(peer.json().getAsJsonObject("data").get("code").getAsString().equals("INVALID_MESSAGE"), "duplicate ready rejected");
                bridge.reconnect();
                try (Peer replacement = new Peer(listener.accept())) {
                    JsonObject newHello = replacement.json();
                    ready.addProperty("reply_to", newHello.get("id").getAsString());
                    replacement.send(BridgeProtocol.message("rabbit-house", "ready", ready).toString()); await(bridge::ready);
                    check(!bridge.current(incoming.generation()), "old generation rejected");
                    replacement.send(text);
                    replacement.send(BridgeProtocol.message("rabbit-house", "chat.external", external("after reconnect")).toString());
                    await(() -> bridge.received.sum() == 3);
                    check(bridge.poll() != null && bridge.poll() == null, "dedup survives reconnect");
                    replacement.frame(2, true, new byte[]{1});
                    Frame close = replacement.read(); check(close.opcode == 8 && close.payload.length >= 2
                            && (((close.payload[0] & 255) << 8) | (close.payload[1] & 255)) == 4003, "binary close code");
                }
            }
        }
    }
    private static void filteringAndLimits() throws Exception {
        try (ServerSocket listener = new ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"))) {
            listener.setSoTimeout(10000);
            String base = "[bridge]\nenabled=true\nurl='ws://127.0.0.1:" + listener.getLocalPort() + "/rabbitcore'\ntoken='test-token'\n";
            BridgeConfig limited = config(base + "[bridge.limits]\nincoming_messages_per_second=1\nincoming_burst=2\nbroadcast_queue_capacity=1\n");
            try (BridgeConnection bridge = new BridgeConnection(limited, "test"); Peer peer = new Peer(listener.accept())) {
                JsonObject hello = peer.json(), ready = new JsonObject(); ready.addProperty("reply_to", hello.get("id").getAsString());
                peer.send(BridgeProtocol.message("rabbit-house", "ready", ready).toString()); await(bridge::ready);
                for (int n = 0; n < 8; n++) peer.send(BridgeProtocol.message("rabbit-house", "chat.external", external("burst")).toString());
                await(() -> bridge.received.sum() == 8);
                check(bridge.queueDropped.sum() > 0 && bridge.rateDropped.sum() > 0, "bounded queue and rate limiting");
                check(bridge.poll() != null && bridge.poll() == null, "broadcast queue bounded");
                BridgeConfig blocked = config(base + "[bridge.chat.external_to_game]\nfilter_mode='whitelist'\n");
                bridge.configure(blocked); await(bridge::ready);
                Thread.sleep(1100);
                peer.send(BridgeProtocol.message("rabbit-house", "chat.external", external("blocked")).toString());
                await(() -> bridge.filtered.sum() == 1);
                check(bridge.poll() == null, "sender whitelist filters before broadcast");
                peer.send("x".repeat(32769));
                Frame close = peer.read(); check(close.opcode == 8 && (((close.payload[0] & 255) << 8)
                        | (close.payload[1] & 255)) == 4009, "oversized message closes connection");
            }
        }
    }
    private record Frame(int opcode, byte[] payload) {}
    private static final class Peer implements AutoCloseable {
        final Socket socket;
        final InputStream input;
        final OutputStream output;
        final String headers;
        Peer(Socket socket) throws Exception {
            this.socket = socket; socket.setSoTimeout(10000); input = socket.getInputStream(); output = socket.getOutputStream();
            ByteArrayOutputStream header = new ByteArrayOutputStream();
            while (true) {
                int value = input.read(); if (value < 0) throw new EOFException(); header.write(value);
                String text = header.toString(StandardCharsets.US_ASCII);
                if (text.endsWith("\r\n\r\n")) { headers = text; break; }
            }
            String key = Arrays.stream(headers.split("\r\n")).filter(s -> s.toLowerCase(Locale.ROOT).startsWith("sec-websocket-key:"))
                    .findFirst().orElseThrow().split(":", 2)[1].strip();
            String accept = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1")
                    .digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.US_ASCII)));
            output.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: "
                    + accept + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII)); output.flush();
        }
        void send(String text) throws IOException { frame(1, true, text.getBytes(StandardCharsets.UTF_8)); }
        void frame(int opcode, boolean last, byte[] payload) throws IOException {
            output.write((last ? 128 : 0) | opcode);
            if (payload.length < 126) output.write(payload.length);
            else { output.write(126); output.write(payload.length >> 8); output.write(payload.length & 255); }
            output.write(payload); output.flush();
        }
        Frame read() throws IOException {
            int first = input.read(), second = input.read(); if (first < 0 || second < 0) throw new EOFException();
            int length = second & 127;
            if (length == 126) length = (input.read() << 8) | input.read();
            if (length == 127) throw new IOException("Unexpected large frame");
            byte[] mask = (second & 128) != 0 ? input.readNBytes(4) : null;
            byte[] payload = input.readNBytes(length); if (payload.length != length) throw new EOFException();
            if (mask != null) for (int i = 0; i < payload.length; i++) payload[i] ^= mask[i % 4];
            if ((first & 15) == 9) { frame(10, true, payload); return read(); }
            return new Frame(first & 15, payload);
        }
        JsonObject json() throws IOException {
            Frame frame = read(); check(frame.opcode == 1, "text response");
            return JsonParser.parseString(new String(frame.payload, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        @Override public void close() throws IOException { socket.close(); }
    }
}
