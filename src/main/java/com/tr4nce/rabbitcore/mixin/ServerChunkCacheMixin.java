package com.tr4nce.rabbitcore.mixin;

import com.tr4nce.rabbitcore.world.WorldRules;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ServerChunkCache.class)
abstract class ServerChunkCacheMixin {
    @Shadow @Final public ServerLevel level;

    @ModifyVariable(method = "setSpawnSettings", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private boolean rabbitcore$applyMonsterSpawning(boolean spawnMonsters) {
        Difficulty difficulty = WorldRules.difficulty(level.dimension());
        if (difficulty == null) {
            return spawnMonsters;
        }
        return difficulty != Difficulty.PEACEFUL
                && (!(level.getServer() instanceof DedicatedServer dedicated) || dedicated.getProperties().spawnMonsters);
    }
}
