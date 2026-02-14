package com.tr4nce.rabbitcore;

import com.mojang.logging.LogUtils;
import com.tr4nce.rabbitcore.command.ChestRegistryCommand;
import com.tr4nce.rabbitcore.command.PlayerStatsCommand;
import com.tr4nce.rabbitcore.registry.ModBlockEntities;
import com.tr4nce.rabbitcore.registry.ModBlocks;
import com.tr4nce.rabbitcore.registry.ModItems;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(Rabbitcore.MODID)
public class Rabbitcore {

    // Define mod id in a common place for everything to reference
    public static final String MODID = "rabbitcore";
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();

    public Rabbitcore(FMLJavaModLoadingContext context) {
        var modEventBus = context.getModEventBus();

        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModBlockEntities.register(modEventBus);

        // Register ourselves for game events.
        MinecraftForge.EVENT_BUS.register(this);

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
    }
}
