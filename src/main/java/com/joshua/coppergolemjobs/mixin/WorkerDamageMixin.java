package com.joshua.coppergolemjobs.mixin;

import com.joshua.coppergolemjobs.Job;
import com.joshua.coppergolemjobs.WorkerAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class WorkerDamageMixin {
    @Inject(method = "push", at = @At("HEAD"), cancellable = true)
    private void copperJobs$avoidWorkerShoving(Entity other, CallbackInfo ci) {
        if ((Object)this instanceof WorkerAccess self && other instanceof WorkerAccess teammate
                && self.copperJobs$state().job != Job.IDLE && self.copperJobs$state().job != Job.SWORD
                && teammate.copperJobs$state().job != Job.IDLE && teammate.copperJobs$state().job != Job.SWORD) ci.cancel();
    }
    @Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
    private void copperJobs$protectWorker(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if ((Object)this instanceof WorkerAccess access) {
            Job job = access.copperJobs$state().job;
            if (job != Job.IDLE && job != Job.SWORD) cir.setReturnValue(false);
        }
    }
}
