package com.joshua.coppergolemjobs;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Replants cleared tree bases using real saplings in the same Copper Chests as its output. */
public final class TreeFarmJob {
    public record Plot(BlockPos pos, Identifier sapling, int size) {
        public static final Codec<Plot> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockPos.CODEC.fieldOf("pos").forGetter(Plot::pos),
                Identifier.CODEC.fieldOf("sapling").forGetter(Plot::sapling),
                Codec.intRange(1, 2).fieldOf("size").forGetter(Plot::size)
        ).apply(i, Plot::new));
        public Plot { pos = pos.immutable(); }
        public List<BlockPos> cells() {
            List<BlockPos> cells = new ArrayList<>();
            for (int x = 0; x < size; x++) for (int z = 0; z < size; z++) cells.add(pos.offset(x, 0, z));
            return cells;
        }
    }

    public static void work(ServerLevel level, CopperGolem golem, WorkerState worker, ChestStorage storage) {
        if (replant(level, golem, worker, storage)) return;
        if (BlockJobs.work(level, golem, worker, storage)) return;
        if (newPlanting(level, golem, worker, storage)) replant(level, golem, worker, storage);
    }

    private static boolean newPlanting(ServerLevel level, CopperGolem golem, WorkerState worker, ChestStorage storage) {
        List<BlockItem> supplies = storage.treeSupplies();
        if (supplies.isEmpty()) { worker.status = "No mature tree; add saplings to a Copper Chest"; return false; }
        int start = Math.floorMod(worker.treeSeedCursor, supplies.size());
        List<BlockPos> ground = new ArrayList<>();
        BlockPos here = golem.blockPosition();
        int cx = (worker.origin.getX() >> 4) << 4, cz = (worker.origin.getZ() >> 4) << 4;
        for (BlockPos p : BlockPos.betweenClosed(Math.max(cx-16,here.getX()-12),Math.max(level.getMinY()+1,here.getY()-2),Math.max(cz-16,here.getZ()-12),
                Math.min(cx+31,here.getX()+12),Math.min(level.getMaxY()-5,here.getY()+2),Math.min(cz+31,here.getZ()+12))) {
            if (WorkerBrain.loaded(level,p) && level.getBlockState(p).isAir()
                    && level.getBlockState(p.below()).is(BlockTags.DIRT)
                    && worker.skipped.getOrDefault(p,0L)<=level.getGameTime()) ground.add(p.immutable());
        }
        ground.sort(Comparator.comparingDouble(p -> p.distSqr(here)));
        for (int offset = 0; offset < supplies.size(); offset++) {
            BlockItem item = supplies.get((start+offset)%supplies.size());
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            int size = id.getPath().equals("dark_oak_sapling") || id.getPath().equals("pale_oak_sapling") ? 2 : 1;
            if (!storage.plan().take(item,size*size)) continue;
            // Keep a matching supply reserved for its remembered site while that site is blocked.
            if (worker.treePlots.stream().anyMatch(p -> p.sapling().equals(id))) continue;
            for (Plot previous : new ArrayList<>(worker.treePlots)) {
                Plot alternative = new Plot(previous.pos(),id,size);
                if (available(level,alternative,item.getBlock(),false)) {
                    worker.treePlots.remove(previous);worker.treePlots.add(alternative);
                    worker.treeSeedCursor=(start+offset+1)%supplies.size();return true;
                }
            }
            for (BlockPos p : ground) {
                Plot plot = new Plot(p,id,size);
                if (plot.cells().stream().anyMatch(cell -> cell.distSqr(here)<4)) continue;
                if (!available(level,plot,item.getBlock(),true)) continue;
                worker.treePlots.add(plot);worker.treeSeedCursor=(start+offset+1)%supplies.size();return true;
            }
        }
        worker.status = "Waiting for a clear planting site or enough saplings for a 2x2 tree";
        return false;
    }

    private static boolean available(ServerLevel level, Plot plot, Block plant, boolean spacing) {
        for (BlockPos p : plot.cells()) {
            if (plantingState(level,p,plant,Set.of())==null) return false;
            if (spacing) for (int h=1;h<=4;h++) {
                if (!WorkerBrain.loaded(level,p.above(h)) || !level.getBlockState(p.above(h)).isAir()) return false;
            }
        }
        if (spacing) for (BlockPos p : BlockPos.betweenClosed(plot.pos().offset(-3,-1,-3),plot.pos().offset(plot.size()+2,1,plot.size()+2))) {
            if (!WorkerBrain.loaded(level,p)) return false;
            BlockState state=level.getBlockState(p);
            if (state.is(BlockTags.LOGS)||state.is(BlockTags.SAPLINGS)||state.getBlock() instanceof net.minecraft.world.level.block.AzaleaBlock) return false;
        }
        return true;
    }

    private static boolean replant(ServerLevel level, CopperGolem golem, WorkerState worker, ChestStorage storage) {
        List<Plot> plots = new ArrayList<>(worker.treePlots);
        plots.sort(Comparator.comparingDouble(p -> p.pos().distSqr(golem.blockPosition())));
        int attempts = 0;
        for (Plot plot : plots) {
            if (++attempts > 256) break;
            if (worker.skipped.getOrDefault(plot.pos(), 0L) > level.getGameTime()) continue;
            Item item = BuiltInRegistries.ITEM.getValue(plot.sapling());
            if (!(item instanceof BlockItem sapling)) continue;
            List<BlockPos> cells = plot.cells();
            if (cells.stream().anyMatch(p -> !WorkerBrain.loaded(level, p) || !WorkerBrain.loaded(level, p.below()))) continue;
            // A player may already have replanted this site; never replace or charge for their plants.
            if (cells.stream().allMatch(p -> level.getBlockState(p).is(sapling.getBlock())
                    || level.getBlockState(p).is(BlockTags.SAPLINGS))) {
                worker.treePlots.remove(plot); continue;
            }
            List<BlockState> plants = new ArrayList<>();
            for (BlockPos p : cells) {
                BlockState plant = plantingState(level, p, sapling.getBlock(), Set.of());
                if (plant == null) break;
                plants.add(plant);
            }
            if (plants.size() != cells.size()) continue;
            ChestStorage.Transaction plan = storage.plan();
            if (!plan.take(item, cells.size())) continue;
            if (!WorkerBrain.approach(level, golem, worker, plot.pos(), false)) {
                if (worker.approach != null) return true;
                continue;
            }
            if (!plan.unchanged()) return true;
            List<BlockState> originals = cells.stream().map(level::getBlockState).toList();
            int placed = 0;
            for (int n = 0; n < cells.size(); n++) {
                if (!level.setBlock(cells.get(n), plants.get(n), Block.UPDATE_CLIENTS)) break;
                placed++;
            }
            if (placed != cells.size() || !plan.commit()) {
                for (int n = 0; n < placed; n++) level.setBlock(cells.get(n), originals.get(n), Block.UPDATE_CLIENTS);
                WorkerBrain.wait(golem, worker, "Replanting interrupted; retrying without spending saplings");
                return true;
            }
            worker.treePlots.remove(plot);
            WorkerBrain.worked(golem, worker, 20);
            worker.status = "Replanted " + cells.size() + " " + plot.sapling().getPath();
            return true;
        }
        return false;
    }

    /** Capture planting sites before felling; add them to worker state only after a successful harvest. */
    public static List<Plot> plots(ServerLevel level, Set<BlockPos> logs, Set<BlockPos> leaves) {
        Map<BlockPos, Identifier> roots = new LinkedHashMap<>();
        List<BlockPos> sorted = new ArrayList<>(logs);
        sorted.sort(Comparator.comparingInt((BlockPos p) -> p.getY()).thenComparingInt(p -> p.getX()).thenComparingInt(p -> p.getZ()));
        for (BlockPos log : sorted) {
            if (logs.contains(log.below())) continue;
            Identifier sapling = saplingFor(level, log, leaves);
            if (sapling == null) continue;
            Item item = BuiltInRegistries.ITEM.getValue(sapling);
            if (!(item instanceof BlockItem plant)) continue;
            BlockPos site = log;
            if (plantingState(level, site, plant.getBlock(), logs) == null) {
                if (!sapling.getPath().equals("mangrove_propagule")) continue;
                site = mangroveSite(level, log, plant.getBlock(), logs);
                if (site == null) continue;
            }
            roots.putIfAbsent(site.immutable(), sapling);
        }
        Set<BlockPos> used = new HashSet<>();
        List<Plot> result = new ArrayList<>();
        for (var root : roots.entrySet()) {
            if (used.contains(root.getKey())) continue;
            String kind = root.getValue().getPath();
            boolean required = kind.equals("dark_oak_sapling") || kind.equals("pale_oak_sapling");
            boolean giant = required || kind.equals("spruce_sapling") || kind.equals("jungle_sapling");
            Plot plot = null;
            if (giant) {
                long best = 0;
                for (int dx = -1; dx <= 0; dx++) for (int dz = -1; dz <= 0; dz++) {
                    Plot candidate = new Plot(root.getKey().offset(dx, 0, dz), root.getValue(), 2);
                    Block plant = ((BlockItem) BuiltInRegistries.ITEM.getValue(root.getValue())).getBlock();
                    if (candidate.cells().stream().anyMatch(p -> used.contains(p)
                            || plantingState(level, p, plant, logs) == null)) continue;
                    long matches = candidate.cells().stream().filter(p -> root.getValue().equals(roots.get(p))).count();
                    if ((!required && matches != 4) || matches <= best) continue;
                    plot = candidate; best = matches;
                }
            }
            if (plot == null && !required) plot = new Plot(root.getKey(), root.getValue(), 1);
            if (plot != null) { result.add(plot); used.addAll(plot.cells()); }
        }
        return result;
    }

    private static BlockState plantingState(ServerLevel level, BlockPos p, Block plant, Set<BlockPos> removed) {
        if (!WorkerBrain.loaded(level, p) || !WorkerBrain.loaded(level, p.below())) return null;
        BlockState existing = level.getBlockState(p);
        BlockState state = plant.defaultBlockState();
        if (!existing.isAir() && !removed.contains(p)) {
            if (!existing.is(Blocks.WATER) || !state.hasProperty(BlockStateProperties.WATERLOGGED)) return null;
        }
        if (!level.getFluidState(p).isEmpty()) {
            if (!state.hasProperty(BlockStateProperties.WATERLOGGED) || !level.getFluidState(p).is(FluidTags.WATER)) return null;
            state = state.setValue(BlockStateProperties.WATERLOGGED, true);
        }
        return state.canSurvive(level, p) ? state : null;
    }

    private static BlockPos mangroveSite(ServerLevel level, BlockPos root, Block sapling, Set<BlockPos> logs) {
        for (int depth = 0; depth <= 8; depth++) for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            BlockPos p = root.offset(dx, -depth, dz);
            if (plantingState(level, p, sapling, logs) != null) return p;
        }
        return null;
    }

    private static Identifier saplingFor(ServerLevel level, BlockPos root, Set<BlockPos> leaves) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(root).getBlock());
        String kind = id.getPath().replaceFirst("^stripped_", "").replaceFirst("_(log|wood)$", "");
        String name = kind.equals("mangrove") ? "mangrove_propagule" : kind + "_sapling";
        if (kind.equals("oak") && leaves.stream().anyMatch(p -> level.getBlockState(p).is(Blocks.AZALEA_LEAVES)
                || level.getBlockState(p).is(Blocks.FLOWERING_AZALEA_LEAVES))
                && leaves.stream().noneMatch(p -> level.getBlockState(p).is(Blocks.OAK_LEAVES))) name = "azalea";
        Identifier result = Identifier.fromNamespaceAndPath(id.getNamespace(), name);
        return BuiltInRegistries.ITEM.getValue(result) instanceof BlockItem ? result : null;
    }
}
