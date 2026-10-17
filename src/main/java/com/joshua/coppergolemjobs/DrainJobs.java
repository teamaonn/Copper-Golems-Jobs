package com.joshua.coppergolemjobs;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/** Exterior-only, top-down construction. Never traverses or edits a monument room. */
public final class DrainJobs {
    private static boolean plant(BlockState s) {
        return s.is(Blocks.KELP) || s.is(Blocks.KELP_PLANT) || s.is(Blocks.SEAGRASS) || s.is(Blocks.TALL_SEAGRASS);
    }
    private static boolean open(BlockState s) {
        return s.isAir() || s.is(Blocks.WATER) || s.is(Blocks.BUBBLE_COLUMN) || plant(s);
    }
    public static void stop(CopperGolem g, WorkerState s) {
        s.drain.travel = null;
        s.drain.route.clear();
        g.setDeltaMovement(Vec3.ZERO);
        g.setNoGravity(false);
    }

    /** Controlled swimming/climbing with normal collision, rather than teleporting or entering rooms. */
    public static void motion(ServerLevel level, CopperGolem g, WorkerState s) {
        DrainState d = s.drain;
        if (d.travel == null) return;
        Vec3 end = Vec3.atBottomCenterOf(d.travel);
        double horizontal = (g.getX()-end.x)*(g.getX()-end.x)+(g.getZ()-end.z)*(g.getZ()-end.z);
        Vec3 waypoint;
        if (d.routePhase == 0) {
            if (g.getY() >= d.surfaceY + 1.08 || horizontal < .1) d.routePhase = 1;
            waypoint = new Vec3(g.getX(), d.surfaceY + 1.1, g.getZ());
        } else if (d.routePhase == 1) {
            if (horizontal < .015) { d.routePhase = 2; waypoint = new Vec3(end.x,d.surfaceY+1.1,end.z); }
            else {
                if (d.route.isEmpty() && !surfaceRoute(level,g,d)) { s.status="Surface route obstructed; waiting for a clear path"; return; }
                waypoint=Vec3.atBottomCenterOf(d.route.getFirst()).add(0,.1,0);
                if(g.position().distanceToSqr(waypoint)<.015) { d.route.removeFirst(); return; }
            }
        } else waypoint = end;
        if (d.routePhase == 2 && d.travel.getY() <= d.surfaceY) {
            BlockPos lid = new BlockPos(d.travel.getX(), d.surfaceY, d.travel.getZ());
            if (d.scaffolds.remove(lid) && level.getBlockState(lid).is(Blocks.GLASS)) {
                boolean underwater = d.stage == DrainState.Stage.CLEAR || d.stage == DrainState.Stage.WALLS;
                level.setBlock(lid, (underwater ? Blocks.WATER : Blocks.AIR).defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        if (d.routePhase < 2 && g.getY() > d.surfaceY + 1.2) waypoint = new Vec3(waypoint.x,g.getY(),waypoint.z);
        if (d.routePhase == 2 && g.position().distanceToSqr(end) < .04) {
            g.setDeltaMovement(Vec3.ZERO); g.setNoGravity(true); return;
        }
        if (!WorkerBrain.loaded(level, BlockPos.containing(waypoint))) { stop(g,s); s.status="Waiting for drain route chunks"; return; }
        Vec3 delta = waypoint.subtract(g.position());
        if (delta.lengthSqr() > .0484) delta = delta.normalize().scale(.22);
        if (d.routePhase == 1 && Math.abs(g.getY()-d.surfaceY-1)<.3) {
            BlockPos next=BlockPos.containing(g.position().add(delta));
            BlockPos footing=new BlockPos(next.getX(),d.surfaceY,next.getZ());
            if (!WorkerBrain.loaded(level,footing)) { stop(g,s); return; }
            BlockState b=level.getBlockState(footing);
            if (b.isAir() || b.is(Blocks.WATER) || b.is(Blocks.BUBBLE_COLUMN)) {
                level.setBlock(footing,Blocks.GLASS.defaultBlockState(),Block.UPDATE_ALL);
                d.scaffolds.add(footing.immutable());
            }
        }
        g.getNavigation().stop();
        g.setNoGravity(true); g.setDeltaMovement(Vec3.ZERO);
        g.move(MoverType.SELF, delta);
        if (++d.stuck >= 80) {
            if (d.checkpoint != null && g.position().distanceToSqr(d.checkpoint) < .04) {
                d.routePhase=0; d.route.clear(); s.status="Drain route obstructed; retrying from the surface";
            }
            d.checkpoint=g.position(); d.stuck=0;
        }
    }

    private static boolean arrive(ServerLevel level, CopperGolem g, WorkerState s, BlockPos stand) {
        DrainState d=s.drain;
        if (!WorkerBrain.loaded(level,stand) || !WorkerBrain.loaded(level,stand.above())) {
            WorkerBrain.wait(g,s,"Waiting for drain route chunks"); return false;
        }
        if (!stand.equals(d.travel)) {
            d.travel=stand.immutable(); d.stuck=0; d.checkpoint=g.position(); d.route.clear();
            double dx=g.getX()-stand.getX()-.5,dz=g.getZ()-stand.getZ()-.5;
            d.routePhase = dx*dx+dz*dz < .1 ? 2 : 0;
        }
        // Surface crossings get a temporary glass footing, removed after the return trip.
        if (stand.getY() == d.surfaceY+1) {
            BlockPos floor=stand.below(); BlockState block=level.getBlockState(floor);
            if (block.isAir() || block.is(Blocks.WATER) || block.is(Blocks.BUBBLE_COLUMN)) {
                if (!level.setBlock(floor,Blocks.GLASS.defaultBlockState(),Block.UPDATE_ALL)) return false;
                d.scaffolds.add(floor.immutable());
            }
        }
        return g.position().distanceToSqr(Vec3.atBottomCenterOf(stand)) < .16;
    }

    private static boolean surfaceRoute(ServerLevel level,CopperGolem g,DrainState d) {
        BlockPos start=new BlockPos(g.blockPosition().getX(),d.surfaceY+1,g.blockPosition().getZ());
        BlockPos end=new BlockPos(d.travel.getX(),d.surfaceY+1,d.travel.getZ());
        var queue=new java.util.ArrayDeque<BlockPos>();
        var previous=new java.util.HashMap<BlockPos,BlockPos>();
        queue.add(start);previous.put(start,start);
        while(!queue.isEmpty() && previous.size()<16000) {
            BlockPos p=queue.removeFirst();
            if(p.equals(end)) {
                var reverse=new java.util.ArrayDeque<BlockPos>();
                while(!p.equals(start)){reverse.addFirst(p);p=previous.get(p);}
                if(reverse.isEmpty())reverse.add(end);
                d.route.addAll(reverse);return true;
            }
            for(Direction dir:Direction.Plane.HORIZONTAL) {
                BlockPos next=p.relative(dir);
                if(previous.containsKey(next)||next.getX()<d.minX-8||next.getX()>d.maxX+8
                        ||next.getZ()<d.minZ-8||next.getZ()>d.maxZ+8||!WorkerBrain.loaded(level,next))continue;
                Vec3 destination=Vec3.atBottomCenterOf(next).add(0,.1,0);
                if(!level.noCollision(g,g.getBoundingBox().move(destination.subtract(g.position()))))continue;
                previous.put(next,p);queue.addLast(next);
            }
        }
        return false;
    }

    public static void work(ServerLevel level, CopperGolem g, WorkerState s, ChestStorage storage) {
        DrainState d=s.drain;
        if (d.stage == DrainState.Stage.NEW) { discover(level,g,s); return; }
        if (d.stage == DrainState.Stage.DONE) { WorkerBrain.wait(g,s,"Monument exterior drained; project complete"); return; }
        // No force-loading: hold progress until the whole footprint is loaded again.
        for (BlockPos corner : List.of(new BlockPos(d.minX,d.surfaceY,d.minZ),new BlockPos(d.maxX,d.surfaceY,d.minZ),
                new BlockPos(d.minX,d.surfaceY,d.maxZ),new BlockPos(d.maxX,d.surfaceY,d.maxZ))) {
            if(!WorkerBrain.loaded(level,corner)){WorkerBrain.wait(g,s,"Waiting for monument chunks or world boundary");return;}
        }
        for (int x=d.minX>>4; x<=d.maxX>>4; x++) for (int z=d.minZ>>4;z<=d.maxZ>>4;z++) {
            if (!WorkerBrain.loaded(level,new BlockPos(Math.max(d.minX,x<<4),d.surfaceY,Math.max(d.minZ,z<<4)))) {
                WorkerBrain.wait(g,s,"Waiting for monument chunks to load");return;
            }
        }
        switch(d.stage) {
            case SURVEY -> survey(level,g,s);
            case CLEAR -> clear(level,g,s,storage);
            case SURFACE -> {
                s.status="Returning to starting platform before sand filling";
                if (arrive(level,g,s,s.origin)) d.next(DrainState.Stage.FILL);
            }
            case FILL -> fill(level,g,s);
            case WALLS -> walls(level,g,s,false);
            case VERIFY -> walls(level,g,s,true);
            case REMOVE -> remove(level,g,s);
            case RETURN -> {
                s.status="Returning to starting platform";
                if (arrive(level,g,s,s.origin)) d.next(DrainState.Stage.CLEANUP);
            }
            case CLEANUP -> cleanup(level,g,s);
            default -> {}
        }
    }

    private static void discover(ServerLevel level, CopperGolem g, WorkerState s) {
        int surface=s.origin.getY()-1;
        if (level.getBlockState(s.origin.below()).getCollisionShape(level,s.origin.below()).isEmpty()) {
            WorkerBrain.wait(g,s,"Place the drain golem on a solid platform at water level");return;
        }
        boolean water=false;
        for(Direction dir:Direction.Plane.HORIZONTAL) {
            BlockPos p=s.origin.below().relative(dir);
            if (WorkerBrain.loaded(level,p) && level.getFluidState(p).is(FluidTags.WATER)
                    && level.getFluidState(p.above()).isEmpty()) water=true;
        }
        if (!water) { WorkerBrain.wait(g,s,"Starting platform must be beside the ocean surface");return; }
        var monument=level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(BuiltinStructures.OCEAN_MONUMENT).value();
        var starts=level.structureManager().startsForStructure(new ChunkPos(s.origin.getX() >> 4, s.origin.getZ() >> 4), st -> st==monument);
        for(var start:starts) {
            if (!start.isValid()) continue;
            var box=start.getBoundingBox();
            if(s.origin.getX()<box.minX() || s.origin.getX()>box.maxX() || s.origin.getZ()<box.minZ() || s.origin.getZ()>box.maxZ())continue;
            if(box.getXSpan()>96 || box.getZSpan()>96 || surface<box.maxY() || surface+2>=level.getMaxY())continue;
            s.drain.begin(box,surface);
            s.status="Surveying the monument exterior";return;
        }
        WorkerBrain.wait(g,s,"No generated Ocean Monument beneath the starting platform");
    }

    private static void survey(ServerLevel level, CopperGolem g, WorkerState s) {
        DrainState d=s.drain;s.status="Surveying monument exterior: "+d.index+"/"+d.columns();
        for(int budget=0;budget<32 && d.index<d.columns();budget++,d.index++) {
            int floor=d.surfaceY;
            while(floor>=Math.max(level.getMinY(),d.surfaceY-96)) {
                BlockPos p=d.column(d.index,floor);
                if(!WorkerBrain.loaded(level,p)){WorkerBrain.wait(g,s,"Waiting for survey chunks");return;}
                if(!open(level.getBlockState(p)))break;
                floor--;
            }
            if(floor<Math.max(level.getMinY(),d.surfaceY-96)) { WorkerBrain.wait(g,s,"No seabed within 96 blocks; drain survey paused");return; }
            d.floors[d.index]=floor;d.bottomY=Math.min(d.bottomY,floor);
        }
        if(d.index==d.columns())d.next(DrainState.Stage.CLEAR);
    }

    private static void clear(ServerLevel level,CopperGolem g,WorkerState s,ChestStorage storage) {
        DrainState d=s.drain;s.status="Clearing exterior kelp and seagrass: "+d.index+"/"+d.columns();
        for(int budget=0;budget<128 && d.index<d.columns();budget++) {
            if(d.y<=d.floors[d.index]){d.index++;d.y=d.surfaceY;continue;}
            BlockPos p=d.column(d.index,d.y);BlockState b=level.getBlockState(p);
            if(!plant(b)){d.y--;continue;}
            if(!arrive(level,g,s,p))return;
            boolean upper=b.is(Blocks.TALL_SEAGRASS)&&b.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF)==DoubleBlockHalf.UPPER;
            var drops=upper?List.<ItemStack>of():List.of(new ItemStack(b.is(Blocks.KELP)||b.is(Blocks.KELP_PLANT)?Items.KELP:Items.SEAGRASS));
            var plan=storage.plan();
            if(!plan.add(drops)){WorkerBrain.wait(g,s,"Chest needs room for kelp or seagrass");return;}
            if(!plan.unchanged())return;
            // Top-down clearing prevents uprooting a whole kelp plant into loose item entities.
            if(!level.setBlock(p,Blocks.WATER.defaultBlockState(),Block.UPDATE_CLIENTS))return;
            if(!plan.commit())s.pending.addAll(drops);
            g.swing(InteractionHand.MAIN_HAND);d.y--;return;
        }
        if(d.index==d.columns())d.next(DrainState.Stage.SURFACE);
    }

    private static void fill(ServerLevel level,CopperGolem g,WorkerState s) {
        DrainState d=s.drain;s.status="Stacking temporary sand: "+d.index+"/"+d.columns();
        for(int skip=0;skip<32 && d.index<d.columns();skip++) {
            if(d.wall(d.column(d.index,d.surfaceY)) || d.floors[d.index]>=d.surfaceY){d.index++;continue;}
            BlockPos top=d.column(d.index,d.surfaceY);
            if(!arrive(level,g,s,top.above()))return;
            int next=d.sandTops[d.index]==Integer.MIN_VALUE?d.floors[d.index]+1:d.sandTops[d.index]+1;
            for(int n=0;n<8 && next<=d.surfaceY;n++,next++) {
                BlockPos p=d.column(d.index,next);BlockState b=level.getBlockState(p);
                boolean scaffold=d.scaffolds.contains(p)&&b.is(Blocks.GLASS);
                if(!(b.isAir()||b.is(Blocks.WATER)||b.is(Blocks.BUBBLE_COLUMN)||scaffold)) {
                    WorkerBrain.wait(g,s,"New obstruction in sand column; leaving existing blocks untouched");return;
                }
                if(net.minecraft.world.level.block.FallingBlock.isFree(level.getBlockState(p.below()))) {
                    WorkerBrain.wait(g,s,"Sand column needs a stable base; existing blocks left untouched");return;
                }
                if(!level.setBlock(p,Blocks.SAND.defaultBlockState(),Block.UPDATE_ALL))return;
                d.scaffolds.remove(p);d.sandTops[d.index]=next;
            }
            g.swing(InteractionHand.MAIN_HAND);
            if(next>d.surfaceY)d.index++;
            return;
        }
        if(d.index==d.columns()){d.next(DrainState.Stage.WALLS);d.y=d.bottomY;}
    }

    private static boolean sealed(ServerLevel level,BlockPos p) {
        BlockState b=level.getBlockState(p);
        return b.is(Blocks.GLASS) || (!b.hasBlockEntity() && b.isCollisionShapeFullBlock(level,p) && b.getFluidState().isEmpty());
    }
    private static BlockPos outside(DrainState d,BlockPos p) {
        if(p.getX()==d.minX)return p.west();
        if(p.getX()==d.maxX)return p.east();
        return p.getZ()==d.minZ?p.north():p.south();
    }

    /** Wall bottoms share the lowest surveyed floor, closing terrain dips at the perimeter. */
    private static void walls(ServerLevel level,CopperGolem g,WorkerState s,boolean verify) {
        DrainState d=s.drain;s.status=verify?"Checking the glass barrier":"Building exterior glass walls";
        for(int budget=0;budget<128 && d.index<d.columns();budget++) {
            BlockPos p=d.column(d.index,d.y);
            if(!d.wall(p)){d.index++;d.y=d.bottomY;continue;}
            if(d.y>d.surfaceY){d.index++;d.y=d.bottomY;continue;}
            if(sealed(level,p)){d.scaffolds.remove(p);d.y++;continue;}
            if(verify){d.stage=DrainState.Stage.WALLS;return;}
            BlockState b=level.getBlockState(p);
            if(!open(b)||plant(b)){WorkerBrain.wait(g,s,"Wall site obstructed; existing blocks left untouched");return;}
            BlockPos stand=null;
            BlockPos first=outside(d,p);
            int dx=first.getX()-p.getX(),dz=first.getZ()-p.getZ();
            for(int distance=1;distance<=4;distance++) {
                BlockPos candidate=p.offset(dx*distance,0,dz*distance);
                while(candidate.getY()<=d.surfaceY && !open(level.getBlockState(candidate)) && !d.scaffolds.contains(candidate))candidate=candidate.above();
                boolean clear=true;
                for(int h=candidate.getY();h<=d.surfaceY+2;h++) {
                    BlockPos q=new BlockPos(candidate.getX(),h,candidate.getZ());
                    if(!WorkerBrain.loaded(level,q)||(!d.scaffolds.contains(q)&&!level.getBlockState(q).getCollisionShape(level,q).isEmpty())){clear=false;break;}
                }
                if(clear){stand=candidate;break;}
            }
            if(stand==null){WorkerBrain.wait(g,s,"Clear access outside the glass wall to continue");return;}
            if(!arrive(level,g,s,stand))return;
            if(!level.setBlock(p,Blocks.GLASS.defaultBlockState(),Block.UPDATE_ALL))return;
            d.scaffolds.remove(p);d.y++;g.swing(InteractionHand.MAIN_HAND);return;
        }
        if(d.index==d.columns()) {
            if(verify)d.next(DrainState.Stage.REMOVE);
            else{d.next(DrainState.Stage.VERIFY);d.y=d.bottomY;}
        }
    }

    private static void remove(ServerLevel level,CopperGolem g,WorkerState s) {
        DrainState d=s.drain;s.status="Removing temporary sand: "+d.index+"/"+d.columns();
        for(int budget=0;budget<128 && d.index<d.columns();budget++) {
            int top=d.sandTops[d.index];
            if(top==Integer.MIN_VALUE || top<=d.floors[d.index]){d.index++;continue;}
            BlockPos p=d.column(d.index,top);
            // Clear from above; the worker descends only through its own now-cleared sand column.
            if(!arrive(level,g,s,p.above()))return;
            if(level.getBlockState(p).is(Blocks.SAND)) {
                if(!level.setBlock(p,Blocks.AIR.defaultBlockState(),Block.UPDATE_ALL))return;
                g.swing(InteractionHand.MAIN_HAND);
            }
            // An unrelated replacement block is never removed, even inside the sand ledger.
            d.sandTops[d.index]--;return;
        }
        if(d.index==d.columns())d.next(DrainState.Stage.RETURN);
    }

    private static void cleanup(ServerLevel level,CopperGolem g,WorkerState s) {
        DrainState d=s.drain;s.status="Removing temporary surface footholds";
        var it=d.scaffolds.iterator();
        for(int n=0;n<32&&it.hasNext();n++) {
            BlockPos p=it.next();
            if(!WorkerBrain.loaded(level,p)){WorkerBrain.wait(g,s,"Waiting for scaffold cleanup chunks");return;}
            if(level.getBlockState(p).is(Blocks.GLASS))level.setBlock(p,Blocks.AIR.defaultBlockState(),Block.UPDATE_ALL);
            it.remove();
        }
        if(d.scaffolds.isEmpty()){d.next(DrainState.Stage.DONE);stop(g,s);s.status="Monument exterior drained; project complete";}
    }
}
