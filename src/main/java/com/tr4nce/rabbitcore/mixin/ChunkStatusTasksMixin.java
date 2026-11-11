package com.tr4nce.rabbitcore.mixin;

import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatusTasks;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import net.minecraft.world.level.levelgen.WorldOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ChunkStatusTasks.class)
abstract class ChunkStatusTasksMixin {
    @Redirect(method = "generateStructureStarts", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/WorldOptions;generateStructures()Z"))
    private static boolean rabbitcore$applyStructureRule(WorldOptions options, WorldGenContext context,
                                                         ChunkStep step, StaticCache2D<GenerationChunkHolder> cache,
                                                         ChunkAccess chunk) {
        return context.level().structureManager().shouldGenerateStructures();
    }
}
