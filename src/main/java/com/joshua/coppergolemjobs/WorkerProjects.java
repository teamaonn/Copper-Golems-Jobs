package com.joshua.coppergolemjobs;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.golem.CopperGolem;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

final class WorkerProjects {
    private record Claim(WeakReference<CopperGolem> worker, long until) {}
    private static final Map<ServerLevel, Map<BlockPos, Claim>> CLAIMS = new WeakHashMap<>();

    static boolean available(ServerLevel level, CopperGolem golem, BlockPos pos) {
        Map<BlockPos, Claim> claims = CLAIMS.computeIfAbsent(level, ignored -> new HashMap<>());
        claims.entrySet().removeIf(entry -> {
            Claim claim = entry.getValue();
            CopperGolem owner = claim.worker().get();
            return claim.until()<level.getGameTime() || owner == null || !owner.isAlive()
                    || !entry.getKey().equals(((WorkerAccess)owner).copperJobs$state().target);
        });
        Claim claim = claims.get(pos);
        return claim == null || claim.worker().get() == golem;
    }

    static void reserve(ServerLevel level, CopperGolem golem, BlockPos pos) {
        CLAIMS.computeIfAbsent(level, ignored -> new HashMap<>())
                .put(pos.immutable(), new Claim(new WeakReference<>(golem), level.getGameTime()+100));
    }

    /** Merge persisted construction records when another miner joins an existing chunk. */
    static void quarry(ServerLevel level, CopperGolem golem, WorkerState worker) {
        var peers = level.getEntitiesOfClass(CopperGolem.class, golem.getBoundingBox().inflate(128), other -> {
            WorkerState state = ((WorkerAccess)other).copperJobs$state();
            return other.isAlive() && state.job == Job.PICKAXE && state.inWorkChunk(worker.origin);
        });
        WorkerState lead = worker;
        for (CopperGolem peer:peers) {
            WorkerState state = ((WorkerAccess)peer).copperJobs$state();
            if (state.cursor.getY()<lead.cursor.getY()
                    || (state.cursor.getY() == lead.cursor.getY() && state.origin.asLong()<lead.origin.asLong())) lead = state;
        }
        for (CopperGolem peer:peers) {
            WorkerState state = ((WorkerAccess)peer).copperJobs$state();
            lead.structuralBlocks.addAll(state.structuralBlocks);
            lead.quarryFloor.addAll(state.quarryFloor);
            lead.fluidPlugs.addAll(state.fluidPlugs);
        }
        for (CopperGolem peer:peers) {
            WorkerState state = ((WorkerAccess)peer).copperJobs$state();
            if (state.cursor.getY() != lead.cursor.getY()) {
                state.clearTarget();
                state.preparedQuarryLayer = Integer.MIN_VALUE;
            }
            state.origin = lead.origin;
            state.cursor = lead.cursor;
            state.structuralBlocks = lead.structuralBlocks;
            state.quarryFloor = lead.quarryFloor;
            state.fluidPlugs = lead.fluidPlugs;
        }
    }
}
