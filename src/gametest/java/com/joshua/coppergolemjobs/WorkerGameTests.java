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

        for(BlockPos p:BlockPos.betweenClosed(base.offset(-5,-4,-5),base.offset(5,3,5)))
            level.setBlock(p,p.getY()==base.getY()-4?Blocks.STONE.defaultBlockState():
                p.getY()<=base.getY()?Blocks.WATER.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
        // Contain the artificial ocean so its flowing water cannot spill into adjacent tests.
        for(BlockPos p:BlockPos.betweenClosed(base.offset(-5,-4,-5),base.offset(5,0,5)))
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
        helper.onEachTick(()->{
            if(helper.getTick()%1000==0)System.out.println("LIVE DRAIN "+helper.getTick()+" "+s.drain.stage+" "+s.drain.index+" "+s.drain.y+" "+s.status+" "+g.position()+" target="+s.drain.travel+" phase="+s.drain.routePhase+" route="+s.drain.route+" lid="+(s.drain.travel==null?"none":level.getBlockState(new BlockPos(s.drain.travel.getX(),s.drain.surfaceY,s.drain.travel.getZ()))) );
            checkQuiet(level.getBlockState(base.below(2)).is(Blocks.PRISMARINE),"live drain must preserve roof");
            if(s.drain.stage==DrainState.Stage.DONE) {
                check(g.position().distanceToSqr(Vec3.atBottomCenterOf(base.above()))<.5,"live drain walks, swims, finishes and returns home without test teleports");
                check(level.getBlockState(base.below(3)).is(Blocks.WATER),"live drain keeps sealed interior water intact through fluid ticks");
                check(count((Container)level.getBlockEntity(chestPos),Items.SAND)==0,"live drain creates no sand payout");
                g.discard();helper.succeed();
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
    private static void checkQuiet(boolean pass,String description) { if(!pass)throw new IllegalStateException(description); }

}
