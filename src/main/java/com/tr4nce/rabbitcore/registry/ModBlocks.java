package com.tr4nce.rabbitcore.registry;

import com.tr4nce.rabbitcore.Rabbitcore;
import com.tr4nce.rabbitcore.block.RabbitChestBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 方块注册中心。
 */
public final class ModBlocks {
    private ModBlocks() {}

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, Rabbitcore.MODID);

    /**
     * 一个“像原版箱子一样”的自定义箱子方块。
     *
     * 这里直接使用 {@link ChestBlock}，并把 BlockEntityType 的 supplier 指向我们的自定义 BlockEntity。
     */
    public static final RegistryObject<Block> RABBIT_CHEST = BLOCKS.register("rabbit_chest",
            () -> new RabbitChestBlock(BlockBehaviour.Properties.copy(Blocks.CHEST).strength(2.5F)));

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
