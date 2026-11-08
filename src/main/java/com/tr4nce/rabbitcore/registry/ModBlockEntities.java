package com.tr4nce.rabbitcore.registry;

import com.tr4nce.rabbitcore.Rabbitcore;
import com.tr4nce.rabbitcore.block.entity.RabbitChestBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * BlockEntity 注册中心。
 */
public final class ModBlockEntities {
    private ModBlockEntities() {}

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Rabbitcore.MODID);

    /**
     * 继承原版箱子的 BlockEntityType。
     *
     * 注意：需要一个对应的方块注册（例如 `ModBlocks.RABBIT_CHEST`），否则无法构建 BlockEntityType。
     */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RabbitChestBlockEntity>> RABBIT_CHEST =
            BLOCK_ENTITY_TYPES.register("rabbit_chest",
                    () -> BlockEntityType.Builder.of(RabbitChestBlockEntity::new, ModBlocks.RABBIT_CHEST.get()).build(null));

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITY_TYPES.register(modEventBus);
    }
}
