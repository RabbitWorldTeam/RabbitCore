package com.tr4nce.rabbitcore.world.storage;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player action statistics stored as {@link SavedData} in the overworld.
 */
public class PlayerActionStatsData extends SavedData {

    public static class PlayerActionStats {
        public long blocksBroken;
        public long mobsKilled;
        public double damageDealt;
        public double distanceMoved;
        // New: per-type maps
        public final Map<String, Long> blocksBrokenByType = new HashMap<>();
        public final Map<String, Long> mobsKilledByType = new HashMap<>();

        public PlayerActionStats() {
        }

        public PlayerActionStats(long blocksBroken, long mobsKilled, double damageDealt, double distanceMoved) {
            this.blocksBroken = blocksBroken;
            this.mobsKilled = mobsKilled;
            this.damageDealt = damageDealt;
            this.distanceMoved = distanceMoved;
        }
    }

    private final Map<UUID, PlayerActionStats> statsMap = new HashMap<>();

    public static PlayerActionStatsData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
        if (overworld == null) return new PlayerActionStatsData();

        return overworld.getDataStorage().computeIfAbsent(
                PlayerActionStatsData::load,
                PlayerActionStatsData::new,
                "rabbit_player_action_stats"
        );
    }

    public PlayerActionStatsData() {
    }

    public PlayerActionStats getOrCreate(UUID playerId) {
        return statsMap.computeIfAbsent(playerId, id -> new PlayerActionStats());
    }

    public Map<UUID, PlayerActionStats> getAllStats() {
        return statsMap;
    }

    public void addBlockBroken(UUID playerId) {
        PlayerActionStats stats = getOrCreate(playerId);
        stats.blocksBroken++;
        setDirty();
    }

    public void addBlockBroken(UUID playerId, ResourceLocation blockId) {
        PlayerActionStats stats = getOrCreate(playerId);
        stats.blocksBroken++;
        if (blockId != null) {
            String key = blockId.toString();
            stats.blocksBrokenByType.merge(key, 1L, Long::sum);
        }
        setDirty();
    }

    public void addMobKilled(UUID playerId) {
        PlayerActionStats stats = getOrCreate(playerId);
        stats.mobsKilled++;
        setDirty();
    }

    public void addMobKilled(UUID playerId, ResourceLocation mobId) {
        PlayerActionStats stats = getOrCreate(playerId);
        stats.mobsKilled++;
        if (mobId != null) {
            String key = mobId.toString();
            stats.mobsKilledByType.merge(key, 1L, Long::sum);
        }
        setDirty();
    }

    public void addDamageDealt(UUID playerId, float amount) {
        if (amount <= 0) return;
        PlayerActionStats stats = getOrCreate(playerId);
        stats.damageDealt += amount;
        setDirty();
    }

    public void addDistanceMoved(UUID playerId, double distance) {
        if (distance <= 0) return;
        PlayerActionStats stats = getOrCreate(playerId);
        stats.distanceMoved += distance;
        setDirty();
    }

    public static PlayerActionStatsData load(CompoundTag nbt) {
        PlayerActionStatsData data = new PlayerActionStatsData();
        ListTag list = nbt.getList("PlayerActionStats", Tag.TAG_COMPOUND);

        for (Tag base : list) {
            CompoundTag entry = asCompound(base);
            if (entry == null) continue;

            UUID id = readId(entry);
            if (id == null) continue;

            PlayerActionStats stats = readEntryStats(entry);
            data.statsMap.put(id, stats);
        }

        return data;
    }

    private static PlayerActionStats readEntryStats(CompoundTag entry) {
        PlayerActionStats stats = new PlayerActionStats(
                entry.getLong("bb"),
                entry.getLong("mk"),
                entry.getDouble("dd"),
                entry.getDouble("dm")
        );

        readCountList(entry, "bbt", stats.blocksBrokenByType);
        readCountList(entry, "mkt", stats.mobsKilledByType);

        return stats;
    }

    private static UUID readId(CompoundTag entry) {
        // CompoundTag#getString never returns null (it returns "" when missing).
        String idStr = entry.getString("id");
        if (idStr.isEmpty()) return null;

        try {
            return UUID.fromString(idStr);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static CompoundTag asCompound(Tag tag) {
        return (tag instanceof CompoundTag c) ? c : null;
    }

    private static void readCountList(CompoundTag entry, String listKey, Map<String, Long> out) {
        ListTag list = entry.getList(listKey, Tag.TAG_COMPOUND);
        if (list.isEmpty()) return;

        for (Tag t : list) {
            CompoundTag e = asCompound(t);
            if (e == null) continue;

            // CompoundTag#getString never returns null.
            String type = e.getString("id");
            if (type.isEmpty()) continue;

            long count = e.getLong("c");
            if (count <= 0) continue;

            out.put(type, count);
        }
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag nbt) {
        ListTag list = new ListTag();
        statsMap.forEach((id, stats) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("id", id.toString());
            entry.putLong("bb", stats.blocksBroken);
            entry.putLong("mk", stats.mobsKilled);
            entry.putDouble("dd", stats.damageDealt);
            entry.putDouble("dm", stats.distanceMoved);

            // New: per-block-type list
            ListTag blockList = new ListTag();
            stats.blocksBrokenByType.forEach((type, count) -> {
                CompoundTag bEntry = new CompoundTag();
                bEntry.putString("id", type);
                bEntry.putLong("c", count);
                blockList.add(bEntry);
            });
            entry.put("bbt", blockList);

            // New: per-mob-type list
            ListTag mobList = new ListTag();
            stats.mobsKilledByType.forEach((type, count) -> {
                CompoundTag mEntry = new CompoundTag();
                mEntry.putString("id", type);
                mEntry.putLong("c", count);
                mobList.add(mEntry);
            });
            entry.put("mkt", mobList);

            list.add(entry);
        });
        nbt.put("PlayerActionStats", list);
        return nbt;
    }
}
