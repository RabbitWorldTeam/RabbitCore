package com.tr4nce.rabbitcore.mixin;

import com.tr4nce.rabbitcore.world.DimensionClock;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PlayerList.class)
abstract class PlayerListMixin {
    @Redirect(method = "sendLevelInfo", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/GameRules;getBoolean(Lnet/minecraft/world/level/GameRules$Key;)Z"),
            require = 2)
    private boolean rabbitcore$sendDaylightCycle(GameRules rules, GameRules.Key<GameRules.BooleanValue> key,
                                                ServerPlayer player, ServerLevel level) {
        return DimensionClock.cycles(level);
    }
}
