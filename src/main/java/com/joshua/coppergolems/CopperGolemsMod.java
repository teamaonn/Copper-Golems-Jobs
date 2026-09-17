package com.joshua.coppergolems;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class CopperGolemsMod implements ModInitializer {
    public static final String MOD_ID = "coppergolems";
    public static final int STARTER_HEART_MAX_ENERGY = 100;

    private static final Set<UUID> HEARTED_GOLEMS = new HashSet<>();
    private static final Set<UUID> PICKAXE_READY_GOLEMS = new HashSet<>();

    public static final ResourceKey<Item> STARTER_HEART_KEY = itemKey("starter_heart");

    public static final Item STARTER_HEART = register(
            STARTER_HEART_KEY,
            new Item(new Item.Properties().setId(STARTER_HEART_KEY).stacksTo(16))
    );

    @Override
    public void onInitialize() {
        UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
            if (!isCopperGolem(entity)) {
                return InteractionResult.PASS;
            }

            ItemStack stack = player.getItemInHand(hand);
            UUID golemId = entity.getUUID();

            if (stack.getItem() == STARTER_HEART) {
                if (level.isClientSide()) {
                    return InteractionResult.CONSUME;
                }

                if (HEARTED_GOLEMS.contains(golemId)) {
                    player.sendSystemMessage(Component.literal("This copper golem already has a Starter Heart."));
                    return InteractionResult.CONSUME;
                }

                HEARTED_GOLEMS.add(golemId);
                if (!player.isCreative()) {
                    stack.shrink(1);
                }

                player.sendSystemMessage(Component.literal("Starter Heart installed. Max energy: " + STARTER_HEART_MAX_ENERGY + "."));
                return InteractionResult.CONSUME;
            }

            if (stack.is(ItemTags.PICKAXES)) {
                if (level.isClientSide()) {
                    return InteractionResult.CONSUME;
                }

                if (!HEARTED_GOLEMS.contains(golemId)) {
                    player.sendSystemMessage(Component.literal("Install a Starter Heart first."));
                    return InteractionResult.CONSUME;
                }

                PICKAXE_READY_GOLEMS.add(golemId);
                player.sendSystemMessage(Component.literal("Pickaxe recognized. Tool storage is next."));
                return InteractionResult.CONSUME;
            }

            return InteractionResult.PASS;
        });
    }

    private static boolean isCopperGolem(Entity entity) {
        Identifier entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return entityId != null && entityId.toString().equals("minecraft:copper_golem");
    }

    private static ResourceKey<Item> itemKey(String path) {
        return ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MOD_ID, path));
    }

    private static Item register(ResourceKey<Item> key, Item item) {
        return Registry.register(BuiltInRegistries.ITEM, key, item);
    }
}
