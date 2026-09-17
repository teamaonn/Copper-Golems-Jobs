package com.joshua.coppergolemjobs;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Loaded Copper Chests only. All inventory changes run on the server thread. */
public final class ChestStorage {
    public static final int RANGE = 64;
    private final List<Container> containers;

    private ChestStorage(List<Container> containers) { this.containers = containers; }

    public static ChestStorage nearby(ServerLevel level, Entity worker) {
        BlockPos center = worker.blockPosition();
        List<BlockEntity> found = new ArrayList<>();
        for (int cx = (center.getX() - RANGE) >> 4; cx <= (center.getX() + RANGE) >> 4; cx++) {
            for (int cz = (center.getZ() - RANGE) >> 4; cz <= (center.getZ() + RANGE) >> 4; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (!(be instanceof Container) || be.isRemoved()) continue;
                    BlockPos pos = be.getBlockPos();
                    if (worker.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) > RANGE * RANGE) continue;
                    String id = BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()).getPath();
                    if (!id.equals("copper_chest") && !id.endsWith("_copper_chest")) continue;
                    if (level.getBlockState(pos.above()).isRedstoneConductor(level, pos.above())) continue;
                    found.add(be);
                }
            }
        }
        found.sort(Comparator.comparingDouble(be -> be.getBlockPos().distSqr(center)));
        return new ChestStorage(found.stream().map(be -> (Container) be).toList());
    }

    public boolean exists() { return !containers.isEmpty(); }

    public boolean hasSpace() {
        for (Container container : containers) {
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack s = container.getItem(i);
                if (s.isEmpty() || s.getCount() < Math.min(s.getMaxStackSize(), container.getMaxStackSize())) return true;
            }
        }
        return false;
    }

    public Transaction plan() { return new Transaction(containers); }

    public static final class Transaction {
        private record Slot(Container container, int index, ItemStack original) {}
        private final List<Slot> slots = new ArrayList<>();
        private List<ItemStack> contents = new ArrayList<>();

        public Transaction(List<Container> containers) {
            for (Container c : containers) {
                for (int i = 0; i < c.getContainerSize(); i++) {
                    ItemStack copy = c.getItem(i).copy();
                    slots.add(new Slot(c, i, copy));
                    contents.add(copy.copy());
                }
            }
        }

        public boolean take(Item item, int count) {
            int available = contents.stream().filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum();
            if (available < count) return false;
            for (ItemStack s : contents) {
                if (!s.is(item)) continue;
                int n = Math.min(count, s.getCount());
                s.shrink(n);
                count -= n;
                if (count == 0) break;
            }
            return true;
        }

        /** Simulates the entire output. Failure leaves this plan unchanged. */
        public boolean add(List<ItemStack> output) {
            List<ItemStack> draft = new ArrayList<>(contents.stream().map(ItemStack::copy).toList());
            for (ItemStack incoming : output) {
                ItemStack remaining = incoming.copy();
                for (int pass = 0; pass < 2 && !remaining.isEmpty(); pass++) {
                    for (int i = 0; i < draft.size() && !remaining.isEmpty(); i++) {
                        ItemStack here = draft.get(i);
                        Slot slot = slots.get(i);
                        if (!slot.container.canPlaceItem(slot.index, remaining)) continue;
                        int cap = Math.min(remaining.getMaxStackSize(), slot.container.getMaxStackSize());
                        if (pass == 0 && !here.isEmpty() && ItemStack.isSameItemSameComponents(here, remaining)) {
                            int n = Math.min(remaining.getCount(), Math.max(0, cap - here.getCount()));
                            here.grow(n);
                            remaining.shrink(n);
                        } else if (pass == 1 && here.isEmpty()) {
                            int n = Math.min(remaining.getCount(), cap);
                            draft.set(i, remaining.copyWithCount(n));
                            remaining.shrink(n);
                        }
                    }
                }
                if (!remaining.isEmpty()) return false;
            }
            contents = draft;
            return true;
        }

        public boolean unchanged() {
            for (Slot slot : slots) {
                if (!ItemStack.matches(slot.original, slot.container.getItem(slot.index))) return false;
            }
            return true;
        }

        public boolean commit() {
            if (!unchanged()) return false;
            for (int i = 0; i < slots.size(); i++) {
                Slot slot = slots.get(i);
                if (!ItemStack.matches(slot.original, contents.get(i))) {
                    slot.container.setItem(slot.index, contents.get(i).copy());
                    slot.container.setChanged();
                }
            }
            return true;
        }
    }
}
