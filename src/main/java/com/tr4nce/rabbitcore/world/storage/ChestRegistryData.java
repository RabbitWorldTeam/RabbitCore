package com.tr4nce.rabbitcore.world.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class ChestRegistryData extends SavedData {
    public static class ChestInfo {
        public final String questId;
        public final long gameTime;
        public final long wallTime;

        public ChestInfo(String questId, long gameTime, long wallTime) {
            this.questId = questId;
            this.gameTime = gameTime;
            this.wallTime = wallTime;
        }
    }

    private final Map<GlobalPos, ChestInfo> registeredChests = new HashMap<>();

    public static ChestRegistryData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
        if (overworld == null) return new ChestRegistryData();

        return overworld.getDataStorage().computeIfAbsent(new SavedData.Factory<>(ChestRegistryData::new, ChestRegistryData::load, null),
                "rabbit_chest_registry"
        );
    }

    public ChestRegistryData() {}

    public void addChest(GlobalPos pos, String questId) {
        // backward-compat helper: no timestamp provided
        addChest(pos, questId, 0L, 0L);
    }

    public void addChest(GlobalPos pos, String questId, long gameTime, long wallTime) {
        this.registeredChests.put(pos, new ChestInfo(questId, gameTime, wallTime));
        this.setDirty();
    }

    public void removeChest(GlobalPos pos) {
        this.registeredChests.remove(pos);
        this.setDirty();
    }

    public Map<GlobalPos, ChestInfo> getAllChests() {
        return registeredChests;
    }

    // 反序列化：从 NBT 读回所有带 questId 的箱子
    public static ChestRegistryData load(CompoundTag nbt, HolderLookup.Provider registries) {
        ChestRegistryData data = new ChestRegistryData();
        ListTag list = nbt.getList("RabbitChests", Tag.TAG_COMPOUND);

        for (Tag base : list) {
            if (!(base instanceof CompoundTag entry)) continue;

            int x = entry.getInt("x");
            int y = entry.getInt("y");
            int z = entry.getInt("z");
            String dimStr = entry.getString("dim");
            String questId = entry.getString("qid");

            long gameTime = entry.contains("gt", Tag.TAG_LONG) ? entry.getLong("gt") : 0L;
            long wallTime = entry.contains("wt", Tag.TAG_LONG) ? entry.getLong("wt") : 0L;

            if (dimStr.isEmpty()) continue;

            // 解析维度 ID -> GlobalPos（使用非弃用的 ResourceLocation API）
            ResourceLocation dimLoc = ResourceLocation.parse(dimStr);
            ResourceKey<Level> dimKey = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dimLoc);
            GlobalPos gPos = GlobalPos.of(dimKey, new BlockPos(x, y, z));

            data.registeredChests.put(gPos, new ChestInfo(questId, gameTime, wallTime));
        }

        return data;
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag nbt, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        registeredChests.forEach((pos, info) -> {
            CompoundTag entry = new CompoundTag();
            entry.putInt("x", pos.pos().getX());
            entry.putInt("y", pos.pos().getY());
            entry.putInt("z", pos.pos().getZ());
            entry.putString("dim", pos.dimension().location().toString());
            entry.putString("qid", info.questId == null ? "" : info.questId);
            entry.putLong("gt", info.gameTime);
            entry.putLong("wt", info.wallTime);
            list.add(entry);
        });
        nbt.put("RabbitChests", list);
        return nbt;
    }
}
