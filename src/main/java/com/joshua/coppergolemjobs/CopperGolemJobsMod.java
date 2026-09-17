package com.joshua.coppergolemjobs;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.entity.animal.golem.CopperGolemState;
import net.minecraft.world.item.ItemStack;

public final class CopperGolemJobsMod implements ModInitializer {
    @Override
    public void onInitialize() {
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (!(entity instanceof CopperGolem golem) || player.isSpectator() || hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            WorkerState state = ((WorkerAccess) golem).copperJobs$state();
            ItemStack stack = player.getItemInHand(hand);
            Job job = Job.of(stack);
            if (player.isShiftKeyDown()) {
                if (!stack.isEmpty()) return InteractionResult.PASS; // Vanilla scraping/waxing/antenna shearing.
                if (!level.isClientSide()) player.sendSystemMessage(Component.literal(state.job.label + ": " + state.status));
                return InteractionResult.SUCCESS;
            }
            // Worker save data is server-owned; suppress the vanilla client-side item toss.
            if (level.isClientSide() && job != null) return InteractionResult.SUCCESS;
            if (job == null || (job == Job.IDLE && state.job == Job.IDLE)) return InteractionResult.PASS;
            if (state.job == Job.IDLE && !golem.getMainHandItem().isEmpty()) {
                player.sendSystemMessage(Component.literal("Retrieve the item it is carrying with an empty hand first."));
                return InteractionResult.SUCCESS;
            }
            if (!state.pending.isEmpty()) {
                ChestStorage.Transaction plan = ChestStorage.nearby((ServerLevel) level, golem).plan();
                if (!plan.add(state.pending) || !plan.commit()) {
                    player.sendSystemMessage(Component.literal("Make room in a nearby Copper Chest for its collected drops first."));
                    return InteractionResult.SUCCESS;
                }
                state.pending.clear();
            }
            state.assign(job, golem.blockPosition(), golem.getDirection());
            golem.getNavigation().stop();
            golem.getBrain().stopAll((ServerLevel) level, golem);
            golem.setState(CopperGolemState.IDLE);
            golem.clearOpenedChestPos();
            golem.setCanPickUpLoot(false);
            golem.setItemSlot(EquipmentSlot.MAINHAND, job == Job.IDLE ? ItemStack.EMPTY : stack.copyWithCount(1));
            golem.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
            player.sendSystemMessage(Component.literal(job == Job.IDLE ? "Copper Golem: idle." : "Copper Golem: " + job.label + ". Copper Chest range: 64 blocks."));
            return InteractionResult.SUCCESS;
        });
    }
}
