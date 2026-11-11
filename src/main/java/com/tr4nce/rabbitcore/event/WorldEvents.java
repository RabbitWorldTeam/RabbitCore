package com.tr4nce.rabbitcore.event;

import com.tr4nce.rabbitcore.Rabbitcore;
import com.tr4nce.rabbitcore.world.WorldRules;
import net.minecraft.network.protocol.game.ClientboundChangeDifficultyPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

@EventBusSubscriber(modid = Rabbitcore.MODID)
public final class WorldEvents {
    private WorldEvents() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onExplosion(ExplosionEvent.Detonate event) {
        Level level = event.getLevel();
        if (!level.isClientSide() && WorldRules.protectsTerrain(level)) {
            event.getAffectedBlocks().clear();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onMobGriefing(EntityMobGriefingEvent event) {
        Level level = event.getEntity().level();
        if (!level.isClientSide() && WorldRules.protectsTerrain(level)) {
            event.setCanGrief(false);
        }
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && (WorldRules.difficulty(event.getFrom()) != null || WorldRules.difficulty(event.getTo()) != null)) {
            var data = player.serverLevel().getLevelData();
            player.connection.send(new ClientboundChangeDifficultyPacket(data.getDifficulty(), data.isDifficultyLocked()));
        }
    }
}
