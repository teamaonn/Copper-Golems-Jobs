package com.joshua.coppergolemjobs;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

public final class WorkerBrain {
    public static void tick(ServerLevel level, CopperGolem golem, WorkerState state) {
        if (state.cooldown > 0) state.cooldown--;
        if (golem.tickCount % 10 != 0) return;
        ChestStorage storage = ChestStorage.nearby(level, golem);
        if (!storage.exists()) { wait(golem, state, "No Copper Chest within 64 blocks"); return; }
        if (!state.pending.isEmpty()) {
            ChestStorage.Transaction plan = storage.plan();
            if (!plan.add(state.pending) || !plan.commit()) { wait(golem, state, "Chest needs room for collected drops"); return; }
            state.pending.clear();
        }
        if (!storage.hasSpace()) { wait(golem, state, "Copper Chests full"); return; }
        if (state.cooldown > 0) return;
        switch (state.job) {
            case PICKAXE -> BlockJobs.quarry(level, golem, state, storage);
            case SPEAR -> BlockJobs.tunnel(level, golem, state, storage);
            case SHOVEL, AXE, HOE, STICK -> BlockJobs.work(level, golem, state, storage);
            case SHEARS -> {
                if (!AnimalJobs.shear(level, golem, state, storage)) BlockJobs.work(level, golem, state, storage);
            }
            case BUCKET -> AnimalJobs.milk(level, golem, state, storage);
            case SWORD -> AnimalJobs.fight(level, golem, state, storage);
            case FISHING_ROD -> AnimalJobs.fish(level, golem, state, storage);
            default -> {}
        }
    }

    public static void wait(CopperGolem golem, WorkerState state, String reason) {
        golem.getNavigation().stop();
        state.status = reason;
    }

    public static void worked(CopperGolem golem, WorkerState state, int delay) {
        golem.swing(InteractionHand.MAIN_HAND);
        golem.getNavigation().stop();
        state.status = state.job.label;
        state.cooldown = delay;
        state.clearTarget();
    }

    public static boolean loaded(ServerLevel level, BlockPos pos) {
        return level.isInWorldBounds(pos) && level.getWorldBorder().isWithinBounds(pos)
                && level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null;
    }

    public static AABB workBox(CopperGolem golem, WorkerState state) {
        int x = (state.origin.getX() >> 4) << 4;
        int z = (state.origin.getZ() >> 4) << 4;
        return new AABB(x, golem.getY() - 8, z, x + 16, golem.getY() + 9, z + 16);
    }

    public static boolean inReach(ServerLevel level, CopperGolem golem, BlockPos target) {
        Vec3 end = Vec3.atCenterOf(target);
        if (golem.getEyePosition().distanceToSqr(end) > 9) return false;
        BlockHitResult hit = level.clip(new ClipContext(golem.getEyePosition(), end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, golem));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(target);
    }

    public static boolean standable(ServerLevel level, CopperGolem golem, BlockPos pos) {
        if (!loaded(level, pos) || !loaded(level, pos.above()) || !loaded(level, pos.below())) return false;
        BlockState floor = level.getBlockState(pos.below());
        return !floor.is(Blocks.MAGMA_BLOCK)
                && !floor.getCollisionShape(level, pos.below()).isEmpty()
                && floor.getCollisionShape(level, pos.below()).max(Direction.Axis.Y) >= .875
                && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
                && level.getFluidState(pos).isEmpty() && level.getFluidState(pos.below()).isEmpty();
    }

