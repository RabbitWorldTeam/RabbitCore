package com.tr4nce.rabbitcore.event;

import com.tr4nce.rabbitcore.Rabbitcore;
import com.tr4nce.rabbitcore.client.renderer.RabbitChestRenderer;
import com.tr4nce.rabbitcore.registry.ModBlockEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 客户端侧绑定：注册兔子箱子的 BlockEntity 渲染器。
 */
@Mod.EventBusSubscriber(modid = Rabbitcore.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents {
    private ClientModEvents() {}

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // 使用自定义的 RabbitChestRenderer，内部仍然复用原版 ChestRenderer 的模型和动画
        event.registerBlockEntityRenderer(ModBlockEntities.RABBIT_CHEST.get(), RabbitChestRenderer::new);
    }
}
