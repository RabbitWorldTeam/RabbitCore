package com.tr4nce.rabbitcore.bridge;

import com.tr4nce.rabbitcore.Rabbitcore;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.bus.api.*;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = Rabbitcore.MODID)
public final class BridgeEvents {
    private static BridgeService service;
    private BridgeEvents() {}
    @SubscribeEvent public static void started(ServerStartedEvent event) {
        String version = ModList.get().getModContainerById(Rabbitcore.MODID).orElseThrow().getModInfo().getVersion().toString();
        service = new BridgeService(event.getServer(), version);
    }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {
        if (service != null) { service.close(); service = null; }
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (service != null) service.tick();
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void chat(ServerChatEvent event) {
        if (!event.isCanceled() && service != null) service.gameChat(event.getPlayer(), event.getMessage().getString());
    }
    @SubscribeEvent public static void joined(PlayerEvent.PlayerLoggedInEvent event) {
        if (service != null && event.getEntity() instanceof ServerPlayer player) service.presence(player, true);
    }
    @SubscribeEvent public static void left(PlayerEvent.PlayerLoggedOutEvent event) {
        if (service != null && event.getEntity() instanceof ServerPlayer player) service.presence(player, false);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void died(LivingDeathEvent event) {
        if (!event.isCanceled() && service != null && event.getEntity() instanceof ServerPlayer player) {
            service.death(player);
        }
    }
    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        var root = Commands.literal("rabbit_bridge");
        for (String action : new String[]{"status", "reload", "reconnect"}) {
            root.then(Commands.literal(action).requires(source -> source.hasPermission(2)).executes(context -> {
                String response = service == null ? "桥接服务尚未启动" : switch (action) {
                    case "reload" -> service.reload(); case "reconnect" -> service.reconnect(); default -> service.status();
                };
                context.getSource().sendSuccess(() -> Component.literal(response), false); return 1;
            }));
        }
        for (String kind : new String[]{"sound", "receive"}) {
            boolean sound = kind.equals("sound");
            var command = Commands.literal(kind).executes(context -> reply(context.getSource(), sound, null, false));
            command.then(Commands.literal("on").executes(context -> reply(context.getSource(), sound, true, false)));
            command.then(Commands.literal("off").executes(context -> reply(context.getSource(), sound, false, false)));
            if (sound) command.then(Commands.literal("test").executes(context -> reply(context.getSource(), true, null, true)));
            root.then(command);
        }
        event.getDispatcher().register(root);
    }
    private static int reply(net.minecraft.commands.CommandSourceStack source, boolean sound, Boolean enabled, boolean test)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        String response = service == null ? "桥接服务尚未启动" : test ? service.testSound(player) : service.preference(player, sound, enabled);
        source.sendSuccess(() -> Component.literal(response), false); return 1;
    }
}