    public static boolean approach(ServerLevel level, CopperGolem golem, WorkerState state, BlockPos target, boolean scoped) {
        if (inReach(level, golem, target)) {
            state.stalledTicks = 0;
            state.recoveryAttempts = 0;
            return true;
        }
        if (target.equals(state.target) && state.pathTicks >= 1200) {
            state.skip(target, level.getGameTime() + 1200);
            wait(golem, state, "Work target could not be reached");
            return false;
        }
        state.status = "Walking to work";
        if (target.equals(state.target) && state.approach != null) {
            if (!golem.getNavigation().isDone() && state.pathTicks < 1200) {
                state.pathTicks += 10;
                state.stalledTicks += 10;
                if (state.progressCheckpoint == Vec3.ZERO) state.progressCheckpoint = golem.position();
                if (state.stalledTicks < 60) return false;
                if (golem.position().distanceToSqr(state.progressCheckpoint) >= .25) {
                    state.progressCheckpoint = golem.position();
                    state.stalledTicks = 0;
                    return false;
                }
            }
            // The path ended short or claimed to be active without moving for three seconds.
            golem.getNavigation().stop();
            state.approach = null;
            state.stalledTicks = 0;
            state.progressCheckpoint = golem.position();
            state.recoveryAttempts++;
            state.status = "Recalculating stalled path";
            if (state.recoveryAttempts >= 3) {
                state.skip(target, level.getGameTime() + 1200);
                state.status = "Skipping blocked target and continuing";
                state.cooldown = 10;
                return false;
            }
        }
        if (!target.equals(state.target)) {
            state.target = target;
            state.pathTicks = 0;
            state.recoveryAttempts = 0;
        }
        state.approach = null;
        List<BlockPos> stands = new ArrayList<>();
        for (int dy = -1; dy <= 1; dy++) for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos pos = target.relative(dir).offset(0, dy, 0);
            if ((!scoped || state.inWorkChunk(pos)) && standable(level, golem, pos)) stands.add(pos);
        }
        stands.sort(Comparator.comparingDouble(p -> p.distSqr(golem.blockPosition())));
        for (BlockPos pos : stands) {
            Path path = golem.getNavigation().createPath(pos, 0);
            if (path == null || !path.canReach()) continue;
            if (scoped) {
                boolean outside = false;
                for (int i = 0; i < path.getNodeCount(); i++) if (!state.inWorkChunk(path.getNode(i).asBlockPos())) { outside = true; break; }
                if (outside) continue;
            }
            golem.getNavigation().moveTo(path, 1.0);
            state.approach = pos;
            state.stalledTicks = 0;
            state.progressCheckpoint = golem.position();
            return false;
        }
        if (buildGlassPath(level, golem, state, target, scoped)) return false;
        wait(golem, state, "No reachable work target");
        // Retry on its own. Reassigning the job must never be required to wake it up.
        state.skip(target, level.getGameTime() + 1200);
        state.cooldown = 10;
        return false;
    }

    /** Gives the worker one safe floor tile when vanilla navigation encounters a gap. */
    private static boolean buildGlassPath(ServerLevel level, CopperGolem golem, WorkerState state, BlockPos target, boolean scoped) {
        BlockPos here = golem.blockPosition();
        List<Direction> directions = new ArrayList<>();
        for (Direction direction : Direction.Plane.HORIZONTAL) directions.add(direction);
        directions.sort(Comparator.comparingInt(d -> here.relative(d).distManhattan(target)));
        for (Direction direction : directions) {
            BlockPos step = here.relative(direction);
            if (scoped && !state.inWorkChunk(step)) continue;
            if (!loaded(level, step) || !loaded(level, step.above()) || !loaded(level, step.below())) continue;
            if (!level.getBlockState(step).getCollisionShape(level, step).isEmpty()
                    || !level.getBlockState(step.above()).getCollisionShape(level, step.above()).isEmpty()) continue;
            BlockPos floor = step.below();
            BlockState floorState = level.getBlockState(floor);
            if (!floorState.getCollisionShape(level, floor).isEmpty() && floorState.getFluidState().isEmpty()) continue;
            if (floorState.hasBlockEntity() || floorState.getDestroySpeed(level, floor) < 0) continue;
            level.setBlock(floor, Blocks.COBBLESTONE.defaultBlockState(), Block.UPDATE_ALL);
            state.structuralBlocks.add(floor.immutable());
            golem.getNavigation().moveTo(step.getX() + .5, step.getY(), step.getZ() + .5, 1.0);
            state.approach = step;
            state.pathTicks = 0;
            state.status = "Building a cobblestone path";
            return true;
        }
        return false;
    }

    /** Scans the assigned work area and a reachable vertical band. */
    public static BlockPos findBlock(ServerLevel level, CopperGolem golem, WorkerState state, Predicate<BlockPos> valid) {
        if (state.target != null && loaded(level, state.target) && valid.test(state.target)) return state.target;
        state.clearTarget();
        state.skipped.entrySet().removeIf(e -> e.getValue() <= level.getGameTime());
        List<BlockPos> candidates = new ArrayList<>();
        int chunkX = (state.origin.getX() >> 4) << 4;
        int chunkZ = (state.origin.getZ() >> 4) << 4;
        // Axes may leave the assignment chunk to reach neighboring trees.
        int x0 = state.job == Job.AXE ? chunkX - 16 : chunkX;
        int z0 = state.job == Job.AXE ? chunkZ - 16 : chunkZ;
        int x1 = state.job == Job.AXE ? chunkX + 31 : chunkX + 15;
        int z1 = state.job == Job.AXE ? chunkZ + 31 : chunkZ + 15;
        if (!loaded(level, state.origin)) return null;
        for (BlockPos p : BlockPos.betweenClosed(x0, Math.max(level.getMinY(), golem.blockPosition().getY() - 6), z0,
                x1, Math.min(level.getMaxY(), golem.blockPosition().getY() + 12), z1)) {
            if (!state.skipped.containsKey(p) && valid.test(p)) candidates.add(p.immutable());
        }
        candidates.sort(Comparator.comparingDouble(p -> p.distSqr(golem.blockPosition())));
        int attempts = 0;
        for (BlockPos p : candidates) {
            if (inReach(level, golem, p)) { state.target = p; return p; }
            if (attempts++ >= 64) break;
            approach(level, golem, state, p, state.job != Job.AXE);
            if (state.approach != null) return p;
        }
        state.cooldown = 20;
        return null;
    }
}
