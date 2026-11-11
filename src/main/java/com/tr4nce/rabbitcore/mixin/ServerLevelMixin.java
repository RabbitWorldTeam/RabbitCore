package com.tr4nce.rabbitcore.mixin;

import com.tr4nce.rabbitcore.world.DimensionClock;
import com.tr4nce.rabbitcore.world.DimensionDifficulty;
import com.tr4nce.rabbitcore.world.WorldRules;
import com.tr4nce.rabbitcore.world.storage.DimensionClockData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.levelgen.WorldOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
abstract class ServerLevelMixin {
    @Redirect(method = {"<init>", "getSeed"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/WorldOptions;seed()J"), require = 2)
    private long rabbitcore$useDimensionSeed(WorldOptions options) {
        ServerLevel level = (ServerLevel) (Object) this;
        return WorldRules.generationSeed(level.dimension(), options.seed());
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void rabbitcore$initializeWorldRules(CallbackInfo callback) {
        ServerLevel level = (ServerLevel) (Object) this;
        if (level.getLevelData() instanceof DimensionClock data) {
            data.rabbitcore$setClock(DimensionClockData.get(level));
        }
        Difficulty difficulty = WorldRules.difficulty(level.dimension());
        if (difficulty != null && level.getLevelData() instanceof DimensionDifficulty data) {
            data.rabbitcore$setDifficultyOverride(difficulty);
            level.setSpawnSettings(level.getServer().isSpawningMonsters(), level.getServer().isSpawningAnimals());
        }
    }

    @Inject(method = "tickTime", at = @At("RETURN"))
    private void rabbitcore$tickDimensionClock(CallbackInfo callback) {
        ServerLevel level = (ServerLevel) (Object) this;
        if (level.getLevelData() instanceof DimensionClock && DimensionClock.cycles(level)) {
            level.setDayTime(level.getDayTime() + ((LevelTimeInvoker) level).rabbitcore$advanceDaytime());
        }
    }

    @Redirect(method = "tick", slice = @Slice(from = @At(value = "FIELD",
            target = "Lnet/minecraft/world/level/GameRules;RULE_DAYLIGHT:Lnet/minecraft/world/level/GameRules$Key;")),
            at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/GameRules;getBoolean(Lnet/minecraft/world/level/GameRules$Key;)Z",
            ordinal = 0))
    private boolean rabbitcore$shouldSkipNight(GameRules rules, GameRules.Key<GameRules.BooleanValue> key) {
        return DimensionClock.cycles((ServerLevel) (Object) this);
    }
}
