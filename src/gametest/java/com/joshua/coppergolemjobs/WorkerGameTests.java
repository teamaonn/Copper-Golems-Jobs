package com.joshua.coppergolemjobs;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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

    @GameTest(maxTicks = 300, skyAccess = true)
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
        step(level,g,s);step(level,g,s);step(level,g,s);step(level,g,s);
        check(level.getBlockState(ore).isAir()&&level.getBlockState(second).isAir(),"pickaxe clears the assigned chunk layer instead of one staircase block");
        check(count(chest,Items.DIAMOND_ORE)==1 && count(chest,Items.DIAMOND)==0, "pickaxe uses Silk Touch");
        step(level,g,s);
        BlockPos quarryStep=new BlockPos(quarryX+1,base.getY()-1,quarryZ);
        g.setPos(Vec3.atBottomCenterOf(quarryStep));step(level,g,s);
        check(s.cursor.getY()==base.getY()-1&&level.getBlockState(quarryStep.below()).is(Blocks.COBBLESTONE),"quarry builds its own descending cobblestone staircase");
        ore=quarryStep.east(); level.setBlock(ore,Blocks.DIAMOND_ORE.defaultBlockState(),3);
        BlockPos fluid=ore.east(); level.setBlock(fluid,Blocks.WATER.defaultBlockState(),3);
        step(level,g,s);
        check(level.getBlockState(fluid).is(Blocks.GLASS), "miner seals exposed water with glass");
        level.setBlock(ore,Blocks.AIR.defaultBlockState(),3);step(level,g,s);
        check(level.getBlockState(fluid).isAir(),"quarry removes temporary water-sealing glass on its next pass");
        level.setBlock(ore,Blocks.STONE.defaultBlockState(),3);level.setBlock(fluid,Blocks.LAVA.defaultBlockState(),3);step(level,g,s);
        check(level.getBlockState(fluid).is(Blocks.GLASS), "miner seals exposed lava with glass");
        level.setBlock(ore,Blocks.AIR.defaultBlockState(),3);step(level,g,s);
        check(level.getBlockState(fluid).isAir(),"quarry removes temporary lava-sealing glass on its next pass");
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
}
