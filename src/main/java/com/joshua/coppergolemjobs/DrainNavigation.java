package com.joshua.coppergolemjobs;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/** Collision-aware swimming around the outside of the enclosure. */
final class DrainNavigation {
    private record Node(BlockPos pos, int cost, int estimate) {}

    static boolean arriveAtWall(ServerLevel level, CopperGolem golem, WorkerState worker, BlockPos wall) {
        DrainState d = worker.drain;
        if (wall.equals(d.wallTarget) && d.travel != null && d.routePhase == 3) {
            if (golem.position().distanceToSqr(Vec3.atBottomCenterOf(d.travel)) < .04) {
                if (WorkerBrain.inReach(level, golem, wall)) return true;
                d.route.clear(); // A teammate may have placed glass across the previous sightline.
            }
            if (!d.route.isEmpty()) return false;
        }

        Set<BlockPos> goals = new HashSet<>();
        for (BlockPos pos : BlockPos.betweenClosed(wall.offset(-3, -2, -3), wall.offset(3, 3, 3))) {
            if (clear(level, golem, d, pos) && WorkerBrain.visibleFrom(level, golem,
                    Vec3.atBottomCenterOf(pos).add(0, golem.getEyeHeight(), 0), wall)) goals.add(pos.immutable());
        }
        if (goals.isEmpty() || !plan(level, golem, d, goals)) {
            DrainJobs.stop(golem, worker);
            worker.status = "No exterior swimming route to wall at " + wall.toShortString();
            worker.cooldown = 20;
            return false;
        }
        d.wallTarget = wall.immutable();
        d.routePhase = 3;
        d.checkpoint = golem.position();
        d.stuck = 0;
        return false;
    }

    private static boolean clear(ServerLevel level, CopperGolem golem, DrainState d, BlockPos pos) {
        if (pos.getX() < d.minX - 10 || pos.getX() > d.maxX + 10
                || pos.getZ() < d.minZ - 10 || pos.getZ() > d.maxZ + 10
                || pos.getY() < d.bottomY - 2 || pos.getY() > d.surfaceY + 2) return false;
        // Underwater routes remain outside the wall, keeping the monument rooms out of reach.
        if (pos.getY() <= d.surfaceY && pos.getX() >= d.minX && pos.getX() <= d.maxX
                && pos.getZ() >= d.minZ && pos.getZ() <= d.maxZ) return false;
        if (!WorkerBrain.loaded(level, pos) || !WorkerBrain.loaded(level, pos.above())) return false;
        return !level.getBlockCollisions(golem, golem.getBoundingBox()
                .move(Vec3.atBottomCenterOf(pos).subtract(golem.position()))).iterator().hasNext();
    }

    private static int distance(BlockPos pos, Set<BlockPos> goals) {
        int best = Integer.MAX_VALUE;
        for (BlockPos goal : goals) best = Math.min(best, pos.distManhattan(goal));
        return best;
    }

    private static boolean plan(ServerLevel level, CopperGolem golem, DrainState d, Set<BlockPos> goals) {
        BlockPos start = golem.blockPosition();
        Map<BlockPos, BlockPos> previous = new HashMap<>();
        Map<BlockPos, Integer> costs = new HashMap<>();
        var queue = new PriorityQueue<Node>(Comparator.comparingInt((Node n) -> n.cost() + n.estimate())
                .thenComparingInt(Node::estimate));
        previous.put(start, start);
        costs.put(start, 0);
        queue.add(new Node(start, 0, distance(start, goals)));
        for (int budget = 0; budget < 12000 && !queue.isEmpty(); budget++) {
            Node node = queue.remove();
            if (node.cost() != costs.get(node.pos())) continue;
            if (goals.contains(node.pos())) {
                var route = new ArrayDeque<BlockPos>();
                BlockPos pos = node.pos();
                while (!pos.equals(start)) {
                    route.addFirst(pos);
                    pos = previous.get(pos);
                }
                if (route.isEmpty()) route.add(node.pos());
                d.route.clear();
                d.route.addAll(route);
                d.travel = node.pos();
                return true;
            }
            for (Direction direction : Direction.values()) {
                BlockPos next = node.pos().relative(direction);
                int cost = node.cost() + 1;
                if (cost >= costs.getOrDefault(next, Integer.MAX_VALUE) || !clear(level, golem, d, next)) continue;
                costs.put(next, cost);
                previous.put(next, node.pos());
                queue.add(new Node(next, cost, distance(next, goals)));
            }
        }
        return false;
    }

    static void motion(ServerLevel level, CopperGolem golem, WorkerState worker) {
        DrainState d = worker.drain;
        if (d.route.isEmpty()) return;
        BlockPos next = d.route.getFirst();
        if (!clear(level, golem, d, next)) {
            d.route.clear();
            return;
        }
        Vec3 destination = Vec3.atBottomCenterOf(next);
        Vec3 delta = destination.subtract(golem.position());
        if (delta.lengthSqr() < .0025) {
            d.route.removeFirst();
            return;
        }
        if (delta.lengthSqr() > .0484) delta = delta.normalize().scale(.22);
        golem.getNavigation().stop();
        golem.setNoGravity(true);
        golem.setDeltaMovement(Vec3.ZERO);
        golem.move(MoverType.SELF, delta);
        if (++d.stuck >= 40) {
            if (d.checkpoint != null && golem.position().distanceToSqr(d.checkpoint) < .04) d.route.clear();
            d.checkpoint = golem.position();
            d.stuck = 0;
        }
    }
}
