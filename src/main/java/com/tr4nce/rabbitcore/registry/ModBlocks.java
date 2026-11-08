package com.tr4nce.rabbitcore.registry;

import com.tr4nce.rabbitcore.Rabbitcore;
import com.tr4nce.rabbitcore.block.RabbitChestBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * 方块注册中心。
 */
public final class ModBlocks {
    private ModBlocks() {}

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, Rabbitcore.MODID);

    /**
     * 一个“像原版箱子一样”的自定义箱子方块。
     *
     * 这里直接使用 {@link ChestBlock}，并把 BlockEntityType 的 supplier 指向我们的自定义 BlockEntity。
     */
    public static final DeferredHolder<Block, Block> RABBIT_CHEST = BLOCKS.register("rabbit_chest",
            () -> new RabbitChestBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.CHEST).strength(2.5F)));

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
