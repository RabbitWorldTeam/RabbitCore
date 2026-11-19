package com.tr4nce.rabbitcore;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

public final class Config {
    public static final ModConfigSpec SERVER_SPEC;
    public static final ModConfigSpec.BooleanValue USE_HARD_DIMENSION_SEED;
    public static final ModConfigSpec.LongValue HARD_DIMENSION_SEED;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ADDITIONAL_TELEPORT_COMMANDS;

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
        builder.push("teleportCommands");
        ADDITIONAL_TELEPORT_COMMANDS = builder
                .comment("Additional command paths to block when rabbitcoreRestrictTeleportCommands is true.",
                        "Use literal names without the leading slash, e.g. \"return\" or \"travel town\".")
                .<String>defineListAllowEmpty("additionalBlockedCommands", List.of(),
                        value -> value instanceof String path && path.matches("[^\\s]+( [^\\s]+)*"));
        builder.pop();
        SERVER_SPEC = builder.build();
    }

    private Config() {}
}
