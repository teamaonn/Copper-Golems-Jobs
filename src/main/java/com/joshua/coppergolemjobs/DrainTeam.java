package com.joshua.coppergolemjobs;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** A shared stage barrier and exclusive column assignments for a monument crew. */
final class DrainTeam {
    private record Site(int minX, int maxX, int minZ, int maxZ, int surfaceY) {}
    private record Member(WeakReference<CopperGolem> golem, WorkerState state, long seen) {}
    private static final Map<ServerLevel, Map<Site, DrainTeam>> LEVELS = new WeakHashMap<>();
    private final Map<UUID, Member> members = new HashMap<>();
    private final Map<Integer, UUID> claims = new HashMap<>();
    private final java.util.Set<UUID> returned = new java.util.HashSet<>();
    private DrainState progress;
    private int spongeColumn, spongeY = Integer.MAX_VALUE;
    private int checkColumn, checkY = Integer.MAX_VALUE;

    static boolean joinExisting(ServerLevel level, CopperGolem golem, WorkerState worker) {
        CopperGolem sponsor = null;
        for (CopperGolem candidate : level.getEntitiesOfClass(CopperGolem.class, golem.getBoundingBox().inflate(150))) {
            if (candidate == golem || !candidate.isAlive()) continue;
            WorkerState other = ((WorkerAccess) candidate).copperJobs$state();
            DrainState project = other.drain;
            if (!project.active() || other.job != Job.DRAIN
                    || worker.origin.getY() != project.surfaceY + 1
                    || worker.origin.getX() < project.minX || worker.origin.getX() > project.maxX
                    || worker.origin.getZ() < project.minZ || worker.origin.getZ() > project.maxZ) continue;
            if (sponsor == null || project.stage.ordinal()
                    > ((WorkerAccess) sponsor).copperJobs$state().drain.stage.ordinal()) sponsor = candidate;
        }
        if (sponsor == null) return false;
        WorkerState existing = ((WorkerAccess) sponsor).copperJobs$state();
        // Recover the shared project from a loaded crew member even after a server restart.
        join(level, sponsor, existing);
        DrainState source = existing.drain;
        DrainState target = worker.drain;
        target.minX = source.minX;
        target.maxX = source.maxX;
        target.minZ = source.minZ;
        target.maxZ = source.maxZ;
        target.surfaceY = source.surfaceY;
        // The sand under a new helper disappears later. Return it to an established platform.
        worker.origin = existing.origin;
        join(level, golem, worker);
        worker.status = "Joined the existing monument drain crew";
        return true;
    }

    static DrainTeam join(ServerLevel level, CopperGolem golem, WorkerState worker) {
        DrainState d = worker.drain;
        if(d.finishVersion<2 && d.stage.ordinal()>=DrainState.Stage.REPAIR_WALLS.ordinal()) {
            d.next(DrainState.Stage.REPAIR_WALLS);
            d.finishVersion=2; d.repairPass++;
            d.cleanupFloors=new int[d.columns()]; java.util.Arrays.fill(d.cleanupFloors,Integer.MIN_VALUE);
        }
        Site site = new Site(d.minX, d.maxX, d.minZ, d.maxZ, d.surfaceY);
        Map<Site, DrainTeam> teams = LEVELS.computeIfAbsent(level, ignored -> new HashMap<>());
        teams.entrySet().removeIf(entry -> entry.getValue().empty(level.getGameTime()));
        DrainTeam team = teams.computeIfAbsent(site, ignored -> new DrainTeam());
        team.empty(level.getGameTime());
        team.members.put(golem.getUUID(), new Member(new WeakReference<>(golem), worker, level.getGameTime()));
        if (team.progress == null) team.progress = snapshot(d);
        else if (d != team.progress && newer(d,team.progress)) {
            team.progress = snapshot(d);
            team.claims.clear();
            team.returned.clear();
        } else if (d != team.progress && d.stage == team.progress.stage && d.repairPass==team.progress.repairPass
                && (!d.rowCleanup() || d.cleanupLayer==team.progress.cleanupLayer) && d.completed != team.progress.completed) {
            DrainState p = team.progress;
            p.completed.or(d.completed);
            p.scaffolds.addAll(d.scaffolds);
            p.cleanupGlass.addAll(d.cleanupGlass);
            if(p.stage==DrainState.Stage.REPAIR_RELEASE)p.cleanupGlass.removeIf(pos ->
                    pos.getY()==p.cleanupLayer && p.completed.get((pos.getZ()-p.minZ)*p.width()));
            p.bottomY = Math.min(p.bottomY, d.bottomY);
            for (int i = 0;i<d.columns();i++) {
                if (p.floors[i] == Integer.MIN_VALUE) p.floors[i] = d.floors[i];
                if (p.sandTops[i] == Integer.MIN_VALUE) p.sandTops[i] = d.sandTops[i];
                else if (d.sandTops[i] != Integer.MIN_VALUE) p.sandTops[i] = d.stage.ordinal() >= DrainState.Stage.REMOVE.ordinal()
                        ?Math.min(p.sandTops[i], d.sandTops[i]):Math.max(p.sandTops[i], d.sandTops[i]);
                if (p.heights[i] == Integer.MIN_VALUE) p.heights[i] = d.heights[i];
            }
            team.claims.keySet().removeIf(p.completed::get);
        }
        team.syncAll();
        return team;
    }

