package com.tr4nce.rabbitcore.quest;

import dev.ftb.mods.ftbquests.quest.task.Task;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

public final class QuestTagConditionChecker {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT);

    private QuestTagConditionChecker() {}

    public enum EventKind {
        HARVEST,
        KILL,
        DAMAGE,
        MOVE
    }

    public record CheckContext(ServerPlayer player, EventKind eventKind, long currentCount, ResourceLocation targetId) { }

    public static boolean shouldComplete(Task task, CheckContext ctx) {
        // 日期条件
        if (!isDateInRange(task)) {
            return false;
        }

        // 事件类型 + 具体对象条件
        if (!isEventKindAllowed(task, ctx.eventKind)) {
            return false;
        }
        if (!isTargetTypeAllowed(task, ctx.targetId)) {
            return false;
        }

        // 数量条件
        long required = getRequiredCount(task);
        return ctx.currentCount >= required;
    }

    /**
     * Date tag format (underscore only):
     *   date_YYYY-MM-DD_YYYY-MM-DD
     * Either start or end can be omitted by leaving empty or skipping, but typical usage is both.
     */
    public static boolean isDateInRange(Task task) {
        String dateTag = findTagStartingWith(task, "date_");
        if (dateTag == null) {
            return true; // no date limit
        }

        // Examples:
        //   date_2026-02-01_2026-02-28
        //   date_2026-02-01_        (only start)
        //   date__2026-02-28        (only end)
        String[] parts = dateTag.split("_");
        // [0]=date, [1]=start?, [2]=end?
        String startStr = parts.length > 1 ? parts[1].trim() : "";
        String endStr = parts.length > 2 ? parts[2].trim() : "";

        LocalDate today = LocalDate.now();
        try {
            if (!startStr.isEmpty()) {
                LocalDate start = LocalDate.parse(startStr, DATE_FMT);
                if (today.isBefore(start)) return false;
            }
            if (!endStr.isEmpty()) {
                LocalDate end = LocalDate.parse(endStr, DATE_FMT);
                if (today.isAfter(end)) return false;
            }
        } catch (DateTimeParseException e) {
            return false;
        }
        return true;
    }

    /**
     * Event tag format (underscore only):
     *   event_harvest
     *   event_harvest_minecraft_stone
     *   event_kill
     *   event_kill_minecraft_zombie
     *   event_damage
     *   event_damage_minecraft_zombie
     *   event_move
     */
    public static boolean isEventKindAllowed(Task task, EventKind kind) {
        String eventTag = findTagStartingWith(task, "event_");
        if (eventTag == null) {
            return true; // no restriction
        }

        String[] parts = eventTag.split("_");
        // [0]=event, [1]=kind, [2]=namespace, [3]=path
        if (parts.length < 2) return true;
        String kindPart = parts[1].toLowerCase(Locale.ROOT);

        return switch (kindPart) {
            case "harvest" -> kind == EventKind.HARVEST;
            case "kill" -> kind == EventKind.KILL;
            case "damage" -> kind == EventKind.DAMAGE;
            case "move" -> kind == EventKind.MOVE;
            default -> false;
        };
    }

    /**
     * If a specific block/entity type is required, the event tag must be:
     *   event_<kind>_<namespace>_<path>
     * e.g. event_harvest_minecraft_stone, event_kill_minecraft_zombie.
     * If there is no namespace+path part, this check passes.
     */
    public static boolean isTargetTypeAllowed(Task task, ResourceLocation actualId) {
        String eventTag = findTagStartingWith(task, "event_");
        if (eventTag == null) {
            return true;
        }
        String[] parts = eventTag.split("_");
        if (parts.length < 4) {
            // no concrete ID specified
            return true;
        }

        String namespace = parts[2];
        String path = parts[3];
        ResourceLocation requiredId = ResourceLocation.fromNamespaceAndPath(namespace, path);

        if (actualId == null) return false;
        return actualId.equals(requiredId);
    }

    /**
     * Count tag format (underscore only):
     *   count_123
     */
    public static long getRequiredCount(Task task) {
        String countTag = findTagStartingWith(task, "count_");
        if (countTag == null) {
            return 1L;
        }
        String[] parts = countTag.split("_", 2);
        if (parts.length < 2) return 1L;
        String payload = parts[1].trim();
        if (payload.isEmpty()) return 1L;
        try {
            return Long.parseLong(payload);
        } catch (NumberFormatException e) {
            return 1L;
        }
    }

    private static String findTagStartingWith(Task task, String prefix) {
        for (String raw : task.getTags()) {
            if (raw == null) continue;
            String s = raw.trim();
            if (s.startsWith(prefix)) {
                return s;
            }
        }
        return null;
    }
}
