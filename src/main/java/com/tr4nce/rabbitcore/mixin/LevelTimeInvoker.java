package com.tr4nce.rabbitcore.mixin;

import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Level.class)
public interface LevelTimeInvoker {
    @Invoker("advanceDaytime")
    long rabbitcore$advanceDaytime();
}
