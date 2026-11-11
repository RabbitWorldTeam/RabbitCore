package com.tr4nce.rabbitcore.mixin;

import com.tr4nce.rabbitcore.world.DimensionClock;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(MinecraftServer.class)
abstract class MinecraftServerMixin {
    @Redirect(method = "synchronizeTime", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/GameRules;getBoolean(Lnet/minecraft/world/level/GameRules$Key;)Z"),
            require = 2)
    private boolean rabbitcore$syncDaylightCycle(GameRules rules, GameRules.Key<GameRules.BooleanValue> key,
                                                ServerLevel level) {
        return DimensionClock.cycles(level);
    }
}
