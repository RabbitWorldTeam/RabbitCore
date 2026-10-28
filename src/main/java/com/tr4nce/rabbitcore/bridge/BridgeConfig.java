package com.tr4nce.rabbitcore.bridge;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.toml.TomlParser;
import com.electronwill.nightconfig.toml.TomlWriter;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.regex.Pattern;

/** Immutable validated configuration; malformed reloads never replace the active snapshot. */
public final class BridgeConfig {
    public static final String FILE = "rabbitcore-bridge-server.toml";
    private static final Map<String, Object> DEFAULTS = new LinkedHashMap<>();
    static {
        DEFAULTS.put("enabled", false);
        DEFAULTS.put("url", "ws://127.0.0.1:6700/rabbitcore");
        DEFAULTS.put("token", "");
        DEFAULTS.put("server_id", "rabbit-house");
        DEFAULTS.put("game_to_external", true);
        DEFAULTS.put("external_to_game", true);
        DEFAULTS.put("incoming_format", "[{source}] {sender}：{message}");
        DEFAULTS.put("limits.incoming_messages_per_second", 10);
        DEFAULTS.put("limits.incoming_burst", 20);
        DEFAULTS.put("limits.outgoing_queue_capacity", 256);
        DEFAULTS.put("limits.broadcast_queue_capacity", 256);
        for (String[] pair : new String[][]{{"source", "gold"}, {"sender", "aqua"},
                {"separator", "gray"}, {"message", "white"}, {"mention", "yellow"}})
            DEFAULTS.put("colors." + pair[0], pair[1]);
        for (String prefix : List.of("chat.game_to_external", "chat.external_to_game", "presence")) {
            DEFAULTS.put(prefix + ".filter_mode", "all");
            if (prefix.equals("chat.external_to_game")) DEFAULTS.put(prefix + ".sender_ids", List.of());
            else {
                DEFAULTS.put(prefix + ".player_uuids", List.of());
                DEFAULTS.put(prefix + ".player_names", List.of());
            }
        }
        DEFAULTS.put("mentions.enabled", true);
        DEFAULTS.put("mentions.sound_enabled", true);
        DEFAULTS.put("mentions.sound", "minecraft:block.note_block.pling");
        DEFAULTS.put("mentions.volume", 1.0);
        DEFAULTS.put("mentions.pitch", 1.0);
        DEFAULTS.put("mentions.cooldown_seconds", 3);
        DEFAULTS.put("presence.enabled", true);
        DEFAULTS.put("presence.join_enabled", true);
        DEFAULTS.put("presence.leave_enabled", true);
        DEFAULTS.put("presence.join_format", "{player}进入服务器");
        DEFAULTS.put("presence.leave_format", "{player}退出服务器");
    }

    private final Map<String, Object> values;
    private BridgeConfig(Map<String, Object> values) { this.values = Map.copyOf(values); }
    public static BridgeConfig disabled() { return new BridgeConfig(DEFAULTS); }
    public boolean flag(String key) { return (Boolean) values.get(key); }
    public String text(String key) { return (String) values.get(key); }
    public int number(String key) { return ((Number) values.get(key)).intValue(); }
    public double decimal(String key) { return ((Number) values.get(key)).doubleValue(); }
    @SuppressWarnings("unchecked")
    private List<String> list(String key) { return (List<String>) values.get(key); }
    public ChatFormatting color(String key) { return ChatFormatting.getByName(text("colors." + key)); }

