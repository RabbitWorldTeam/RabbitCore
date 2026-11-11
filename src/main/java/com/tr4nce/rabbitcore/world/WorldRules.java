package com.tr4nce.rabbitcore.world;

import com.tr4nce.rabbitcore.Config;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;

public final class WorldRules {
    private static GameRules.Key<GameRules.BooleanValue> terrainProtection;

    private static final ResourceKey<Level> HARD = key("hard");
    private static final ResourceKey<Level> PEACEFUL = key("peaceful");

    private WorldRules() {}

    public static void register() {
        terrainProtection = GameRules.register("rabbitcoreTerrainProtection", GameRules.Category.UPDATES,
                GameRules.BooleanValue.create(true));
    }

    public static boolean protectsTerrain(Level level) {
        return level.getGameRules().getBoolean(terrainProtection);
    }

    public static long generationSeed(ResourceKey<Level> dimension, long worldSeed) {
        return HARD.equals(dimension) && Config.USE_HARD_DIMENSION_SEED.get()
                ? Config.HARD_DIMENSION_SEED.get() : worldSeed;
    }

    public static Difficulty difficulty(ResourceKey<Level> dimension) {
        if (HARD.equals(dimension)) {
            return Difficulty.HARD;
        }
        return PEACEFUL.equals(dimension) ? Difficulty.PEACEFUL : null;
    }

    public static boolean allowsStructures(ResourceKey<Level> dimension) {
        return !PEACEFUL.equals(dimension);
    }

    private static ResourceKey<Level> key(String name) {
        return ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("rabbitworld", name));
    }
}