    private static boolean newer(DrainState incoming,DrainState current) {
        if(incoming.repairPass!=current.repairPass)return incoming.repairPass>current.repairPass;
        if(incoming.rowCleanup()&&current.rowCleanup()&&incoming.cleanupLayer!=current.cleanupLayer)
            return incoming.cleanupLayer<current.cleanupLayer;
        return incoming.stage.ordinal()>current.stage.ordinal();
    }

    private static DrainState snapshot(DrainState d) {
        DrainState p = new DrainState();
        p.minX = d.minX;
        p.maxX = d.maxX;
        p.minZ = d.minZ;
        p.maxZ = d.maxZ;
        p.surfaceY = d.surfaceY;
        p.structureBottom = d.structureBottom;
        p.finishVersion = d.finishVersion;
        p.repairPass = d.repairPass; p.cleanupLayer = d.cleanupLayer;
        p.cleanupFloors = d.cleanupFloors; p.cleanupGlass = d.cleanupGlass;
        p.bottomY = d.bottomY;
        p.stage = d.stage;
        p.floors = d.floors;
        p.sandTops = d.sandTops;
        p.scaffolds = d.scaffolds;
        p.completed = d.completed;
        p.heights = d.heights;
        return p;
    }

    private boolean empty(long now) {
        members.entrySet().removeIf(entry -> {
            Member member = entry.getValue();
            CopperGolem golem = member.golem().get();
            return golem == null || !golem.isAlive() || member.state().job != Job.DRAIN || now - member.seen() > 100;
        });
        claims.values().removeIf(id -> !members.containsKey(id));
        returned.retainAll(members.keySet());
        return members.isEmpty();
    }

    private void syncAll() {
        for (Member member : members.values()) {
            DrainState d = member.state().drain;
            if (d == progress) continue;
            if (d.stage != progress.stage) {
                d.travel = null;
                d.route.clear();
                d.index = 0;
            }
            d.stage = progress.stage;
            d.bottomY = progress.bottomY;
            d.structureBottom = progress.structureBottom;
            d.finishVersion = progress.finishVersion;
            d.repairPass = progress.repairPass; d.cleanupLayer = progress.cleanupLayer;
            d.cleanupFloors = progress.cleanupFloors; d.cleanupGlass = progress.cleanupGlass;
            d.floors = progress.floors;
            d.sandTops = progress.sandTops;
            d.scaffolds = progress.scaffolds;
            d.completed = progress.completed;
            d.heights = progress.heights;
        }
    }

