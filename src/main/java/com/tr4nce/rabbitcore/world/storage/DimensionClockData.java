package com.tr4nce.rabbitcore.world.storage;

import com.tr4nce.rabbitcore.world.DimensionClock;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class DimensionClockData extends SavedData {
    private long dayTime;
    private boolean cycles;
    private float fraction;

    private DimensionClockData(long dayTime, boolean cycles, float fraction) {
        this.dayTime = dayTime;
        this.cycles = cycles;
        this.fraction = fraction;
    }

    public static DimensionClockData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new Factory<>(
                () -> {
                    var clock = new DimensionClockData(level.getDayTime(), DimensionClock.cycles(level),
                            level.getDayTimeFraction());
                    clock.setDirty();
                    return clock;
                },
                DimensionClockData::load, null), "rabbitcore_dimension_clock");
    }

    public long dayTime() {
        return dayTime;
    }

    public void setDayTime(long dayTime) {
        if (this.dayTime != dayTime) {
            this.dayTime = dayTime;
            setDirty();
        }
    }

    public boolean cycles() {
        return cycles;
    }

    public void setCycles(boolean cycles) {
        if (this.cycles != cycles) {
            this.cycles = cycles;
            setDirty();
        }
    }

    public float fraction() {
        return fraction;
    }

    public void setFraction(float fraction) {
        if (this.fraction != fraction) {
            this.fraction = fraction;
            setDirty();
        }
    }

    private static DimensionClockData load(CompoundTag tag, HolderLookup.Provider registries) {
        return new DimensionClockData(tag.getLong("day_time"), tag.getBoolean("cycles"), tag.getFloat("fraction"));
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLong("day_time", dayTime);
        tag.putBoolean("cycles", cycles);
        tag.putFloat("fraction", fraction);
        return tag;
    }
}
