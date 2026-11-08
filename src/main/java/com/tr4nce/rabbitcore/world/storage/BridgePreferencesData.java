package com.tr4nce.rabbitcore.world.storage;

import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** Personal preferences belong to the world, keyed by immutable player UUID. */
public final class BridgePreferencesData extends SavedData {
    private final Set<UUID> muted = new HashSet<>(), hidden = new HashSet<>();
    public static BridgePreferencesData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(BridgePreferencesData::new, BridgePreferencesData::load, null), "rabbit_bridge_preferences");
    }
    public boolean sound(UUID id) { return !muted.contains(id); }
    public boolean receive(UUID id) { return !hidden.contains(id); }
    public void set(UUID id, boolean sound, boolean enabled) {
        Set<UUID> target = sound ? muted : hidden;
        if (enabled) target.remove(id); else target.add(id);
        setDirty();
    }
    public static BridgePreferencesData load(CompoundTag tag, HolderLookup.Provider registries) {
        BridgePreferencesData data = new BridgePreferencesData();
        read(tag, "muted", data.muted); read(tag, "hidden", data.hidden); return data;
    }
    private static void read(CompoundTag tag, String key, Set<UUID> output) {
        for (Tag item : tag.getList(key, Tag.TAG_STRING)) {
            try { output.add(UUID.fromString(item.getAsString())); } catch (IllegalArgumentException ignored) { }
        }
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        write(tag, "muted", muted); write(tag, "hidden", hidden); return tag;
    }
    private static void write(CompoundTag tag, String key, Set<UUID> values) {
        ListTag list = new ListTag(); values.forEach(id -> list.add(StringTag.valueOf(id.toString()))); tag.put(key, list);
    }
}