    boolean column(CopperGolem golem, WorkerState worker) {
        DrainState d = worker.drain;
        UUID id = golem.getUUID();
        // A restart can merge partial member records into a completed stage without
        // calling complete(). Recover that barrier before assigning more work.
        if (d.completed.cardinality() == d.columns()) {
            DrainState.Stage next = switch (d.stage) {
                case SURVEY -> DrainState.Stage.CLEAR;
                case CLEAR -> DrainState.Stage.SURFACE;
                case FILL -> DrainState.Stage.WALLS;
                case WALLS -> DrainState.Stage.VERIFY;
                case VERIFY -> DrainState.Stage.REMOVE;
                case REMOVE -> DrainState.Stage.REPAIR_WALLS;
                case REPAIR_WALLS -> DrainState.Stage.REPAIR_WATER;
                case REPAIR_WATER -> DrainState.Stage.REPAIR_RELEASE;
                case REPAIR_RELEASE -> DrainState.Stage.REPAIR_WATER;
                case CHECK_DRY -> DrainState.Stage.RETURN;
                default -> null;
            };
            if (next != null) {
                advance(next);
                worker.status = "Crew resumed: " + next.name().toLowerCase(java.util.Locale.ROOT);
                return false;
            }
        }
        claims.keySet().removeIf(d.completed::get);
        int selected = -1;
        for (var claim : claims.entrySet()) {
            if (claim.getValue().equals(id)) {
                selected = claim.getKey();
                break;
            }
        }
        if (selected < 0) {
            for (int i = 0; i<d.columns(); i++) {
                if (!d.completed.get(i) && !claims.containsKey(i)) {
                    selected = i;
                    break;
                }
            }
            boolean assisting = false;
            if (selected < 0) {
                // Keep helping the same column until it is finished. Shared heights and
                // sand ledgers are read again before every action on the server thread.
                if (d.index < d.columns() && !d.completed.get(d.index) && claims.containsKey(d.index)) {
                    selected = d.index;
                } else {
                    int smallestCrew = Integer.MAX_VALUE;
                    for (int c=0;c<d.columns();c++) {
                        if (d.completed.get(c)) continue;
                        int assigned = 0;
                        for (Member member : members.values()) if (member.state().drain.index == c) assigned++;
                        if (assigned < smallestCrew) { selected = c; smallestCrew = assigned; }
                    }
                }
                assisting = true;
            }
            if (selected < 0) return false;
            if (!assisting) claims.put(selected, id);
            if (d.index != selected) { d.travel = null; d.route.clear(); }
        }
        d.index = selected;
        d.y = d.heights[selected] == Integer.MIN_VALUE
                ? (d.stage == DrainState.Stage.WALLS || d.stage == DrainState.Stage.VERIFY || d.stage == DrainState.Stage.REPAIR_WALLS
                        ? (d.stage == DrainState.Stage.REPAIR_WALLS && d.structureBottom != Integer.MIN_VALUE
                            ? Math.min(d.bottomY,d.structureBottom) : d.bottomY) : d.surfaceY)
                : d.heights[selected];
        return true;
    }

    boolean platform(net.minecraft.core.BlockPos p) {
        for(Member member:members.values())if(member.state().origin.below().equals(p))return true;
        return false;
    }

    /** Budgeted shared exterior scan; solid roofs protect enclosed sponge rooms. */
    void cleanSponges(ServerLevel level) {
        DrainState d=progress;
        int limit=Math.max(level.getMinY(),d.structureBottom==Integer.MIN_VALUE?d.surfaceY-96:d.structureBottom);
        for(int budget=0;budget<512;budget++) {
            if(spongeColumn>=d.columns()) { spongeColumn=0; spongeY=Integer.MAX_VALUE; return; }
            if(spongeY==Integer.MAX_VALUE)spongeY=d.surfaceY+2;
            if(spongeY<limit) { spongeColumn++; spongeY=Integer.MAX_VALUE; continue; }
            var p=d.column(spongeColumn,spongeY);
            if(!WorkerBrain.loaded(level,p)) { spongeColumn++; spongeY=Integer.MAX_VALUE; continue; }
            var block=level.getBlockState(p);
            boolean sponge=block.is(Blocks.SPONGE)
                    ||block.is(Blocks.WET_SPONGE);
            if(sponge)level.setBlock(p,Blocks.AIR.defaultBlockState(),
                    Block.UPDATE_ALL);
            boolean ownedSand=block.is(Blocks.SAND)
                    &&d.sandTops[spongeColumn]!=Integer.MIN_VALUE
                    &&spongeY>d.floors[spongeColumn]&&spongeY<=d.sandTops[spongeColumn];
            boolean accessGlass=block.is(Blocks.GLASS)&&d.scaffolds.contains(p);
            if(!sponge&&!ownedSand&&!accessGlass&&!platform(p)&&!block.getCollisionShape(level,p).isEmpty()) {
                spongeColumn++; spongeY=Integer.MAX_VALUE;
            } else spongeY--;
        }
    }

