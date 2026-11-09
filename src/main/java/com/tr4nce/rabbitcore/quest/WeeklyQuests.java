package com.tr4nce.rabbitcore.quest;

import com.mojang.logging.LogUtils;
import com.tr4nce.rabbitcore.world.storage.WeeklyQuestData;
import dev.architectury.event.EventResult;
import dev.architectury.networking.NetworkManager;
import dev.ftb.mods.ftbquests.api.FTBQuestsAPI;
import dev.ftb.mods.ftbquests.events.CustomTaskEvent;
import dev.ftb.mods.ftbquests.net.SyncTeamDataMessage;
import dev.ftb.mods.ftbquests.quest.BaseQuestFile;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.CustomTask;
import dev.ftb.mods.ftbquests.quest.task.Task;
import dev.ftb.mods.ftbquests.util.ProgressChange;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.biome.Biomes;
import org.slf4j.Logger;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

public final class WeeklyQuests {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String WEEKLY_TAG = "rabbit_weekly";
    private static final String DAMAGE_TAG = "quest_damage";
    private static final String DEEP_DARK_DAMAGE_TAG = "quest_damage_deep_dark";
    private static final long DAMAGE_GOAL = 6657;
    private static final long DEEP_DARK_DAMAGE_GOAL = 500;
    private static final ZoneId RESET_ZONE = ZoneId.of("Asia/Hong_Kong");
    private static final int RESET_HOUR = 4;
    private static final long WEEK_MILLIS = Duration.ofDays(7).toMillis();

    private WeeklyQuests() {}

    public static void register() {
        CustomTaskEvent.EVENT.register(event -> {
            CustomTask task = event.getTask();
            if (isWeeklyDamageTask(task)) {
                task.setMaxProgress(task.hasTag(DEEP_DARK_DAMAGE_TAG) ? DEEP_DARK_DAMAGE_GOAL : DAMAGE_GOAL);
            }
            return EventResult.pass();
        });
    }

    public static void refresh(MinecraftServer server) {
        BaseQuestFile file = FTBQuestsAPI.api().getQuestFile(false);
        if (file != null) {
            refresh(server, file, WeeklyQuestData.get(server));
        }
    }

    public static void initialize(MinecraftServer server) {
        BaseQuestFile file = FTBQuestsAPI.api().getQuestFile(false);
        if (file == null) {
            return;
        }
        WeeklyQuestData data = WeeklyQuestData.get(server);
        if (!refresh(file, data, Instant.now()) && data.getWeekStart() != Long.MIN_VALUE) {
            EngineeringOrders.apply(file, getWeeklyQuests(file), data, data.getWeekStart());
        }
        if (data.isDirty()) {
            server.overworld().getDataStorage().save();
        }
    }

    private static void refresh(MinecraftServer server, BaseQuestFile file, WeeklyQuestData data) {
        if (refresh(file, data, Instant.now())) {
            server.overworld().getDataStorage().save();
        }
    }

    static boolean refresh(BaseQuestFile file, WeeklyQuestData data, Instant now) {
        if (now.toEpochMilli() < data.getWeekStart() + WEEK_MILLIS) {
            return false;
        }
        List<Quest> weeklyQuests = getWeeklyQuests(file);
        if (weeklyQuests.isEmpty()) {
            return false;
        }

        long weekStart = weekStart(now).toEpochMilli();
        for (TeamData team : file.getAllTeamData()) {
            resetTeam(team, weeklyQuests);
        }
        EngineeringOrders.apply(file, weeklyQuests, data, weekStart);
        data.startWeek(weekStart);
        LOGGER.info("Refreshed {} weekly quests for the cycle starting {}", weeklyQuests.size(),
                Instant.ofEpochMilli(weekStart).atZone(RESET_ZONE));
        return true;
    }

    private static List<Quest> getWeeklyQuests(BaseQuestFile file) {
        return file.getAllChapters().stream()
                .flatMap(chapter -> chapter.getQuests().stream())
                .filter(quest -> quest.hasTag(WEEKLY_TAG))
                .toList();
    }

    static Instant weekStart(Instant now) {
        return now.atZone(RESET_ZONE).minusHours(RESET_HOUR)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .toLocalDate().atTime(RESET_HOUR, 0).atZone(RESET_ZONE).toInstant();
    }

    private static void resetTeam(TeamData team, List<Quest> quests) {
        boolean locked = team.isLocked();
        if (locked) {
            team.setLocked(false);
        }
        try {
            for (Quest quest : quests) {
                quest.forceProgressRaw(team, new ProgressChange(quest, team.getTeamId()));
                team.clearRepeatCooldown(quest);
                quest.getRewards().forEach(team::deleteReward);
            }
        } finally {
            if (locked) {
                team.setLocked(true);
            }
        }
        team.saveIfChanged();
        // Removing claim records for offline members does not send individual reward packets.
        NetworkManager.sendToPlayers(team.getOnlineMembers(), new SyncTeamDataMessage(team));
    }

    public static void recordDamage(ServerPlayer player, Mob target, float damage) {
        if (!Float.isFinite(damage) || damage <= 0) {
            return;
        }
        BaseQuestFile file = FTBQuestsAPI.api().getQuestFile(false);
        if (file == null) {
            return;
        }
        MinecraftServer server = player.serverLevel().getServer();
        WeeklyQuestData data = WeeklyQuestData.get(server);
        refresh(server, file, data);
        TeamData team = file.getOrCreateTeamData(player);
        if (team == null || team.isLocked()) {
            return;
        }
        for (Task task : file.getAllTasks()) {
            if (isWeeklyDamageTask(task) && !team.isCompleted(task) && team.canStartTasks(task.getQuest())
                    && acceptsDamageTarget(task, target)) {
                long progress = data.accumulateDamage(team.getTeamId(), task.getId(), Math.min(damage, task.getMaxProgress()));
                if (progress > 0) {
                    team.addProgress(task, progress);
                }
            }
        }
    }

    private static boolean isWeeklyDamageTask(Task task) {
        return task instanceof CustomTask && task.getQuest().hasTag(WEEKLY_TAG)
                && (task.hasTag(DAMAGE_TAG) || task.hasTag(DEEP_DARK_DAMAGE_TAG));
    }

    private static boolean acceptsDamageTarget(Task task, Mob target) {
        return !task.hasTag(DEEP_DARK_DAMAGE_TAG) || (target.getType() == EntityType.WARDEN
                && target.level().getBiome(target.blockPosition()).is(Biomes.DEEP_DARK));
    }
}
