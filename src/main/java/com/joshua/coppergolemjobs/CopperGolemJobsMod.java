package com.joshua.coppergolemjobs;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import java.util.Comparator;
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
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (player.isSpectator() || hand != InteractionHand.MAIN_HAND
                    || !player.getItemInHand(hand).is(Items.WOODEN_PICKAXE)
                    || !GeneratorJob.valid(level.getBlockState(hit.getBlockPos()))) return InteractionResult.PASS;
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            var candidates = level.getEntitiesOfClass(CopperGolem.class, player.getBoundingBox().inflate(64), g -> {
                WorkerState s = ((WorkerAccess) g).copperJobs$state();
                return s.job == Job.GENERATOR && s.inWorkChunk(hit.getBlockPos())
                        && g.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(hit.getBlockPos())) <= 64 * 64;
            });
            CopperGolem selected = candidates.stream().min(Comparator
                    .comparingDouble((CopperGolem g) -> g.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(hit.getBlockPos())))
                    .thenComparing(g -> g.getUUID().toString())).orElse(null);
            if (selected == null) {
                player.sendSystemMessage(Component.literal("No wooden-pickaxe worker assigned to this chunk within 64 blocks."));
            } else {
                WorkerState s = ((WorkerAccess) selected).copperJobs$state();
                s.generator = hit.getBlockPos().immutable();
                s.clearTarget(); s.cooldown = 0;
                selected.getNavigation().stop();
                player.sendSystemMessage(Component.literal("Generator worker assigned to " + s.generator.toShortString() + "."));
            }
            return InteractionResult.SUCCESS;
        });
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (!(entity instanceof CopperGolem golem) || player.isSpectator() || hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            WorkerState state = ((WorkerAccess) golem).copperJobs$state();
            ItemStack stack = player.getItemInHand(hand);
            Job job = Job.of(stack);
            if (stack.is(Items.TRIDENT)) {
                int loyalty = EnchantmentHelper.getItemEnchantmentLevel(level.registryAccess()
                        .lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.LOYALTY), stack);
                if (loyalty < 3) {
                    if (!level.isClientSide()) player.sendSystemMessage(Component.literal("Drain crew requires a trident with Loyalty III."));
                    return InteractionResult.SUCCESS;
                }
                job = Job.DRAIN;
            }
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
            if (state.drain.active() && job != Job.IDLE && job != Job.DRAIN) {
                player.sendSystemMessage(Component.literal("This golem has an unfinished drain project. Give it its Loyalty III trident to resume."));
                return InteractionResult.SUCCESS;
            }
            boolean resume = state.drain.active();
            if (resume) { state.job = job; state.clearTarget(); state.cooldown = 0; }
            else state.assign(job, golem.blockPosition(), golem.getDirection());
            golem.setNoGravity(false);
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
