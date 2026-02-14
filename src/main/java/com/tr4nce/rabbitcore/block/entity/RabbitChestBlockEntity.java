package com.tr4nce.rabbitcore.block.entity;

import com.tr4nce.rabbitcore.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class RabbitChestBlockEntity extends ChestBlockEntity {

    public RabbitChestBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RABBIT_CHEST.get(), pos, state);
    }
}
