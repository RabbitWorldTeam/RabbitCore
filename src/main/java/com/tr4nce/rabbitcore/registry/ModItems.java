package com.tr4nce.rabbitcore.registry;

import com.tr4nce.rabbitcore.Rabbitcore;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    private ModItems() {}

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, Rabbitcore.MODID);

    public static final RegistryObject<Item> RABBIT_CHEST = ITEMS.register("rabbit_chest",
            () -> new BlockItem(ModBlocks.RABBIT_CHEST.get(), new Item.Properties()));

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
