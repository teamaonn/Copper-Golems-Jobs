package com.joshua.coppergolemjobs;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;

public final class TreeCutRules {
    public static boolean stripped(BlockState state) {
        String name = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        return name.startsWith("stripped_") || name.contains("_stripped_") || name.endsWith("_stripped");
    }
    public static boolean cuttable(BlockState state) {
        return state.is(BlockTags.LOGS) && !stripped(state) && !state.hasBlockEntity();
    }
}
