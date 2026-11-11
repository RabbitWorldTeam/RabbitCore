package com.tr4nce.rabbitcore.mixin;

import com.tr4nce.rabbitcore.world.DimensionClock;
import com.tr4nce.rabbitcore.world.DimensionDifficulty;
import com.tr4nce.rabbitcore.world.storage.DimensionClockData;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.storage.DerivedLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DerivedLevelData.class)
abstract class DerivedLevelDataMixin implements DimensionDifficulty, DimensionClock {
    @Unique
    private Difficulty rabbitcore$difficultyOverride;
    @Unique
    private DimensionClockData rabbitcore$clock;

    @Override
    public DimensionClockData rabbitcore$clock() {
        return rabbitcore$clock;
    }

    @Override
    public void rabbitcore$setClock(DimensionClockData clock) {
        rabbitcore$clock = clock;
    }

    @Inject(method = "getDayTime", at = @At("HEAD"), cancellable = true)
    private void rabbitcore$getDayTime(CallbackInfoReturnable<Long> callback) {
        if (rabbitcore$clock != null) {
            callback.setReturnValue(rabbitcore$clock.dayTime());
        }
    }

    @Inject(method = "setDayTime", at = @At("HEAD"))
    private void rabbitcore$setDayTime(long dayTime, CallbackInfo callback) {
        if (rabbitcore$clock != null) {
            rabbitcore$clock.setDayTime(dayTime);
        }
    }

    @Inject(method = "getDayTimeFraction", at = @At("HEAD"), cancellable = true)
    private void rabbitcore$getDayTimeFraction(CallbackInfoReturnable<Float> callback) {
        if (rabbitcore$clock != null) {
            callback.setReturnValue(rabbitcore$clock.fraction());
        }
    }

    @Inject(method = "setDayTimeFraction", at = @At("HEAD"))
    private void rabbitcore$setDayTimeFraction(float fraction, CallbackInfo callback) {
        if (rabbitcore$clock != null) {
            rabbitcore$clock.setFraction(fraction);
        }
    }

    @Override
    public void rabbitcore$setDifficultyOverride(Difficulty difficulty) {
        rabbitcore$difficultyOverride = difficulty;
    }

    @Inject(method = "getDifficulty", at = @At("HEAD"), cancellable = true)
    private void rabbitcore$getDifficulty(CallbackInfoReturnable<Difficulty> callback) {
        if (rabbitcore$difficultyOverride != null) {
            callback.setReturnValue(rabbitcore$difficultyOverride);
        }
    }

    @Inject(method = "isDifficultyLocked", at = @At("HEAD"), cancellable = true)
    private void rabbitcore$isDifficultyLocked(CallbackInfoReturnable<Boolean> callback) {
        if (rabbitcore$difficultyOverride != null) {
            callback.setReturnValue(true);
        }
    }
}
