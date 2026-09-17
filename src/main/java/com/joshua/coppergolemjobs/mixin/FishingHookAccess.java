package com.joshua.coppergolemjobs.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.projectile.FishingHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(FishingHook.class)
public interface FishingHookAccess {
    @Accessor("openWater") void copperJobs$setOpenWater(boolean value);
    @Invoker("calculateOpenWater") boolean copperJobs$calculateOpenWater(BlockPos pos);
}
