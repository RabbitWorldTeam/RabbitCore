package com.tr4nce.rabbitcore.world.storage;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.UUID;

public final class WeeklyQuestData extends SavedData {
    private static final String WEEK_START = "week_start";
    private static final String ENGINEERING_START = "engineering_start_week";
    private static final String DAMAGE_REMAINDERS = "damage_remainders";

    private long weekStart = Long.MIN_VALUE;
    private long engineeringStartWeek = Long.MIN_VALUE;
    private CompoundTag damageRemainders = new CompoundTag();

    public static WeeklyQuestData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(WeeklyQuestData::new, WeeklyQuestData::load, null),
                "rabbit_weekly_quests");
    }

    public long getWeekStart() {
        return weekStart;
    }

    public void startWeek(long start) {
        weekStart = start;
        damageRemainders = new CompoundTag();
        setDirty();
    }

    public long getOrStartEngineeringRotation(long start) {
        if (engineeringStartWeek == Long.MIN_VALUE) {
            engineeringStartWeek = start;
            setDirty();
        }
        return engineeringStartWeek;
    }

    public long accumulateDamage(UUID teamId, long taskId, float damage) {
        String key = teamId + ":" + taskId;
        double total = damageRemainders.getDouble(key) + damage;
        long whole = (long) total;
        double remainder = total - whole;
        if (remainder == 0) {
            damageRemainders.remove(key);
        } else {
            damageRemainders.putDouble(key, remainder);
        }
        setDirty();
        return whole;
    }

    private static WeeklyQuestData load(CompoundTag tag, HolderLookup.Provider registries) {
        WeeklyQuestData data = new WeeklyQuestData();
        if (tag.contains(WEEK_START)) {
            data.weekStart = tag.getLong(WEEK_START);
        }
        if (tag.contains(ENGINEERING_START)) {
            data.engineeringStartWeek = tag.getLong(ENGINEERING_START);
        }
        data.damageRemainders = tag.getCompound(DAMAGE_REMAINDERS);
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLong(WEEK_START, weekStart);
        if (engineeringStartWeek != Long.MIN_VALUE) {
            tag.putLong(ENGINEERING_START, engineeringStartWeek);
        }
        tag.put(DAMAGE_REMAINDERS, damageRemainders.copy());
        return tag;
    }
}
