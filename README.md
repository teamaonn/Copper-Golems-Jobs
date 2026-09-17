# Copper Golems Fabric Mod for Minecraft 26.2

Early foundation build for Joshua's copper golem/minion mod.

## Current v0.3 Feature

- Adds the Starter Heart item.
- Starter Heart is the planned basic golem power core.
- Starter Heart max energy target: 100.
- Adds a survival recipe for Starter Heart.
- Right-click a copper golem with a Starter Heart to install it.
- Right-click a hearted copper golem with a pickaxe to confirm the tool interaction path.
- Pickaxes are not consumed yet. This build only proves the interaction layer.
- Heart and pickaxe state is remembered in memory for this test build and resets when the world/server reloads.

## Planned v1 Loop

- Create/activate a copper golem or minion.
- Give it a Starter Heart.
- Feed it food for energy.
- Give it a pickaxe.
- Link it to a chest.
- Let it mine a safe descending staircase in the current chunk.
- Skip ores.
- Store drops in the linked chest.
- Stop when it runs out of energy, tool, or storage.

## Build

Use Java 25.

```bash
C:\Gradle\bin\gradle.bat build
```

The jar will be in:

```text
build/libs/
```