    /** Recheck the exposed columns after completion as neighboring fluids settle. */
    void inspectFinished(ServerLevel level) {
        DrainState d=progress;
        if(d.stage!=DrainState.Stage.DONE)return;
        int limit=DrainJobs.repairBottom(level,d);
        for(int budget=0;budget<256;budget++) {
            if(checkColumn>=d.columns()) { checkColumn=0;checkY=Integer.MAX_VALUE;return; }
            if(checkY==Integer.MAX_VALUE)checkY=d.surfaceY;
            if(d.wall(d.column(checkColumn,d.surfaceY)) || checkY<limit) {
                checkColumn++;checkY=Integer.MAX_VALUE;continue;
            }
            var p=d.column(checkColumn,checkY);
            if(!WorkerBrain.loaded(level,p))return;
            var block=level.getBlockState(p);
            if(platform(p)) { checkY--;continue; }
            if(DrainJobs.needsRepair(block,d,checkColumn,checkY)) {
                advance(DrainState.Stage.REPAIR_WALLS);
                checkColumn=0;checkY=Integer.MAX_VALUE;return;
            }
            if(!block.getCollisionShape(level,p).isEmpty()) { checkColumn++;checkY=Integer.MAX_VALUE; }
            else checkY--;
        }
    }

    void checkpoint(DrainState d) {
        progress.bottomY = Math.min(progress.bottomY, d.bottomY);
        if (d.index<d.columns() && claims.containsKey(d.index)) d.heights[d.index] = d.y;
        syncAll();
    }

    void complete(CopperGolem golem, DrainState d, DrainState.Stage next) {
        progress.bottomY = Math.min(progress.bottomY, d.bottomY);
        d.completed.set(d.index);
        claims.remove(d.index);
        if (d.completed.cardinality() == d.columns()) advance(next);
        else {
            d.index = d.columns();
            d.travel = null;
            d.route.clear();
        }
        syncAll();
    }

    void advance(DrainState.Stage next) {
        DrainState.Stage before=progress.stage;
        if(before==DrainState.Stage.REPAIR_RELEASE&&next==DrainState.Stage.REPAIR_WATER) {
            progress.cleanupLayer--;
            int bottom=progress.structureBottom==Integer.MIN_VALUE?progress.bottomY:Math.min(progress.bottomY,progress.structureBottom);
            if(progress.cleanupLayer<bottom)next=DrainState.Stage.CHECK_DRY;
        } else if(next==DrainState.Stage.REPAIR_WATER)progress.cleanupLayer=progress.surfaceY;
        progress.next(next);
        if(next==DrainState.Stage.REPAIR_WALLS) {
            progress.finishVersion=2; progress.repairPass++;
            progress.cleanupFloors=new int[progress.columns()];java.util.Arrays.fill(progress.cleanupFloors,Integer.MIN_VALUE);
        }
        if(progress.rowCleanup()) {
            // Only the first column of each interior row represents a row task.
            progress.completed.set(0,progress.columns());
            for(int row=1;row<progress.depth()-1;row++)progress.completed.clear(row*progress.width());
        }
        claims.clear();
        returned.clear();
        syncAll();
    }

    void home(CopperGolem golem, DrainState.Stage next) {
        returned.add(golem.getUUID());
        if (returned.containsAll(members.keySet())) advance(next);
    }
}
