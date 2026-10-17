package com.joshua.coppergolemjobs;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import java.util.List;

public final class GeneratorJob {
    public static boolean valid(BlockState block) {
        return block.is(Blocks.STONE) || block.is(Blocks.COBBLESTONE);
    }

    public static void work(ServerLevel level, CopperGolem golem, WorkerState state, ChestStorage storage) {
        BlockPos pos = state.generator;
        if (pos == null) { WorkerBrain.wait(golem, state, "Right-click stone or cobblestone with a wooden pickaxe to mark a generator"); return; }
        if (!WorkerBrain.loaded(level, pos)) { WorkerBrain.wait(golem, state, "Waiting for generator chunk to load"); return; }
        if (!valid(level.getBlockState(pos))) { WorkerBrain.wait(golem, state, "Waiting for stone or cobblestone to regenerate"); return; }
        if (!WorkerBrain.approach(level, golem, state, pos, false)) return;
        ItemStack tool = new ItemStack(Items.NETHERITE_PICKAXE);
        tool.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH), 1);
        // Never seal adjacent fluids: they are what makes the generator work.
        if (BlockJobs.breakIntoChest(level, golem, state, storage, List.of(pos), tool, false)) {
            WorkerBrain.worked(golem, state, 20);
        }
    }
}
