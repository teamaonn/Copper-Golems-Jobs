package com.joshua.coppergolemjobs;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

final class QuarryWalkway {
    private static final int EDGE_MARGIN = 2;
    private static final int SEARCH_LIMIT = 512;

    private record Waypoint(BlockPos position, BlockPos firstStep, int cost, boolean needsFloor) {}

    /** Plans through existing and potential floor tiles. Only the returned step is built. */
    static BlockPos nextStep(ServerLevel level, CopperGolem golem, WorkerState worker, BlockPos target,
                             Set<BlockPos> rejected, boolean staircase) {
        BlockPos start = golem.blockPosition();
        int minX = ((worker.origin.getX() >> 4) << 4) - EDGE_MARGIN;
        int minZ = ((worker.origin.getZ() >> 4) << 4) - EDGE_MARGIN;
        int width = 15 + 2 * EDGE_MARGIN;
        int minY = Math.min(start.getY(), target.getY()) - 2;
        int maxY = Math.max(start.getY(), target.getY()) + 2;
        // Climb back toward a work layer before routing a lateral detour. Replanning a long
        // climb one tile at a time can otherwise favor the same lower ledge on every pass.
        if (Math.abs(start.getY() - target.getY()) > 2) {
            BlockPos climb = heightRecoveryStep(level, golem, worker, start, target, rejected);
            if (climb != null) return climb;
        }
        var queue = new PriorityQueue<Waypoint>(Comparator
                .comparingInt((Waypoint point) -> point.cost() + distance(point.position(), target))
                .thenComparingInt(point -> distance(point.position(), target)));
        Map<BlockPos, Integer> costs = new HashMap<>();
        queue.add(new Waypoint(start, null, 0, false));
        costs.put(start, 0);

        for (int checked = 0; checked < SEARCH_LIMIT && !queue.isEmpty(); checked++) {
            Waypoint current = queue.remove();
            if (current.cost() != costs.get(current.position())) continue;
            if (current.firstStep() != null && reachesWork(level, golem, current, target, staircase)) {
                return current.firstStep();
            }
            for (Direction direction : Direction.Plane.HORIZONTAL) for (int dy = -1; dy <= 1; dy++) {
                BlockPos pos = current.position().relative(direction).offset(0, dy, 0);
                if (pos.getX() < minX || pos.getX() > minX + width || pos.getZ() < minZ || pos.getZ() > minZ + width
                        || pos.getY() < minY || pos.getY() > maxY || pos.below().equals(target)
                        || pos.below().equals(target.above())) continue;
                boolean first = current.firstStep() == null;
                if (first && rejected.contains(pos)) continue;

                if (!walkableOrBuildable(level, golem, pos)) continue;
                boolean needsFloor = level.getBlockState(pos.below()).isAir();
                int cost = current.cost() + 1 + Math.abs(dy) + (needsFloor ? 1 : 0);
                if (cost >= costs.getOrDefault(pos, Integer.MAX_VALUE)) continue;

                costs.put(pos, cost);
                queue.add(new Waypoint(pos, first ? pos : current.firstStep(), cost, needsFloor));
            }
        }
        return null;
    }

    private static BlockPos heightRecoveryStep(ServerLevel level, CopperGolem golem, WorkerState worker,
                                               BlockPos start, BlockPos target, Set<BlockPos> rejected) {
        int dy = Integer.compare(target.getY(), start.getY());
        List<BlockPos> steps = new ArrayList<>();
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos side = start.relative(direction);
            steps.add(side);
            steps.add(side.offset(0, dy, 0));
        }
        steps.sort(Comparator.comparingInt(pos -> distance(pos, target)));
        for (BlockPos pos : steps) {
            if (distance(pos, target) >= distance(start, target)) break;
            if (worker.inWorkChunk(pos) && !rejected.contains(pos) && !pos.below().equals(target)
                    && !pos.below().equals(target.above()) && walkableOrBuildable(level, golem, pos)) return pos;
        }
        return null;
    }

    private static boolean walkableOrBuildable(ServerLevel level, CopperGolem golem, BlockPos pos) {
        if (!WorkerBrain.loaded(level, pos) || !WorkerBrain.loaded(level, pos.above())
                || !WorkerBrain.loaded(level, pos.below())) return false;
        if (level.getBlockState(pos.below()).isAir()) {
            return level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir();
        }
        return WorkerBrain.standable(level, golem, pos);
    }

    private static int distance(BlockPos pos, BlockPos target) {
        return pos.distManhattan(target) + Math.abs(pos.getY() - target.getY());
    }

    private static boolean reachesWork(ServerLevel level, CopperGolem golem, Waypoint point, BlockPos target,
                                       boolean staircase) {
        BlockPos pos = point.position();
        if (staircase) return pos.equals(target);
        if (pos.distSqr(target) > 16) return false;
        Vec3 feet = point.needsFloor() ? Vec3.atBottomCenterOf(pos) : WorkerBrain.standingPosition(level, pos);
        return WorkerBrain.visibleFrom(level, golem, feet.add(0, golem.getEyeHeight(), 0), target);
    }
}
