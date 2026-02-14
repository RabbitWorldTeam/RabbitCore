package com.tr4nce.rabbitcore.event;

import com.tr4nce.rabbitcore.Rabbitcore;
import com.tr4nce.rabbitcore.command.PlayerStatsCommand;
import com.tr4nce.rabbitcore.quest.QuestTagConditionChecker;
import com.tr4nce.rabbitcore.quest.QuestTriggerHelper;
import com.tr4nce.rabbitcore.world.storage.PlayerActionStatsData;
import dev.ftb.mods.ftbquests.api.FTBQuestsAPI;
import dev.ftb.mods.ftbquests.quest.BaseQuestFile;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.Task;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Listens for player actions (block breaking, killing mobs, damaging mobs, movement)
 * and records simple statistics via {@link PlayerActionStatsData}.
 */
@Mod.EventBusSubscriber(modid = Rabbitcore.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PlayerActionHandler {

    private static final Map<UUID, Vec3> LAST_POSITIONS = new HashMap<>();
    private static final Logger LOGGER = org.slf4j.LoggerFactory.getLogger("RabbitCore-PlayerAction");

    private PlayerActionHandler() {}

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (!PlayerStatsCommand.isEnabled()) return;
        ServerLevel level = player.serverLevel();

        BlockPos pos = event.getPos();
        BlockState state = event.getState();
        Block block = state.getBlock();
        ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(block);

        PlayerActionStatsData data = PlayerActionStatsData.get(level);
        data.addBlockBroken(player.getUUID(), blockId);

        LOGGER.info("[PlayerAction] {} broke block {} at {} in {}", player.getGameProfile().getName(),
                blockId, pos, level.dimension().location());

        // FTB Quests integration: check harvest-related tasks
        triggerQuestTasksForEvent(level, player,
                QuestTagConditionChecker.EventKind.HARVEST,
                blockId,
                data.getOrCreate(player.getUUID()).blocksBroken);
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        Entity src = event.getSource().getEntity();
        if (!(src instanceof ServerPlayer player)) return;
        if (!(victim instanceof Mob mob)) return;
        if (!PlayerStatsCommand.isEnabled()) return;

        ServerLevel level = player.serverLevel();
        ResourceLocation mobId = ForgeRegistries.ENTITY_TYPES.getKey(mob.getType());
        PlayerActionStatsData data = PlayerActionStatsData.get(level);
        data.addMobKilled(player.getUUID(), mobId);

        LOGGER.info("[PlayerAction] {} killed mob {} at {} in {}", player.getGameProfile().getName(),
                mobId, victim.blockPosition(), level.dimension().location());

        // FTB Quests integration: check kill-related tasks
        triggerQuestTasksForEvent(level, player,
                QuestTagConditionChecker.EventKind.KILL,
                mobId,
                data.getOrCreate(player.getUUID()).mobsKilled);
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        Entity src = event.getSource().getEntity();
        if (!(src instanceof ServerPlayer player)) return;
        if (!(victim instanceof Mob mob)) return;
        if (!PlayerStatsCommand.isEnabled()) return;

        float amount = event.getAmount();
        if (amount <= 0) return;

        ServerLevel level = player.serverLevel();
        PlayerActionStatsData data = PlayerActionStatsData.get(level);
        data.addDamageDealt(player.getUUID(), amount);

        ResourceLocation mobId = ForgeRegistries.ENTITY_TYPES.getKey(mob.getType());
        LOGGER.info("[PlayerAction] {} damaged mob {} for {} at {} in {}", player.getGameProfile().getName(),
                mobId, amount, victim.blockPosition(), level.dimension().location());

        // FTB Quests integration: check damage-related tasks
        long currentCount = (long) Math.floor(data.getOrCreate(player.getUUID()).damageDealt);
        triggerQuestTasksForEvent(level, player,
                QuestTagConditionChecker.EventKind.DAMAGE,
                mobId,
                currentCount);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;
        if (player.level().isClientSide) return;
        if (!PlayerStatsCommand.isEnabled()) return;

        UUID id = player.getUUID();
        Vec3 current = player.position();
        Vec3 last = LAST_POSITIONS.get(id);
        if (last != null) {
            double dx = current.x - last.x;
            double dy = current.y - last.y;
            double dz = current.z - last.z;
            double distSq = dx * dx + dy * dy + dz * dz;
            if (distSq > 0.0001D) { // ignore tiny jitter
                double dist = Math.sqrt(distSq);
                ServerLevel level = player.serverLevel();
                PlayerActionStatsData data = PlayerActionStatsData.get(level);
                data.addDistanceMoved(id, dist);
                // 可选：将来可以在这里集成 MOVE 类型的任务
            }
        }
        LAST_POSITIONS.put(id, current);
    }

    /**
     * Scan all tasks for the player's team and complete those whose tags
     * (date_X_X, event_*, count_*) are satisfied by the current event.
     */
    private static void triggerQuestTasksForEvent(ServerLevel level,
                                                  ServerPlayer player,
                                                  QuestTagConditionChecker.EventKind kind,
                                                  ResourceLocation targetId,
                                                  long currentCount) {
        BaseQuestFile file = FTBQuestsAPI.api().getQuestFile(true);
        if (file == null) return;

        TeamData teamData = file.getOrCreateTeamData(player.getUUID());
        if (teamData == null) return;

        QuestTagConditionChecker.CheckContext ctx =
                new QuestTagConditionChecker.CheckContext(player, kind, currentCount, targetId);

        for (Task task : file.getAllTasks()) {
            // 仅检查拥有 event_ 前缀标签的任务，避免无关任务开销
            if (task.getTags().stream().noneMatch(t -> t != null && t.startsWith("event_"))) {
                continue;
            }

            if (!QuestTagConditionChecker.shouldComplete(task, ctx)) {
                continue;
            }

            // 与 RabbitChestBlock 相同的前置/完成检查
            if (teamData.isCompleted(task)) {
                continue;
            }

            QuestTriggerHelper.completeTaskByTag(level, player, findFirstQuestTag(task));
        }
    }

    /**
     * Helper to choose a quest ID tag from the task's tags.
     * For now we simply reuse the first non-empty tag; you can refine this
     * to use a specific prefix like "qt_" if desired.
     */
    private static String findFirstQuestTag(Task task) {
        for (String tag : task.getTags()) {
            if (tag != null && !tag.isEmpty() && !tag.startsWith("date_") && !tag.startsWith("event_") && !tag.startsWith("count_")) {
                return tag;
            }
        }
        return "";
    }
}
