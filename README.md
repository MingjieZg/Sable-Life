# Sable Life

Sable Life is a Minecraft 1.21.1 NeoForge mod built on top of **Create** and **Sable**, designed to work with **Create: Aeronautics** and turn physical structures into configurable living constructs.

Downloads, screenshots and the full gameplay description are available on [CurseForge](https://www.curseforge.com/minecraft/mc-mods/sable-life). This repository focuses on source code, dependencies, compatibility and development.

## Current Version

**0.1.403**

[中文更新清单](docs/releases/0.1.403.md) · [English changelog](docs/releases/0.1.403_EN.md) · [Stress-test results](docs/performance/STRESS_TESTS_EN.md)

Minecraft **1.21.1** · **NeoForge** · **Java 21**

## Requirements

### Required

- Minecraft 1.21.1
- NeoForge 21.1.235 or newer
- Create **6.0.10 or newer** (`[6.0.10,)`)
- Sable **2.0.5 or newer** (`[2.0.5,)`)

Create and Sable have minimum versions without upper version caps. The development dependencies remain pinned to Create **6.0.10** and Sable **2.0.5** for reproducible builds. Higher versions for Minecraft 1.21.1 / NeoForge are allowed by Sable Life's dependency metadata, but are not all verified compatible. If a newer version causes a problem, include the exact mod versions and relevant log when opening an issue.

### Designed to work with

- Create: Aeronautics **1.3.2** (pinned development version; other versions are not guaranteed)

Create: Aeronautics is part of the primary intended gameplay environment, but is not currently declared as a hard runtime dependency.

### Optional for blueprint features

- **Sable Photomancy**, version **1.0.1 or newer**, with a compatible blueprint API. Version 1.0.1 is the compiled and tested baseline; newer versions are checked by required interfaces rather than an exact version match.

Blueprint placement, Arena automatic respawn and natural blueprint spawning require compatible Photomancy interfaces. Other modules remain available without these features. The server log identifies an incompatible API. See [Photomancy compatibility](docs/PHOTOMANCY_COMPATIBILITY.md) for the contract and limits.

## The Structure Is the Body

The structure itself receives body damage. Optional Entity Hitbox modules add configurable entity receivers for ordinary entity-based combat; Hybrid mode lets both entry points share creature health and part-damage rules.

The actual physical blocks of the construct form its body. Different sections can function as body parts, attack points, limbs, weapons, sensors, or other mechanical systems.

Receivers can also attract supported hostile or retaliating mobs. Dimensions, offsets and active limits are configurable.

## Compatibility Notice

Structure-body damage and entity-receiver damage use different entry points. Standard entity damage can reach configured receivers; direct structure hits may require a weapon adapter. Neither path guarantees every mod's custom weapon or secondary effects.

Combat integration versions:

| Integration | Version / scope |
| --- | --- |
| Epic Fight | **21.17.3.1**; its bridge is enabled only for this exact version. |
| Iron's Spells 'n Spellbooks | **1.21.1-3.16.3**; its bridge is enabled only for this exact version. |
| TaCZ | Pinned test artifact: **NeoForge 1.1.8-hotfix-r7**, an unofficial Minecraft 1.21.1 port. |
| Create Big Cannons (CBC) | Pinned test version: **5.11.7**, with Ritchie's Projectile Library **2.1.2**. |

The exact-version gates above apply to Epic Fight and Iron's Spells. The TaCZ/CBC entries describe the pinned test environment, not an assertion that every other version works.

### CBC addon coverage

Damage forwarding and mounted-structure cleanup are separate compatibility features:

| Addon | Scope covered by the integration and regression tests |
| --- | --- |
| CBC Advanced Technology (AT) | Heavy Autocannon AP, APDSFS and HE direct-hit damage forwarding, with per-projectile/per-creature deduplication. Mounted twin and heavy autocannons are also covered by death cleanup tests. |
| CBC Neo Warfare | Compact cannon mount recognition and cleanup of mounted rotary cannons on body death, including checks that later disassembly does not restore the gun blocks. This entry does not claim separate damage coverage for every Neo ammunition type. |
| Create Radar | Smart cannon mount subclass recognition, mounted cannon cleanup, and radar antenna/bearing death cleanup. This is lifecycle compatibility, not a blanket claim about radar targeting or every addon feature. |

A CBCAT fix addon was also present in a historical compatibility test environment. That records the tested combination; it is not a separate full-support claim for the fix addon or all CBC addons.

The public source retains the [AT heavy-cannon damage tests](src/gameTest/java/dev/sablelife/testing/HeavyCannonGameTests.java) and [mounted cannon/radar cleanup tests](src/gameTest/java/dev/sablelife/testing/MountedCleanupGameTests.java). These optional tests require their corresponding addon JARs; the default unit-test run does not exercise those native addon callbacks.

Coverage is limited to the named behavior and inspected addon versions. An ammunition type that replaces the collision/penetration pipeline may need its own adapter even if it shares a CBC projectile base class.

Other mods using standard Minecraft damage or block interaction mechanics may work without additional integration, but compatibility is not guaranteed. Even supported integrations may not cover every weapon, spell, addon, or special damage mechanic.

For addon authors, see the [public body combat API guide](docs/COMBAT_API.md) for server-side damage entry points, coordinate conventions and attack cadence.

## Server Module Configuration

The server's `sablelife-server.toml` provides module switches under `[modules.heartCore]`, `[modules.survivalHeartCore]` and `[modules.redstoneCore]`. Supported Redstone Core modules are enabled by default. Setting a switch to `false` hides that type from the Add catalog and suspends existing modules without deleting their settings. Multiplayer clients follow the server's availability list.

`allowEditHeartCoreUnderSurvivalMode` controls ordinary Heart Core Survival editing separately from the Survival Heart Core. `enableWildEncounters` is the master switch for natural blueprint spawning. Spawn caps, workbench protection, projection limits and Energy costs are server-configurable.

See [module availability and configuration](docs/MODULE_AVAILABILITY.md) for examples, existing-module behavior and runtime reset details.

## Building From Source

Sable Life requires **Java 21**.

On Windows PowerShell:

```powershell
# Restore all compile-time dependencies, including the typed compatibility bridges.
.\scripts\setup-mods.ps1
.\scripts\setup-epicfight.ps1
.\scripts\setup-iron-spells.ps1

# Build the mod.
.\scripts\dev.ps1 -Task build

# Launch the development client.
.\scripts\dev.ps1 -Task runClient

# Run unit tests.
.\scripts\dev.ps1 -Task test

# Run the native GameTest server.
.\scripts\dev.ps1 -Task runGameTestServer
```

The compiled JAR is generated under `build/libs/`.

Epic Fight and Iron's Spells are optional for players, but their JARs are required to compile the typed bridge source. Running the setup scripts does not enable these mods in the default development client. For optional compatibility tests, use the corresponding setup scripts and Gradle properties in `build.gradle`; downloaded dependencies are not bundled into the Sable Life release JAR.

## License

Sable Life is released under the [MIT License](LICENSE).

The NeoForge MDK template material retains its original license in [TEMPLATE_LICENSE.txt](TEMPLATE_LICENSE.txt).

## Disclaimer

Sable Life is an independent mod and is not affiliated with Mojang, Microsoft, Create, Sable, or the authors of compatible third-party mods.

**Development note:** Sable Life’s original code is entirely AI-written and open source. You’re welcome to review it, use it, or modify it to suit your needs.
