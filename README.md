# Copper Golem Jobs — Fabric 26.3 — 0.3.5 test build

Give a vanilla Copper Golem a job by right-clicking it with a vanilla tool. No fuel, power source, hearts, upgrades, or tool durability costs.

[Download the 0.3.5 test jar](dist/copper-golem-jobs-fabric-26.3-0.3.5.jar).

## Install

Use Minecraft Java **26.3**, Fabric Loader **0.19.5 or newer**, and Fabric API **0.160.6+26.3**. Put `copper-golem-jobs-fabric-26.3-0.3.5.jar` into `mods` alongside Fabric API. Remove the earlier `copper-golems` test mod before installing this restart; the two mods deliberately cannot load together.

The mod uses vanilla mobs, items, blocks, and visuals. The jar works in single-player and on a Fabric server. Begin in a separate Creative test world because several jobs excavate terrain automatically.

## Controls

- Right-click a Copper Golem with a job item to assign it. Your item stays in your inventory. The item in the golem's hand is a display copy and does not drop as loot.
- Right-click with an empty main hand to clear the job. An unfinished drain project is paused and retained; reassign a Loyalty III trident to resume it. Other jobs cannot replace an unfinished drain project.
- Sneak-right-click with an empty main hand to read its job and current waiting reason in chat.
- Sneak-right-click with an axe for normal vanilla scraping. Honeycomb still waxes it normally. Vanilla oxidation remains active; wax workers you want to keep working indefinitely.
- If an unassigned golem is already carrying a real item, retrieve that item with an empty hand before assigning a job.
- Reassigning a job resets its work chunk and uses the golem's current facing for mining. Changing jobs does not create another physical tool.

## Generator setup

1. Assign a wooden pickaxe to a Copper Golem in the generator's chunk.
2. Hold another wooden pickaxe and right-click the stone or cobblestone output block. The nearest generator worker in that assignment chunk, within 64 blocks of the block, receives that position.
3. Provide Copper Chest storage. It mines the marked position and waits for stone/cobblestone to reappear. It never switches to neighboring blocks or replaces the generator's lava/water with glass.

Re-marking changes the target. The marked position survives save/reload. Wooden pickaxes no longer start the full-chunk quarry; use another pickaxe for that job.

## Tree farmer setup

Give the golem a **wooden axe** near your trees, with open Copper Chest storage within 64 blocks. It uses the same tree search area as other axes: its assignment chunk and the eight neighboring chunks, following connected trees within the existing 64-block work range.

After harvesting, it remembers each cleared tree base and prefers matching saplings from Copper Chest storage to replant. If that species is unavailable, another supplied sapling can use the cleared site. It also plants supplied saplings on suitable free ground without needing a previous tree or remembered planting site. New sites leave roughly four blocks between tree bases and cycle through the available sapling types. Wood, sticks, and other tree drops go to storage; saplings are taken from storage only when planting succeeds. It can harvest the resulting trees again without reassignment. No saplings are created, no bone meal is supplied, and normal vanilla growth requirements still apply. You can add any supported sapling type to the Copper Chest to start a new tree farm or replenish its supply. The worker can approach trees under a low canopy using its actual body clearance.

Dark oak and pale oak use four saplings in a 2×2 planting site. Existing giant spruce/jungle footprints are also replanted as 2×2 groups. A group waits until all four saplings and all four planting spaces are available. Existing blocks and player-planted saplings are preserved. Waiting planting sites and their species survive save/reload. Missing/full Copper Chests pause the job, just as they do other workers.

**Netherite axes handle large trees; other axes continue ordinary clearing without replanting.** Changing a tree farmer's job resets its remembered planting sites, like other ordinary job assignments.

## Netherite axe and protected logs

Give a golem a **netherite axe** to fell connected trees without the ordinary 256-log or 64-block tree-size limits. It first reaches a trunk in the normal tree search area, surveys connected logs and leaves within ten blocks of each log, then harvests in small batches. Progress survives save/reload, and full or missing Copper Chests pause work. Keep the tree chunks loaded; the worker does not force-load them. This role clears trees without replanting.

Modded mega trees are supported when their blocks use Minecraft's standard `logs` and `leaves` tags. The survey accepts persistent leaves as well. Compatibility with a particular tree mod still needs an in-game check.

**Every axe worker skips stripped logs and stripped wood**, including blocks stripped while a large-tree job is underway. Vanilla blocks and modded blocks with `stripped` in the usual registry-name position are protected. Strip structural logs before using tree workers nearby.

