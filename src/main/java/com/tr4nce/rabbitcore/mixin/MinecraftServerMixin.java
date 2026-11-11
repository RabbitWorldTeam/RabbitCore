package com.tr4nce.rabbitcore.mixin;

import com.tr4nce.rabbitcore.world.DimensionClock;
import com.tr4nce.rabbitcore.world.WorldRules;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(MinecraftServer.class)
abstract class MinecraftServerMixin {
    @ModifyArgs(method = "createLevels", at = @At(value = "INVOKE", ordinal = 1, target =
            "Lnet/minecraft/server/level/ServerLevel;<init>(Lnet/minecraft/server/MinecraftServer;"
                    + "Ljava/util/concurrent/Executor;"
                    + "Lnet/minecraft/world/level/storage/LevelStorageSource$LevelStorageAccess;"
                    + "Lnet/minecraft/world/level/storage/ServerLevelData;Lnet/minecraft/resources/ResourceKey;"
                    + "Lnet/minecraft/world/level/dimension/LevelStem;"
                    + "Lnet/minecraft/server/level/progress/ChunkProgressListener;ZJLjava/util/List;Z"
                    + "Lnet/minecraft/world/RandomSequences;)V"))
    private void rabbitcore$useDimensionBiomeSeed(Args args) {
        MinecraftServer server = (MinecraftServer) (Object) this;
        long worldSeed = server.getWorldData().worldGenOptions().seed();
        long dimensionSeed = WorldRules.generationSeed(args.get(4), worldSeed);
        if (dimensionSeed != worldSeed) {
            args.set(8, BiomeManager.obfuscateSeed(dimensionSeed));
        }
    }

    @Redirect(method = "synchronizeTime", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/GameRules;getBoolean(Lnet/minecraft/world/level/GameRules$Key;)Z"),
            require = 2)
    private boolean rabbitcore$syncDaylightCycle(GameRules rules, GameRules.Key<GameRules.BooleanValue> key,
                                                ServerLevel level) {
        return DimensionClock.cycles(level);
    }
}
