package com.tr4nce.rabbitcore.event;

import com.tr4nce.rabbitcore.Rabbitcore;
import com.tr4nce.rabbitcore.client.renderer.RabbitChestRenderer;
import com.tr4nce.rabbitcore.registry.ModBlockEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * 客户端侧绑定：注册兔子箱子的 BlockEntity 渲染器。
 */
@EventBusSubscriber(modid = Rabbitcore.MODID, value = Dist.CLIENT)
public final class ClientModEvents {
    private ClientModEvents() {}

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // 使用自定义的 RabbitChestRenderer，内部仍然复用原版 ChestRenderer 的模型和动画
        event.registerBlockEntityRenderer(ModBlockEntities.RABBIT_CHEST.get(), RabbitChestRenderer::new);
    }
}
