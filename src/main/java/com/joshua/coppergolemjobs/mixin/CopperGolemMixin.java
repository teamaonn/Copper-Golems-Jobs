package com.joshua.coppergolemjobs.mixin;

import com.joshua.coppergolemjobs.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CopperGolem.class)
public abstract class CopperGolemMixin implements WorkerAccess {
    @Unique private final WorkerState copperJobs$state = new WorkerState();
    @Override public WorkerState copperJobs$state() { return copperJobs$state; }

    @Inject(method = "customServerAiStep", at = @At("HEAD"), cancellable = true)
    private void copperJobs$work(ServerLevel level, CallbackInfo ci) {
        if (copperJobs$state.job != Job.IDLE) {
            WorkerBrain.tick(level, (CopperGolem) (Object) this, copperJobs$state);
            ci.cancel();
        }
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void copperJobs$save(ValueOutput output, CallbackInfo ci) { copperJobs$state.save(output); }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void copperJobs$load(ValueInput input, CallbackInfo ci) { copperJobs$state.load(input); }

    @Inject(method = "dropEquipment", at = @At("TAIL"))
    private void copperJobs$releaseCollectedDrops(ServerLevel level, CallbackInfo ci) {
        CopperGolem golem = (CopperGolem) (Object) this;
        for (var stack : copperJobs$state.pending) golem.spawnAtLocation(level, stack.copy());
        copperJobs$state.pending.clear();
    }
}
