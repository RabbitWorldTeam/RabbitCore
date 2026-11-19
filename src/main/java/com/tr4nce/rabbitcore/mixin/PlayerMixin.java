package com.tr4nce.rabbitcore.mixin;

import com.tr4nce.rabbitcore.world.WorldRules;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerMixin {
    @Inject(method = "tryToStartFallFlying", at = @At("HEAD"), cancellable = true)
    private void rabbitcore$checkElytraFlight(CallbackInfoReturnable<Boolean> callback) {
        if ((Object) this instanceof ServerPlayer player && !WorldRules.allowsElytraFlight(player.level())) {
            callback.setReturnValue(false);
        }
    }
}
