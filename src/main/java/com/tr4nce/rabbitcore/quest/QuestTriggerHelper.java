package com.tr4nce.rabbitcore.quest;

import dev.ftb.mods.ftbquests.api.FTBQuestsAPI;
import dev.ftb.mods.ftbquests.quest.BaseQuestFile;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.Task;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Helper methods to resolve and complete FTB Quests tasks from in-game triggers.
 */
public final class QuestTriggerHelper {

    private QuestTriggerHelper() {}

    /**
     * Find a task by tag (questId) and complete it for the given player, using the
     * same semantics as {@link com.tr4nce.rabbitcore.block.RabbitChestBlock}.
     */
    public static void completeTaskByTag(ServerLevel level, ServerPlayer player, String questId) {
        if (questId == null || questId.isEmpty()) {
            return;
        }

        // Use server-safe access to the quest file; avoid client-only context
        BaseQuestFile file = FTBQuestsAPI.api().getQuestFile(false);
        if (file == null) {
            return;
        }

        TeamData teamData = file.getOrCreateTeamData(player.getUUID());
        if (teamData == null) {
            return;
        }

        Task targetTask = findTaskByTag(file, questId);
        if (targetTask == null) {
            return;
        }

        Quest quest = targetTask.getQuest();

        // 与 RabbitChestBlock 一致：不显示任务名，避免接口变化导致的崩溃
//        if (!teamData.canStartTasks(quest)) {
//            player.sendSystemMessage(Component.literal("§c前置任务未完成，暂不能完成此任务！"));
//            return;
//        }

        if (teamData.isCompleted(targetTask)) {
            return;
        }

        MinecraftServer server = level.getServer();
        CommandSourceStack source = server.createCommandSourceStack()
                .withPermission(4)
                .withSuppressedOutput();
        String hexId = Long.toHexString(targetTask.id).toUpperCase();
        String command = String.format("ftbquests change_progress %s complete %s",
                player.getGameProfile().getName(),
                hexId);
        server.getCommands().performPrefixedCommand(source, command);
    }

    private static Task findTaskByTag(BaseQuestFile file, String questId) {
        for (Task task : file.getAllTasks()) {
            if (task.hasTag(questId)) {
                return task;
            }
        }
        return null;
    }
}
