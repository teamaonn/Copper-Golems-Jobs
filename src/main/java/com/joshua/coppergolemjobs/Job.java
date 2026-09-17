package com.joshua.coppergolemjobs;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public enum Job {
    IDLE("Idle"), PICKAXE("Mining"), SHOVEL("Excavating"), AXE("Tree cutting"),
    FISHING_ROD("Fishing"), SPEAR("3x3 tunneling"), HOE("Farming"),
    SWORD("Hostile mob fighting"), STICK("Cocoa farming"), BUCKET("Milking"), SHEARS("Shearing");

    public final String label;
    Job(String label) { this.label = label; }

    public static Job of(ItemStack stack) {
        if (stack.isEmpty()) return IDLE;
        if (stack.is(ItemTags.PICKAXES)) return PICKAXE;
        if (stack.is(ItemTags.SHOVELS)) return SHOVEL;
        if (stack.is(ItemTags.AXES)) return AXE;
        if (stack.is(ItemTags.HOES)) return HOE;
        if (stack.is(ItemTags.SWORDS)) return SWORD;
        if (stack.is(Items.FISHING_ROD)) return FISHING_ROD;
        if (stack.is(Items.STICK)) return STICK;
        if (stack.is(Items.BUCKET)) return BUCKET;
        if (stack.is(Items.SHEARS)) return SHEARS;
        // Vanilla 26.2 spears, including copper. Also recognizes matching modded spears.
        if (BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().endsWith("_spear")) return SPEAR;
        return null;
    }
}
