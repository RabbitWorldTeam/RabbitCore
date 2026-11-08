package com.tr4nce.rabbitcore.bridge;

import com.google.gson.JsonObject;
import java.util.*;

/** Strict whitelist-only requests. No server command text is ever executed. */
public final class BridgeAdmin {
    public record Request(String sourceId, String senderId, String action, String name, boolean enabled, int page) {}
    private BridgeAdmin() {}
    public static boolean fresh(long timestamp, long now) { return timestamp >= now - 30000 && timestamp <= now + 30000; }
    public static boolean textCommand(String text) {
        String root = text.strip().split("\\s+", 2)[0];
        return Set.of("白名单", "/白名单", "whitelist", "/whitelist").contains(root);
    }
    public static Request structured(JsonObject data) {
        String source = BridgeProtocol.string(BridgeProtocol.object(data, "source"), "id", 128);
        String sender = BridgeProtocol.string(BridgeProtocol.object(data, "sender"), "id", 128);
        String action = BridgeProtocol.string(data, "action", 16);
        String name = Set.of("add", "remove", "info", "set").contains(action) ? BridgeProtocol.string(data, "name", 16) : "";
        boolean enabled = true;
        if (action.equals("set")) {
            if (!data.has("enabled") || !data.get("enabled").isJsonPrimitive() || !data.get("enabled").getAsJsonPrimitive().isBoolean())
                throw new BridgeProtocol.Invalid("INVALID_MESSAGE", "enabled");
            enabled = data.get("enabled").getAsBoolean();
        }
        int page = 1;
        if (data.has("page")) {
            String raw = data.get("page").toString();
            if (!raw.matches("[1-9][0-9]{0,5}")) throw new BridgeProtocol.Invalid("INVALID_MESSAGE", "page");
            page = Integer.parseInt(raw);
        }
        return validate(new Request(source, sender, action, name, enabled, page));
    }
    public static Request text(JsonObject data, String text) {
        String[] parts = text.strip().split("\\s+");
        if (parts.length < 2) throw new BridgeProtocol.Invalid("INVALID_MESSAGE", "whitelist command");
        String action = switch (parts[1]) {
            case "添加", "add" -> "add"; case "移除", "删除", "remove" -> "remove";
            case "查询", "info" -> "info"; case "列表", "list" -> "list";
            case "启用", "暂停", "enable", "disable" -> "set";
            case "开启", "on" -> "on"; case "关闭", "off" -> "off";
            case "状态", "status" -> "status";
            default -> throw new BridgeProtocol.Invalid("INVALID_MESSAGE", "whitelist action");
        };
        JsonObject request = data.deepCopy(); request.addProperty("action", action);
        if (Set.of("add", "remove", "info", "set").contains(action)) {
            if (parts.length != 3) throw new BridgeProtocol.Invalid("INVALID_MESSAGE", "whitelist arguments");
            request.addProperty("name", parts[2]);
            if (action.equals("set")) request.addProperty("enabled", parts[1].equals("启用") || parts[1].equals("enable"));
        } else if (action.equals("list")) {
            if (parts.length > 3) throw new BridgeProtocol.Invalid("INVALID_MESSAGE", "whitelist arguments");
            if (parts.length == 3) {
                if (!parts[2].matches("[1-9][0-9]{0,5}")) throw new BridgeProtocol.Invalid("INVALID_MESSAGE", "page");
                request.addProperty("page", Integer.parseInt(parts[2]));
            }
        } else if (parts.length != 2) throw new BridgeProtocol.Invalid("INVALID_MESSAGE", "whitelist arguments");
        return structured(request);
    }
    private static Request validate(Request request) {
        if (request.sourceId().isBlank() || request.senderId().isBlank()
                || request.sourceId().chars().anyMatch(Character::isISOControl) || request.senderId().chars().anyMatch(Character::isISOControl))
            throw new BridgeProtocol.Invalid("INVALID_MESSAGE", "identity");
        if (!Set.of("add", "remove", "set", "info", "list", "status", "on", "off").contains(request.action()))
            throw new BridgeProtocol.Invalid("INVALID_MESSAGE", "action");
        if (Set.of("add", "remove", "set", "info").contains(request.action()) && !request.name().matches("[A-Za-z0-9_]{3,16}"))
            throw new BridgeProtocol.Invalid("INVALID_MESSAGE", "name");
        return request;
    }
}
