package com.tr4nce.rabbitcore.mixin;

import com.tr4nce.rabbitcore.world.WorldRules;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.StructureManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(StructureManager.class)
abstract class StructureManagerMixin {
    @Shadow @Final private LevelAccessor level;

    @Inject(method = "shouldGenerateStructures", at = @At("HEAD"), cancellable = true)
    private void rabbitcore$applyStructureRule(CallbackInfoReturnable<Boolean> callback) {
        ServerLevel serverLevel = switch (level) {
            case ServerLevel server -> server;
            case WorldGenRegion region -> region.getLevel();
            default -> null;
        };
        if (serverLevel != null && !WorldRules.allowsStructures(serverLevel.dimension())) {
            callback.setReturnValue(false);
        }
    }
}
