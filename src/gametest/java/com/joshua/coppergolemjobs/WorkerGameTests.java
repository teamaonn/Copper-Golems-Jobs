package com.joshua.coppergolemjobs;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import java.util.List;

public final class WorkerGameTests {
    private static void check(boolean pass, String description) {
        if (!pass) throw new IllegalStateException(description);
        System.out.println("COPPER JOBS PASS: " + description);
    }
    private static int count(Container c, Item item) {
        int n = 0; for (int i = 0; i < c.getContainerSize(); i++) if (c.getItem(i).is(item)) n += c.getItem(i).getCount(); return n;
    }
    private static void step(ServerLevel level, CopperGolem g, WorkerState s) { g.tickCount = 10; s.cooldown = 0; WorkerBrain.tick(level, g, s); }

    @GameTest(maxTicks = 300, skyAccess = true, padding = 96)
    public void jobsAndGates(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(2, 1, 2));
        // One isolated test uses the chunk containing its own structure.
        base = new BlockPos((base.getX() >> 4) * 16 + 4, base.getY(), (base.getZ() >> 4) * 16 + 4);
        for (BlockPos p : BlockPos.betweenClosed(base.offset(-2,-3,-2), base.offset(8,5,8))) level.setBlock(p, p.getY() < base.getY() ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
        CopperGolem g = EntityTypes.COPPER_GOLEM.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        g.setPos(Vec3.atBottomCenterOf(base)); level.addFreshEntity(g); g.setNoAi(true);
        WorkerState s = ((WorkerAccess)g).copperJobs$state();
        var player=helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        ItemStack assignment=new ItemStack(Items.NETHERITE_PICKAXE);assignment.setDamageValue(7);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,assignment);
        net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.invoker().interact(player,level,net.minecraft.world.InteractionHand.MAIN_HAND,g,null);
        check(s.job==Job.PICKAXE && assignment.getCount()==1 && assignment.getDamageValue()==7 && g.getMainHandItem()!=assignment,"assignment keeps the original tool and its durability");
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,ItemStack.EMPTY);
        net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.invoker().interact(player,level,net.minecraft.world.InteractionHand.MAIN_HAND,g,null);
        check(s.job==Job.IDLE && g.getMainHandItem().isEmpty(),"empty hand clears job and display item");
        BlockPos dirt = base.east(); level.setBlock(dirt, Blocks.DIRT.defaultBlockState(), 3);
        s.assign(Job.SHOVEL, base, Direction.EAST);
        step(level,g,s); check(level.getBlockState(dirt).is(Blocks.DIRT), "missing Copper Chest stops work");
        BlockPos chestPos = base.offset(-1,0,-1);
        Block chestBlock = BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace("copper_chest"));
        level.setBlock(chestPos, chestBlock.defaultBlockState(), 3);
        Container chest = (Container)level.getBlockEntity(chestPos);
        for (int i=0;i<chest.getContainerSize();i++) chest.setItem(i,new ItemStack(Items.COBBLESTONE,64));
        step(level,g,s); check(level.getBlockState(dirt).is(Blocks.DIRT), "full chest stops shovel before breaking");
        chest.clearContent(); step(level,g,s);
        check(level.getBlockState(dirt).isAir() && count(chest,Items.DIRT)==1, "shovel resumes and deposits exact drop");
        s.assign(Job.SPEAR,base,Direction.EAST); chest.clearContent();
        for (int w=-1;w<=1;w++) for(int h=0;h<3;h++) level.setBlock(base.east().south(w).above(h),Blocks.STONE.defaultBlockState(),3);
        step(level,g,s);
        check(count(chest,Items.COBBLESTONE)==9, "spear clears a 3x3 tunnel slice");
        for (int w=-1;w<=1;w++) for(int h=0;h<3;h++) check(level.getBlockState(base.east().south(w).above(h)).isAir(), "tunnel cell cleared");
        BlockPos tunnelGapFloor=base.east(2).below(); level.setBlock(tunnelGapFloor,Blocks.AIR.defaultBlockState(),3);
        s.cursor=base.east();step(level,g,s);
        check(level.getBlockState(tunnelGapFloor).is(Blocks.COBBLESTONE),"3x3 tunnel builds a cobblestone path across a gap");
        s.assign(Job.PICKAXE,base,Direction.EAST); chest.clearContent();
        int quarryX=(base.getX()>>4)<<4,quarryZ=(base.getZ()>>4)<<4;
        for(int x=quarryX;x<quarryX+16;x++)for(int z=quarryZ;z<quarryZ+16;z++){BlockPos p=new BlockPos(x,base.getY(),z);if(!p.equals(chestPos))level.setBlock(p,Blocks.AIR.defaultBlockState(),3);}
        BlockPos ore=base.east();BlockPos second=base.south();
        level.setBlock(ore,Blocks.DIAMOND_ORE.defaultBlockState(),3);level.setBlock(second,Blocks.STONE.defaultBlockState(),3);
        BlockPos gap=base.east(3).below(), farGap=new BlockPos(quarryX+15,base.getY()-1,quarryZ+15);
        level.setBlock(gap,Blocks.AIR.defaultBlockState(),3);level.setBlock(farGap,Blocks.AIR.defaultBlockState(),3);
        step(level,g,s);
        check(level.getBlockState(gap).is(Blocks.COBBLESTONE) && level.getBlockState(farGap).is(Blocks.COBBLESTONE)
                && level.getBlockState(base.below()).is(Blocks.STONE),"quarry prepares the whole floor in one work tick and preserves existing blocks");
        step(level,g,s);step(level,g,s);step(level,g,s);
        check(level.getBlockState(ore).isAir()&&level.getBlockState(second).isAir(),"pickaxe clears the assigned chunk layer instead of one staircase block");
        check(count(chest,Items.DIAMOND_ORE)==1 && count(chest,Items.DIAMOND)==0, "pickaxe uses Silk Touch");
        step(level,g,s);
        BlockPos quarryStep=new BlockPos(quarryX+1,base.getY()-1,quarryZ);
        g.setPos(Vec3.atBottomCenterOf(quarryStep));step(level,g,s);
        check(s.cursor.getY()==base.getY()-1&&level.getBlockState(quarryStep.below()).is(Blocks.COBBLESTONE),"quarry builds its own descending cobblestone staircase: "+s.status+" cursor="+s.cursor+" expected="+quarryStep+" floor="+level.getBlockState(quarryStep.below()));
        ore=quarryStep.east(); level.setBlock(ore,Blocks.DIAMOND_ORE.defaultBlockState(),3);
        BlockPos fluid=ore.east(); level.setBlock(fluid,Blocks.WATER.defaultBlockState(),3);
        step(level,g,s);
        check(level.getBlockState(fluid).is(Blocks.GLASS), "miner seals exposed water with glass");
        check(level.getBlockState(gap).isAir() && level.getBlockState(farGap).isAir() && count(chest,Items.COBBLESTONE)==0,
                "miner reclaims its previous floor after descending with no cobblestone payout");
        var quarrySave=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,level.registryAccess());
        s.save(quarrySave);
        s.load(TagValueInput.create(ProblemReporter.DISCARDING,level.registryAccess(),quarrySave.buildResult()));
        level.setBlock(ore,Blocks.AIR.defaultBlockState(),3);step(level,g,s);
        check(level.getBlockState(fluid).isAir() && count(chest,Items.GLASS)==0,"quarry reabsorbs temporary water-sealing glass after save and reload");
        level.setBlock(ore,Blocks.STONE.defaultBlockState(),3);level.setBlock(fluid,Blocks.LAVA.defaultBlockState(),3);step(level,g,s);
        check(level.getBlockState(fluid).is(Blocks.GLASS), "miner seals exposed lava with glass");
        level.setBlock(ore,Blocks.AIR.defaultBlockState(),3);step(level,g,s);
        check(level.getBlockState(fluid).isAir() && count(chest,Items.GLASS)==0,"quarry reabsorbs temporary lava-sealing glass without a drop");
        level.setBlock(ore,Blocks.GLASS.defaultBlockState(),3);step(level,g,s);
        level.setBlock(ore,Blocks.COBBLESTONE.defaultBlockState(),3);step(level,g,s);
        check(count(chest,Items.GLASS)==1 && count(chest,Items.COBBLESTONE)==1,"untracked glass and cobblestone still yield their normal items");
        for(int x=quarryX;x<quarryX+16;x++)for(int z=quarryZ;z<quarryZ+16;z++)if(!s.structuralBlocks.contains(new BlockPos(x,s.cursor.getY(),z)))level.setBlock(new BlockPos(x,s.cursor.getY(),z),Blocks.AIR.defaultBlockState(),3);
        BlockPos bedrockStep=new BlockPos(quarryX+2,base.getY()-2,quarryZ);level.setBlock(bedrockStep,Blocks.BEDROCK.defaultBlockState(),3);step(level,g,s);
        check(level.getBlockState(bedrockStep).is(Blocks.BEDROCK) && s.status.equals("Reached bedrock"), "bedrock stops mining");
        level.setBlock(ore,Blocks.STONE.defaultBlockState(),3);
        g.setPos(Vec3.atBottomCenterOf(base));
        s.assign(Job.STICK,base,Direction.EAST); chest.clearContent();
        level.setBlock(base.east(2),Blocks.JUNGLE_LOG.defaultBlockState(),3);
        level.setBlock(dirt,Blocks.COCOA.defaultBlockState().setValue(CocoaBlock.FACING,Direction.EAST).setValue(CocoaBlock.AGE,2),3);
        step(level,g,s);
        check(count(chest,Items.COCOA_BEANS)==2 && level.getBlockState(dirt).getValue(CocoaBlock.AGE)==0, "cocoa harvest replants and stores two beans");
        level.setBlock(dirt,Blocks.AIR.defaultBlockState(),3);level.setBlock(base.east(2),Blocks.AIR.defaultBlockState(),3);
        s.assign(Job.HOE,base,Direction.EAST);chest.clearContent();
        level.setBlock(dirt.below(),Blocks.FARMLAND.defaultBlockState(),3);
        level.setBlock(dirt,((CropBlock)Blocks.WHEAT).getStateForAge(7),3);
        step(level,g,s);
        check(count(chest,Items.WHEAT)==1 && ((CropBlock)Blocks.WHEAT).getAge(level.getBlockState(dirt))==0, "hoe harvests and replants wheat");
        level.setBlock(dirt,Blocks.AIR.defaultBlockState(),3);chest.clearContent();chest.setItem(0,new ItemStack(Items.CARROT,2));s.clearTarget();step(level,g,s);
        check(level.getBlockState(dirt).is(Blocks.CARROTS), "hoe withdraws planting supplies from chest");
        BlockPos dryDirt=base.south(2).below();level.setBlock(dryDirt,Blocks.DIRT.defaultBlockState(),3);level.setBlock(dryDirt.above(),Blocks.AIR.defaultBlockState(),3);chest.clearContent();s.clearTarget();step(level,g,s);
        check(level.getBlockState(dryDirt).is(Blocks.FARMLAND),"hoe tills dry tillable dirt even without nearby water");
        level.setBlock(dirt,Blocks.AIR.defaultBlockState(),3);level.setBlock(dirt.below(),Blocks.STONE.defaultBlockState(),3);
        s.assign(Job.AXE,base,Direction.EAST);chest.clearContent();
        BlockPos edgeLog=new BlockPos(quarryX,base.getY(),base.getZ());BlockPos outsideLog=edgeLog.west();
        level.setBlock(edgeLog,Blocks.OAK_LOG.defaultBlockState(),3);level.setBlock(outsideLog,Blocks.OAK_LOG.defaultBlockState(),3);
        level.setBlock(outsideLog.above(),Blocks.OAK_LEAVES.defaultBlockState(),3);g.setPos(Vec3.atBottomCenterOf(edgeLog.east()));s.clearTarget();
        step(level,g,s);check(count(chest,Items.OAK_LOG)==2 && level.getBlockState(outsideLog).isAir(), "axe crosses the assignment chunk boundary to clear a connected tree");
        g.setPos(Vec3.atBottomCenterOf(base));
        s.assign(Job.BUCKET,base,Direction.EAST);chest.clearContent();
        var cow=EntityTypes.COW.create(level,net.minecraft.world.entity.EntitySpawnReason.COMMAND);cow.setPos(Vec3.atBottomCenterOf(dirt));cow.setNoAi(true);level.addFreshEntity(cow);
        step(level,g,s);check(count(chest,Items.MILK_BUCKET)==0,"milking requires a chest bucket");
        chest.setItem(0,new ItemStack(Items.BUCKET,2));step(level,g,s);
        check(count(chest,Items.MILK_BUCKET)==1 && count(chest,Items.BUCKET)==1 && s.cooldown==1200,"one milk consumes one bucket and starts 60-second cooldown");cow.discard();
        s.assign(Job.SHEARS,base,Direction.EAST);chest.clearContent();
        var sheep=EntityTypes.SHEEP.create(level,net.minecraft.world.entity.EntitySpawnReason.COMMAND);sheep.setPos(Vec3.atBottomCenterOf(dirt));sheep.setNoAi(true);level.addFreshEntity(sheep);
        step(level,g,s);check(sheep.isSheared() && count(chest,BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace("white_wool")))>0,"sheep shearing stores wool");sheep.discard();
        level.setBlock(dirt.east(),Blocks.STONE.defaultBlockState(),3);level.setBlock(dirt.east().above(),Blocks.STONE.defaultBlockState(),3);
        level.setBlock(dirt.above(),Blocks.VINE.defaultBlockState().setValue(BlockStateProperties.EAST,true),3);
        level.setBlock(dirt,Blocks.VINE.defaultBlockState().setValue(BlockStateProperties.EAST,true),3);s.clearTarget();chest.clearContent();step(level,g,s);
        check(level.getBlockState(dirt).isAir() && level.getBlockState(dirt.above()).is(Blocks.VINE) && count(chest,Items.VINE)==1,"vine shearing preserves upper vine");
        level.setBlock(dirt.above(),Blocks.AIR.defaultBlockState(),3);level.setBlock(dirt.east(),Blocks.AIR.defaultBlockState(),3);level.setBlock(dirt.east().above(),Blocks.AIR.defaultBlockState(),3);
        s.assign(Job.SWORD,base,Direction.EAST);chest.clearContent();
        var zombie=EntityTypes.ZOMBIE.create(level,net.minecraft.world.entity.EntitySpawnReason.COMMAND);zombie.setPos(Vec3.atBottomCenterOf(dirt));zombie.setNoAi(true);zombie.setHealth(1);zombie.setItemSlot(EquipmentSlot.OFFHAND,new ItemStack(Items.DIAMOND));zombie.setGuaranteedDrop(EquipmentSlot.OFFHAND);level.addFreshEntity(zombie);
        step(level,g,s);check(!zombie.isAlive() && count(chest,Items.DIAMOND)==1,"sword kills hostile and captures guaranteed equipment drop");zombie.discard();
        s.assign(Job.FISHING_ROD,base,Direction.EAST);g.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.FISHING_ROD));chest.clearContent();level.setBlock(dirt,Blocks.WATER.defaultBlockState(),3);
        step(level,g,s);check(!chest.isEmpty(),"fishing produces vanilla loot in Copper Chest");level.setBlock(dirt,Blocks.AIR.defaultBlockState(),3);
        s.assign(Job.SPEAR,base,Direction.WEST);s.cursor=base.below(2);s.cooldown=1199;s.pending.add(new ItemStack(Items.DIAMOND,3));s.structuralBlocks.add(base.below());
        var out=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,level.registryAccess());g.addAdditionalSaveData(out);
        CopperGolem restored=EntityTypes.COPPER_GOLEM.create(level,net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        restored.readAdditionalSaveData(TagValueInput.create(ProblemReporter.DISCARDING,level.registryAccess(),out.buildResult()));
        var read=((WorkerAccess)restored).copperJobs$state();
        check(read.job==Job.SPEAR && read.direction==Direction.WEST && read.cursor.equals(s.cursor) && read.cooldown==1199 && read.pending.getFirst().getCount()==3 && read.structuralBlocks.contains(base.below()),"mixin save/reload preserves assignment, direction, cooldown, structural blocks and overflow");
        s.pending.clear();s.assign(Job.SPEAR,base,Direction.EAST);s.cursor=base.east(100000);chest.clearContent();step(level,g,s);
        check(s.status.contains("unloaded") && level.getChunkSource().getChunkNow(s.cursor.getX()>>4,s.cursor.getZ()>>4)==null,"spear stops before unknown chunk without loading it");
        s.job=Job.IDLE;
        g.setPos(chestPos.getX()+.5+64,chestPos.getY()+.5,chestPos.getZ()+.5);check(ChestStorage.nearby(level,g).exists(),"chest exactly 64 blocks away is valid");
        g.setPos(g.getX()+.01,g.getY(),g.getZ());check(!ChestStorage.nearby(level,g).exists(),"chest beyond 64 blocks is rejected");
        SimpleContainer small=new SimpleContainer(1);small.setItem(0,new ItemStack(Items.DIRT,63));
        var tx=new ChestStorage.Transaction(List.of(small));check(!tx.add(List.of(new ItemStack(Items.DIRT,2))) && small.getItem(0).getCount()==63,"overflow cannot partially mutate inventory");
        var one=new ChestStorage.Transaction(List.of(small));check(one.add(List.of(new ItemStack(Items.DIRT,1))) && one.commit() && small.getItem(0).getCount()==64,"exact stack fit commits");
        g.setPos(Vec3.atBottomCenterOf(base));g.setNoAi(true);s.assign(Job.SHOVEL,base,Direction.EAST);s.pending.clear();chest.clearContent();
        for(int i=0;i<80;i++){level.setBlock(dirt,Blocks.DIRT.defaultBlockState(),3);step(level,g,s);}
        check(count(chest,Items.DIRT)==80,"digging continues beyond the first 64-item stack");
        BlockPos stalledTarget=base.east(6);level.setBlock(stalledTarget,Blocks.DIRT.defaultBlockState(),3);s.clearTarget();
        for(int i=0;i<22;i++)step(level,g,s);
        check(s.skipped.containsKey(stalledTarget)&&!s.status.equals("Walking to work"),"stale walking path is detected and abandoned automatically");
        level.setBlock(stalledTarget,Blocks.AIR.defaultBlockState(),3);
        g.setPos(Vec3.atBottomCenterOf(base));s.assign(Job.SHOVEL,base,Direction.EAST);s.pending.clear();chest.clearContent();
        // Isolate the complete work chunk so the shovel cannot select natural terrain
        // outside the small structure and walk off the edge of the test platform.
        int chunkX=(base.getX()>>4)<<4,chunkZ=(base.getZ()>>4)<<4;
        for(int x=chunkX;x<chunkX+16;x++) for(int z=chunkZ;z<chunkZ+16;z++) for(int y=base.getY()-6;y<=base.getY()+6;y++) {
            BlockPos p=new BlockPos(x,y,z);
            if(p.equals(chestPos)) continue;
            level.setBlock(p,y<base.getY()?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),3);
        }
        BlockPos walkingTarget=base.east(6);level.setBlock(walkingTarget,Blocks.DIRT.defaultBlockState(),3);
        for(int x=base.getX()+1;x<base.getX()+6;x++)for(int z=chunkZ;z<chunkZ+16;z++)level.setBlock(new BlockPos(x,base.getY()-1,z),Blocks.AIR.defaultBlockState(),3);
        g.setNoAi(false);
        BlockPos start=base;
        helper.runAfterDelay(200,()->{
            System.out.println("WALK DEBUG position="+g.position()+" start="+start+" status="+s.status+" target="+s.target+" approach="+s.approach+" ticks="+g.tickCount+" ground="+g.onGround()+" alive="+g.isAlive()+" dirt="+count(chest,Items.DIRT));
            check(level.getBlockState(walkingTarget).isAir() && count(chest,Items.DIRT)==1 && g.distanceToSqr(Vec3.atBottomCenterOf(start))>1,"live AI builds across a gap, resumes digging, and deposits its drop");
            g.discard();helper.succeed();
        });
    }

    @GameTest(maxTicks = 400, skyAccess = true, padding = 96)
    public void quarryRecoversFromLowerLedge(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(new BlockPos(0, 8, 0));
        BlockPos layer = new BlockPos((anchor.getX() >> 4) << 4, anchor.getY(), (anchor.getZ() >> 4) << 4);
        ChunkPos chunk = new ChunkPos(layer.getX() >> 4, layer.getZ() >> 4);
        if (level.getChunkSource().updateChunkForced(chunk, true)) {
            helper.runBeforeTestEnd(() -> level.getChunkSource().updateChunkForced(chunk, false));
        }
        BlockPos step = layer.east().below();
        BlockPos ledge = step.east(5).below(2);
        for (BlockPos pos : BlockPos.betweenClosed(layer.below(7), layer.offset(15, 3, 15))) {
            level.setBlock(pos, pos.getY() == layer.getY() - 7 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
        }
        Block grate = BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace("waxed_copper_grate"));
        level.setBlock(ledge.below(), grate.defaultBlockState(), 3);
        BlockPos chestPos = ledge.south();
        Block chestBlock = BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace("copper_chest"));
        level.setBlock(chestPos, chestBlock.defaultBlockState(), 3);
        Container chest = (Container) level.getBlockEntity(chestPos);
        level.setBlock(step.below(), Blocks.COBBLESTONE.defaultBlockState(), 3);
        BlockPos ore = step.south();
        level.setBlock(ore, Blocks.DIAMOND_ORE.defaultBlockState(), 3);
        level.setBlock(step.east().below(), Blocks.BEDROCK.defaultBlockState(), 3);

        CopperGolem golem = EntityTypes.COPPER_GOLEM.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        golem.setPos(Vec3.atBottomCenterOf(ledge));
        WorkerState worker = ((WorkerAccess) golem).copperJobs$state();
        worker.assign(Job.PICKAXE, layer, Direction.EAST);
        worker.structuralBlocks.add(step.below());
        level.addFreshEntity(golem);
        helper.runAfterDelay(300, () -> {
            check(worker.cursor.equals(step) && level.getBlockState(ore).isAir() && count(chest, Items.DIAMOND_ORE) == 1,
                    "live miner climbs back from a lower ledge, crosses a gap and resumes quarrying (" + worker.status + ", cursor="+worker.cursor+", expected="+step+", drops="+count(chest,Items.DIAMOND_ORE)+")");
            check(worker.structuralBlocks.size() > 1 && level.getBlockState(ledge.below()).is(grate),
                    "quarry approach uses its own cobblestone and preserves the ledge");
            check(level.getBlockState(chestPos.above()).isAir() && ChestStorage.nearby(level, golem).exists(),
                    "temporary floor leaves the Copper Chest lid clear after descent");
            golem.discard();
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 350, skyAccess = true, padding = 32)
    public void quarryUsesWalkwayAcrossChunkBorder(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(new BlockPos(0, 8, 0));
        BlockPos layer = new BlockPos((anchor.getX() >> 4) << 4, anchor.getY(), (anchor.getZ() >> 4) << 4);
        for (BlockPos center : List.of(layer, layer.north())) {
            ChunkPos chunk = new ChunkPos(center.getX() >> 4, center.getZ() >> 4);
            if (level.getChunkSource().updateChunkForced(chunk, true)) {
                helper.runBeforeTestEnd(() -> level.getChunkSource().updateChunkForced(chunk, false));
            }
        }
        for (BlockPos pos : BlockPos.betweenClosed(layer.offset(0, -4, -4), layer.offset(15, 3, 15))) {
            level.setBlock(pos, pos.getY() == layer.getY() - 4 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
        }
        for (BlockPos pos : BlockPos.betweenClosed(layer.offset(3, -1, -2), layer.offset(12, -1, -1))) {
            level.setBlock(pos, Blocks.POLISHED_TUFF.defaultBlockState(), 3);
        }
        BlockPos ledge = layer.east(4).north(2);
        BlockPos chestPos = ledge.north();
        Block chestBlock = BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace("copper_chest"));
        level.setBlock(chestPos, chestBlock.defaultBlockState(), 3);
        Container chest = (Container) level.getBlockEntity(chestPos);
        BlockPos ore = layer.east(11);
        BlockPos outsideOre = ledge.east(2).north();
        level.setBlock(ore, Blocks.DIAMOND_ORE.defaultBlockState(), 3);
        level.setBlock(outsideOre, Blocks.DIAMOND_ORE.defaultBlockState(), 3);
        level.setBlock(layer.east().below(), Blocks.BEDROCK.defaultBlockState(), 3);

        CopperGolem golem = EntityTypes.COPPER_GOLEM.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        golem.setPos(Vec3.atBottomCenterOf(ledge));
        WorkerState worker = ((WorkerAccess) golem).copperJobs$state();
        worker.assign(Job.PICKAXE, layer, Direction.EAST);
        level.addFreshEntity(golem);
        helper.runAfterDelay(250, () -> {
            check(level.getBlockState(ore).isAir() && count(chest, Items.DIAMOND_ORE) == 1,
                    "live miner uses a walkway outside its assigned chunk to reach an edge block (" + worker.status + ")");
            check(level.getBlockState(outsideOre).is(Blocks.DIAMOND_ORE) && worker.structuralBlocks.isEmpty(),
                    "quarry navigation leaves neighboring ore and the existing walkway in place");
            golem.discard();
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 650, skyAccess = true, padding = 80)
    public void quarryReturnsToUnfinishedLayer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(new BlockPos(0, 20, 0));
        BlockPos layer = new BlockPos((anchor.getX() >> 4) << 4, anchor.getY(), (anchor.getZ() >> 4) << 4);
        ChunkPos chunk = new ChunkPos(layer.getX() >> 4, layer.getZ() >> 4);
        if (level.getChunkSource().updateChunkForced(chunk, true)) {
            helper.runBeforeTestEnd(() -> level.getChunkSource().updateChunkForced(chunk, false));
        }
        for (BlockPos pos : BlockPos.betweenClosed(layer.below(16), layer.offset(15, 3, 15))) {
            level.setBlock(pos, pos.getY() == layer.getY() - 16 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
        }
        BlockPos ledge = layer.offset(3, -14, 5);
        level.setBlock(ledge.below(), Blocks.POLISHED_TUFF.defaultBlockState(), 3);
        BlockPos ore = layer.offset(10, 0, 5);
        level.setBlock(ore, Blocks.DIAMOND_ORE.defaultBlockState(), 3);
        level.setBlock(layer.east().below(), Blocks.BEDROCK.defaultBlockState(), 3);
        BlockPos chestPos = layer.offset(1, -14, 13);
        Block chestBlock = BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace("copper_chest"));
        level.setBlock(chestPos, chestBlock.defaultBlockState(), 3);
        Container chest = (Container) level.getBlockEntity(chestPos);

        CopperGolem golem = EntityTypes.COPPER_GOLEM.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        golem.setPos(Vec3.atBottomCenterOf(ledge));
        WorkerState worker = ((WorkerAccess) golem).copperJobs$state();
        worker.assign(Job.PICKAXE, layer, Direction.EAST);
        level.addFreshEntity(golem);
        helper.runAfterDelay(580, () -> {
            check(level.getBlockState(ore).isAir() && count(chest, Items.DIAMOND_ORE) == 1 && worker.cursor.equals(layer),
                    "live miner builds back up to unfinished work after falling fourteen blocks (" + worker.status + ", " + golem.blockPosition() + ")");
            golem.discard();
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 450, skyAccess = true, padding = 80)
    public void quarryBuildsWalkwayAroundObstruction(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(new BlockPos(0, 8, 0));
        BlockPos layer = new BlockPos((anchor.getX() >> 4) << 4, anchor.getY(), (anchor.getZ() >> 4) << 4);
        for (BlockPos center : List.of(layer, layer.north())) {
            ChunkPos chunk = new ChunkPos(center.getX() >> 4, center.getZ() >> 4);
            if (level.getChunkSource().updateChunkForced(chunk, true)) {
                helper.runBeforeTestEnd(() -> level.getChunkSource().updateChunkForced(chunk, false));
            }
        }
        for (BlockPos pos : BlockPos.betweenClosed(layer.offset(0, -6, -4), layer.offset(15, 3, 15))) {
            level.setBlock(pos, pos.getY() == layer.getY() - 6 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
        }
        BlockPos ledge = layer.east(4);
        level.setBlock(ledge.below(), Blocks.POLISHED_TUFF.defaultBlockState(), 3);
        List<BlockPos> walls = List.of(ledge.east(), ledge.south(), ledge.west());
        for (BlockPos wall : walls) for (int dy = -3; dy <= 3; dy++) {
            level.setBlock(wall.offset(0, dy, 0), Blocks.BEDROCK.defaultBlockState(), 3);
        }
        BlockPos chestPos = layer.east(2).south(2);
        Block chestBlock = BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace("copper_chest"));
        level.setBlock(chestPos, chestBlock.defaultBlockState(), 3);
        Container chest = (Container) level.getBlockEntity(chestPos);
        BlockPos ore = layer.east(11);
        level.setBlock(ore, Blocks.DIAMOND_ORE.defaultBlockState(), 3);
        level.setBlock(layer.east().below(), Blocks.BEDROCK.defaultBlockState(), 3);

        CopperGolem golem = EntityTypes.COPPER_GOLEM.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        golem.setPos(Vec3.atBottomCenterOf(ledge));
        WorkerState worker = ((WorkerAccess) golem).copperJobs$state();
        worker.assign(Job.PICKAXE, layer, Direction.EAST);
        level.addFreshEntity(golem);
        helper.runAfterDelay(380, () -> {
            check(level.getBlockState(ore).isAir() && count(chest, Items.DIAMOND_ORE) == 1,
                    "live miner builds a walkway around the obstruction and mines across the gap (" + worker.status + ", " + golem.blockPosition() + ")");
            check(worker.structuralBlocks.stream().filter(p -> !worker.inWorkChunk(p)).count() >= 2
                    && walls.stream().allMatch(p -> level.getBlockState(p).is(Blocks.BEDROCK)),
                    "walkway spans the chunk edge and leaves the obstruction intact");
            golem.discard();
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 150, skyAccess = true, padding = 80)
    public void workerProtectionAndSharedQuarry(GameTestHelper helper) {
        ServerLevel level=helper.getLevel();
        BlockPos anchor=helper.absolutePos(new BlockPos(3,5,3));
        BlockPos base=new BlockPos(((anchor.getX()>>4)<<4)+5,anchor.getY(),((anchor.getZ()>>4)<<4)+5);
        for(BlockPos p:BlockPos.betweenClosed(base.offset(-3,-2,-3),base.offset(9,4,9)))
            level.setBlock(p,p.getY()<base.getY()?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
        CopperGolem first=EntityTypes.COPPER_GOLEM.create(level,net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        first.setPos(Vec3.atBottomCenterOf(base));first.setNoAi(true);level.addFreshEntity(first);
        WorkerState a=((WorkerAccess)first).copperJobs$state();
        for(Job job:Job.values()) {
            a.assign(job,base,Direction.EAST);first.setInvulnerableTime(0);
            float health=first.getHealth();
            boolean hurt=first.hurtServer(level,level.damageSources().generic(),job==Job.SWORD?2:1);
            if(job!=Job.IDLE && job!=Job.SWORD) check(!hurt&&first.getHealth()==health,job+" worker ignores incoming damage");
            else {
                check(hurt&&first.getHealth()<health,job+" keeps normal damage behavior");
                first.setHealth(first.getMaxHealth());
            }
        }
        a.assign(Job.PICKAXE,base,Direction.EAST);
        for(var source:List.of(level.damageSources().fall(),level.damageSources().inFire(),level.damageSources().drown())) {
            first.setInvulnerableTime(0);float health=first.getHealth();
            check(!first.hurtServer(level,source,100)&&first.getHealth()==health,"worker survives environmental damage");
        }
        CopperGolem second=EntityTypes.COPPER_GOLEM.create(level,net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        second.setPos(Vec3.atBottomCenterOf(base.south()));second.setNoAi(true);level.addFreshEntity(second);
        WorkerState b=((WorkerAccess)second).copperJobs$state();b.assign(Job.PICKAXE,base.south(),Direction.EAST);
        a.structuralBlocks.add(base.east(3));b.fluidPlugs.add(base.east(4));
        WorkerProjects.quarry(level,first,a);
        check(a.origin.equals(b.origin)&&a.structuralBlocks==b.structuralBlocks&&a.fluidPlugs==b.fluidPlugs,
                "quarry helpers share their origin and construction ownership");
        BlockPos target=base.east(5);level.setBlock(target,Blocks.DIAMOND_ORE.defaultBlockState(),3);
        a.target=target;WorkerProjects.reserve(level,first,target);
        check(!WorkerProjects.available(level,second,target),"another worker cannot claim an active block target");
        a.clearTarget();check(WorkerProjects.available(level,second,target),"abandoned target is available immediately");
        b.assign(Job.IDLE,base,Direction.EAST);
        check(a.structuralBlocks.contains(base.east(3)),"reassigning a helper preserves the remaining crew's construction records");
        BlockPos remoteChest=base.offset(-1,0,-1);
        level.setBlock(remoteChest,BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace("copper_chest")).defaultBlockState(),3);
        first.setPos(remoteChest.getX()+.5+150,remoteChest.getY()+.5,remoteChest.getZ()+.5);
        a.assign(Job.DRAIN,base,Direction.EAST);
        check(ChestStorage.nearby(level,first).exists(),"drain crew detects a Copper Chest exactly 150 blocks away");
        first.setPos(first.getX()+.01,first.getY(),first.getZ());
        check(!ChestStorage.nearby(level,first).exists(),"drain crew rejects storage beyond 150 blocks");
        first.setPos(remoteChest.getX()+.5+100,remoteChest.getY()+.5,remoteChest.getZ()+.5);
        a.assign(Job.PICKAXE,base,Direction.EAST);
        check(!ChestStorage.nearby(level,first).exists(),"ordinary workers retain their 64-block Copper Chest range");
        first.discard();second.discard();helper.succeed();
    }

    @GameTest(maxTicks = 100, skyAccess = true, padding = 80)
    public void drainSpongeCleanup(GameTestHelper helper) {
        ServerLevel level=helper.getLevel();BlockPos base=helper.absolutePos(new BlockPos(4,12,4));
        for(BlockPos p:BlockPos.betweenClosed(base.offset(-4,-6,-4),base.offset(4,3,4)))
            level.setBlock(p,p.getY()==base.getY()-6?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
        CopperGolem g=EntityTypes.COPPER_GOLEM.create(level,net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        g.setPos(Vec3.atBottomCenterOf(base.above()));g.setNoAi(true);level.addFreshEntity(g);
        WorkerState s=((WorkerAccess)g).copperJobs$state();s.assign(Job.DRAIN,base.above(),Direction.NORTH);
        s.drain.begin(new net.minecraft.world.level.levelgen.structure.BoundingBox(base.getX()-1,base.getY()-6,base.getZ()-1,
                base.getX()+1,base.getY()-2,base.getZ()+1),base.getY());
        java.util.Arrays.fill(s.drain.floors,base.getY()-6);s.drain.stage=DrainState.Stage.DONE;
        BlockPos dry=base.east(2).above(2),wet=base.offset(-2,-3,0),room=base.below(3),outside=base.offset(4,-1,0);
        level.setBlock(dry,Blocks.SPONGE.defaultBlockState(),2);level.setBlock(wet,Blocks.WET_SPONGE.defaultBlockState(),2);
        level.setBlock(room,Blocks.WET_SPONGE.defaultBlockState(),2);level.setBlock(room.above(),Blocks.PRISMARINE.defaultBlockState(),2);
        level.setBlock(outside,Blocks.SPONGE.defaultBlockState(),2);
        BlockPos chestPos=base.offset(0,1,3);
        level.setBlock(chestPos,BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace("copper_chest")).defaultBlockState(),2);
        Container chest=(Container)level.getBlockEntity(chestPos);
        for(int slot=0;slot<chest.getContainerSize();slot++)chest.setItem(slot,new ItemStack(Items.COBBLESTONE,64));
        g.tickCount=10;WorkerBrain.tick(level,g,s);
        check(level.getBlockState(dry).isAir()&&level.getBlockState(wet).isAir(),"dry and wet exterior sponges poof even with full storage and a finished drain crew");
        check(level.getBlockState(room).is(Blocks.WET_SPONGE)&&level.getBlockState(outside).is(Blocks.SPONGE),
                "sponge cleanup preserves enclosed monument rooms and blocks outside the drain project");
        check(count(chest,Items.SPONGE)==0&&count(chest,Items.WET_SPONGE)==0&&s.pending.isEmpty(),"sponge cleanup generates no chest payout");
        check(level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(base).inflate(8)).isEmpty(),
                "sponge cleanup creates no loose item drops");
        g.discard();helper.succeed();
    }

    @GameTest(maxTicks = 100, skyAccess = true, padding = 80)
    public void drainHelpersFinishClaimedColumn(GameTestHelper helper) {
        ServerLevel level=helper.getLevel();BlockPos base=helper.absolutePos(new BlockPos(4,26,4));
        CopperGolem[] crew=new CopperGolem[2];WorkerState[] states=new WorkerState[2];
        for(BlockPos p:BlockPos.betweenClosed(base.offset(-4,-21,-4),base.offset(4,3,4)))
            level.setBlock(p,p.getY()==base.getY()-21?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
        for(int i=0;i<2;i++) {
            crew[i]=EntityTypes.COPPER_GOLEM.create(level,net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            crew[i].setNoAi(true);level.addFreshEntity(crew[i]);states[i]=((WorkerAccess)crew[i]).copperJobs$state();
            states[i].assign(Job.DRAIN,base.above(),Direction.NORTH);
            states[i].drain.begin(new net.minecraft.world.level.levelgen.structure.BoundingBox(base.getX()-1,base.getY()-21,base.getZ()-1,
                    base.getX()+1,base.getY()-2,base.getZ()+1),base.getY());
            states[i].drain.stage=DrainState.Stage.FILL;
            java.util.Arrays.fill(states[i].drain.floors,base.getY()-21);
            states[i].drain.completed.set(0,states[i].drain.columns());states[i].drain.completed.clear(8);
            states[i].drain.index=states[i].drain.columns();
        }
        DrainTeam team=DrainTeam.join(level,crew[0],states[0]);DrainTeam.join(level,crew[1],states[1]);
        check(team.column(crew[0],states[0])&&states[0].drain.index==8,"primary worker claims the remaining interior column");
        check(team.column(crew[1],states[1])&&states[1].drain.index==8,"available worker assists an already claimed column instead of waiting");
        BlockPos top=states[1].drain.column(8,base.getY());
        crew[1].setPos(Vec3.atBottomCenterOf(top.above()));
        // The primary never works: its helper must finish the remaining job itself.
        for(int n=0;n<10&&states[1].drain.stage==DrainState.Stage.FILL;n++)
            DrainJobs.work(level,crew[1],states[1],ChestStorage.nearby(level,crew[1]));
        check(states[0].drain.stage==DrainState.Stage.WALLS&&states[1].drain.stage==DrainState.Stage.WALLS
                &&states[1].drain.sandTops[8]==base.getY(),"helper completes claimed work and advances the whole crew without losing sand ownership");
        for(CopperGolem g:crew)g.discard();helper.succeed();
    }

    @GameTest(maxTicks = 200, skyAccess = true, padding = 80)
    public void drainCrewBarrierRecovery(GameTestHelper helper) {
        ServerLevel level=helper.getLevel();BlockPos base=helper.absolutePos(new BlockPos(4,8,4));
        CopperGolem[] crew=new CopperGolem[64];WorkerState[] states=new WorkerState[64];
        var stages=new DrainState.Stage[]{DrainState.Stage.SURVEY,DrainState.Stage.CLEAR,DrainState.Stage.FILL,
                DrainState.Stage.WALLS,DrainState.Stage.VERIFY,DrainState.Stage.REMOVE};
        var next=new DrainState.Stage[]{DrainState.Stage.CLEAR,DrainState.Stage.SURFACE,DrainState.Stage.WALLS,
                DrainState.Stage.VERIFY,DrainState.Stage.REMOVE,DrainState.Stage.RETURN};
        for(int n=0;n<stages.length;n++) {
            // Separate sites keep each simulated restart independent of the live team cache.
            BlockPos site=base.above(n*2);
            for(int i=0;i<crew.length;i++) {
                if(crew[i]==null) {
                    crew[i]=EntityTypes.COPPER_GOLEM.create(level,net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                    crew[i].setPos(Vec3.atBottomCenterOf(base.above()));crew[i].setNoAi(true);level.addFreshEntity(crew[i]);
                    states[i]=((WorkerAccess)crew[i]).copperJobs$state();
                }
                WorkerState s=states[i];s.assign(Job.DRAIN,site,Direction.NORTH);
                s.drain.begin(new net.minecraft.world.level.levelgen.structure.BoundingBox(site.getX()-1,site.getY()-4,site.getZ()-1,
                        site.getX()+1,site.getY()-2,site.getZ()+1),site.getY());
                s.drain.stage=stages[n];s.drain.bottomY=site.getY()-4;
                java.util.Arrays.fill(s.drain.floors,site.getY()-4);
                java.util.Arrays.fill(s.drain.sandTops,site.getY()-1);
                s.drain.index=s.drain.columns();
                // Individually saved workers each hold only a portion of the completed stage.
                for(int c=i;c<s.drain.columns();c+=crew.length)s.drain.completed.set(c);
                var out=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,level.registryAccess());s.save(out);
                s.load(TagValueInput.create(ProblemReporter.DISCARDING,level.registryAccess(),out.buildResult()));
                DrainTeam.join(level,crew[i],s);
            }
            DrainTeam team=DrainTeam.join(level,crew[0],states[0]);
            checkQuiet(states[0].drain.completed.cardinality()==states[0].drain.columns(),"saved crew completion records merge across 64 workers");
            team.column(crew[0],states[0]);
            for(WorkerState s:states) {
                checkQuiet(s.drain.stage==next[n],"64-worker crew must advance after merged completion of "+stages[n]);
                checkQuiet(s.drain.sandTops[0]==site.getY()-1,"stage recovery preserves sand ownership");
            }
        }
        check(true,"64-worker drain crew resumes every completed column stage after save/reload");
        for(CopperGolem g:crew)g.discard();helper.succeed();
    }

    @GameTest(maxTicks = 500, skyAccess = true, padding = 80)
    public void cooperativeDrainAndLegacyGlass(GameTestHelper helper) {
        ServerLevel level=helper.getLevel();BlockPos base=helper.absolutePos(new BlockPos(0,8,0));
        for(BlockPos p:BlockPos.betweenClosed(base.offset(-6,-5,-6),base.offset(6,3,6)))
            level.setBlock(p,p.getY()==base.getY()-4?Blocks.STONE.defaultBlockState():
                p.getY()<=base.getY()?Blocks.WATER.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
        for(BlockPos p:BlockPos.betweenClosed(base.offset(-5,-4,-5),base.offset(5,0,5)))
            if(Math.abs(p.getX()-base.getX())==5||Math.abs(p.getZ()-base.getZ())==5)
                level.setBlock(p,Blocks.GLASS.defaultBlockState(),2);
        for(BlockPos p:BlockPos.betweenClosed(base.offset(-1,-3,-1),base.offset(1,-2,1)))
            if(p.getY()==base.getY()-2||p.getX()!=base.getX()||p.getZ()!=base.getZ())
                level.setBlock(p,Blocks.PRISMARINE.defaultBlockState(),2);
        BlockPos room=base.below(3),roof=base.below(2);
        BlockPos chestPos=base.offset(0,1,-4);
        level.setBlock(chestPos,BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace("copper_chest")).defaultBlockState(),3);
        Container chest=(Container)level.getBlockEntity(chestPos);
        var box=new net.minecraft.world.level.levelgen.structure.BoundingBox(base.getX()-1,base.getY()-4,base.getZ()-1,base.getX()+1,base.getY()-2,base.getZ()+1);
        CopperGolem[] workers=new CopperGolem[64];WorkerState[] states=new WorkerState[64];
        for(int i=0;i<workers.length;i++) {
            BlockPos home=base.offset((i%2)*2-1,1,0);level.setBlock(home.below(),Blocks.COBBLESTONE.defaultBlockState(),3);
            workers[i]=EntityTypes.COPPER_GOLEM.create(level,net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            workers[i].setPos(Vec3.atBottomCenterOf(home));workers[i].setNoAi(true);level.addFreshEntity(workers[i]);
            states[i]=((WorkerAccess)workers[i]).copperJobs$state();states[i].assign(Job.DRAIN,home,Direction.NORTH);
            states[i].drain.begin(box,base.getY());
        }
        boolean glassAdded=false,reloaded=false,parallel=false,barrier=false;
        for(int n=0;n<20000&&states[0].drain.stage!=DrainState.Stage.DONE;n++) {
            for(int i=0;i<workers.length;i++) {
                WorkerState s=states[i];CopperGolem g=workers[i];
                if(s.drain.travel!=null)g.setPos(Vec3.atBottomCenterOf(s.drain.travel));
                if(s.drain.stage==DrainState.Stage.FILL&&!glassAdded) {
                    for(int c=0;c<s.drain.columns();c++) {
                        BlockPos top=s.drain.column(c,s.drain.surfaceY);
                        if(!s.drain.wall(top)&&s.drain.floors[c]<s.drain.surfaceY&&level.getBlockState(top).is(Blocks.WATER))
                            level.setBlock(top,Blocks.GLASS.defaultBlockState(),3);
                    }
                    glassAdded=true;
                }
                DrainJobs.work(level,g,s,ChestStorage.nearby(level,g));
                if(states[0].drain.stage==DrainState.Stage.CLEAR&&states[1].drain.stage==DrainState.Stage.CLEAR
                        &&states[0].drain.index!=states[1].drain.index)parallel=true;
                if(s.drain.stage==DrainState.Stage.WALLS) {
                    for(int c=0;c<s.drain.columns();c++) {
                        if(!s.drain.wall(s.drain.column(c,s.drain.surfaceY))&&s.drain.floors[c]<s.drain.surfaceY)
                            checkQuiet(s.drain.sandTops[c]==s.drain.surfaceY,"all columns must be filled before either worker starts walls");
                    }
                    barrier=true;
                }
                if(s.drain.stage==DrainState.Stage.FILL&&!reloaded&&java.util.Arrays.stream(s.drain.sandTops).anyMatch(v->v!=Integer.MIN_VALUE)) {
                    var out=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,level.registryAccess());s.save(out);
                    s.load(TagValueInput.create(ProblemReporter.DISCARDING,level.registryAccess(),out.buildResult()));reloaded=true;
                }
            }
            if(n==19999)throw new IllegalStateException("Cooperative drain stalled: "+states[0].status+" / "+states[1].status+" pos="+workers[0].position()+" travel="+states[0].drain.travel+" phase="+states[0].drain.routePhase+" stage="+states[0].drain.stage+" y="+states[0].drain.y+" index="+states[0].drain.index);
        }
        check(parallel&&barrier&&glassAdded&&reloaded,"64 drain workers split columns, replace legacy glass and resume a shared project after reload");
        for(WorkerState state:states)checkQuiet(state.drain.stage==DrainState.Stage.DONE,"all 64 workers finish the shared project");
        check(states[0].drain.stage==DrainState.Stage.DONE&&states[1].drain.stage==DrainState.Stage.DONE,
                "crew completes together after all sand and scaffolds are cleared");
        check(level.getBlockState(roof).is(Blocks.PRISMARINE)&&level.getBlockState(room).is(Blocks.WATER)&&count(chest,Items.SAND)==0,
                "cooperative drain preserves monument rooms and creates no sand payout");
        for(int c=0;c<states[0].drain.columns();c++) {
            DrainState d=states[0].drain;
            if(d.wall(d.column(c,d.surfaceY)))continue;
            for(int y=d.floors[c]+1;y<=d.surfaceY;y++)
                checkQuiet(level.getBlockState(d.column(c,y)).isAir(),"64-worker crew leaves no water or temporary sand above the surveyed exterior floor");
        }
        for(CopperGolem golem:workers)golem.discard();helper.succeed();
    }

    @GameTest(maxTicks = 400, skyAccess = true)
    public void generatorAndDrain(GameTestHelper helper) {
        ServerLevel level=helper.getLevel();
        BlockPos base=helper.absolutePos(new BlockPos(384,4,384));
        base=new BlockPos((base.getX()>>4)*16+7,base.getY(),(base.getZ()>>4)*16+7);
        for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++)level.getChunk((base.getX()+x)>>4,(base.getZ()+z)>>4);
        for(BlockPos p:BlockPos.betweenClosed(base.offset(-8,-6,-8),base.offset(8,5,8)))
            level.setBlock(p,p.getY()<base.getY()-4?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
        CopperGolem g=EntityTypes.COPPER_GOLEM.create(level,net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        g.setPos(Vec3.atBottomCenterOf(base));g.setNoAi(true);level.addFreshEntity(g);
        WorkerState s=((WorkerAccess)g).copperJobs$state();
        BlockPos chestPos=base.offset(0,0,-5);
        level.setBlock(chestPos,BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace("copper_chest")).defaultBlockState(),3);
        Container chest=(Container)level.getBlockEntity(chestPos);
        var player=helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.setPos(Vec3.atBottomCenterOf(base));
        var hand=net.minecraft.world.InteractionHand.MAIN_HAND;
        player.setItemInHand(hand,new ItemStack(Items.WOODEN_PICKAXE));
        net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.invoker().interact(player,level,hand,g,null);
        check(s.job==Job.GENERATOR,"wooden pickaxe selects generator mode, not quarry");
        BlockPos generator=base.east();
        level.setBlock(base.below(),Blocks.STONE.defaultBlockState(),3);
        level.setBlock(generator,Blocks.COBBLESTONE.defaultBlockState(),3);
        level.setBlock(generator.east(),Blocks.LAVA.defaultBlockState(),2);
        var hit=new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(generator),Direction.UP,generator,false);
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.invoker().interact(player,level,hand,hit);
        check(generator.equals(s.generator),"wooden pickaxe right-click marks a generator position");
        for(int cycle=0;cycle<3;cycle++) {
            level.setBlock(generator,Blocks.COBBLESTONE.defaultBlockState(),3);step(level,g,s);
        }
        check(count(chest,Items.COBBLESTONE)==3,"generator repeatedly mines regenerated blocks into Copper Chest");
        check(level.getBlockState(generator.east()).is(Blocks.LAVA),"generator leaves its lava supply untouched");
        step(level,g,s);check(s.generator.equals(generator)&&s.status.contains("regenerate"),"empty generator waits without losing its marked position");
        level.setBlock(generator,Blocks.STONE.defaultBlockState(),3);
        for(int n=0;n<chest.getContainerSize();n++)chest.setItem(n,new ItemStack(Items.DIRT,64));
        step(level,g,s);check(level.getBlockState(generator).is(Blocks.STONE),"full chest pauses generator before breaking");
        chest.clearContent();step(level,g,s);check(count(chest,Items.STONE)==1,"generator resumes with Silk Touch stone after chest is emptied");
        level.setBlock(generator,Blocks.DIAMOND_BLOCK.defaultBlockState(),3);step(level,g,s);
        check(level.getBlockState(generator).is(Blocks.DIAMOND_BLOCK),"generator ignores replacement blocks other than stone or cobblestone");
        var saved=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,level.registryAccess());s.save(saved);
        WorkerState restored=new WorkerState();restored.load(TagValueInput.create(ProblemReporter.DISCARDING,level.registryAccess(),saved.buildResult()));
        check(generator.equals(restored.generator)&&restored.job==Job.GENERATOR,"generator assignment survives save and reload");
        player.setItemInHand(hand,new ItemStack(Items.TRIDENT));
        net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.invoker().interact(player,level,hand,g,null);
        check(s.job==Job.GENERATOR,"unenchanted trident cannot assign drain crew");
        player.getItemInHand(hand).enchant(level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
            .getOrThrow(net.minecraft.world.item.enchantment.Enchantments.LOYALTY),3);
        net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.invoker().interact(player,level,hand,g,null);
        check(s.job==Job.DRAIN,"Loyalty III trident assigns drain crew");
        step(level,g,s);check(s.drain.stage==DrainState.Stage.NEW,"no monument or ocean surface means no construction");
        // Small monument-shaped exterior fixture: a sealed water-filled room under a prismarine roof.
        for(BlockPos p:BlockPos.betweenClosed(base.offset(-4,-4,-4),base.offset(4,1,4)))
            level.setBlock(p,p.getY()==base.getY()-4?Blocks.SAND.defaultBlockState():
                p.getY()<=base.getY()?Blocks.WATER.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
        for(BlockPos p:BlockPos.betweenClosed(base.offset(-1,-3,-1),base.offset(1,-2,1)))
            if(p.getY()==base.getY()-2 || p.getX()!=base.getX() || p.getZ()!=base.getZ())level.setBlock(p,Blocks.PRISMARINE.defaultBlockState(),2);
        BlockPos room=base.below(3);
        BlockPos roof=base.below(2);
        BlockPos kelp=base.offset(2,-3,0),grass=base.offset(-2,-3,0);
        level.setBlock(kelp,Blocks.KELP.defaultBlockState(),2);level.setBlock(grass,Blocks.SEAGRASS.defaultBlockState(),2);
        BlockPos home=base.above();level.setBlock(base,Blocks.COBBLESTONE.defaultBlockState(),2);
        s.assign(Job.DRAIN,home,Direction.NORTH);g.setPos(Vec3.atBottomCenterOf(home));chest.clearContent();
        var monument=level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE)
            .getOrThrow(net.minecraft.world.level.levelgen.structure.BuiltinStructures.OCEAN_MONUMENT).value();
        var piece=new net.minecraft.world.level.levelgen.structure.structures.OceanMonumentPieces.MonumentBuilding(
            net.minecraft.util.RandomSource.create(1),base.getX()-29,base.getZ()-29,Direction.NORTH);
        var pb=piece.getBoundingBox();
        piece.move(base.getX()-pb.getCenter().getX(),base.getY()-1-pb.maxY(),base.getZ()-pb.getCenter().getZ());
        var chunk=new net.minecraft.world.level.ChunkPos(base.getX()>>4,base.getZ()>>4);
        var start=new net.minecraft.world.level.levelgen.structure.StructureStart(monument,chunk,0,
            new net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer(List.of(piece)));
        var originChunk=level.getChunk(chunk.x(),chunk.z());
        originChunk.setStartForStructure(monument,start);
        originChunk.addReferenceForStructure(monument,chunk.pack());
        DrainJobs.work(level,g,s,ChestStorage.nearby(level,g));
        check(s.drain.stage==DrainState.Stage.SURVEY&&s.drain.minX==piece.getBoundingBox().minX()-2,
            "drain discovers monument metadata beneath its water-level platform and uses full structure bounds");
        originChunk.setStartForStructure(monument,net.minecraft.world.level.levelgen.structure.StructureStart.INVALID_START);
        s.assign(Job.DRAIN,home,Direction.NORTH);
        s.drain.begin(new net.minecraft.world.level.levelgen.structure.BoundingBox(base.getX()-1,base.getY()-4,base.getZ()-1,
            base.getX()+1,base.getY()-2,base.getZ()+1),base.getY());
        boolean reloaded=false,sawWalls=false;
        for(int n=0;n<10000 && s.drain.stage!=DrainState.Stage.DONE;n++) {
            if(s.drain.travel!=null)g.setPos(Vec3.atBottomCenterOf(s.drain.travel));
            if(s.drain.stage==DrainState.Stage.FILL && !reloaded && java.util.Arrays.stream(s.drain.sandTops).anyMatch(v->v!=Integer.MIN_VALUE)) {
                var output=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,level.registryAccess());s.save(output);
                s.load(TagValueInput.create(ProblemReporter.DISCARDING,level.registryAccess(),output.buildResult()));reloaded=true;
            }
            if(s.drain.stage==DrainState.Stage.WALLS)sawWalls=true;
            DrainJobs.work(level,g,s,ChestStorage.nearby(level,g));
            checkQuiet(level.getBlockState(roof).is(Blocks.PRISMARINE),"drain must never mine monument roof");
            checkQuiet(level.getBlockState(room).is(Blocks.WATER),"drain must never edit room water");
            if(n==9999)throw new IllegalStateException("Drain failed to finish: "+s.drain.stage+" "+s.status+" index="+s.drain.index+" y="+s.drain.y);
        }
        check(reloaded&&sawWalls&&s.drain.stage==DrainState.Stage.DONE,"drain completes all exterior construction stages after a mid-fill reload");
        check(count(chest,Items.KELP)==1&&count(chest,Items.SEAGRASS)==1&&count(chest,Items.SAND)==0,"only kelp and seagrass are deposited; temporary sand gives no drops");
        check(level.getBlockState(room).is(Blocks.WATER)&&level.getBlockState(roof).is(Blocks.PRISMARINE),"monument blocks and interior water remain untouched");
        for(int c=0;c<s.drain.columns();c++) {
            BlockPos p=s.drain.column(c,base.getY());
            if(s.drain.wall(p))checkQuiet(level.getBlockState(p).is(Blocks.GLASS),"outside glass wall remains");
            else if(s.drain.floors[c]<base.getY())for(int y=s.drain.floors[c]+1;y<=base.getY();y++)
                checkQuiet(level.getBlockState(s.drain.column(c,y)).isAir(),"exterior column must be drained and cleared of temporary sand");
            checkQuiet(level.getBlockState(s.drain.column(c,base.getY()-4)).is(Blocks.SAND),"pre-existing seabed sand must remain");
        }
        check(s.drain.scaffolds.isEmpty()&&g.position().distanceToSqr(Vec3.atBottomCenterOf(home))<.16,"drain returns home and cleans temporary footholds");
        // Collision-aware swimming is exercised without teleporting the worker.
        s.assign(Job.DRAIN,home,Direction.NORTH);
        s.drain.begin(new net.minecraft.world.level.levelgen.structure.BoundingBox(base.getX()-1,base.getY()-4,base.getZ()-1,base.getX()+1,base.getY()-2,base.getZ()+1),base.getY());
        g.setPos(Vec3.atBottomCenterOf(base.offset(4,-3,0)));s.drain.travel=base.offset(4,1,0);s.drain.routePhase=2;
        double before=g.getY();for(int n=0;n<12;n++)DrainJobs.motion(level,g,s);
        check(g.getY()>before+1,"drain swimming control raises the golem through exterior water");
        DrainJobs.stop(g,s);check(!g.isNoGravity()&&s.drain.travel==null,"pausing drain movement restores gravity");
        g.discard();helper.succeed();
    }
    @GameTest(maxTicks = 100, skyAccess = true, padding = 80)
    public void buriedLegacyWall(GameTestHelper helper) {
        ServerLevel level=helper.getLevel();BlockPos base=helper.absolutePos(new BlockPos(4,30,4));
        CopperGolem g=EntityTypes.COPPER_GOLEM.create(level,net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        BlockPos home=base.above();g.setPos(Vec3.atBottomCenterOf(home));g.setNoAi(true);level.addFreshEntity(g);
        WorkerState s=((WorkerAccess)g).copperJobs$state();s.assign(Job.DRAIN,home,Direction.NORTH);
        s.drain.begin(new net.minecraft.world.level.levelgen.structure.BoundingBox(base.getX()-1,base.getY()-4,base.getZ()-1,base.getX()+1,base.getY()-2,base.getZ()+1),base.getY());
        s.drain.structureBottom=Integer.MIN_VALUE;s.drain.bottomY=base.getY()-22;
        s.drain.stage=DrainState.Stage.WALLS;
        java.util.Arrays.fill(s.drain.floors,s.drain.bottomY);
        s.drain.completed.set(1,s.drain.columns());
        var saved=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,level.registryAccess());s.save(saved);
        var legacy=saved.buildResult();legacy.remove("DrainStructureBottom");
        s.load(TagValueInput.create(ProblemReporter.DISCARDING,level.registryAccess(),legacy));
        check(s.drain.structureBottom==Integer.MIN_VALUE&&s.drain.bottomY==base.getY()-22,
                "pre-update saves resume with their existing wall depth and sand records");
        BlockPos target=s.drain.column(0,s.drain.bottomY+1);
        for(BlockPos p:BlockPos.betweenClosed(target.offset(-3,-1,-3),target.offset(3,3,3)))
            level.setBlock(p,Blocks.STONE.defaultBlockState(),2);
        level.setBlock(target,Blocks.WATER.defaultBlockState(),2);
        Vec3 before=g.position();
        DrainJobs.work(level,g,s,ChestStorage.nearby(level,g));
        check(level.getBlockState(target).is(Blocks.GLASS),"legacy buried wall gap is sealed remotely without excavating twenty blocks");
        check(g.position().equals(before)&&!WorkerBrain.inReach(level,g,target),"remote construction does not move or teleport the worker to the buried target");
        check(level.getBlockState(target.west()).is(Blocks.STONE),"solid terrain is preserved beside the buried wall");
        g.discard();helper.succeed();
    }
    @GameTest(maxTicks = 12000, skyAccess = true, padding = 32)
    public void liveDrain(GameTestHelper helper) {
        ServerLevel level=helper.getLevel();
        BlockPos base=helper.absolutePos(new BlockPos(4,5,4));
        // The worker leaves the 8x8 template. Keep the surrounding fixture entity-ticking,
        // as a nearby player would, rather than depending on the random test chunk alignment.
        java.util.List<net.minecraft.world.level.ChunkPos> forced=new java.util.ArrayList<>();
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++) {
            var c=new net.minecraft.world.level.ChunkPos((base.getX()>>4)+dx,(base.getZ()>>4)+dz);
            if(level.getChunkSource().updateChunkForced(c,true))forced.add(c);
            level.getChunk(c.x(),c.z());
        }
        helper.runBeforeTestEnd(()->forced.forEach(c->level.getChunkSource().updateChunkForced(c,false)));

        for(BlockPos p:BlockPos.betweenClosed(base.offset(-5,-24,-5),base.offset(5,3,5)))
            level.setBlock(p,p.getY()==base.getY()-24?Blocks.STONE.defaultBlockState():
                p.getY()<=base.getY()?Blocks.WATER.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
        // Contain the artificial ocean so its flowing water cannot spill into adjacent tests.
        for(BlockPos p:BlockPos.betweenClosed(base.offset(-5,-24,-5),base.offset(5,0,5)))
            if(Math.abs(p.getX()-base.getX())==5||Math.abs(p.getZ()-base.getZ())==5)
                level.setBlock(p,Blocks.GLASS.defaultBlockState(),2);
        for(BlockPos p:BlockPos.betweenClosed(base.offset(-1,-3,-1),base.offset(1,-2,1)))
            if(p.getY()==base.getY()-2 || p.getX()!=base.getX() || p.getZ()!=base.getZ())level.setBlock(p,Blocks.PRISMARINE.defaultBlockState(),2);
        level.setBlock(base.offset(2,-3,0),Blocks.KELP.defaultBlockState(),2);
        level.setBlock(base.offset(-2,-3,0),Blocks.SEAGRASS.defaultBlockState(),2);
        level.setBlock(base,Blocks.COBBLESTONE.defaultBlockState(),3);
        BlockPos chestPos=base.offset(0,1,-4);level.setBlock(chestPos.below(),Blocks.STONE.defaultBlockState(),3);
        level.setBlock(chestPos,BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace("copper_chest")).defaultBlockState(),3);
        CopperGolem g=EntityTypes.COPPER_GOLEM.create(level,net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        g.setPos(Vec3.atBottomCenterOf(base.above()));level.addFreshEntity(g);
        WorkerState s=((WorkerAccess)g).copperJobs$state();s.assign(Job.DRAIN,base.above(),Direction.NORTH);
        s.drain.begin(new net.minecraft.world.level.levelgen.structure.BoundingBox(base.getX()-1,base.getY()-4,base.getZ()-1,
            base.getX()+1,base.getY()-2,base.getZ()+1),base.getY());
        BlockPos partnerHome=base.west(2).above();
        level.setBlock(partnerHome.below(),Blocks.COBBLESTONE.defaultBlockState(),3);
        CopperGolem partner=EntityTypes.COPPER_GOLEM.create(level,net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        partner.setPos(Vec3.atBottomCenterOf(partnerHome));level.addFreshEntity(partner);
        WorkerState partnerState=((WorkerAccess)partner).copperJobs$state();
        partnerState.assign(Job.DRAIN,partnerHome,Direction.NORTH);
        partnerState.drain.begin(new net.minecraft.world.level.levelgen.structure.BoundingBox(base.getX()-1,base.getY()-4,base.getZ()-1,
            base.getX()+1,base.getY()-2,base.getZ()+1),base.getY());
        CopperGolem[] lateHelper=new CopperGolem[1];
        helper.onEachTick(()->{
            if(lateHelper[0]==null && s.drain.stage==DrainState.Stage.FILL) {
                for(int column=0;column<s.drain.columns();column++) {
                    if(s.drain.sandTops[column]!=s.drain.surfaceY)continue;
                    BlockPos sand=s.drain.column(column,s.drain.surfaceY);
                    CopperGolem recruit=EntityTypes.COPPER_GOLEM.create(level,net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                    recruit.setPos(Vec3.atBottomCenterOf(sand.above()));level.addFreshEntity(recruit);
                    var player=helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
                    player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                            enchanted(level,Items.TRIDENT,net.minecraft.world.item.enchantment.Enchantments.LOYALTY,3));
                    net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.invoker()
                            .interact(player,level,net.minecraft.world.InteractionHand.MAIN_HAND,recruit,null);
                    lateHelper[0]=recruit;
                    break;
                }
            }
            if(helper.getTick()%1000==0)System.out.println("LIVE DRAIN "+helper.getTick()+" "+s.drain.stage+" "+s.drain.index+" "+s.drain.y+" "+s.status+" "+g.position()+" target="+s.drain.travel+" phase="+s.drain.routePhase+" route="+s.drain.route+" lid="+(s.drain.travel==null?"none":level.getBlockState(new BlockPos(s.drain.travel.getX(),s.drain.surfaceY,s.drain.travel.getZ()))) );
            checkQuiet(level.getBlockState(base.below(2)).is(Blocks.PRISMARINE),"live drain must preserve roof");
            if(s.drain.stage==DrainState.Stage.DONE) {
                check(g.position().distanceToSqr(Vec3.atBottomCenterOf(base.above()))<.5,"live drain walks, swims, finishes and returns home without test teleports");
                check(level.getBlockState(base.below(3)).is(Blocks.WATER),"live drain keeps sealed interior water intact through fluid ticks");
                check(count((Container)level.getBlockEntity(chestPos),Items.SAND)==0,"live drain creates no sand payout");
                check(level.getBlockState(base.offset(2,-4,0)).is(Blocks.GLASS)
                        &&level.getBlockState(base.offset(2,-5,0)).is(Blocks.WATER),
                        "ravine is sealed at the monument base while deeper water stays untouched");
                check(s.drain.bottomY==base.getY()-4,"ravine does not lower the drain depth below the structure");
                var saved=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,level.registryAccess());s.save(saved);
                WorkerState resumed=new WorkerState();resumed.load(TagValueInput.create(ProblemReporter.DISCARDING,level.registryAccess(),saved.buildResult()));
                check(resumed.drain.structureBottom==base.getY()-4,"structure bottom survives save and reload");
                check(partnerState.drain.stage==DrainState.Stage.DONE&&partner.position().distanceToSqr(Vec3.atBottomCenterOf(partnerHome))<.5,
                        "second live drain worker completes the shared project and returns home without test teleports");
                check(lateHelper[0]!=null,"a helper was assigned after the sand cap was already present");
                WorkerState joined=((WorkerAccess)lateHelper[0]).copperJobs$state();
                check(joined.drain.stage==DrainState.Stage.DONE&&joined.drain.floors==s.drain.floors,
                        "late helper joins a partially sand-covered monument through normal trident assignment and completes with the crew");
                check((joined.origin.equals(s.origin)||joined.origin.equals(partnerState.origin))
                        && lateHelper[0].position().distanceToSqr(Vec3.atBottomCenterOf(joined.origin))<.5,
                        "late helper returns to an established platform after its sand footing is removed");
                g.discard();partner.discard();lateHelper[0].discard();helper.succeed();
            }
        });
    }
    @GameTest(maxTicks = 100, skyAccess = true)
    public void woodenAxeTreeFarm(GameTestHelper helper) {
        ServerLevel level=helper.getLevel();
        BlockPos ref=helper.absolutePos(new BlockPos(640,5,640));
        BlockPos base=new BlockPos((ref.getX()>>4)*16+5,ref.getY(),(ref.getZ()>>4)*16+5);
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)level.getChunk((base.getX()>>4)+dx,(base.getZ()>>4)+dz);
        for(BlockPos p:BlockPos.betweenClosed(base.offset(-4,-2,-4),base.offset(10,5,10)))
            level.setBlock(p,p.getY()<base.getY()?Blocks.DIRT.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
        CopperGolem g=EntityTypes.COPPER_GOLEM.create(level,net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        g.setPos(Vec3.atBottomCenterOf(base));g.setNoAi(true);level.addFreshEntity(g);
        WorkerState worker=((WorkerAccess)g).copperJobs$state();
        BlockPos chestPos=base.offset(-2,0,-2);
        Block copper=BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace("copper_chest"));
        level.setBlock(chestPos,copper.defaultBlockState(),3);Container chest=(Container)level.getBlockEntity(chestPos);
        chest.setItem(0,new ItemStack(Items.OAK_SAPLING,3));
        var player=helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        ItemStack axe=new ItemStack(Items.WOODEN_AXE);axe.setDamageValue(3);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,axe);
        net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.invoker().interact(player,level,net.minecraft.world.InteractionHand.MAIN_HAND,g,null);
        check(worker.job==Job.TREE_FARM&&axe.getCount()==1&&axe.getDamageValue()==3,"wooden axe selects tree farming without consuming or wearing the tool");
        check(Job.of(new ItemStack(Items.IRON_AXE))==Job.AXE,"other axes retain the clearing job");
        BlockPos root=base.east(2);testTree(level,root,Blocks.OAK_LOG,Blocks.OAK_LEAVES,1);
        step(level,g,worker);
        check(level.getBlockState(root).isAir()&&count(chest,Items.OAK_LOG)==2&&worker.treePlots.size()==1,"tree farmer harvests wood and remembers the cleared planting site");
        var out=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,level.registryAccess());worker.save(out);
        worker.load(TagValueInput.create(ProblemReporter.DISCARDING,level.registryAccess(),out.buildResult()));
        check(worker.job==Job.TREE_FARM&&worker.treePlots.getFirst().pos().equals(root)
            &&worker.treePlots.getFirst().sapling().equals(Identifier.withDefaultNamespace("oak_sapling")),"tree planting site and species survive save/reload");
        int before=count(chest,Items.OAK_SAPLING);step(level,g,worker);
        check(level.getBlockState(root).is(Blocks.OAK_SAPLING)&&count(chest,Items.OAK_SAPLING)==before-1&&worker.treePlots.isEmpty(),"replanting spends exactly one real sapling and keeps the spares in storage");
        testTree(level,root,Blocks.OAK_LOG,Blocks.OAK_LEAVES,1);step(level,g,worker);step(level,g,worker);
        check(level.getBlockState(root).is(Blocks.OAK_SAPLING)&&count(chest,Items.OAK_LOG)==4,"tree farmer harvests regrowth and replants without reassignment");
        testTree(level,root,Blocks.OAK_LOG,Blocks.OAK_LEAVES,1);step(level,g,worker);
        for(int n=0;n<chest.getContainerSize();n++)chest.setItem(n,new ItemStack(Items.DIRT,64));
        step(level,g,worker);check(level.getBlockState(root).isAir()&&worker.treePlots.size()==1,"full Copper Chest pauses tree replanting without losing the site");
        chest.clearContent();step(level,g,worker);
        check(level.getBlockState(root).isAir()&&worker.treePlots.size()==1,"tree farmer waits for real saplings instead of creating free ones");
        level.setBlock(chestPos,Blocks.AIR.defaultBlockState(),3);step(level,g,worker);
        check(level.getBlockState(root).isAir()&&worker.status.contains("No Copper Chest"),"missing Copper Chest also pauses the tree farmer");
        level.setBlock(chestPos,copper.defaultBlockState(),3);chest=(Container)level.getBlockEntity(chestPos);
        chest.setItem(0,new ItemStack(Items.OAK_SAPLING,2));step(level,g,worker);
        check(level.getBlockState(root).is(Blocks.OAK_SAPLING),"tree farmer resumes automatically when storage and saplings return");
        worker.assign(Job.AXE,base,Direction.NORTH);testTree(level,root,Blocks.OAK_LOG,Blocks.OAK_LEAVES,1);step(level,g,worker);
        before=count(chest,Items.OAK_SAPLING);step(level,g,worker);
        check(level.getBlockState(root).isAir()&&worker.treePlots.isEmpty()&&count(chest,Items.OAK_SAPLING)==before,"clearing axes never replant or spend saplings");
        BlockPos giant=base.south(4);g.setPos(Vec3.atBottomCenterOf(giant.west(2)));
        worker.assign(Job.TREE_FARM,base,Direction.NORTH);chest.clearContent();
        testTree(level,giant,Blocks.DARK_OAK_LOG,Blocks.DARK_OAK_LEAVES,2);step(level,g,worker);
        check(worker.treePlots.size()==1&&worker.treePlots.getFirst().size()==2&&worker.treePlots.getFirst().pos().equals(giant),"dark oak records its original 2x2 footprint as one planting site");
        chest.clearContent();chest.setItem(0,new ItemStack(Items.DARK_OAK_SAPLING,3));step(level,g,worker);
        check(count(chest,Items.DARK_OAK_SAPLING)==3&&level.getBlockState(giant).isAir(),"2x2 replanting waits for all four saplings without spending a partial batch");
        chest.setItem(0,new ItemStack(Items.DARK_OAK_SAPLING,4));level.setBlock(giant.east().south(),Blocks.STONE.defaultBlockState(),3);step(level,g,worker);
        check(level.getBlockState(giant.east().south()).is(Blocks.STONE)&&count(chest,Items.DARK_OAK_SAPLING)==4,"replanting preserves an obstructed site and its sapling supply");
        level.setBlock(giant.east().south(),Blocks.AIR.defaultBlockState(),3);step(level,g,worker);
        for(int x=0;x<2;x++)for(int z=0;z<2;z++)checkQuiet(level.getBlockState(giant.offset(x,0,z)).is(Blocks.DARK_OAK_SAPLING),"all four dark oak saplings must be planted");
        check(count(chest,Items.DARK_OAK_SAPLING)==0&&worker.treePlots.isEmpty(),"2x2 planting consumes exactly four saplings and completes the site");
        BlockPos border=new BlockPos(((base.getX()>>4)+1)*16,base.getY(),base.getZ());
        level.setBlock(border.below(),Blocks.DIRT.defaultBlockState(),3);testTree(level,border,Blocks.BIRCH_LOG,Blocks.BIRCH_LEAVES,1);
        g.setPos(Vec3.atBottomCenterOf(border.west(2)));worker.assign(Job.TREE_FARM,base,Direction.NORTH);chest.clearContent();
        chest.setItem(0,new ItemStack(Items.BIRCH_SAPLING,2));step(level,g,worker);step(level,g,worker);
        check(level.getBlockState(border).is(Blocks.BIRCH_SAPLING)&&count(chest,Items.BIRCH_LOG)==2,"wooden axe harvests and replants matching species across the assignment chunk border");
        g.discard();helper.succeed();
    }
    private static void testTree(ServerLevel level,BlockPos root,Block log,Block leaves,int size) {
        for(int x=0;x<size;x++)for(int z=0;z<size;z++) {
            for(int y=0;y<2;y++)level.setBlock(root.offset(x,y,z),log.defaultBlockState(),2);
            level.setBlock(root.offset(x,2,z),leaves.defaultBlockState().setValue(BlockStateProperties.PERSISTENT,false),2);
        }
    }
    @GameTest(maxTicks = 100, skyAccess = true)
    public void chestSaplingsOnEmptyGround(GameTestHelper helper) {
        ServerLevel level=helper.getLevel();BlockPos base=helper.absolutePos(new BlockPos(900,4,900));
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)level.getChunk((base.getX()>>4)+dx,(base.getZ()>>4)+dz);
        for(BlockPos p:BlockPos.betweenClosed(base.offset(-12,-2,-12),base.offset(12,6,12)))
            level.setBlock(p,p.getY()<base.getY()?Blocks.COARSE_DIRT.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
        CopperGolem g=EntityTypes.COPPER_GOLEM.create(level,net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        g.setPos(Vec3.atBottomCenterOf(base));g.setNoAi(true);level.addFreshEntity(g);WorkerState s=((WorkerAccess)g).copperJobs$state();
        s.assign(Job.TREE_FARM,base,Direction.NORTH);
        BlockPos chestPos=base.offset(0,0,-3);Block copper=BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace("copper_chest"));
        level.setBlock(chestPos,copper.defaultBlockState(),3);Container chest=(Container)level.getBlockEntity(chestPos);
        chest.setItem(0,new ItemStack(Items.OAK_SAPLING,3));chest.setItem(1,new ItemStack(Items.BIRCH_SAPLING,3));
        step(level,g,s);
        int oak=0,birch=0;
        for(BlockPos p:BlockPos.betweenClosed(base.offset(-4,0,-4),base.offset(4,0,4)))if(level.getBlockState(p).is(Blocks.OAK_SAPLING))oak++;
        check(oak==1&&count(chest,Items.OAK_SAPLING)==2,"chest saplings plant on fresh coarse dirt without any previous tree or remembered plot");
        for(int n=0;n<20;n++) {
            if(s.approach!=null)g.setPos(Vec3.atBottomCenterOf(s.approach));
            step(level,g,s);
            if(count(chest,Items.BIRCH_SAPLING)<3)break;
        }
        for(BlockPos p:BlockPos.betweenClosed(base.offset(-12,0,-12),base.offset(12,0,12)))if(level.getBlockState(p).is(Blocks.BIRCH_SAPLING))birch++;
        check(birch==1&&count(chest,Items.BIRCH_SAPLING)==2,"fresh planting rotates through different sapling types supplied in storage");
        check(!level.getBlockState(g.blockPosition()).is(BlockTags.SAPLINGS),"fresh planting does not put a sapling under the worker's feet");
        g.setPos(Vec3.atBottomCenterOf(base));s.assign(Job.TREE_FARM,base,Direction.NORTH);chest.clearContent();
        BlockPos remembered=base.east(2);level.setBlock(remembered,Blocks.AIR.defaultBlockState(),2);
        s.treePlots.add(new TreeFarmJob.Plot(remembered,Identifier.withDefaultNamespace("oak_sapling"),1));
        chest.setItem(0,new ItemStack(Items.CHERRY_SAPLING,2));step(level,g,s);
        check(level.getBlockState(remembered).is(Blocks.CHERRY_SAPLING)&&count(chest,Items.CHERRY_SAPLING)==1,
            "a remembered site can use a different supplied sapling when its original species is unavailable");
        chest.clearContent();s.assign(Job.TREE_FARM,base,Direction.NORTH);step(level,g,s);
        check(s.treePlots.isEmpty()&&chest.isEmpty(),"empty storage never creates new planting supplies");
        g.discard();helper.succeed();
    }

    @GameTest(maxTicks = 1500, skyAccess = true, padding = 32)
    public void treeUnderLowLeaves(GameTestHelper helper) {
        ServerLevel level=helper.getLevel();BlockPos base=helper.absolutePos(new BlockPos(3,4,3));
        var forced=new java.util.ArrayList<net.minecraft.world.level.ChunkPos>();
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){
            var c=new net.minecraft.world.level.ChunkPos((base.getX()>>4)+dx,(base.getZ()>>4)+dz);
            if(level.getChunkSource().updateChunkForced(c,true))forced.add(c);level.getChunk(c.x(),c.z());
        }
        helper.runBeforeTestEnd(()->forced.forEach(c->level.getChunkSource().updateChunkForced(c,false)));
        for(BlockPos p:BlockPos.betweenClosed(base.offset(-2,-2,-4),base.offset(9,5,4)))
            level.setBlock(p,p.getY()<base.getY()?Blocks.COARSE_DIRT.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
        BlockPos root=base.east(6);
        for(int y=0;y<4;y++)level.setBlock(root.above(y),Blocks.OAK_LOG.defaultBlockState(),2);
        for(BlockPos p:BlockPos.betweenClosed(base.offset(2,1,-2),base.offset(8,1,2)))
            if(p.getX()!=root.getX()||p.getZ()!=root.getZ())level.setBlock(p,Blocks.OAK_LEAVES.defaultBlockState()
                .setValue(BlockStateProperties.PERSISTENT,false).setValue(BlockStateProperties.DISTANCE,1),2);
        BlockPos chestPos=base.offset(-1,0,-2);level.setBlock(chestPos,BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace("copper_chest")).defaultBlockState(),3);
        Container chest=(Container)level.getBlockEntity(chestPos);
        CopperGolem g=EntityTypes.COPPER_GOLEM.create(level,net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        g.setPos(Vec3.atBottomCenterOf(base));level.addFreshEntity(g);WorkerState s=((WorkerAccess)g).copperJobs$state();s.assign(Job.TREE_FARM,base,Direction.EAST);
        check(WorkerBrain.standable(level,g,root.west()),"copper golem fits in the one-block space beneath low leaves");
        helper.onEachTick(()->{
            if(count(chest,Items.OAK_LOG)==4){
                check(g.getX()>base.getX()+2,"live tree farmer approaches and harvests under a low canopy without a player breaking leaves");
                g.discard();helper.succeed();
            }
        });
    }
    @GameTest(maxTicks = 100, skyAccess = true)
    public void megaTreesAndStrippedProtection(GameTestHelper helper) {
        ServerLevel level=helper.getLevel();BlockPos base=helper.absolutePos(new BlockPos(1200,5,1200));
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)level.getChunk((base.getX()>>4)+dx,(base.getZ()>>4)+dz);
        for(BlockPos p:BlockPos.betweenClosed(base.offset(-4,-2,-4),base.offset(7,82,7)))
            level.setBlock(p,p.getY()<base.getY()?Blocks.DIRT.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
        CopperGolem g=EntityTypes.COPPER_GOLEM.create(level,net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        g.setPos(Vec3.atBottomCenterOf(base));g.setNoAi(true);level.addFreshEntity(g);
        WorkerState s=((WorkerAccess)g).copperJobs$state();
        BlockPos chestPos=base.offset(-2,0,-2);level.setBlock(chestPos,BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace("copper_chest")).defaultBlockState(),3);
        Container chest=(Container)level.getBlockEntity(chestPos);BlockPos root=base.east(2);
        check(Job.of(new ItemStack(Items.NETHERITE_AXE))==Job.MEGA_AXE,"netherite axe selects the unlimited-size tree cutter");
        for(int x=0;x<2;x++)for(int z=0;z<2;z++)for(int y=0;y<80;y++)level.setBlock(root.offset(x,y,z),Blocks.OAK_LOG.defaultBlockState(),2);
        level.setBlock(root.above(80),Blocks.OAK_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT,true),2);
        BlockPos outer=root.above(78).north(10),corner=root.offset(-10,78,-10),top=root.above(89),outside=root.above(78).north(11);
        for(BlockPos p:List.of(outer,corner,top,outside))level.setBlock(p,Blocks.OAK_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT,true),2);
        BlockPos stripped=root.north(),wood=root.east(2);
        level.setBlock(stripped,Blocks.STRIPPED_OAK_LOG.defaultBlockState(),2);level.setBlock(wood,Blocks.STRIPPED_OAK_WOOD.defaultBlockState(),2);
        s.assign(Job.MEGA_AXE,base,Direction.EAST);step(level,g,s);
        check(s.megaTree.phase==MegaTreeJob.Phase.LOGS&&s.megaTree.logs.size()<=128&&level.getBlockState(root).is(Blocks.OAK_LOG),"mega-tree discovery is budgeted and does not break the entire tree in one tick");
        var out=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,level.registryAccess());s.save(out);
        int discovered=s.megaTree.logs.size();s.load(TagValueInput.create(ProblemReporter.DISCARDING,level.registryAccess(),out.buildResult()));
        check(s.megaTree.phase==MegaTreeJob.Phase.LOGS&&s.megaTree.logs.size()==discovered&&!s.megaTree.frontier.isEmpty(),"large-tree frontier and progress survive save/reload");
        for(int n=0;n<100&&s.megaTree.phase!=MegaTreeJob.Phase.HARVEST;n++)step(level,g,s);
        check(s.megaTree.phase==MegaTreeJob.Phase.HARVEST&&s.megaTree.logs.size()==320,"large-tree survey follows all 320 logs including those above the old 64-block range");
        check(s.megaTree.leaves.contains(outer)&&s.megaTree.leaves.contains(corner)&&s.megaTree.leaves.contains(top)&&!s.megaTree.leaves.contains(outside),"mega-tree canopy includes leaves ten blocks out horizontally, vertically and diagonally, but stops at eleven");
        out=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,level.registryAccess());s.save(out);
        var legacy=out.buildResult();legacy.remove("MegaTreeLeafRadius");
        s.load(TagValueInput.create(ProblemReporter.DISCARDING,level.registryAccess(),legacy));
        check(s.megaTree.phase==MegaTreeJob.Phase.LEAVES&&s.megaTree.index==0,"saved jobs from the old three-block canopy resurvey before resuming harvest");
        step(level,g,s);
        int scanIndex=s.megaTree.index,scanOffset=s.megaTree.offset;
        out=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,level.registryAccess());s.save(out);
        s.load(TagValueInput.create(ProblemReporter.DISCARDING,level.registryAccess(),out.buildResult()));
        check(s.megaTree.index==scanIndex&&s.megaTree.offset==scanOffset,"ten-block canopy scan cursor survives save/reload");
        for(int n=0;n<100&&s.megaTree.phase!=MegaTreeJob.Phase.HARVEST;n++)step(level,g,s);
        // Protect a previously surveyed log by stripping it before the cutter reaches it.
        BlockPos changed=root.above(79);level.setBlock(changed,Blocks.STRIPPED_OAK_LOG.defaultBlockState(),2);
        for(int n=0;n<chest.getContainerSize();n++)chest.setItem(n,new ItemStack(Items.DIRT,64));
        step(level,g,s);check(s.megaTree.index==0&&level.getBlockState(root).is(Blocks.OAK_LOG),"full storage pauses a surveyed mega tree before harvesting");
        chest.clearContent();step(level,g,s);
        check(s.megaTree.index<=16&&count(chest,Items.OAK_LOG)<=16,"mega-tree harvesting respects the 16-block per-work-tick budget");
        out=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,level.registryAccess());s.save(out);
        s.load(TagValueInput.create(ProblemReporter.DISCARDING,level.registryAccess(),out.buildResult()));
        for(int n=0;n<40&&s.megaTree.phase!=MegaTreeJob.Phase.IDLE;n++)step(level,g,s);
        check(s.megaTree.phase==MegaTreeJob.Phase.IDLE&&count(chest,Items.OAK_LOG)==319&&level.getBlockState(root.offset(1,79,1)).isAir(),"mega cutter finishes beyond 256 logs with exact drops after harvest save/reload");
        check(level.getBlockState(stripped).is(Blocks.STRIPPED_OAK_LOG)&&level.getBlockState(wood).is(Blocks.STRIPPED_OAK_WOOD)&&level.getBlockState(changed).is(Blocks.STRIPPED_OAK_LOG),"netherite cutter preserves stripped logs, stripped wood and logs stripped mid-job");
        check(level.getBlockState(outer).isAir()&&level.getBlockState(corner).isAir()&&level.getBlockState(top).isAir()&&level.getBlockState(outside).is(Blocks.OAK_LEAVES),"mega worker harvests the extended canopy while preserving leaves beyond the ten-block boundary");
        for(Job job:List.of(Job.AXE,Job.TREE_FARM)) {
            BlockPos normalOuter=root.east(8);level.setBlock(normalOuter,Blocks.OAK_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT,true),2);
            s.assign(job,base,Direction.EAST);testTree(level,root,Blocks.OAK_LOG,Blocks.OAK_LEAVES,1);step(level,g,s);
            check(level.getBlockState(root).isAir()&&level.getBlockState(stripped).is(Blocks.STRIPPED_OAK_LOG)&&level.getBlockState(wood).is(Blocks.STRIPPED_OAK_WOOD),job+" skips stripped structure blocks while harvesting adjacent trees");
            check(level.getBlockState(normalOuter).is(Blocks.OAK_LEAVES),job+" retains the ordinary three-block canopy range");
        }
        g.discard();helper.succeed();
    }
    private static void checkQuiet(boolean pass,String description) { if(!pass)throw new IllegalStateException(description); }

    private static ItemStack enchanted(ServerLevel level,Item item,net.minecraft.resources.ResourceKey<net.minecraft.world.item.enchantment.Enchantment> key,int amount) {
        ItemStack stack=new ItemStack(item);
        stack.enchant(level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getOrThrow(key),amount);
        return stack;
    }

    private static net.minecraft.world.entity.monster.zombie.Zombie combatTarget(ServerLevel level,BlockPos pos) {
        var zombie=EntityTypes.ZOMBIE.create(level,net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        zombie.setPos(Vec3.atBottomCenterOf(pos));zombie.setNoAi(true);
        zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR).setBaseValue(0);
        level.addFreshEntity(zombie);return zombie;
    }

    @GameTest(maxTicks = 100, skyAccess = true)
    public void enchantedCombatAndFishing(GameTestHelper helper) {
        ServerLevel level=helper.getLevel();BlockPos anchor=helper.absolutePos(new BlockPos(1500,5,1500));
        // Keep the combat and fishing targets inside the assigned chunk for every test position.
        BlockPos base=new BlockPos(((anchor.getX()>>4)<<4)+6,anchor.getY(),((anchor.getZ()>>4)<<4)+6);
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)level.getChunk((base.getX()>>4)+dx,(base.getZ()>>4)+dz);
        for(BlockPos p:BlockPos.betweenClosed(base.offset(-5,-3,-5),base.offset(12,5,8)))
            level.setBlock(p,p.getY()<base.getY()?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
        CopperGolem g=EntityTypes.COPPER_GOLEM.create(level,net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        g.setPos(Vec3.atBottomCenterOf(base));g.setNoAi(true);level.addFreshEntity(g);
        WorkerState s=((WorkerAccess)g).copperJobs$state();
        BlockPos chestPos=base.offset(-2,0,-2);level.setBlock(chestPos,BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace("copper_chest")).defaultBlockState(),3);
        Container chest=(Container)level.getBlockEntity(chestPos);
        var player=helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        ItemStack sword=enchanted(level,Items.DIAMOND_SWORD,net.minecraft.world.item.enchantment.Enchantments.SHARPNESS,5);sword.setDamageValue(7);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,sword);
        net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.invoker().interact(player,level,net.minecraft.world.InteractionHand.MAIN_HAND,g,null);
        check(s.job==Job.SWORD&&g.getMainHandItem().isEnchanted()&&sword.getCount()==1&&sword.getDamageValue()==7,"combat assignment copies the actual enchanted sword without consuming or wearing the player's item");
        var target=combatTarget(level,base.east());float health=target.getHealth();step(level,g,s);float sharpDamage=health-target.getHealth();target.discard();
        g.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.DIAMOND_SWORD));target=combatTarget(level,base.east());health=target.getHealth();step(level,g,s);float plainDamage=health-target.getHealth();target.discard();
        check(Math.abs(plainDamage-6)<.01&&Math.abs(sharpDamage-9)<.01,"Sharpness V adds vanilla bonus damage to the worker's six-damage base hit");
        g.setItemSlot(EquipmentSlot.MAINHAND,enchanted(level,Items.DIAMOND_SWORD,net.minecraft.world.item.enchantment.Enchantments.SMITE,5));
        target=combatTarget(level,base.east());health=target.getHealth();step(level,g,s);
        check(Math.abs(health-target.getHealth()-18.5)<.01,"Smite V applies its undead-specific damage bonus");target.discard();
        ItemStack effects=enchanted(level,Items.DIAMOND_SWORD,net.minecraft.world.item.enchantment.Enchantments.FIRE_ASPECT,2);
        effects.enchant(level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getOrThrow(net.minecraft.world.item.enchantment.Enchantments.KNOCKBACK),2);
        g.setItemSlot(EquipmentSlot.MAINHAND,effects);target=combatTarget(level,base.east());step(level,g,s);
        check(target.isOnFire(),"Fire Aspect II ignites the attacked mob through vanilla post-attack effects");
        check(target.getDeltaMovement().horizontalDistanceSqr()>0.05,"Knockback II pushes the attacked mob");target.discard();
        for(int looting:List.of(0,3)) {
            chest.clearContent();g.setItemSlot(EquipmentSlot.MAINHAND,looting==0?new ItemStack(Items.DIAMOND_SWORD):enchanted(level,Items.DIAMOND_SWORD,net.minecraft.world.item.enchantment.Enchantments.LOOTING,looting));
            target=combatTarget(level,base.east());var out=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,level.registryAccess());target.saveWithoutId(out);
            var tag=out.buildResult();tag.putString("DeathLootTable","coppergolemjobs-test:looting_probe");target.load(TagValueInput.create(ProblemReporter.DISCARDING,level.registryAccess(),tag));target.setHealth(1);step(level,g,s);
            check(!target.isAlive()&&count(chest,Items.DIAMOND)==1+looting,"Looting "+looting+" reaches the real entity death loot table and stores the exact bonus drops");target.discard();
        }
        ItemStack rod=enchanted(level,Items.FISHING_ROD,net.minecraft.world.item.enchantment.Enchantments.LURE,3);
        rod.enchant(level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getOrThrow(net.minecraft.world.item.enchantment.Enchantments.LUCK_OF_THE_SEA),3);rod.setDamageValue(9);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,rod);
        net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.invoker().interact(player,level,net.minecraft.world.InteractionHand.MAIN_HAND,g,null);
        var saved=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,level.registryAccess());g.saveWithoutId(saved);g.load(TagValueInput.create(ProblemReporter.DISCARDING,level.registryAccess(),saved.buildResult()));
        check(s.job==Job.FISHING_ROD&&g.getMainHandItem().isEnchanted()&&g.getMainHandItem().getDamageValue()==9&&rod.getDamageValue()==9,"enchanted rod assignment and durability survive actual golem save/reload");
        BlockPos water=base.east(4);
        for(BlockPos p:BlockPos.betweenClosed(water.offset(-2,-1,-2),water.offset(2,2,2)))
            level.setBlock(p,p.getY()<=water.getY()?Blocks.WATER.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
        var lucky=AnimalJobs.fishingParams(level,g,water);
        check(lucky.getLuck()==3,"Luck of the Sea III supplies three luck points to the actual fishing loot context");
        g.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.FISHING_ROD));var normal=AnimalJobs.fishingParams(level,g,water);
        var loot=level.getServer().reloadableRegistries().getLootTable(net.minecraft.world.level.storage.loot.BuiltInLootTables.FISHING);
        int normalTreasure=0,luckyTreasure=0;
        for(int seed=1;seed<=1024;seed++) {
            for(ItemStack item:loot.getRandomItems(normal,seed*0x9e3779b97f4a7c15L))if(treasure(item))normalTreasure++;
            for(ItemStack item:loot.getRandomItems(lucky,seed*0x9e3779b97f4a7c15L))if(treasure(item))luckyTreasure++;
        }
        check(normalTreasure>0&&luckyTreasure>normalTreasure,"Luck of the Sea III increases vanilla open-water treasure results across 1024 paired seeded catches");
        BlockPos small=base.south(4);level.setBlock(small,Blocks.WATER.defaultBlockState(),2);
        g.setItemSlot(EquipmentSlot.MAINHAND,rod.copy());var closed=AnimalJobs.fishingParams(level,g,small);int closedTreasure=0;
        for(int seed=1;seed<=128;seed++)for(ItemStack item:loot.getRandomItems(closed,seed*0x9e3779b97f4a7c15L))if(treasure(item))closedTreasure++;
        check(closedTreasure==0,"Luck of the Sea never bypasses vanilla open-water treasure requirements");
        g.setPos(Vec3.atBottomCenterOf(small.north()));chest.clearContent();s.clearTarget();s.target=small;
        g.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.FISHING_ROD));g.getRandom().setSeed(42);AnimalJobs.fish(level,g,s,ChestStorage.nearby(level,g));int plainDelay=s.cooldown;
        for(int lure:List.of(1,3)) {
            g.setItemSlot(EquipmentSlot.MAINHAND,enchanted(level,Items.FISHING_ROD,net.minecraft.world.item.enchantment.Enchantments.LURE,lure));g.getRandom().setSeed(42);s.target=small;
            AnimalJobs.fish(level,g,s,ChestStorage.nearby(level,g));
            check(s.cooldown==Math.max(20,plainDelay-lure*100),"Lure "+lure+" shortens a real catch cooldown by five seconds per level with a one-second floor");
        }
        for(int n=0;n<chest.getContainerSize();n++)chest.setItem(n,new ItemStack(Items.DIRT,64));chest.setItem(0,new ItemStack(Items.DIRT,63));s.target=small;
        AnimalJobs.fish(level,g,s,ChestStorage.nearby(level,g));int pending=s.pending.stream().mapToInt(ItemStack::getCount).sum(),delay=s.cooldown;
        check(pending>0&&delay>=20&&delay<=300,"blocked enchanted fishing preserves the rolled catch and applies the Lure cooldown");
        g.tickCount=10;WorkerBrain.tick(level,g,s);
        check(s.pending.stream().mapToInt(ItemStack::getCount).sum()==pending,"blocked storage never rerolls an enchanted catch");
        chest.clearContent();s.cooldown=50;WorkerBrain.tick(level,g,s);int stored=0;for(int n=0;n<chest.getContainerSize();n++)stored+=chest.getItem(n).getCount();
        check(s.pending.isEmpty()&&stored==pending,"enchanted fishing resumes by depositing the saved catch exactly once");
        g.discard();helper.succeed();
    }

    private static boolean treasure(ItemStack item) {
        return item.is(Items.ENCHANTED_BOOK)||item.is(Items.NAME_TAG)||item.is(Items.SADDLE)||item.is(Items.NAUTILUS_SHELL)
            ||((item.is(Items.BOW)||item.is(Items.FISHING_ROD))&&item.isEnchanted());
    }

}
