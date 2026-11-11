package com.tr4nce.rabbitcore.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.tr4nce.rabbitcore.Rabbitcore;
import com.tr4nce.rabbitcore.world.DimensionClock;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public final class WorldCommand {
    private WorldCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(Rabbitcore.MODID)
                .then(Commands.literal("world").requires(source -> source.hasPermission(2))
                        .then(Commands.literal("list").executes(context -> list(context.getSource())))
                        .then(timeCommand())
                        .then(Commands.literal("tp")
                                .then(Commands.argument("dimension", DimensionArgument.dimension())
                                        .then(Commands.argument("position", Vec3Argument.vec3())
                                                .executes(context -> teleport(context.getSource(),
                                                        context.getSource().getPlayerOrException(),
                                                        DimensionArgument.getDimension(context, "dimension"),
                                                        Vec3Argument.getVec3(context, "position"))))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> timeCommand() {
        return Commands.literal("time")
                .then(Commands.argument("dimension", DimensionArgument.dimension())
                        .executes(context -> showTime(context.getSource(),
                                DimensionArgument.getDimension(context, "dimension")))
                        .then(Commands.literal("set")
                                .then(Commands.argument("ticks", IntegerArgumentType.integer(0))
                                        .executes(context -> setTime(context.getSource(),
                                                DimensionArgument.getDimension(context, "dimension"),
                                                IntegerArgumentType.getInteger(context, "ticks")))))
                        .then(Commands.literal("pause")
                                .executes(context -> setCycle(context.getSource(),
                                        DimensionArgument.getDimension(context, "dimension"), false)))
                        .then(Commands.literal("resume")
                                .executes(context -> setCycle(context.getSource(),
                                        DimensionArgument.getDimension(context, "dimension"), true))));
    }

    private static int showTime(CommandSourceStack source, ServerLevel level) {
        source.sendSuccess(() -> Component.literal("%s | day time %d | cycle %s".formatted(
                level.dimension().location(), level.getDayTime(),
                DimensionClock.cycles(level) ? "running" : "paused")), false);
        return 1;
    }

    private static int setTime(CommandSourceStack source, ServerLevel level, int ticks) {
        level.setDayTime(ticks);
        level.setDayTimeFraction(0);
        source.getServer().forceTimeSynchronization();
        return showTime(source, level);
    }

    private static int setCycle(CommandSourceStack source, ServerLevel level, boolean enabled) {
        DimensionClock.setCycles(level, enabled);
        source.getServer().forceTimeSynchronization();
        return showTime(source, level);
    }

    private static int list(CommandSourceStack source) {
        int count = 0;
        for (ServerLevel level : source.getServer().getAllLevels()) {
            source.sendSuccess(() -> Component.literal("%s | %s | structures %s".formatted(
                    level.dimension().location(), level.getDifficulty().getKey(),
                    level.structureManager().shouldGenerateStructures() ? "on" : "off")), false);
            count++;
        }
        return count;
    }

    private static int teleport(CommandSourceStack source, ServerPlayer player, ServerLevel level, Vec3 position) {
        if (!level.isInWorldBounds(BlockPos.containing(position))) {
            source.sendFailure(Component.literal("The destination is outside the world bounds."));
            return 0;
        }
        player.teleportTo(level, position.x, position.y, position.z, player.getYRot(), player.getXRot());
        return 1;
    }
}