    /** Keep secrets out of Forge SERVER configs, which are synchronized to clients. */
    public static void createIfMissing(Path path) throws IOException {
        if (Files.exists(path)) return;
        Files.createDirectories(path.getParent());
        Config defaults = Config.inMemory();
        DEFAULTS.forEach((key, value) -> defaults.set("bridge." + key, value));
        try (Writer writer = Files.newBufferedWriter(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            new TomlWriter().write(defaults, writer);
        }
    }

    public static BridgeConfig load(Path path) throws IOException {
        Config parsed;
        try (Reader reader = Files.newBufferedReader(path)) { parsed = new TomlParser().parse(reader); }
        catch (RuntimeException e) { throw new IllegalArgumentException("配置 TOML 无法解析"); }
        Map<String, Object> result = new LinkedHashMap<>();
        DEFAULTS.forEach((key, fallback) -> {
            Object value = parsed.get("bridge." + key);
            if (value == null) value = fallback;
            boolean valid = fallback instanceof Boolean ? value instanceof Boolean
                    : fallback instanceof String ? value instanceof String
                    : fallback instanceof Number ? value instanceof Number
                    : value instanceof List<?> items && items.stream().allMatch(String.class::isInstance);
            require(valid, key);
            result.put(key, value instanceof List<?> items ? List.copyOf(items) : value);
        });
        BridgeConfig config = new BridgeConfig(result);
        require(config.text("server_id").matches("[A-Za-z0-9_-]{1,64}"), "server_id");
        if (config.flag("enabled")) {
            URI uri;
            try { uri = URI.create(config.text("url")); }
            catch (RuntimeException e) { throw new IllegalArgumentException("无效配置字段: url"); }
            require(Set.of("ws", "wss").contains(uri.getScheme()) && uri.getHost() != null
                    && uri.getRawUserInfo() == null && uri.getRawFragment() == null, "url");
            require(!config.text("token").isBlank() && config.text("token").chars()
                    .allMatch(c -> c >= 33 && c <= 126), "token");
        }
        for (String key : List.of("source", "sender", "separator", "message", "mention")) {
            ChatFormatting color = config.color(key);
            require(color != null && color.isColor(), "colors." + key);
        }
        require(ResourceLocation.tryParse(config.text("mentions.sound")) != null, "mentions.sound");
        config.range("mentions.volume", 0, 1, false);
        config.range("mentions.pitch", .5, 2, false);
        config.range("mentions.cooldown_seconds", 0, 3600, true);
        config.range("limits.incoming_messages_per_second", 1, 1000, true);
        config.range("limits.incoming_burst", 1, 1000, true);
        config.range("limits.outgoing_queue_capacity", 1, 4096, true);
        config.range("limits.broadcast_queue_capacity", 1, 4096, true);
        for (String prefix : List.of("chat.game_to_external", "chat.external_to_game", "presence")) {
            require(Set.of("all", "blacklist", "whitelist").contains(config.text(prefix + ".filter_mode")), prefix + ".filter_mode");
            if (prefix.equals("chat.external_to_game")) {
                for (String id : config.list(prefix + ".sender_ids"))
                    require(!id.isBlank() && id.codePointCount(0, id.length()) <= 128, prefix + ".sender_ids");
            } else {
                for (String id : config.list(prefix + ".player_uuids")) {
                    try { BridgeProtocol.uuid(id); }
                    catch (RuntimeException e) { throw new IllegalArgumentException("无效配置字段: " + prefix + ".player_uuids"); }
                }
                for (String name : config.list(prefix + ".player_names"))
                    require(name.matches("[A-Za-z0-9_]{1,16}"), prefix + ".player_names");
            }
        }
        template(config.text("incoming_format"), Set.of("source", "sender", "message"));
        template(config.text("presence.join_format"), Set.of("player"));
        template(config.text("presence.leave_format"), Set.of("player"));
        return config;
    }

    private void range(String key, double min, double max, boolean integer) {
        double n = decimal(key);
        require(Double.isFinite(n) && n >= min && n <= max && (!integer || n == Math.rint(n)), key);
    }
    private static void require(boolean condition, String key) {
        if (!condition) throw new IllegalArgumentException("无效配置字段: " + key);
    }
    private static void template(String format, Set<String> allowed) {
        require(format.length() <= 512, "template");
        var matcher = Pattern.compile("\\{([^{}]*)}").matcher(format);
        while (matcher.find()) require(allowed.contains(matcher.group(1)), "template placeholder");
        require(!format.replaceAll("\\{[^{}]*}", "").matches(".*[{}].*"), "template braces");
    }
    private boolean allow(String prefix, boolean match) {
        return switch (text(prefix + ".filter_mode")) {
            case "all" -> true;
            case "blacklist" -> !match;
            case "whitelist" -> match;
            default -> false;
        };
    }
    public boolean allowsPlayer(String prefix, UUID id, String name) {
        return allow(prefix, list(prefix + ".player_uuids").stream().anyMatch(s -> UUID.fromString(s).equals(id))
                || list(prefix + ".player_names").stream().anyMatch(name::equalsIgnoreCase));
    }
    public boolean allowsSender(String id) {
        return allow("chat.external_to_game", list("chat.external_to_game.sender_ids").contains(id));
    }
}
