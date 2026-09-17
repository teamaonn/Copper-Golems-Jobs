package com.joshua.coppergolemjobs;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class WorkerState {
    public Job job = Job.IDLE;
    public BlockPos origin = BlockPos.ZERO;
    public BlockPos cursor = BlockPos.ZERO;
    public Direction direction = Direction.NORTH;
    public int cooldown;
    public String status = "Idle";
    public BlockPos target;
    public BlockPos approach;
    public int pathTicks;
    public int searchDelay;
    public int stalledTicks;
    public int recoveryAttempts;
    public Vec3 progressCheckpoint = Vec3.ZERO;
    public final List<ItemStack> pending = new ArrayList<>();
    public final Map<BlockPos, Long> skipped = new HashMap<>();
    public final Set<BlockPos> structuralBlocks = new HashSet<>();

    public boolean inWorkChunk(BlockPos pos) {
        return (pos.getX() >> 4) == (origin.getX() >> 4)
                && (pos.getZ() >> 4) == (origin.getZ() >> 4);
    }

    public void assign(Job job, BlockPos pos, Direction direction) {
        this.job = job;
        this.origin = pos.immutable();
        this.cursor = pos.immutable();
        this.direction = direction;
        cooldown = 0;
        skipped.clear();
        structuralBlocks.clear();
        clearTarget();
        status = job == Job.IDLE ? "Idle" : "Looking for a Copper Chest";
    }

    public void clearTarget() {
        target = null;
        approach = null;
        pathTicks = 0;
        searchDelay = 0;
        stalledTicks = 0;
        recoveryAttempts = 0;
        progressCheckpoint = Vec3.ZERO;
    }

    public void skip(BlockPos pos, long until) {
        skipped.put(pos.immutable(), until);
        clearTarget();
    }

    public void save(ValueOutput out) {
        out.putString("CopperJobsJob", job.name());
        out.putLong("CopperJobsOrigin", origin.asLong());
        out.putLong("CopperJobsCursor", cursor.asLong());
        out.putString("CopperJobsDirection", direction.getName());
        out.putInt("CopperJobsCooldown", cooldown);
        out.store("CopperJobsPending", ItemStack.CODEC.listOf(), pending);
        out.store("CopperJobsStructuralBlocks", BlockPos.CODEC.listOf(), new ArrayList<>(structuralBlocks));
    }

    public void load(ValueInput in) {
        try { job = Job.valueOf(in.getStringOr("CopperJobsJob", "IDLE")); }
        catch (IllegalArgumentException ignored) { job = Job.IDLE; }
        origin = BlockPos.of(in.getLongOr("CopperJobsOrigin", 0));
        cursor = BlockPos.of(in.getLongOr("CopperJobsCursor", origin.asLong()));
        Direction saved = Direction.byName(in.getStringOr("CopperJobsDirection", "north"));
        direction = saved != null && saved.getAxis().isHorizontal() ? saved : Direction.NORTH;
        cooldown = Math.max(0, Math.min(1200, in.getIntOr("CopperJobsCooldown", 0)));
        pending.clear();
        pending.addAll(in.read("CopperJobsPending", ItemStack.CODEC.listOf()).orElse(List.of()));
        structuralBlocks.clear();
        structuralBlocks.addAll(in.read("CopperJobsStructuralBlocks", BlockPos.CODEC.listOf()).orElse(List.of()));
        status = job == Job.IDLE ? "Idle" : "Resuming " + job.label;
        clearTarget();
    }
}
