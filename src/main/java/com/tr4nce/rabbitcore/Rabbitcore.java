package com.tr4nce.rabbitcore;

import com.mojang.logging.LogUtils;
import com.tr4nce.rabbitcore.command.ChestRegistryCommand;
import com.tr4nce.rabbitcore.command.PlayerStatsCommand;
import com.tr4nce.rabbitcore.command.WorldCommand;
import com.tr4nce.rabbitcore.quest.WeeklyQuests;
import com.tr4nce.rabbitcore.registry.ModBlockEntities;
import com.tr4nce.rabbitcore.registry.ModBlocks;
import com.tr4nce.rabbitcore.registry.ModItems;
import com.tr4nce.rabbitcore.world.WorldRules;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.bus.api.IEventBus;
import org.slf4j.Logger;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(Rabbitcore.MODID)
public class Rabbitcore {

    // Define mod id in a common place for everything to reference
    public static final String MODID = "rabbitcore";
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();

    public Rabbitcore(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.SERVER, Config.SERVER_SPEC);
        WorldRules.register();
        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        WeeklyQuests.register();

        // Register ourselves for game events.
        NeoForge.EVENT_BUS.register(this);

        LOGGER.info("RabbitCore loaded");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("RabbitCore server starting");
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        ChestRegistryCommand.register(event.getDispatcher());
        PlayerStatsCommand.register(event.getDispatcher());
        WorldCommand.register(event.getDispatcher());
    }
}
