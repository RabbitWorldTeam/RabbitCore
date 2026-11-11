package com.tr4nce.rabbitcore;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class Config {
    public static final ModConfigSpec SERVER_SPEC;
    public static final ModConfigSpec.BooleanValue USE_HARD_DIMENSION_SEED;
    public static final ModConfigSpec.LongValue HARD_DIMENSION_SEED;

    static {
        var builder = new ModConfigSpec.Builder();
        builder.push("worldGeneration");
        USE_HARD_DIMENSION_SEED = builder
                .comment("Use an independent seed for rabbitworld:hard. Existing chunks keep their terrain.")
                .worldRestart()
                .define("useHardDimensionSeed", false);
        HARD_DIMENSION_SEED = builder
                .comment("Seed used by rabbitworld:hard when useHardDimensionSeed is enabled.")
                .worldRestart()
                .defineInRange("hardDimensionSeed", 7263919428147256017L, Long.MIN_VALUE, Long.MAX_VALUE);
        builder.pop();
        SERVER_SPEC = builder.build();
    }

    private Config() {}
}
