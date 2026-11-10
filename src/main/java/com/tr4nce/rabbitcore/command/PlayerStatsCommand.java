package com.tr4nce.rabbitcore.command;

import com.mojang.brigadier.CommandDispatcher;
import com.tr4nce.rabbitcore.Rabbitcore;
import com.tr4nce.rabbitcore.world.storage.PlayerActionStatsData;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PlayerStatsCommand {

    // Simple global toggle for whether stats are recorded
    private static boolean ENABLED = true;

    public static boolean isEnabled() {
        return ENABLED;
    }

    public static void setEnabled(boolean enabled) {
        ENABLED = enabled;
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(Rabbitcore.MODID)
                .then(Commands.literal("stats")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> showStats(ctx.getSource()))
                        .then(Commands.literal("clear_stats")
                                .executes(ctx -> clearStats(ctx.getSource())))
                        .then(Commands.literal("toggle")
                                .executes(ctx -> toggleStats(ctx.getSource())))));
    }

    private static int showStats(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("只能由玩家执行这个命令"));
            return 0;
        }

        ServerLevel level = player.serverLevel();
        PlayerActionStatsData data = PlayerActionStatsData.get(level);
        PlayerActionStatsData.PlayerActionStats stats = data.getOrCreate(player.getUUID());

        source.sendSuccess(() -> Component.literal("[RabbitCore] 玩家统计信息：").withStyle(ChatFormatting.GOLD), false);

        // 总览
        source.sendSuccess(() -> Component.literal(String.format("总采集方块数: %d", stats.blocksBroken)).withStyle(ChatFormatting.AQUA), false);
        source.sendSuccess(() -> Component.literal(String.format("总击杀怪物数: %d", stats.mobsKilled)).withStyle(ChatFormatting.AQUA), false);
        source.sendSuccess(() -> Component.literal(String.format("总伤害: %.2f", stats.damageDealt)).withStyle(ChatFormatting.AQUA), false);
        source.sendSuccess(() -> Component.literal(String.format("总移动距离: %.2f", stats.distanceMoved)).withStyle(ChatFormatting.AQUA), false);

        // Top 3 方块
        if (stats.blocksBrokenByType.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Top 3 采集方块: (暂无数据)").withStyle(ChatFormatting.GRAY), false);
        } else {
            source.sendSuccess(() -> Component.literal("Top 3 采集方块类型:").withStyle(ChatFormatting.YELLOW), false);
            List<Map.Entry<String, Long>> topBlocks = stats.blocksBrokenByType.entrySet().stream()
                    .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                    .limit(3)
                    .toList();
            ListTop3(source, topBlocks);
        }

        // Top 3 怪物
        if (stats.mobsKilledByType.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Top 3 击杀怪物: (暂无数据)").withStyle(ChatFormatting.GRAY), false);
        } else {
            source.sendSuccess(() -> Component.literal("Top 3 击杀怪物类型:").withStyle(ChatFormatting.YELLOW), false);
            List<Map.Entry<String, Long>> topMobs = stats.mobsKilledByType.entrySet().stream()
                    .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                    .limit(3)
                    .toList();
            ListTop3(source, topMobs);
        }

        return 1;
    }

    private static void ListTop3(CommandSourceStack source, List<Map.Entry<String, Long>> topBlocks) {
        int index = 1;
        for (Map.Entry<String, Long> e : topBlocks) {
            MutableComponent line = Component.literal(String.format(" #%d %s x%d", index++, e.getKey(), e.getValue()))
                    .withStyle(ChatFormatting.GREEN);
            source.sendSuccess(() -> line, false);
        }
    }

    private static int clearStats(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        PlayerActionStatsData data = PlayerActionStatsData.get(level);

        // 粗暴清空：重新 new 一个并覆盖所有玩家统计
        // 由于 SavedData API 不直接暴露清除接口，这里简单对 map clear 再 setDirty
        try {
            java.lang.reflect.Field mapField = PlayerActionStatsData.class.getDeclaredField("statsMap");
            mapField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<UUID, PlayerActionStatsData.PlayerActionStats> map = (Map<UUID, PlayerActionStatsData.PlayerActionStats>) mapField.get(data);
            map.clear();
            data.setDirty();
        } catch (Exception e) {
            source.sendFailure(Component.literal("清除统计失败: " + e.getMessage()));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("已清除所有玩家的 RabbitCore 统计数据").withStyle(ChatFormatting.RED), true);
        return 1;
    }

    private static int toggleStats(CommandSourceStack source) {
        ENABLED = !ENABLED;
        String status = ENABLED ? "开启" : "关闭";
        source.sendSuccess(() -> Component.literal("RabbitCore 统计已" + status).withStyle(ChatFormatting.GOLD), true);
        return 1;
    }
}
