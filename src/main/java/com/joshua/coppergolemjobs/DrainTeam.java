package com.joshua.coppergolemjobs;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.golem.CopperGolem;

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

    static DrainTeam join(ServerLevel level, CopperGolem golem, WorkerState worker) {
        DrainState d = worker.drain;
        Site site = new Site(d.minX, d.maxX, d.minZ, d.maxZ, d.surfaceY);
        Map<Site, DrainTeam> teams = LEVELS.computeIfAbsent(level, ignored -> new HashMap<>());
        teams.entrySet().removeIf(entry -> entry.getValue().empty(level.getGameTime()));
        DrainTeam team = teams.computeIfAbsent(site, ignored -> new DrainTeam());
        team.empty(level.getGameTime());
        team.members.put(golem.getUUID(), new Member(new WeakReference<>(golem), worker, level.getGameTime()));
        if (team.progress == null) team.progress = snapshot(d);
        else if (d != team.progress && d.stage.ordinal() > team.progress.stage.ordinal()) {
            team.progress = snapshot(d);
            team.claims.clear();
            team.returned.clear();
        } else if (d != team.progress && d.stage == team.progress.stage && d.completed != team.progress.completed) {
            DrainState p = team.progress;
            p.completed.or(d.completed);
            p.scaffolds.addAll(d.scaffolds);
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

    private static DrainState snapshot(DrainState d) {
        DrainState p = new DrainState();
        p.minX = d.minX;
        p.maxX = d.maxX;
        p.minZ = d.minZ;
        p.maxZ = d.maxZ;
        p.surfaceY = d.surfaceY;
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
            if (selected < 0) {
                worker.status = "Waiting for the crew to finish this stage";
                return false;
            }
            claims.put(selected, id);
            d.travel = null;
            d.route.clear();
        }
        d.index = selected;
        d.y = d.heights[selected] == Integer.MIN_VALUE
                ? (d.stage == DrainState.Stage.WALLS || d.stage == DrainState.Stage.VERIFY ? d.bottomY : d.surfaceY)
                : d.heights[selected];
        return true;
    }

    void checkpoint(DrainState d) {
        progress.bottomY = Math.min(progress.bottomY, d.bottomY);
        if (d.index<d.columns() && claims.containsKey(d.index)) d.heights[d.index] = d.y;
        syncAll();
    }

    void complete(CopperGolem golem, DrainState d, DrainState.Stage next) {
        progress.bottomY = Math.min(progress.bottomY, d.bottomY);
        d.completed.set(d.index);
        claims.remove(d.index, golem.getUUID());
        if (d.completed.cardinality() == d.columns()) advance(next);
        else {
            d.index = d.columns();
            d.travel = null;
            d.route.clear();
        }
        syncAll();
    }

    void advance(DrainState.Stage next) {
        progress.next(next);
        claims.clear();
        returned.clear();
        syncAll();
    }

    void home(CopperGolem golem, DrainState.Stage next) {
        returned.add(golem.getUUID());
        if (returned.containsAll(members.keySet())) advance(next);
    }
}
