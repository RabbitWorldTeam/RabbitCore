package com.tr4nce.rabbitcore.bridge;

import com.google.gson.JsonObject;
import com.tr4nce.rabbitcore.world.storage.BridgePreferencesData;
import net.minecraft.network.chat.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.*;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.LoggerFactory;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/** Game-side operations are exclusively called on the server thread. */
public final class BridgeService implements AutoCloseable {
    private final MinecraftServer server;
    private final Path path;
    private BridgeConfig config = BridgeConfig.disabled();
    private final BridgeConnection connection;
    private final Map<UUID, Long> lastSound = new HashMap<>();
    private final BridgePreferencesData preferences;
    private SoundEvent sound;
    private String configurationError;
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(source|sender|message)}");

    public BridgeService(MinecraftServer server, String modVersion) {
        this.server = server;
        path = server.getWorldPath(LevelResource.ROOT).resolve("serverconfig").resolve(BridgeConfig.FILE);
        preferences = BridgePreferencesData.get(server);
        try { BridgeConfig.createIfMissing(path); config = BridgeConfig.load(path); }
        catch (Exception e) { configurationError = e instanceof IllegalArgumentException ? e.getMessage() : "无法读取配置文件";
            LoggerFactory.getLogger("RabbitCore-Bridge").warn("Bridge disabled: {}", configurationError); }
        resolveSound();
        connection = new BridgeConnection(config, modVersion);
    }
    public String reload() {
        try {
            BridgeConfig next = BridgeConfig.load(path);
            config = next; configurationError = null; resolveSound(); connection.configure(next);
            return "桥接配置已重载";
        } catch (Exception e) {
            return "重载失败，已保留旧配置：" + (e instanceof IllegalArgumentException ? e.getMessage() : "无法读取配置文件");
        }
    }
    private void resolveSound() {
        sound = ForgeRegistries.SOUND_EVENTS.getValue(ResourceLocation.tryParse(config.text("mentions.sound")));
        if (sound == null) {
            LoggerFactory.getLogger("RabbitCore-Bridge").warn("Unknown bridge sound; using default");
            sound = ForgeRegistries.SOUND_EVENTS.getValue(ResourceLocation.tryParse("minecraft:block.note_block.pling"));
        }
    }
    public String status() { return connection.status() + (configurationError == null ? "" : " | 配置错误=" + configurationError); }
    public String reconnect() {
        if (!config.flag("enabled")) return "桥接当前禁用";
        connection.reconnect(); return "已请求重新连接";
    }
    public void gameChat(ServerPlayer player, String original) {
        if (!connection.ready() || !config.flag("game_to_external")) return;
        if (!config.allowsPlayer("chat.game_to_external", player.getUUID(), player.getGameProfile().getName())) {
            connection.filtered.increment(); return;
        }
        if (original.codePointCount(0, original.length()) > 2000) { connection.queueDropped.increment(); return; }
        String text = BridgeProtocol.clean(original, false).strip();
        if (text.isEmpty()) return;
        JsonObject data = new JsonObject(); data.add("sender", player(player)); data.addProperty("text", text);
        connection.send("chat.game", player.getUUID(), player.getGameProfile().getName(), data);
    }
    public void presence(ServerPlayer player, boolean join) {
        if (!join) lastSound.remove(player.getUUID());
        if (!connection.ready() || !config.flag("presence.enabled")
                || !config.flag(join ? "presence.join_enabled" : "presence.leave_enabled")) return;
        if (!config.allowsPlayer("presence", player.getUUID(), player.getGameProfile().getName())) {
            connection.filtered.increment(); return;
        }
        JsonObject data = new JsonObject(); data.add("player", player(player));
        data.addProperty("text", config.text(join ? "presence.join_format" : "presence.leave_format")
                .replace("{player}", player.getGameProfile().getName()));
        connection.send(join ? "player.join" : "player.leave", player.getUUID(), player.getGameProfile().getName(), data);
    }
    private static JsonObject player(ServerPlayer player) {
        JsonObject value = new JsonObject(); value.addProperty("id", player.getUUID().toString());
        value.addProperty("name", player.getGameProfile().getName()); return value;
    }
    public void tick() {
        // A per-tick budget prevents large configured queues monopolizing a tick.
        for (int n = 0; n < 20; n++) {
            BridgeConnection.Incoming incoming = connection.poll();
            if (incoming == null) return;
            BridgeProtocol.External message = incoming.message();
            if (!connection.current(incoming.generation()) || !config.flag("external_to_game")
                    || !config.allowsSender(message.senderId())) { connection.filtered.increment(); continue; }
            Set<String> online = new HashSet<>();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) online.add(player.getGameProfile().getName().toLowerCase(Locale.ROOT));
            Set<String> mentions = new HashSet<>(message.mentions()); mentions.addAll(BridgeProtocol.tokens(message.text()));
            Component formatted = format(message, online);
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (!preferences.receive(player.getUUID())) continue;
                player.sendSystemMessage(formatted);
                if (mentions.contains(player.getGameProfile().getName().toLowerCase(Locale.ROOT))) playSound(player);
            }
        }
    }
    private Component format(BridgeProtocol.External message, Set<String> online) {
        MutableComponent output = Component.empty();
        String template = config.text("incoming_format");
        var matcher = PLACEHOLDER.matcher(template);
        int end = 0;
        while (matcher.find()) {
            output.append(Component.literal(template.substring(end, matcher.start())).withStyle(config.color("separator")));
            String field = matcher.group(1);
            if (field.equals("message")) output.append(body(message.text(), online));
            else output.append(Component.literal(field.equals("source") ? message.source() : message.sender()).withStyle(config.color(field)));
            end = matcher.end();
        }
        return output.append(Component.literal(template.substring(end)).withStyle(config.color("separator")));
    }
    private Component body(String text, Set<String> online) {
        MutableComponent output = Component.empty();
        if (!config.flag("mentions.enabled")) return Component.literal(text).withStyle(config.color("message"));
        var matcher = BridgeProtocol.PLAYER_TOKEN.matcher(text);
        int end = 0;
        while (matcher.find()) {
            if (!online.contains(matcher.group().toLowerCase(Locale.ROOT))) continue;
            int start = matcher.start();
            if (start > end && text.charAt(start - 1) == '@') start--;
            output.append(Component.literal(text.substring(end, start)).withStyle(config.color("message")));
            output.append(Component.literal(text.substring(start, matcher.end())).withStyle(config.color("mention")).withStyle(net.minecraft.ChatFormatting.BOLD));
            end = matcher.end();
        }
        return output.append(Component.literal(text.substring(end)).withStyle(config.color("message")));
    }
    private String playSound(ServerPlayer player) {
        if (!config.flag("mentions.enabled") || !config.flag("mentions.sound_enabled")) return "服务端当前禁用提示音";
        if (!preferences.sound(player.getUUID())) return "个人提示音已关闭";
        long now = System.nanoTime();
        Long previous = lastSound.get(player.getUUID());
        if (previous != null && now - previous < TimeUnit.SECONDS.toNanos(config.number("mentions.cooldown_seconds"))) return "提示音冷却中";
        if (sound == null) return "提示音资源不可用";
        player.playNotifySound(sound, SoundSource.PLAYERS, (float) config.decimal("mentions.volume"), (float) config.decimal("mentions.pitch"));
        lastSound.put(player.getUUID(), now); return "已播放提示音";
    }
    public String preference(ServerPlayer player, boolean soundSetting, Boolean enabled) {
        if (enabled != null) preferences.set(player.getUUID(), soundSetting, enabled);
        boolean value = soundSetting ? preferences.sound(player.getUUID()) : preferences.receive(player.getUUID());
        String result = (soundSetting ? "个人提示音" : "个人群聊接收") + (value ? "已开启" : "已关闭");
        if (soundSetting && value && (!config.flag("mentions.enabled") || !config.flag("mentions.sound_enabled")))
            result += "，服务端当前禁用提示音";
        else if (soundSetting && value && (!preferences.receive(player.getUUID()) || !connection.ready() || !config.flag("external_to_game")))
            result += "，当前未接收外部聊天，正常提及提示暂不生效";
        return result;
    }
    public String testSound(ServerPlayer player) { return playSound(player); }
    @Override public void close() { connection.close(); lastSound.clear(); }
}
