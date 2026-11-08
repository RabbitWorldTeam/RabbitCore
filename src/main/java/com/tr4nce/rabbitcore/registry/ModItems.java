package com.tr4nce.rabbitcore.registry;

import com.tr4nce.rabbitcore.Rabbitcore;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class ModItems {
    private ModItems() {}

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, Rabbitcore.MODID);

    public static final DeferredHolder<Item, Item> RABBIT_CHEST = ITEMS.register("rabbit_chest",
            () -> new BlockItem(ModBlocks.RABBIT_CHEST.get(), new Item.Properties()));

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
