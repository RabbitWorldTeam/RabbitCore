package com.tr4nce.rabbitcore.event;

import com.mojang.brigadier.tree.LiteralCommandNode;
import com.tr4nce.rabbitcore.Config;
import com.tr4nce.rabbitcore.Rabbitcore;
import com.tr4nce.rabbitcore.world.WorldRules;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.CommandEvent;

import java.util.Set;

@EventBusSubscriber(modid = Rabbitcore.MODID)
public final class TeleportCommandEvents {
    private static final Set<String> FTB_TELEPORT_COMMANDS = Set.of(
            "home", "spawn", "playerspawn", "warp", "back", "rtp", "tpa", "tpahere", "tpaccept");

    private TeleportCommandEvents() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onCommand(CommandEvent event) {
        var context = event.getParseResults().getContext();
        var source = context.getSource();
        if (source.getPlayer() == null || !WorldRules.restrictsTeleportCommands(source.getLevel())) {
            return;
        }

        String path = "";
        String qualifiedRoot = "";
        var additionalCommands = Config.ADDITIONAL_TELEPORT_COMMANDS.get();
        for (var parsed : context.getNodes()) {
            if (!(parsed.getNode() instanceof LiteralCommandNode<?> literal)) {
                break;
            }
            String name = literal.getLiteral();
            boolean root = path.isEmpty();
            if (root) {
                qualifiedRoot = name.substring(0, name.indexOf(':') + 1);
                name = name.substring(qualifiedRoot.length());
            }
            boolean ftbEntry = root || path.equals("ftbessentials");
            path = root ? name : path + " " + name;
            if ((ftbEntry && FTB_TELEPORT_COMMANDS.contains(name))
                    || additionalCommands.contains(path)
                    || additionalCommands.contains(qualifiedRoot + path)) {
                source.sendFailure(Component.translatableWithFallback(
                        "command.rabbitcore.teleport_disabled", "Player teleport commands are disabled."));
                event.setCanceled(true);
                return;
            }
        }
    }
}
