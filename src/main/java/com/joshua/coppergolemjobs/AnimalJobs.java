package com.joshua.coppergolemjobs;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import com.joshua.coppergolemjobs.mixin.FishingHookAccess;

import java.util.Comparator;
import java.util.List;

public final class AnimalJobs {
    private static boolean approach(ServerLevel level, CopperGolem golem, WorkerState worker, Entity entity) {
        if (golem.distanceToSqr(entity) <= 6.25 && golem.hasLineOfSight(entity)) return true;
        WorkerBrain.approach(level, golem, worker, entity.blockPosition(), true);
        return false;
    }

    public static void milk(ServerLevel level, CopperGolem golem, WorkerState worker, ChestStorage storage) {
        List<Cow> cows = level.getEntitiesOfClass(Cow.class, WorkerBrain.workBox(golem, worker), c -> c.isAlive() && !c.isBaby() && worker.inWorkChunk(c.blockPosition()));
        cows.sort(Comparator.comparingDouble(golem::distanceToSqr));
        if (cows.isEmpty()) { WorkerBrain.wait(golem, worker, "No adult cow in assignment chunk"); worker.cooldown = 40; return; }
        ChestStorage.Transaction plan = storage.plan();
        if (!plan.take(Items.BUCKET, 1)) { WorkerBrain.wait(golem, worker, "Needs an empty bucket in Copper Chest"); worker.cooldown = 20; return; }
        if (!plan.add(List.of(new ItemStack(Items.MILK_BUCKET)))) { WorkerBrain.wait(golem, worker, "Needs room for a milk bucket"); return; }
        Cow cow = cows.getFirst();
        if (!approach(level, golem, worker, cow)) return;
        if (!plan.commit()) return;
        level.playSound(null, cow, SoundEvents.COW_MILK, SoundSource.NEUTRAL, 1, 1);
        WorkerBrain.worked(golem, worker, 1200);
        worker.status = "Milked cow; one bucket per minute";
    }

    public static boolean shear(ServerLevel level, CopperGolem golem, WorkerState worker, ChestStorage storage) {
        List<Sheep> sheep = level.getEntitiesOfClass(Sheep.class, WorkerBrain.workBox(golem, worker), s -> s.isAlive() && s.readyForShearing() && worker.inWorkChunk(s.blockPosition()));
        sheep.sort(Comparator.comparingDouble(golem::distanceToSqr));
        for (Sheep target : sheep) {
            if (!approach(level, golem, worker, target)) {
                if (worker.approach != null) return true;
                continue;
            }
            LootParams params = new LootParams.Builder(level)
                    .withParameter(LootContextParams.ORIGIN, target.position())
                    .withParameter(LootContextParams.THIS_ENTITY, target)
                    .withParameter(LootContextParams.TOOL, new ItemStack(Items.SHEARS))
                    .create(LootContextParamSets.SHEARING);
            List<ItemStack> wool = level.getServer().reloadableRegistries().getLootTable(BuiltInLootTables.SHEAR_SHEEP).getRandomItems(params);
            ChestStorage.Transaction plan = storage.plan();
            if (!plan.add(wool)) { WorkerBrain.wait(golem, worker, "Chest needs room for wool"); return true; }
            if (!plan.commit()) return true;
            target.setSheared(true);
            level.playSound(null, target, SoundEvents.SHEEP_SHEAR, SoundSource.NEUTRAL, 1, 1);
            WorkerBrain.worked(golem, worker, 20);
            return true;
        }
        return false;
    }

    public static void fight(ServerLevel level, CopperGolem golem, WorkerState worker, ChestStorage storage) {
        // Gather existing mob-farm drops before another attack; never discard a partial stack.
        List<ItemEntity> loose = level.getEntitiesOfClass(ItemEntity.class, golem.getBoundingBox().inflate(3), e -> e.isAlive() && worker.inWorkChunk(e.blockPosition()));
        for (ItemEntity entity : loose) {
            if (!golem.hasLineOfSight(entity)) continue;
            ChestStorage.Transaction pickup = storage.plan();
            if (pickup.add(List.of(entity.getItem())) && pickup.commit()) entity.discard();
        }
        if (!storage.hasSpace()) { WorkerBrain.wait(golem, worker, "Copper Chests full"); return; }
        List<Mob> enemies = level.getEntitiesOfClass(Mob.class, WorkerBrain.workBox(golem, worker), e -> e instanceof Enemy && e.isAlive() && !e.isInvulnerable() && worker.inWorkChunk(e.blockPosition()));
        enemies.sort(Comparator.comparingDouble(golem::distanceToSqr));
        for (Mob enemy : enemies) {
            if (!approach(level, golem, worker, enemy)) {
                if (worker.approach != null) return;
                continue;
            }
            try (DropCapture ignored = new DropCapture(enemy, worker)) {
                enemy.hurtServer(level, level.damageSources().mobAttack(golem), 6.0F);
            }
            if (!worker.pending.isEmpty()) {
                ChestStorage.Transaction deposit = storage.plan();
                if (deposit.add(worker.pending) && deposit.commit()) worker.pending.clear();
            }
            WorkerBrain.worked(golem, worker, 20);
            return;
        }
        WorkerBrain.wait(golem, worker, "No reachable hostile mobs in assignment chunk");
        worker.cooldown = 20;
    }

    public static void fish(ServerLevel level, CopperGolem golem, WorkerState worker, ChestStorage storage) {
        BlockPos water = WorkerBrain.findBlock(level, golem, worker, p -> level.getFluidState(p).is(FluidTags.WATER)
                && level.getFluidState(p).isSource() && level.getBlockState(p.above()).isAir());
        if (water == null) { WorkerBrain.wait(golem, worker, "No reachable fishing water in assignment chunk"); return; }
        if (!WorkerBrain.approach(level, golem, worker, water, true)) return;
        // A context-only bobber lets vanilla fishing predicates and biome loot work.
        FishingHook hook = new FishingHook(EntityTypes.FISHING_BOBBER, level);
        hook.setPos(Vec3.atCenterOf(water));
        FishingHookAccess access = (FishingHookAccess) hook;
        boolean loaded = true;
        for (BlockPos p : BlockPos.betweenClosed(water.offset(-2, -1, -2), water.offset(2, 2, 2))) if (!WorkerBrain.loaded(level, p)) { loaded = false; break; }
        access.copperJobs$setOpenWater(loaded && access.copperJobs$calculateOpenWater(water));
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, hook.position())
                .withParameter(LootContextParams.THIS_ENTITY, hook)
                .withParameter(LootContextParams.TOOL, golem.getMainHandItem())
                .create(LootContextParamSets.FISHING);
        List<ItemStack> catchItems = level.getServer().reloadableRegistries().getLootTable(BuiltInLootTables.FISHING).getRandomItems(params);
        ChestStorage.Transaction plan = storage.plan();
        if (!plan.add(catchItems)) {
            // Keep this actual catch rather than rolling different loot while blocked.
            worker.pending.addAll(catchItems);
            worker.cooldown = 200 + golem.getRandom().nextInt(401);
            WorkerBrain.wait(golem, worker, "Chest needs room for the catch");
            return;
        }
        if (!plan.commit()) return;
        level.sendParticles(ParticleTypes.SPLASH, water.getX() + .5, water.getY() + 1, water.getZ() + .5, 5, .2, .1, .2, .02);
        level.playSound(null, water, SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.NEUTRAL, .5F, 1);
        WorkerBrain.worked(golem, worker, 200 + golem.getRandom().nextInt(401));
    }
}
