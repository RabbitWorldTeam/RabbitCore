package com.tr4nce.rabbitcore.command;

import com.mojang.brigadier.CommandDispatcher;
import com.tr4nce.rabbitcore.world.storage.ChestRegistryData;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class ChestRegistryCommand {

    private static final DateTimeFormatter WALL_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("rabbit_chests")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("list")
                        // 3. 只有输入了 list 才会执行这个逻辑
                        .executes(ctx -> listChests(ctx.getSource()))
                ));
    }

    private static int listChests(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("只能由玩家执行这个命令"));
            return 0;
        }

        ServerLevel level = player.serverLevel();
        ChestRegistryData data = ChestRegistryData.get(level);
        Map<GlobalPos, ChestRegistryData.ChestInfo> all = data.getAllChests();

        if (all.isEmpty()) {
            source.sendSuccess(() -> Component.literal("当前没有已登记的 Rabbit 任务箱子"), false);
            return 1;
        }

        // 按放置时间排序（wallTime 升序）
        List<Map.Entry<GlobalPos, ChestRegistryData.ChestInfo>> entries = all.entrySet().stream()
                .sorted(Comparator.comparingLong(e -> e.getValue().wallTime))
                .toList();

        source.sendSuccess(() -> Component.literal("已登记的 Rabbit 任务箱子："), false);

        entries.stream()
                .map(ChestRegistryCommand::buildChestLine)
                .forEach(line -> source.sendSuccess(() -> line, false));

        return 1;
    }

    private static Component buildChestLine(Map.Entry<GlobalPos, ChestRegistryData.ChestInfo> entry) {
        GlobalPos pos = entry.getKey();
        ChestRegistryData.ChestInfo info = entry.getValue();

        String dimId = pos.dimension().location().toString();
        int x = pos.pos().getX();
        int y = pos.pos().getY();
        int z = pos.pos().getZ();

        String questId = info.questId == null || info.questId.isEmpty()
                ? "<无 Quest 标签>" : info.questId;

        String wallTimeStr = info.wallTime > 0L
                ? WALL_TIME_FORMATTER.format(Instant.ofEpochMilli(info.wallTime))
                : "(旧记录, 无真实时间)";

        String gameTimeStr = info.gameTime > 0L
                ? info.gameTime + " ticks"
                : "(旧记录, 无 gameTime)";

        String tpCommand = String.format("/tp @s %d %d %d", x, y, z);

        MutableComponent coordComponent = Component.literal(
                        String.format("[%s] (%d, %d, %d)", dimId, x, y, z))
                .withStyle(style -> style
                        .withColor(ChatFormatting.AQUA)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, tpCommand))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.literal("点击传送到该 RabbitChest"))));

        MutableComponent questPart = Component.literal(" | questId=" + questId)
                .withStyle(style -> style
                        .withColor(ChatFormatting.YELLOW)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, questId))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.literal("点击复制 questId"))));

        MutableComponent timePart = Component.literal(
                        String.format(" | realTime=%s | gameTime=%s", wallTimeStr, gameTimeStr))
                .withStyle(ChatFormatting.GRAY);

        return coordComponent.append(questPart).append(timePart);
    }
}
