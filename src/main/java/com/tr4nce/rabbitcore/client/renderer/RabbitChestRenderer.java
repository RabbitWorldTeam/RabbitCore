package com.tr4nce.rabbitcore.client.renderer;

import com.tr4nce.rabbitcore.block.entity.RabbitChestBlockEntity;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.ChestRenderer;
import net.minecraft.client.resources.model.Material;
import net.minecraft.world.level.block.state.properties.ChestType;
import org.jetbrains.annotations.NotNull;

public class RabbitChestRenderer extends ChestRenderer<RabbitChestBlockEntity> {
    public RabbitChestRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected @NotNull Material getMaterial(@NotNull RabbitChestBlockEntity blockEntity, @NotNull ChestType chestType) {
        return Sheets.CHEST_LOCATION;
    }
}
