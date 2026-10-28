package com.tr4nce.rabbitcore.bridge;

import com.google.gson.*;
import com.google.gson.internal.Streams;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.StringReader;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

/** Wire validation and text handling, independent of game state. */
public final class BridgeProtocol {
    public static final int MAX_BYTES = 32768;
    public static final Pattern PLAYER_TOKEN = Pattern.compile("[A-Za-z0-9_]+");
    public static final Gson GSON = new Gson();
    private BridgeProtocol() {}
    public record Envelope(UUID id, String type, JsonObject data) {}
    public record External(String source, String senderId, String sender, String text, Set<String> mentions) {}
    public static JsonObject parse(String text) {
        // Bound nesting before Gson's recursive parser sees untrusted extensions.
        int depth = 0;
        boolean quoted = false, escaped = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') quoted = false;
            } else if (c == '"') quoted = true;
            else if (c == '{' || c == '[') {
                if (++depth > 32) throw new IllegalArgumentException("JSON nesting");
            } else if (c == '}' || c == ']') depth--;
        }
        try (JsonReader reader = new JsonReader(new StringReader(text))) {
            reader.setLenient(false);
            JsonElement root = Streams.parse(reader);
            if (!root.isJsonObject() || reader.peek() != JsonToken.END_DOCUMENT) throw new IllegalArgumentException("JSON");
            return root.getAsJsonObject();
        } catch (IOException e) { throw new IllegalArgumentException("JSON"); }
    }
    public static final class Invalid extends RuntimeException {
        public final String code;
        public Invalid(String code, String field) { super(field); this.code = code; }
    }
    public static UUID uuid(String value) {
        UUID id = UUID.fromString(value);
        if (!id.toString().equalsIgnoreCase(value)) throw new IllegalArgumentException("UUID");
        return id;
    }
    public static String string(JsonObject object, String key, int max) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
            throw new Invalid("INVALID_MESSAGE", key);
        String text = value.getAsString();
        if (text.codePointCount(0, text.length()) > max) throw new Invalid("MESSAGE_TOO_LARGE", key);
        return text;
    }
    public static JsonObject object(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        if (value == null || !value.isJsonObject()) throw new Invalid("INVALID_MESSAGE", key);
        return value.getAsJsonObject();
    }
    private static long integer(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()
                || !value.toString().matches("[0-9]+")) throw new Invalid("INVALID_MESSAGE", key);
        try { return Long.parseLong(value.toString()); }
        catch (NumberFormatException e) { throw new Invalid("INVALID_MESSAGE", key); }
    }
    public static Envelope envelope(JsonObject root, String serverId) {
        UUID id;
        try { id = uuid(string(root, "id", 36)); }
        catch (IllegalArgumentException e) { throw new Invalid("INVALID_MESSAGE", "id"); }
        String type = string(root, "type", 64);
        if (integer(root, "version") != 1) throw new Invalid("UNSUPPORTED_VERSION", "version");
        String server = string(root, "server_id", 64);
        if (!server.matches("[A-Za-z0-9_-]{1,64}")) throw new Invalid("INVALID_MESSAGE", "server_id");
        if (!server.equals(serverId)) throw new Invalid("SERVER_ID_MISMATCH", "server_id");
        integer(root, "timestamp");
        return new Envelope(id, type, object(root, "data"));
    }
    public static External external(JsonObject data) {
        JsonObject source = object(data, "source"), sender = object(data, "sender");
        identifier(source, "id");
        String senderId = identifier(sender, "id");
        String sourceName = name(source), senderName = name(sender);
        String text = clean(string(data, "text", 2000), false).strip();
        Set<String> mentions = new HashSet<>();
        if (data.has("mentions")) {
            JsonElement element = data.get("mentions");
            if (!element.isJsonArray() || element.getAsJsonArray().size() > 100)
                throw new Invalid("INVALID_MESSAGE", "mentions");
            for (JsonElement item : element.getAsJsonArray()) {
                if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()
                        || !item.getAsString().matches("[A-Za-z0-9_]{1,16}"))
                    throw new Invalid("INVALID_MESSAGE", "mentions");
                mentions.add(item.getAsString().toLowerCase(Locale.ROOT));
            }
        }
        return new External(sourceName, senderId, senderName, text, Set.copyOf(mentions));
    }
    private static String identifier(JsonObject object, String key) {
        String value = string(object, key, 128);
        if (value.isBlank()) throw new Invalid("INVALID_MESSAGE", key);
        return value;
    }
    private static String name(JsonObject object) {
        String value = clean(string(object, "name", 64), true).strip();
        if (value.isEmpty()) throw new Invalid("INVALID_MESSAGE", "name");
        return value;
    }
    public static String clean(String text, boolean name) {
        StringBuilder result = new StringBuilder();
        String stripped = text.replaceAll("(?i)§[0-9a-fk-orx]", "").replace("§", "");
        stripped.codePoints().forEach(c -> {
            if (c == '\n' || c == '\r' || c == 0x2028 || c == 0x2029) {
                if (!name) result.append(' ');
            } else if (!Character.isISOControl(c) && Character.getType(c) != Character.FORMAT)
                result.appendCodePoint(c);
        });
        return result.toString();
    }
    public static Set<String> tokens(String text) {
        Set<String> result = new HashSet<>();
        var matcher = PLAYER_TOKEN.matcher(text);
        while (matcher.find()) result.add(matcher.group().toLowerCase(Locale.ROOT));
        return result;
    }
    public static JsonObject message(String server, String type, JsonObject data) {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        root.addProperty("type", type);
        root.addProperty("id", UUID.randomUUID().toString());
        root.addProperty("server_id", server);
        root.addProperty("timestamp", System.currentTimeMillis());
        root.add("data", data);
        return root;
    }
}
