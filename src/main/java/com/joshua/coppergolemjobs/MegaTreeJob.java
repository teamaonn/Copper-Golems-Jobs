package com.joshua.coppergolemjobs;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** No fixed tree-size limit. Discovery, canopy inspection and harvesting have per-tick budgets. */
public final class MegaTreeJob {
    public enum Phase { IDLE, LOGS, LEAVES, HARVEST }
    public static final class State {
        public Phase phase = Phase.IDLE;
        public final List<BlockPos> logs = new ArrayList<>();
        public final ArrayDeque<BlockPos> frontier = new ArrayDeque<>();
        public final LinkedHashSet<BlockPos> leaves = new LinkedHashSet<>();
        private final Set<BlockPos> seen = new HashSet<>(), queued = new HashSet<>(), checkedLeaves = new HashSet<>();
        public int index, offset;
        public void reset() {
            phase=Phase.IDLE;logs.clear();frontier.clear();leaves.clear();seen.clear();queued.clear();checkedLeaves.clear();index=0;offset=0;
        }
        private void enqueue(BlockPos p) {
            if(!seen.contains(p)&&queued.add(p))frontier.addLast(p.immutable());
        }
        public void save(ValueOutput out) {
            out.putString("MegaTreePhase",phase.name());
            if(phase==Phase.IDLE)return;
            out.store("MegaTreeLogs",BlockPos.CODEC.listOf(),logs);
            out.store("MegaTreeFrontier",BlockPos.CODEC.listOf(),new ArrayList<>(frontier));
            out.store("MegaTreeLeaves",BlockPos.CODEC.listOf(),new ArrayList<>(leaves));
            out.putInt("MegaTreeIndex",index);out.putInt("MegaTreeOffset",offset);
        }
        public void load(ValueInput in) {
            reset();
            try{phase=Phase.valueOf(in.getStringOr("MegaTreePhase","IDLE"));}catch(IllegalArgumentException ignored){return;}
            if(phase==Phase.IDLE)return;
            logs.addAll(in.read("MegaTreeLogs",BlockPos.CODEC.listOf()).orElse(List.of()));seen.addAll(logs);
            frontier.addAll(in.read("MegaTreeFrontier",BlockPos.CODEC.listOf()).orElse(List.of()));queued.addAll(frontier);
            leaves.addAll(in.read("MegaTreeLeaves",BlockPos.CODEC.listOf()).orElse(List.of()));
            index=Math.max(0,Math.min(logs.size(),in.getIntOr("MegaTreeIndex",0)));
            offset=Math.max(0,Math.min(342,in.getIntOr("MegaTreeOffset",0)));
        }
    }

    private static boolean validPosition(ServerLevel level,BlockPos p) {
        return level.isInWorldBounds(p)&&level.getWorldBorder().isWithinBounds(p);
    }

    public static void work(ServerLevel level,CopperGolem g,WorkerState worker,ChestStorage storage) {
        State s=worker.megaTree;
        if(s.phase==Phase.IDLE) {
            BlockPos root=WorkerBrain.findBlock(level,g,worker,p->TreeCutRules.cuttable(level.getBlockState(p)));
            if(root==null){WorkerBrain.wait(g,worker,"No reachable unstripped tree");return;}
            if(!WorkerBrain.approach(level,g,worker,root,false))return;
            s.reset();s.phase=Phase.LOGS;s.enqueue(root);
        }
        switch(s.phase) {
            case LOGS -> discover(level,g,worker,s);
            case LEAVES -> canopy(level,g,worker,s);
            case HARVEST -> harvest(level,g,worker,storage,s);
            default -> {}
        }
    }

    private static void discover(ServerLevel level,CopperGolem g,WorkerState w,State s) {
        for(int n=0;n<128&&!s.frontier.isEmpty();n++) {
            BlockPos p=s.frontier.getFirst();
            if(!WorkerBrain.loaded(level,p)){WorkerBrain.wait(g,w,"Large tree: waiting for connected chunks to load");return;}
            s.frontier.removeFirst();s.queued.remove(p);
            if(s.seen.contains(p)||!TreeCutRules.cuttable(level.getBlockState(p)))continue;
            s.seen.add(p);s.logs.add(p);
            for(BlockPos q:BlockPos.betweenClosed(p.offset(-1,-1,-1),p.offset(1,1,1))) {
                if(!validPosition(level,q)||s.seen.contains(q)||s.queued.contains(q))continue;
                if(!WorkerBrain.loaded(level,q)||TreeCutRules.cuttable(level.getBlockState(q)))s.enqueue(q);
            }
        }
        w.status="Surveying large tree: "+s.logs.size()+" logs";
        if(s.frontier.isEmpty()){s.phase=Phase.LEAVES;s.index=0;s.offset=0;}
    }

    private static void canopy(ServerLevel level,CopperGolem g,WorkerState w,State s) {
        for(int budget=0;budget<4096&&s.index<s.logs.size();budget++) {
            BlockPos log=s.logs.get(s.index);
            BlockPos p=log.offset(s.offset/49-3,(s.offset/7)%7-3,s.offset%7-3);
            if(validPosition(level,p)&&!s.seen.contains(p)&&!s.checkedLeaves.contains(p)) {
                if(!WorkerBrain.loaded(level,p)){WorkerBrain.wait(g,w,"Large tree: waiting for canopy chunks to load");return;}
                s.checkedLeaves.add(p);
                // Mod-generated mega trees may use persistent leaves. Stripped logs still remain protected.
                if(level.getBlockState(p).is(BlockTags.LEAVES))s.leaves.add(p);
            }
            if(++s.offset==343){s.offset=0;s.index++;}
        }
        w.status="Inspecting large-tree canopy: "+s.index+"/"+s.logs.size();
        if(s.index==s.logs.size()) {
            if(s.leaves.isEmpty()) {
                for(BlockPos p:s.logs)w.skipped.put(p,level.getGameTime()+1200);
                s.reset();w.clearTarget();WorkerBrain.wait(g,w,"No leaves found; unstripped log cluster left intact");return;
            }
            s.phase=Phase.HARVEST;s.index=0;s.checkedLeaves.clear();
        }
    }

    private static void harvest(ServerLevel level,CopperGolem g,WorkerState w,ChestStorage storage,State s) {
        for(int n=0;n<16;n++) {
            boolean leaf=!s.leaves.isEmpty();
            if(!leaf&&s.index>=s.logs.size()) {
                s.reset();WorkerBrain.worked(g,w,20);w.status="Large tree harvested";return;
            }
            BlockPos p=leaf?s.leaves.getFirst():s.logs.get(s.index);
            if(!WorkerBrain.loaded(level,p)){WorkerBrain.wait(g,w,"Large tree: waiting for harvest chunks to load");return;}
            boolean eligible=leaf?level.getBlockState(p).is(BlockTags.LEAVES):TreeCutRules.cuttable(level.getBlockState(p));
            if(eligible&&!BlockJobs.breakIntoChest(level,g,w,storage,List.of(p),new ItemStack(Items.NETHERITE_AXE),false))return;
            if(leaf)s.leaves.removeFirst();else s.index++;
        }
        g.swing(InteractionHand.MAIN_HAND,SwingAnimation.DEFAULT);
        w.status="Harvesting large tree: "+s.index+"/"+s.logs.size()+" logs";
    }
}