## Monument drain setup

Place the golem on a solid platform **at ocean water level, directly above a naturally generated Ocean Monument**, and assign a **Loyalty III trident**. Its feet should be one block above the top water block. Keep open Copper Chests within 64 blocks of its working positions; a chest near the center is preferable. Stay within simulation distance so the golem keeps ticking, keep the monument footprint loaded, and wax the golem for a long project.

The worker surveys the monument's recorded structure bounds, leaves one column of clearance around its footprint, and builds its outer glass wall a further block out. It clears exterior kelp/seagrass, returns to its starting platform, fills the exterior columns with supplied sand, constructs and verifies the perimeter barrier, removes its temporary sand without drops, and returns home. Temporary glass footholds are cleaned up; permanent walls remain. Sand columns are assembled stably from the bottom while the golem works at the surface, avoiding large groups of falling-sand entities.

**Exterior work only:** each surveyed column ends at its first existing solid block. Monument roofs, walls, rooms, and water beneath existing roofs remain untouched. Existing terrain and player blocks are preserved. Only kelp and seagrass are collected, using the same Copper Chest rule as other jobs. The sand/glass supply costs no resources in this test version.

Progress, surveyed floors, temporary sand ownership, and scaffolds survive save/reload. Missing/full storage or unloaded work chunks pause the job. A changed or obstructed construction site may need you to clear access before it can continue. Construction is incremental and a full-sized monument is a long-running job. Sneak-right-click with an empty hand to inspect progress.

## Combat and fishing enchantments

Assign the worker using the enchanted sword or fishing rod. Its equipped job item copies the original enchantments and keeps them through save/reload. Reassign it with the upgraded item if you add enchantments later. Assignment does not consume or wear the player's item. Worker attacks and fishing also retain the existing no-tool-wear behavior, so Unbreaking and Mending do not change these jobs.

Looting applies through the victim's normal death loot table; it does not multiply every drop or bypass player-kill requirements on rare drops. Sword workers use single-target melee attacks, so Sweeping Edge does not add a sweeping attack. Fishing uses the assigned rod for Lure timing and Luck of the Sea loot luck, including vanilla biome and open-water checks. Full storage preserves the actual catch and resumes without rerolling it.

## Copper Chest rule

Every action requires an available, loaded Copper Chest within **64 blocks in three dimensions**, measured from the golem to the chest's center. This is a radius, independent of chunk adjacency. The 64-block distance is inclusive. A mining golem needs storage along its route; one surface chest cannot support mining indefinitely below it.

All oxidized and waxed Copper Chest variants count. Regular chests and barrels do not. Chests with a solid block directly over the lid are unavailable. Nearest chests are used first, and output can span multiple valid chests, including both halves of a double chest.

**No chest or all chests full = no work.** The worker checks before each action and while walking, about twice per second. It keeps its assignment and retries automatically. Removing a blocking lid or making storage room lets it resume.

For this test build, supplies and drops transfer directly between the worker and in-range Copper Chests. It walks to its work targets; it does not carry each load back to the chest. Chest pathfinding is not required.

Block and harvest output is checked as a complete batch before anything is broken. Unpredictable combat drops, or a fishing catch that does not fit, are retained in saved worker data until a chest has room. No further work happens while those drops are waiting. A worker with pending drops must unload before its job can be changed. If killed, its remaining collected items drop normally.

## Jobs

