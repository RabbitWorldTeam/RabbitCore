package com.tr4nce.rabbitcore.bridge;

import com.google.gson.JsonObject;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.util.concurrent.GlobalEventExecutor;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Plain WebSocket endpoint for authenticated event subscriptions and chat. */
final class InboundBridge implements AutoCloseable {
    static final String PATH = "/rabbitcore";
    private static final int MAX_CONNECTIONS = 16;
    private static final Set<String> EVENTS = Set.of("chat.game", "player.join", "player.leave", "player.death");

    private final BridgeConfig config;
    private final String serverId;
    private final byte[] authorization;
    private final EventLoopGroup loops = new NioEventLoopGroup(1);
    private final DefaultChannelGroup channels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE, true);
    private final Set<Session> sessions = ConcurrentHashMap.newKeySet();
    private final ArrayBlockingQueue<Chat> incoming;
    private final Channel listener;

    InboundBridge(BridgeConfig config) {
        this.config = config;
        serverId = config.text("server_id");
        authorization = ("Bearer " + config.text("inbound.token")).getBytes(StandardCharsets.UTF_8);
        incoming = new ArrayBlockingQueue<>(config.number("limits.broadcast_queue_capacity"));
        try {
            listener = new ServerBootstrap().group(loops)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override protected void initChannel(SocketChannel channel) {
                            channels.add(channel);
                            channel.pipeline().addLast(
                                    new HttpServerCodec(4096, 8192, 8192),
                                    new HttpObjectAggregator(8192),
                                    new UpgradeAuthorization(),
                                    new WebSocketServerProtocolHandler(PATH, null, false, BridgeProtocol.MAX_BYTES),
                                    new WebSocketFrameAggregator(BridgeProtocol.MAX_BYTES),
                                    new Session());
                        }
                    }).bind(new InetSocketAddress(config.text("inbound.bind"), config.number("inbound.port")))
                    .syncUninterruptibly().channel();
            channels.add(listener);
        } catch (Exception e) {
            close();
            throw new IllegalStateException("Unable to bind inbound WebSocket", e);
        }
    }

    boolean active() { return listener.isActive(); }
    boolean subscribed(String event) {
        return sessions.stream().anyMatch(session -> session.subscriptions.contains(event));
    }
    void publish(String event, JsonObject data) {
        String text = BridgeProtocol.message(serverId, event, data).toString();
        for (Session session : sessions) {
            if (session.subscriptions.contains(event)) session.send(text);
        }
    }

    Chat poll() { return incoming.poll(); }

    final class Chat {
        private final Session session;
        private final UUID id;
        private final BridgeProtocol.External message;

        Chat(Session session, UUID id, BridgeProtocol.External message) {
            this.session = session;
            this.id = id;
            this.message = message;
        }

        BridgeProtocol.External message() { return message; }
        boolean connected() { return session.channel.isActive(); }
        void reply(String code) { session.reply("chat.result", id, code); }
    }

    private final class UpgradeAuthorization extends SimpleChannelInboundHandler<FullHttpRequest> {
        @Override protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
            HttpResponseStatus status = null;
            String supplied = request.headers().get(HttpHeaderNames.AUTHORIZATION, "");
            if (!request.decoderResult().isSuccess() || !request.method().equals(HttpMethod.GET)) {
                status = HttpResponseStatus.BAD_REQUEST;
            } else if (!PATH.equals(request.uri())) {
                status = HttpResponseStatus.NOT_FOUND;
            } else if (!MessageDigest.isEqual(authorization, supplied.getBytes(StandardCharsets.UTF_8))) {
                status = HttpResponseStatus.UNAUTHORIZED;
            }
            if (status == null) {
                ctx.fireChannelRead(request.retain());
            } else {
                ctx.writeAndFlush(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status))
                        .addListener(ChannelFutureListener.CLOSE);
            }
        }
    }

    private final class Session extends SimpleChannelInboundHandler<WebSocketFrame> {
        private final AtomicInteger pendingWrites = new AtomicInteger();
        private volatile Set<String> subscriptions = Set.of();
        private volatile Channel channel;
        private boolean ready;
        private double tokens = config.number("limits.incoming_burst");
        private long refillAt = System.nanoTime();

        @Override public void channelActive(ChannelHandlerContext ctx) {
            channel = ctx.channel();
            if (sessions.size() >= MAX_CONNECTIONS) {
                ctx.close();
                return;
            }
            sessions.add(this);
            ctx.executor().schedule(() -> {
                if (!ready) ctx.close();
            }, 10, TimeUnit.SECONDS);
            ctx.fireChannelActive();
        }

        @Override public void channelInactive(ChannelHandlerContext ctx) {
            sessions.remove(this);
            ctx.fireChannelInactive();
        }

        @Override protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) {
            if (!(frame instanceof TextWebSocketFrame text)) {
                ctx.close();
                return;
            }
            UUID id = null;
            try {
                var envelope = BridgeProtocol.envelope(BridgeProtocol.parse(text.text()), serverId);
                id = envelope.id();
                if (!ready) {
                    if (!envelope.type().equals("hello")) throw new BridgeProtocol.Invalid("HELLO_REQUIRED", "type");
                    subscribe(envelope.data());
                    ready = true;
                    JsonObject data = new JsonObject();
                    data.addProperty("reply_to", id.toString());
                    data.add("capabilities", BridgeProtocol.GSON.toJsonTree(Set.of("subscribe", "chat.external")));
                    send(BridgeProtocol.message(serverId, "ready", data).toString());
                } else if (!takeToken()) {
                    reply("error", id, "RATE_LIMITED");
                } else if (envelope.type().equals("subscribe")) {
                    subscribe(envelope.data());
                    reply("subscribe.result", id, null);
                } else if (envelope.type().equals("chat.external")) {
                    var chat = BridgeProtocol.external(envelope.data());
                    if (chat.text().isEmpty()) throw new BridgeProtocol.Invalid("INVALID_MESSAGE", "text");
                    if (!incoming.offer(new Chat(this, id, chat))) reply("chat.result", id, "QUEUE_FULL");
                } else {
                    reply("error", id, "UNSUPPORTED_TYPE");
                }
            } catch (BridgeProtocol.Invalid e) {
                reply("error", id, e.code);
            } catch (RuntimeException e) {
                reply("error", id, "INVALID_MESSAGE");
            }
        }

        private void subscribe(JsonObject data) {
            var values = data.get("events");
            if (values == null || !values.isJsonArray() || values.getAsJsonArray().size() > EVENTS.size()) {
                throw new BridgeProtocol.Invalid("INVALID_MESSAGE", "events");
            }
            Set<String> events = new HashSet<>();
            for (var event : values.getAsJsonArray()) {
                if (!event.isJsonPrimitive() || !event.getAsJsonPrimitive().isString()
                        || !EVENTS.contains(event.getAsString())) {
                    throw new BridgeProtocol.Invalid("INVALID_MESSAGE", "events");
                }
                events.add(event.getAsString());
            }
            subscriptions = Set.copyOf(events);
        }

        private boolean takeToken() {
            long now = System.nanoTime();
            tokens = Math.min(config.number("limits.incoming_burst"), tokens
                    + (now - refillAt) / 1_000_000_000.0 * config.number("limits.incoming_messages_per_second"));
            refillAt = now;
            if (tokens < 1) return false;
            tokens--;
            return true;
        }

        private void reply(String type, UUID id, String code) {
            JsonObject data = new JsonObject();
            if (id != null) data.addProperty("reply_to", id.toString());
            data.addProperty("ok", code == null);
            if (code != null) data.addProperty("code", code);
            send(BridgeProtocol.message(serverId, type, data).toString());
        }

        private void send(String text) {
            if (!channel.isActive()) return;
            int limit = config.number("limits.outgoing_queue_capacity");
            int pending = pendingWrites.incrementAndGet();
            if (pending > limit || !channel.isWritable()) {
                pendingWrites.decrementAndGet();
                channel.close();
                return;
            }
            channel.writeAndFlush(new TextWebSocketFrame(text)).addListener(result -> {
                pendingWrites.decrementAndGet();
                if (!result.isSuccess()) channel.close();
            });
        }

        @Override public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) { ctx.close(); }
    }

    @Override public void close() {
        channels.close();
        loops.shutdownGracefully(0, 2, TimeUnit.SECONDS);
    }
}
