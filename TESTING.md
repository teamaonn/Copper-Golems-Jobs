# Testing version 0.3.4

## Automated checks

The Java sources were compiled against the actual Minecraft 26.3 server classes with JDK 25, Fabric Loader 0.19.5, and Fabric API 0.160.6+26.3. Runtime checks use Fabric's headless GameTest server, which applies the actual mod mixins and loads vanilla registries and loot tables.

The regression suite exercises:

- Right-click assignment, retaining the original tool and its durability, and empty-hand job clearing.
- Missing Copper Chests, full storage, automatic resumption, and exact output amounts.
- A nine-block spear tunnel slice, cobblestone gap bridging, full-layer quarrying, a descending cobblestone staircase, Silk Touch ore drops, bedrock, temporary water/lava sealing glass removal, and refusing an unloaded chunk without loading it.
- Tree felling, crop harvesting/replanting, planting supplies from chests, and cocoa replanting.
- Empty buckets, milk bucket output, the one-minute cooldown, sheep wool, and vine regrowth support.
- Hostile mob damage and captured death drops, plus vanilla fishing loot.
- Golem save/reload, including tunnel direction, cooldown, and pending items.
- An inclusive 64-block chest limit and rejection immediately beyond it.
- Inventory overflow rejection and exact stack capacity.
- Actual golem AI movement to a distant target followed by collection and storage.
- More than 64 consecutive shovel drops and automatic recovery from a navigator that claims to be walking while making no progress.
- Wooden-pickaxe assignment, block-click generator marking, repeated output, waiting for regeneration, preserving generator lava, storage pause/resume, and saved target positions.
- Loyalty III assignment gating and discovering a registered monument's actual structure bounds beneath a water-level platform.
- A miniature monument exterior with a sealed room: surveying, plant collection, sand fill, glass walls, barrier verification, sand removal, return home, and scaffold cleanup. A mid-fill save/reload preserves ownership; existing seabed sand, monument blocks, and room water remain untouched.
- A separate full live miniature drain run with normal entity movement and fluid ticks. The test itself holds its surrounding chunks active to simulate a nearby player; the mod does not force-load chunks.

These are headless tests with constructed fixtures and registered structure metadata, not a completed playtest on a full naturally generated Ocean Monument. Full-size terrain, guardians, player structures, and long-running performance still need in-game testing.

`validation/gametest-results.xml` contains the final machine-generated report. The suite includes seven custom tests and Minecraft's built-in always-pass control. Individual assertions are listed in `validation/passed-checks.txt`.

The compiled jar is produced directly from these checked Java classes and the mod resources. Gradle resolved Loom 1.18.2, but its Unix-domain socket capability probe fails with Operation not permitted in this environment, so the Gradle lifecycle itself did not complete here. The supplied Gradle project uses the Fabric 26.3 setup and pinned Loom version; it is intended for your normal Windows build environment. No Minecraft or Fabric dependency binaries are bundled in the deliverables.

## Quick checks in Minecraft

1. Make a new Creative test world. Spawn a Copper Golem and put a Copper Chest nearby with its lid clear. Wax the golem if desired.
2. Right-click it with a shovel beside some dirt. Confirm the tool stays with you, the golem walks to work, and dirt appears in the Copper Chest.
3. Fill the chest completely. Confirm it stops. Clear a slot and confirm it resumes. Break the chest and confirm it waits again.
4. Try each other assignment in a small setup: a tree, water pool, mature crops, cocoa, an adult cow with chest buckets, a sheep, and a hanging vine.
5. Face the golem toward a stone wall and give it a spear. Check that the cleared area is 3×3 and that it bridges gaps with cobblestone. Give it a pickaxe and check that it clears the assigned chunk layer by layer, builds a descending cobblestone staircase, and stores ore blocks with Silk Touch.
6. Empty-hand right-click to clear a job. Sneak-right-click with an empty hand to read the current status. Save and reload while a job is assigned and confirm it resumes.

For a bug report, include the job item, chat status, whether the chest is reachable by the 64-block distance rule, the chest contents, and the relevant error from `logs/latest.log` if one appears. Client animations and natural-world pathfinding still need player testing; the automated scenarios use controlled terrain.

## New job playtest

- Give a golem a wooden pickaxe in a generator's chunk, then right-click that generator's stone/cobblestone output with another wooden pickaxe. Confirm only that position is mined, generation fluids remain intact, and full/missing Copper Chest storage pauses it.
- For the drain crew, use a naturally generated monument. Put the golem on a solid block at the ocean surface directly above it, provide a Loyalty III trident and Copper Chests, and keep it within simulation distance. Confirm exterior vegetation is collected, sand surrounds/covers the exterior, glass walls stay, and temporary sand is removed with no drops.
- Pause with an empty-hand click, save/reload, and give it a Loyalty III trident again to resume. Check progress with sneak + empty-hand right-click.
- Confirm the monument and its interior rooms remain untouched. Work stops at the top existing solid in each column; it does not clear roofed spaces.

## Wooden axe tree farmer checks

The tree-farming GameTest covers assignment without tool wear, ordinary axes retaining clearing behavior, remembered planting species and sites across save/reload, repeated harvest/replant cycles, exact real-sapling consumption, no free saplings when supplies run out, full/missing storage pause and automatic resumption, original 2×2 dark oak footprints, waiting for a complete four-sapling batch, preserving an obstructed planting site, and cross-chunk birch replanting.

For an in-game check, assign a wooden axe near a few trees and an open Copper Chest. Confirm wood and spare saplings accumulate, cleared tree bases receive matching saplings, and the worker harvests them again after normal growth. Use another axe to clear trees permanently; netherite handles large trees.

## Chest-supplied planting and low canopy regression

Additional checks cover a fresh coarse-dirt platform with no trees or saved sites, planting different supplied species with exact inventory consumption, replacing an unavailable remembered species with a supplied alternative, and never creating supplies from an empty chest. A separate live test places a low leaf canopy one block above the ground and verifies that the golem walks underneath and harvests without a player breaking leaves. Approach clearance uses the actual Copper Golem bounding box.

## Netherite mega-tree and stripped-log regression

A constructed 320-log, 80-block-tall tree exercises discovery beyond the ordinary count and distance limits, persistent leaf recognition, saved survey and harvest progress, full-storage pause/resume, exact drops, and the 16-block harvest budget. Adjacent stripped logs and stripped wood remain intact for netherite, ordinary axes, and wooden-axe farmers. A log stripped after surveying also remains intact. This uses vanilla tagged blocks to model a mega tree; individual third-party tree mods have not been playtested. For an in-game check, use a netherite axe beside a modded mega tree, keep its chunks loaded, and check stripped structural blocks alongside it.

## Ten-block giant-tree canopy regression (0.3.4)

The netherite large-tree fixture now includes persistent leaves ten blocks from surveyed logs along horizontal, vertical, and diagonal directions, plus an eleven-block control. The ten-block leaves are collected and the control remains. Ordinary axe and wooden-axe jobs retain their original leaf range. Tests also resume a saved extended-canopy cursor and migrate an old three-block saved harvest back into canopy surveying. Canopy work limits new block reads to 4,096 and duplicate-position visits to 65,536 per work tick; overlapping scan cubes share a visited-position cache.
