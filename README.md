# Copper Golem Jobs — Fabric 26.3 — 0.2.3 test build

Give a vanilla Copper Golem a job by right-clicking it with a vanilla tool. No fuel, power source, hearts, upgrades, or tool durability costs.

## Install

Use Minecraft Java **26.3**, Fabric Loader **0.19.5 or newer**, and Fabric API **0.160.6+26.3**. Put `copper-golem-jobs-fabric-26.3-0.2.3.jar` into `mods` alongside Fabric API. Remove the earlier `copper-golems` test mod before installing this restart; the two mods deliberately cannot load together.

The mod uses vanilla mobs, items, blocks, and visuals. The jar works in single-player and on a Fabric server. Begin in a separate Creative test world because several jobs excavate terrain automatically.

## Controls

- Right-click a Copper Golem with a job item to assign it. Your item stays in your inventory. The item in the golem's hand is a display copy and does not drop as loot.
- Right-click with an empty main hand to clear the job. The golem returns to its normal vanilla behavior.
- Sneak-right-click with an empty main hand to read its job and current waiting reason in chat.
- Sneak-right-click with an axe for normal vanilla scraping. Honeycomb still waxes it normally. Vanilla oxidation remains active; wax workers you want to keep working indefinitely.
- If an unassigned golem is already carrying a real item, retrieve that item with an empty hand before assigning a job.
- Reassigning a job resets its work chunk and uses the golem's current facing for mining. Changing jobs does not create another physical tool.

## Copper Chest rule

Every action requires an available, loaded Copper Chest within **64 blocks in three dimensions**, measured from the golem to the chest's center. This is a radius, independent of chunk adjacency. The 64-block distance is inclusive. A mining golem needs storage along its route; one surface chest cannot support mining indefinitely below it.

All oxidized and waxed Copper Chest variants count. Regular chests and barrels do not. Chests with a solid block directly over the lid are unavailable. Nearest chests are used first, and output can span multiple valid chests, including both halves of a double chest.

**No chest or all chests full = no work.** The worker checks before each action and while walking, about twice per second. It keeps its assignment and retries automatically. Removing a blocking lid or making storage room lets it resume.

For this test build, supplies and drops transfer directly between the worker and in-range Copper Chests. It walks to its work targets; it does not carry each load back to the chest. Chest pathfinding is not required.

Block and harvest output is checked as a complete batch before anything is broken. Unpredictable combat drops, or a fishing catch that does not fit, are retained in saved worker data until a chest has room. No further work happens while those drops are waiting. A worker with pending drops must unload before its job can be changed. If killed, its remaining collected items drop normally.

## Jobs

| Item | Test-build behavior |
|---|---|
| Pickaxe | Quarries the assigned 16×16 chunk one layer at a time, descending toward bedrock on a cobblestone staircase it builds automatically. Block drops use a Netherite pickaxe with Silk Touch. |
| Shovel | Removes reachable shovel-mineable blocks in the assignment chunk: dirt, sand, gravel, clay, snow, and similar blocks. Respects accessibility and retries as the terrain changes. |
| Axe | Searches the assignment chunk and all eight neighboring chunks. It can cross chunk borders and follows connected tree logs up to 64 blocks from its assignment point. Deposits normal log and leaf drops. Log clusters without natural leaves are skipped. |
| Fishing rod | Works at reachable source water in the assignment chunk. Uses vanilla fishing loot, including biome and open-water checks. Catches repeat after 10–30 seconds. A splash marks each catch; there is no persistent cast-line animation. |
| Spear | Clears a 3×3 horizontal tunnel in its assigned facing. Never generates or loads the next chunk itself; “unknown” means not currently loaded. It bridges gaps with cobblestone and stops before unloaded terrain or when storage is unavailable. |
| Hoe | Tills clear dirt, grass, dirt path, coarse dirt, or rooted dirt whether or not water is nearby. Plants and harvests wheat, carrots, potatoes, and beetroot. Replants from the harvest first, then chest supplies. Does not force crops to grow faster. |
| Sword | Approaches hostile mobs in the assignment chunk, deals 6 damage per second while in range, and sends drops to storage. Also gathers nearby accessible item drops. Normal golem health and enemy retaliation apply. |
| Stick | Harvests mature cocoa, reserves one bean to replant, and stores the remaining beans. Leaves younger pods alone. |
| Bucket | Milks an adult cow in the assignment chunk. Consumes one empty bucket from a Copper Chest and stores one milk bucket. Cooldown is 60 seconds per golem. The assignment bucket is only a job token. |
| Shears | Shears grown, woolly sheep. Also cuts the bottom segment of ordinary green vines when another segment remains above it for regrowth. |

Shovel, farming, fishing, milking, shearing, and sword jobs keep the chunk in which they were assigned. Axes also search the eight neighboring chunks. Mining quarries its assigned chunk. Copper Chests can be outside the assignment chunk.

Mining and digging seal exposed water and lava with glass automatically. Temporary sealing glass is removed when the quarry reaches it. Workers create permanent cobblestone beneath themselves for staircases and bridges. No glass or cobblestone supply is required. Unbreakable blocks and blocks with inventories or other block entities are left intact.

Worker navigation has a movement watchdog. If Minecraft reports an active path but the golem makes no progress for three seconds, the route is recalculated. After three failed routes, that blocked target is temporarily skipped so the golem can continue with other work without being reassigned.

This is a first behavior test. Tree detection uses connected logs plus natural leaves, not a perfect distinction between a tree and a structure. It processes at most 256 connected logs per tree and leaves within three blocks of those logs. Farm support in this build is limited to the four crops listed above. Tool material does not change job speed or sword damage.

## Build on your PC

The prebuilt jar is ready to test. To build from source, extract this source zip and double-click `BUILD-WINDOWS.bat`, or run this in PowerShell from the extracted folder:

```powershell
C:\Gradle\bin\gradle.bat build
```

Use **JDK 25** and **Gradle 9.5.1 or newer compatible with Loom 1.18**. The project pins **Loom 1.18.2**, uses the unobfuscated Minecraft 26.3 names, and does not use Yarn mappings. Gradle downloads the dependencies on the first build.

The installable output is `build/libs/copper-golem-jobs-fabric-26.3-0.2.3.jar`. The `-sources.jar` is source code for developers, not the mod to install.

Optional headless game tests:

```powershell
C:\Gradle\bin\gradle.bat -PgameTests=true runGameTest
```

See `TESTING.md` for automated verification and the short in-game test checklist.

API references: [Fabric 26.3 development notes](https://fabricmc.net/2026/06/15/262.html), [Loom](https://docs.fabricmc.net/develop/loom/), [Fabric game tests](https://docs.fabricmc.net/develop/automatic-testing), and the actual Mojang 26.3 server classes. Game and Fabric binaries are not included in this source archive.
