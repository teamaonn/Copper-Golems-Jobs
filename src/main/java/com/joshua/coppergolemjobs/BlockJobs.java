package com.joshua.coppergolemjobs;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BlockJobs {
    private static final Map<Item, Block> CROPS = new LinkedHashMap<>();
    static {
        CROPS.put(Items.WHEAT_SEEDS, Blocks.WHEAT);
        CROPS.put(Items.CARROT, Blocks.CARROTS);
        CROPS.put(Items.POTATO, Blocks.POTATOES);
        CROPS.put(Items.BEETROOT_SEEDS, Blocks.BEETROOTS);
    }

    public static void tunnel(ServerLevel level, CopperGolem golem, WorkerState worker, ChestStorage storage) {
        BlockPos next = worker.cursor.relative(worker.direction);
        List<BlockPos> cut = new ArrayList<>();
        Direction sideways = worker.direction.getClockWise();
        for (int width = -1; width <= 1; width++) {
            for (int height = 0; height < 3; height++) cut.add(next.relative(sideways, width).above(height));
        }
        for (BlockPos pos : cut) {
            if (!WorkerBrain.loaded(level, pos)) { WorkerBrain.wait(golem, worker, "Stopped before an unloaded chunk or world boundary"); return; }
            BlockState state = level.getBlockState(pos);
            if (state.is(Blocks.BEDROCK) || state.getDestroySpeed(level, pos) < 0 || state.hasBlockEntity()) {
                WorkerBrain.wait(golem, worker, state.is(Blocks.BEDROCK) ? "Reached bedrock" : "Protected or unbreakable block ahead"); return;
            }
        }
        if (!WorkerBrain.loaded(level, next.below())) { WorkerBrain.wait(golem, worker, "Unloaded ground ahead"); return; }
        BlockPos floorPos = next.below();
        BlockState floor = level.getBlockState(floorPos);
        if (floor.getCollisionShape(level, floorPos).isEmpty() || !floor.getFluidState().isEmpty()) {
            if (floor.hasBlockEntity() || floor.getDestroySpeed(level, floorPos) < 0) {
                WorkerBrain.wait(golem, worker, "Protected gap ahead"); return;
            }
            level.setBlock(floorPos, Blocks.COBBLESTONE.defaultBlockState(), Block.UPDATE_ALL);
            worker.structuralBlocks.add(floorPos.immutable());
            worker.status = "Building a cobblestone path through the gap";
        }
        boolean needsCut = cut.stream().anyMatch(p -> !level.getBlockState(p).isAir());
        if (needsCut) {
            if (golem.distanceToSqr(Vec3.atBottomCenterOf(worker.cursor)) > 3.0) {
                golem.getNavigation().moveTo(worker.cursor.getX() + .5, worker.cursor.getY(), worker.cursor.getZ() + .5, 1.0);
                worker.status = "Returning to tunnel face";
                return;
            }
            if (!breakIntoChest(level, golem, worker, storage, cut, new ItemStack(Items.NETHERITE_PICKAXE), true)) return;
            WorkerBrain.worked(golem, worker, 10);
        }
        Vec3 destination = Vec3.atBottomCenterOf(next);
        if (golem.distanceToSqr(destination) < .55) {
            worker.cursor = next;
            worker.pathTicks = 0;
        } else {
            boolean path = golem.getNavigation().moveTo(destination.x, destination.y, destination.z, 0, 1.0);
            worker.status = path ? "Walking into tunnel" : "Tunnel cleared; waiting for a walkable route";
        }
    }

    /** Clears the assigned chunk one walkable layer at a time and spirals downward. */
    public static void quarry(ServerLevel level, CopperGolem golem, WorkerState worker, ChestStorage storage) {
        int y = worker.cursor.getY();
        BlockPos target = WorkerBrain.findBlock(level, golem, worker,
                p -> p.getY() == y && quarryBlock(level, worker, p));
        if (target != null) {
            if (!WorkerBrain.approach(level, golem, worker, target, true)) return;
            ItemStack silk = new ItemStack(Items.NETHERITE_PICKAXE);
            silk.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH), 1);
            if (breakIntoChest(level, golem, worker, storage, List.of(target), silk, true)) WorkerBrain.worked(golem, worker, 6);
            return;
        }
        if (hasQuarryBlocksAt(level, worker, y)) return;
        descendQuarry(level, golem, worker, storage);
    }

    private static boolean quarryBlock(ServerLevel level, WorkerState worker, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !state.isAir() && !worker.structuralBlocks.contains(pos) && !state.is(Blocks.BEDROCK)
                && !state.hasBlockEntity() && state.getDestroySpeed(level, pos) >= 0;
    }

    private static boolean hasQuarryBlocksAt(ServerLevel level, WorkerState worker, int y) {
        int x0 = (worker.origin.getX() >> 4) << 4;
        int z0 = (worker.origin.getZ() >> 4) << 4;
        for (BlockPos p : BlockPos.betweenClosed(x0, y, z0, x0 + 15, y, z0 + 15)) {
            if (WorkerBrain.loaded(level, p) && quarryBlock(level, worker, p)) return true;
        }
        return false;
    }

    private static void descendQuarry(ServerLevel level, CopperGolem golem, WorkerState worker, ChestStorage storage) {
        int nextY = worker.cursor.getY() - 1;
        if (nextY < level.getMinY()) { WorkerBrain.wait(golem, worker, "Reached the bottom of the world"); return; }
        BlockPos step = quarryStep(worker, nextY);
        if (!WorkerBrain.loaded(level, step) || !WorkerBrain.loaded(level, step.above()) || !WorkerBrain.loaded(level, step.below())) {
            WorkerBrain.wait(golem, worker, "Stopped before an unloaded chunk or world boundary"); return;
        }
        if (level.getBlockState(step).is(Blocks.BEDROCK) || level.getBlockState(step.below()).is(Blocks.BEDROCK)) {
            WorkerBrain.wait(golem, worker, "Reached bedrock"); return;
        }
        List<BlockPos> clear = new ArrayList<>();
        if (!level.getBlockState(step).isAir()) clear.add(step);
        if (!level.getBlockState(step.above()).isAir()) clear.add(step.above());
        BlockPos support = step.below();
        if (!worker.structuralBlocks.contains(support)) clear.add(support);
        ItemStack silk = new ItemStack(Items.NETHERITE_PICKAXE);
        silk.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH), 1);
        if (!clear.isEmpty() && !breakIntoChest(level, golem, worker, storage, clear, silk, true)) return;
        level.setBlock(support, Blocks.COBBLESTONE.defaultBlockState(), Block.UPDATE_ALL);
        worker.structuralBlocks.add(support.immutable());
        if (golem.distanceToSqr(Vec3.atBottomCenterOf(step)) < .8) {
            worker.cursor = step;
            worker.clearTarget();
            worker.status = "Quarrying layer " + nextY;
            return;
        }
        golem.getNavigation().moveTo(step.getX() + .5, step.getY(), step.getZ() + .5, 1.0);
        worker.status = "Building the quarry staircase";
    }

    private static BlockPos quarryStep(WorkerState worker, int y) {
        int x0 = (worker.origin.getX() >> 4) << 4;
        int z0 = (worker.origin.getZ() >> 4) << 4;
        int index = Math.floorMod(worker.origin.getY() - y, 60);
        if (index < 16) return new BlockPos(x0 + index, y, z0);
        if (index < 31) return new BlockPos(x0 + 15, y, z0 + index - 15);
        if (index < 46) return new BlockPos(x0 + 45 - index, y, z0 + 15);
        return new BlockPos(x0, y, z0 + 60 - index);
    }

    public static void work(ServerLevel level, CopperGolem golem, WorkerState worker, ChestStorage storage) {
        BlockPos target = WorkerBrain.findBlock(level, golem, worker, p -> eligible(level, p, worker.job));
        if (target == null) { WorkerBrain.wait(golem, worker, "No reachable work in assignment chunk"); return; }
        if (!WorkerBrain.approach(level, golem, worker, target, true)) return;
        BlockState block = level.getBlockState(target);
        switch (worker.job) {
            case SHOVEL -> {
                if (breakIntoChest(level, golem, worker, storage, List.of(target), new ItemStack(Items.NETHERITE_SHOVEL), true)) WorkerBrain.worked(golem, worker, 10);
            }
            case AXE -> fellTree(level, golem, worker, storage, target);
            case HOE -> farm(level, golem, worker, storage, target, block);
            case STICK -> cocoa(level, golem, worker, storage, target, block);
            case SHEARS -> {
                if (breakIntoChest(level, golem, worker, storage, List.of(target), new ItemStack(Items.SHEARS), false)) WorkerBrain.worked(golem, worker, 20);
            }
            default -> {}
        }
    }

    private static boolean eligible(ServerLevel level, BlockPos pos, Job job) {
        BlockState block = level.getBlockState(pos);
        if (block.hasBlockEntity() || block.getDestroySpeed(level, pos) < 0) return false;
        return switch (job) {
            case SHOVEL -> block.is(BlockTags.MINEABLE_WITH_SHOVEL) && !block.is(Blocks.FARMLAND);
            case AXE -> block.is(BlockTags.LOGS) && !block.is(BlockTags.LEAVES);
            case STICK -> block.is(Blocks.COCOA) && block.getValue(CocoaBlock.AGE) == 2;
            case SHEARS -> block.is(Blocks.VINE) && level.getBlockState(pos.above()).is(Blocks.VINE)
                    && !level.getBlockState(pos.below()).is(Blocks.VINE);
            case HOE -> (block.getBlock() instanceof CropBlock crop && crop.isMaxAge(block) && CROPS.containsValue(block.getBlock()))
                    || (block.is(Blocks.FARMLAND) && level.getBlockState(pos.above()).isAir())
                    || ((block.is(Blocks.DIRT) || block.is(Blocks.GRASS_BLOCK) || block.is(Blocks.DIRT_PATH)
                        || block.is(Blocks.COARSE_DIRT) || block.is(Blocks.ROOTED_DIRT))
                        && level.getBlockState(pos.above()).isAir());
            default -> false;
        };
    }

    /** Exact loot is rolled once, then checked before changing any blocks. */
    static boolean breakIntoChest(ServerLevel level, CopperGolem golem, WorkerState worker, ChestStorage storage,
                                  List<BlockPos> positions, ItemStack tool, boolean sealFluids) {
        Set<BlockPos> cut = new LinkedHashSet<>(positions);
        Map<BlockPos, BlockState> originals = new LinkedHashMap<>();
        Set<BlockPos> glass = new LinkedHashSet<>();
        for (BlockPos p : cut) {
            if (!WorkerBrain.loaded(level, p)) return false;
            BlockState b = level.getBlockState(p);
            if (b.hasBlockEntity() || b.getDestroySpeed(level, p) < 0) return false;
            originals.put(p, b);
            if (sealFluids) for (Direction d : Direction.values()) {
                BlockPos neighbor = p.relative(d);
                if (cut.contains(neighbor)) continue;
                if (!WorkerBrain.loaded(level, neighbor)) { WorkerBrain.wait(golem, worker, "Waiting at an unloaded boundary"); return false; }
                if (!level.getFluidState(neighbor).isEmpty()) {
                    BlockState boundary = level.getBlockState(neighbor);
                    if (boundary.hasBlockEntity() || boundary.getDestroySpeed(level, neighbor) < 0) return false;
                    glass.add(neighbor);
                }
            }
        }
        List<ItemStack> output = new ArrayList<>();
        for (Map.Entry<BlockPos, BlockState> entry : originals.entrySet()) output.addAll(Block.getDrops(entry.getValue(), level, entry.getKey(), null, golem, tool));
        // Waterlogged boundary blocks are collected too, instead of silently disappearing.
        for (BlockPos p : glass) output.addAll(Block.getDrops(level.getBlockState(p), level, p, null, golem, tool));
        ChestStorage.Transaction plan = storage.plan();
        if (!plan.add(output)) { WorkerBrain.wait(golem, worker, "Chest needs room for the whole drop"); return false; }
        if (!plan.unchanged()) return false;
        for (BlockPos p : glass) level.setBlock(p, Blocks.GLASS.defaultBlockState(), Block.UPDATE_ALL);
        for (Map.Entry<BlockPos, BlockState> entry : originals.entrySet()) {
            if (entry.getValue().isAir()) continue;
            level.setBlock(entry.getKey(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            worker.structuralBlocks.remove(entry.getKey());
        }
        // All operations above are synchronous on the same server tick.
        if (!plan.commit()) worker.pending.addAll(output);
        if (!positions.isEmpty()) level.levelEvent(2001, positions.getFirst(), Block.getId(originals.get(positions.getFirst())));
        return true;
    }

    private static void fellTree(ServerLevel level, CopperGolem golem, WorkerState worker, ChestStorage storage, BlockPos root) {
        Set<BlockPos> logs = new LinkedHashSet<>();
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty() && logs.size() <= 256) {
            BlockPos p = queue.removeFirst();
            if (!visited.add(p) || !WorkerBrain.loaded(level, p) || p.distSqr(worker.origin) > 4096 || !level.getBlockState(p).is(BlockTags.LOGS)) continue;
            logs.add(p);
            for (BlockPos q : BlockPos.betweenClosed(p.offset(-1, -1, -1), p.offset(1, 1, 1))) if (!visited.contains(q)) queue.add(q.immutable());
        }
        if (logs.size() > 256) { WorkerBrain.wait(golem, worker, "Connected tree exceeds test-build limit (256 logs)"); worker.cooldown = 20; for (BlockPos p : logs) worker.skipped.put(p, level.getGameTime() + 1200); worker.clearTarget(); return; }
        Set<BlockPos> leaves = new LinkedHashSet<>();
        for (BlockPos log : logs) for (BlockPos p : BlockPos.betweenClosed(log.offset(-3, -3, -3), log.offset(3, 3, 3))) {
            if (!WorkerBrain.loaded(level, p) || p.distSqr(worker.origin) > 4096) continue;
            BlockState b = level.getBlockState(p);
            if (b.is(BlockTags.LEAVES) && (!b.hasProperty(BlockStateProperties.PERSISTENT) || !b.getValue(BlockStateProperties.PERSISTENT))) leaves.add(p.immutable());
        }
        if (leaves.isEmpty()) { WorkerBrain.wait(golem, worker, "Log cluster has no natural leaves; waiting"); worker.cooldown = 20; for (BlockPos p : logs) worker.skipped.put(p, level.getGameTime() + 1200); worker.clearTarget(); return; }
        List<BlockPos> tree = new ArrayList<>(leaves);
        tree.addAll(logs);
        if (breakIntoChest(level, golem, worker, storage, tree, new ItemStack(Items.NETHERITE_AXE), false)) WorkerBrain.worked(golem, worker, 40);
    }

    private static void farm(ServerLevel level, CopperGolem golem, WorkerState worker, ChestStorage storage, BlockPos pos, BlockState block) {
        ChestStorage.Transaction plan = storage.plan();
        if (block.getBlock() instanceof CropBlock crop) {
            Item seed = CROPS.entrySet().stream().filter(e -> e.getValue() == block.getBlock()).map(Map.Entry::getKey).findFirst().orElse(null);
            if (seed == null) return;
            List<ItemStack> drops = new ArrayList<>(Block.getDrops(block, level, pos, null, golem, new ItemStack(Items.NETHERITE_HOE)));
            if (!removeOne(drops, seed) && !plan.take(seed, 1)) { WorkerBrain.wait(golem, worker, "Needs a seed to replant"); worker.cooldown = 40; worker.skip(pos, level.getGameTime() + 100); return; }
            if (!plan.add(drops)) { WorkerBrain.wait(golem, worker, "Chest needs room for harvest"); return; }
            if (!plan.commit()) return;
            level.setBlock(pos, crop.getStateForAge(0), Block.UPDATE_ALL);
        } else {
            if (!block.is(Blocks.FARMLAND)) {
                level.setBlock(pos, Blocks.FARMLAND.defaultBlockState().setValue(BlockStateProperties.MOISTURE, 7), Block.UPDATE_ALL);
            }
            for (Map.Entry<Item, Block> crop : CROPS.entrySet()) {
                if (!crop.getValue().defaultBlockState().canSurvive(level, pos.above())) continue;
                if (plan.take(crop.getKey(), 1) && plan.commit()) {
                    level.setBlock(pos.above(), crop.getValue().defaultBlockState(), Block.UPDATE_ALL);
                    WorkerBrain.worked(golem, worker, 20);
                    return;
                }
            }
            WorkerBrain.wait(golem, worker, "Farmland ready; needs seeds, carrots, or potatoes in Copper Chest");
            worker.cooldown = 40;
            worker.skip(pos, level.getGameTime() + 100);
            return;
        }
        WorkerBrain.worked(golem, worker, 20);
    }

    private static void cocoa(ServerLevel level, CopperGolem golem, WorkerState worker, ChestStorage storage, BlockPos pos, BlockState block) {
        List<ItemStack> drops = new ArrayList<>(Block.getDrops(block, level, pos, null, golem, ItemStack.EMPTY));
        ChestStorage.Transaction plan = storage.plan();
        if (!removeOne(drops, Items.COCOA_BEANS) && !plan.take(Items.COCOA_BEANS, 1)) return;
        if (!plan.add(drops)) { WorkerBrain.wait(golem, worker, "Chest needs room for cocoa beans"); return; }
        if (!plan.commit()) return;
        level.setBlock(pos, block.setValue(CocoaBlock.AGE, 0), Block.UPDATE_ALL);
        WorkerBrain.worked(golem, worker, 20);
    }

    private static boolean removeOne(List<ItemStack> stacks, Item item) {
        for (ItemStack stack : stacks) if (stack.is(item) && !stack.isEmpty()) { stack.shrink(1); return true; }
        return false;
    }

    private static boolean waterNearby(ServerLevel level, BlockPos pos) {
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-4, 0, -4), pos.offset(4, 1, 4))) {
            if (WorkerBrain.loaded(level, p) && level.getFluidState(p).is(FluidTags.WATER)) return true;
        }
        return false;
    }
}
