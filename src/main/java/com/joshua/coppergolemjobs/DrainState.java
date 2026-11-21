package com.joshua.coppergolemjobs;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

/** Compact per-column sand ownership; no list containing hundreds of thousands of blocks. */
public final class DrainState {
    public enum Stage { NEW, SURVEY, CLEAR, SURFACE, FILL, WALLS, VERIFY, REMOVE, REPAIR_WALLS, REPAIR_WATER, CHECK_DRY, RETURN, CLEANUP, DONE }
    public Stage stage = Stage.NEW;
    public int minX, maxX, minZ, maxZ, surfaceY, bottomY, index, y;
    public int finishVersion;
    public int structureBottom = Integer.MIN_VALUE;
    public int[] floors = new int[0];
    public int[] sandTops = new int[0];
    public Set<BlockPos> scaffolds = new LinkedHashSet<>();
    public java.util.BitSet completed = new java.util.BitSet();
    public int[] heights = new int[0];
    public transient long spongeMaintenanceTick = Long.MIN_VALUE;
    public transient BlockPos travel;
    public transient int routePhase, stuck;
    public transient net.minecraft.world.phys.Vec3 checkpoint;
    public final transient java.util.ArrayDeque<BlockPos> route = new java.util.ArrayDeque<>();

    public boolean active() { return stage != Stage.NEW && stage != Stage.DONE; }
    public int width() { return maxX - minX + 1; }
    public int depth() { return maxZ - minZ + 1; }
    public int columns() { return width() * depth(); }
    public BlockPos column(int i, int height) {
        int row = i / width(), offset = i % width();
        return new BlockPos((row % 2 == 0 ? minX + offset : maxX - offset), height, minZ + row);
    }
    public boolean wall(BlockPos p) {
        return p.getX() == minX || p.getX() == maxX || p.getZ() == minZ || p.getZ() == maxZ;
    }
    public void begin(BoundingBox monument, int surface) {
        // One clear column between the monument footprint and the permanent wall.
        minX = monument.minX() - 2; maxX = monument.maxX() + 2;
        minZ = monument.minZ() - 2; maxZ = monument.maxZ() + 2;
        finishVersion = 0;
        structureBottom = monument.minY();
        surfaceY = surface; bottomY = surface; index = 0; y = surface;
        floors = new int[columns()]; sandTops = new int[columns()];
        heights = new int[columns()]; java.util.Arrays.fill(heights, Integer.MIN_VALUE);
        completed = new java.util.BitSet();
        java.util.Arrays.fill(floors, Integer.MIN_VALUE);
        java.util.Arrays.fill(sandTops, Integer.MIN_VALUE);
        stage = Stage.SURVEY;
    }
    public void reset() {
        stage = Stage.NEW; index = 0; y = 0; finishVersion = 0; structureBottom = Integer.MIN_VALUE; spongeMaintenanceTick = Long.MIN_VALUE;
        floors = new int[0]; sandTops = new int[0]; scaffolds = new LinkedHashSet<>();
        completed = new java.util.BitSet(); heights = new int[0];
        travel = null; routePhase = 0; stuck = 0; checkpoint = null; route.clear();
    }
    public void next(Stage next) {
        stage = next; index = 0; y = surfaceY; travel = null; routePhase = 0; route.clear();
        completed = new java.util.BitSet(); heights = new int[columns()];
        java.util.Arrays.fill(heights, Integer.MIN_VALUE);
    }
    public void save(ValueOutput out) {
        out.putString("DrainStage", stage.name());
        if (stage == Stage.NEW) return;
        out.putIntArray("DrainBounds", new int[]{minX,maxX,minZ,maxZ,surfaceY,bottomY,index,y});
        out.putInt("DrainFinishVersion", finishVersion);
        out.putInt("DrainStructureBottom", structureBottom);
        out.putIntArray("DrainFloors", floors);
        out.putIntArray("DrainSandTops", sandTops);
        out.putIntArray("DrainCompleted", completed.stream().toArray());
        out.putIntArray("DrainHeights", heights);
        out.store("DrainScaffolds", BlockPos.CODEC.listOf(), new ArrayList<>(scaffolds));
    }
    public void load(ValueInput in) {
        reset();
        try { stage = Stage.valueOf(in.getStringOr("DrainStage", "NEW")); }
        catch (IllegalArgumentException ignored) { stage = Stage.NEW; }
        if (stage == Stage.NEW) return;
        int[] b = in.getIntArray("DrainBounds").orElse(new int[0]);
        if (b.length != 8 || b[1] < b[0] || b[3] < b[2] || (long)b[1]-b[0] > 100 || (long)b[3]-b[2] > 100) { reset(); return; }
        minX=b[0];maxX=b[1];minZ=b[2];maxZ=b[3];surfaceY=b[4];bottomY=b[5];index=b[6];y=b[7];
        finishVersion=in.getIntOr("DrainFinishVersion", 0);
        structureBottom=in.getIntOr("DrainStructureBottom", Integer.MIN_VALUE);
        floors=in.getIntArray("DrainFloors").orElse(new int[0]);
        sandTops=in.getIntArray("DrainSandTops").orElse(new int[0]);
        if (floors.length != columns() || sandTops.length != columns() || index < 0 || index > columns()) { reset(); return; }
        heights=in.getIntArray("DrainHeights").orElse(new int[0]);
        if(heights.length!=columns()) {
            heights=new int[columns()]; java.util.Arrays.fill(heights,Integer.MIN_VALUE);
            completed.set(0,index);
            if(index<columns()) heights[index]=y;
        } else for(int column:in.getIntArray("DrainCompleted").orElse(new int[0])) {
            if(column>=0 && column<columns()) completed.set(column);
        }
        scaffolds.addAll(in.read("DrainScaffolds", BlockPos.CODEC.listOf()).orElse(java.util.List.of()));
    }
}