| Item | Test-build behavior |
|---|---|
| Wooden pickaxe | Generator worker: mark stone or cobblestone in its assignment chunk with another wooden pickaxe. Repeatedly mines only that exact position as it regenerates, with Silk Touch. Never seals the generator fluids. |
| Loyalty III trident | Exterior Ocean Monument drain crew. Supplies temporary sand and permanent glass, collects kelp/seagrass, leaves monument blocks and interior rooms alone, then returns home. |
| Other pickaxes | Quarries the assigned 16×16 chunk one layer at a time, descending toward bedrock on a cobblestone staircase it builds automatically. Block drops use a Netherite pickaxe with Silk Touch. |
| Shovel | Removes reachable shovel-mineable blocks in the assignment chunk: dirt, sand, gravel, clay, snow, and similar blocks. Respects accessibility and retries as the terrain changes. |
| Wooden axe | Harvests trees, remembers planting sites, and plants chest-supplied saplings on suitable free ground. Supports matching replants, mixed species, and 2×2 sites. |
| Netherite axe | Large-tree cutter without a fixed connected-log count or tree-size range limit. Surveys and harvests in batches, saves progress, skips stripped logs/wood, and stores normal drops. Does not replant. |
| Other axes | Searches the assignment chunk and all eight neighboring chunks. It can cross chunk borders and follows connected tree logs up to 64 blocks from its assignment point. Deposits normal log and leaf drops. Log clusters without natural leaves are skipped. |
| Fishing rod | Works at reachable source water in the assignment chunk. Uses vanilla fishing loot, including biome and open-water checks. Catches normally repeat after 10–30 seconds. Lure subtracts five seconds per level from the repeat delay, with a one-second minimum. Luck of the Sea changes vanilla loot chances; treasure still requires open water. A splash marks each catch; there is no persistent cast-line animation. |
| Spear | Clears a 3×3 horizontal tunnel in its assigned facing. Never generates or loads the next chunk itself; “unknown” means not currently loaded. It bridges gaps with cobblestone and stops before unloaded terrain or when storage is unavailable. |
| Hoe | Tills clear dirt, grass, dirt path, coarse dirt, or rooted dirt whether or not water is nearby. Plants and harvests wheat, carrots, potatoes, and beetroot. Replants from the harvest first, then chest supplies. Does not force crops to grow faster. |
| Sword | Approaches hostile mobs in the assignment chunk, attacks once per second with 6 base damage plus applicable weapon enchantment effects, and sends drops to storage. Damage enchantments (including Sharpness, Smite, and Bane of Arthropods), Fire Aspect, Knockback, and Looting use vanilla enchantment rules. Also gathers nearby accessible item drops. Normal golem health and enemy retaliation apply. |
| Stick | Harvests mature cocoa, reserves one bean to replant, and stores the remaining beans. Leaves younger pods alone. |
| Bucket | Milks an adult cow in the assignment chunk. Consumes one empty bucket from a Copper Chest and stores one milk bucket. Cooldown is 60 seconds per golem. The assignment bucket is only a job token. |
| Shears | Shears grown, woolly sheep. Also cuts the bottom segment of ordinary green vines when another segment remains above it for regrowth. |

Shovel, farming, fishing, milking, shearing, and sword jobs keep the chunk in which they were assigned. Axes also search the eight neighboring chunks. Mining quarries its assigned chunk. Copper Chests can be outside the assignment chunk.

Mining and digging seal exposed water and lava with glass automatically. Temporary sealing glass is removed when the quarry reaches it. Workers create permanent cobblestone beneath themselves for staircases and bridges. No glass or cobblestone supply is required. Unbreakable blocks and blocks with inventories or other block entities are left intact.

Worker navigation has a movement watchdog. If Minecraft reports an active path but the golem makes no progress for three seconds, the route is recalculated. After three failed routes, that blocked target is temporarily skipped so the golem can continue with other work without being reassigned.

Tree detection uses connected unstripped logs plus leaves; unstripped structures touching tree foliage can still be treated as trees. Ordinary axes and wooden-axe farmers process at most 256 connected logs within 64 blocks of the assignment point; netherite axes remove those tree-size limits. The netherite large-tree worker collects leaves up to ten blocks from surveyed logs in every direction; ordinary axes and wooden-axe farmers keep the three-block leaf range. Farm support in this build is limited to the four crops listed above. Sword material does not change damage.

## Build on your PC

The prebuilt jar is ready to test. To build from source, extract this source zip and double-click `BUILD-WINDOWS.bat`, or run this in PowerShell from the extracted folder:

```powershell
C:\Gradle\bin\gradle.bat build
```

Use **JDK 25** and **Gradle 9.5.1 or newer compatible with Loom 1.17**. The project pins **Loom 1.17.20**, uses the unobfuscated Minecraft 26.3 names, and does not use Yarn mappings. Gradle downloads the dependencies on the first build.

The installable output is `build/libs/copper-golem-jobs-fabric-26.3-0.3.5.jar`. The `-sources.jar` is source code for developers, not the mod to install.

Optional headless game tests:

```powershell
C:\Gradle\bin\gradle.bat -PgameTests=true runGameTest
```

See `TESTING.md` for automated verification and the short in-game test checklist.

API references: [Loom](https://docs.fabricmc.net/develop/loom/), [Fabric game tests](https://docs.fabricmc.net/develop/automatic-testing), and the actual Mojang 26.3 server classes. Game and Fabric binaries are not included in this source archive.
