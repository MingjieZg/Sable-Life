# Stress-test results

[中文](STRESS_TESTS.md)

The default natural-spawn cap of 64 limits object counts; it does not guarantee 20 TPS with 64 complex combatants. With 64 original Core/Wheelcog creatures moving and fighting, the measured average was **50.73 MSPT**, above the 50 ms per-tick budget for 20 TPS.

These retained development benchmarks cover relevant implementations subsequently included in 0.1.403. Visual checks of the new effects are not stress benchmarks. Scenarios ran separately; their averages cannot simply be added to predict a modpack.

## Environment and method

Windows 11, Ryzen 7 9800X3D (8 cores / 16 threads), Java 21.0.6; Minecraft 1.21.1 / NeoForge, Create 6.0.10, Sable 2.0.5, and Photomancy 1.0.1 for blueprint tests. Local dedicated server, with 3–8 GiB maximum heaps across suites. MSPT includes total server-tick work. P95/P99 are percentiles; all table timings are milliseconds.

Most steady phases sampled 15–33 seconds. Raw logs, worlds, screenshots and computer paths remain local; only de-identified results are published.

## Autonomous movement and combat

Core: 48 blocks, 11 parts, 79 modules, 600 HP. Wheelcog: 121 blocks, 6 parts, 65 modules, 500 HP. Equal numbers fought in opposing factions, retaining movement, attacks, particles and real damage. Invulnerability/healing was not added to preserve population. Heap: 8 GiB; 1,089 loaded chunks.

| Alive together | Ticks | Mean MSPT | P95 | P99 | Maximum tick | >50 ms |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 2 | 600 | 3.71 | 6.05 | 8.08 | 16.35 | 0 |
| 8 | 600 | 6.11 | 7.91 | 10.14 | 13.53 | 0 |
| 16 | 600 | 10.56 | 13.57 | 18.35 | 23.02 | 0 |
| 32 | 600 | 23.81 | 35.91 | 46.06 | 52.49 | 2 |
| 64, before the first loss | 660 | **50.73** | 72.07 | 81.94 | 93.10 | 261 |

The 64-creature group had 544 parts. Over the full minute, population fell from 64 to 38 and averaged 44.22 MSPT. That lower figure is not the cost of maintaining 64 living combatants.

## Entity hitboxes

64 simplified creatures containing only a core and an iron block, without complex attack modules. Heap: 4 GiB; 625 loaded chunks. Receivers on the same creature fully overlapped. A 16³ receiver describes its extent, not 4,096 filled blocks.

| Scenario | Receivers | Ticks | Mean MSPT | P95 | P99 | Maximum tick |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 64 cores, receivers disabled | 0 | 300 | 2.08 | 2.61 | 3.15 | 5.43 |
| Eight per creature, 1³ | 512 | 300 | 5.47 | 5.76 | 6.46 | 10.21 |
| Eight per creature, 16³ | 512 | 300 | 5.50 | 5.82 | 8.70 | 10.46 |
| 32 per creature, 16³ | 2048 | 600 | 15.14 | 16.73 | 17.70 | 22.66 |
| 2048 large receivers + 64 moving creatures | 2048 | 400 | 16.80 | 18.77 | 20.26 | 23.28 |
| 2048 large receivers + 64 vanilla arrows/second | 2048 | 400 | 16.11 | 17.59 | 24.49 | **217.63** |
| 2048 large receivers + 128 attacking zombies | 2048 | 600 | 24.14 | 26.75 | 30.50 | 39.61 |

Arrows removed 5,120 HP. At each one-second sample all 128 zombies remained alive and targeted receivers; they removed 11,181 HP. Only the arrow scenario had one tick above 50 ms, coinciding with the first autosave cycle. Without a recorded call stack, the entire spike cannot be attributed exclusively to saving or one module.

Large-receiver entity-section misses were found and fixed; these are post-fix reruns. The default remains eight active receivers per creature. 32 was used to stress the configurable server maximum.

## Natural spawning and large blueprints

64 rules used simplified creatures. The default nearby cap stopped at four; raising that limit allowed the server-wide cap of 64 to be reached and held. Cleanup returned tracked records, cores, receivers and structure bodies to zero.

| Phase | Ticks | Final objects / receivers | Mean MSPT | P95 | Maximum tick |
| --- | ---: | ---: | ---: | ---: | ---: |
| Default nearby cap | 400 | 4 / 32 | 0.93 | 1.30 | 6.59 |
| Natural population 4 → 64 | 1190 | 64 / 512 | 2.36 | 3.70 | 7.82 |
| Continuing at the server cap | 400 | 64 / 512 | 3.15 | 3.42 | 7.08 |
| After complete cleanup | 300 | 0 / 0 | 0.52 | 0.63 | 1.34 |

A separate ship blueprint contained 63,993 blocks, 22 parts and bounds of 188×147×43. It had no core, AI or attack workload. With a 3 GiB heap and 1,089 loaded chunks, the placement tick took **1,532.51 ms**. The following 600 stable ticks averaged **4.37 MSPT**, maximum 8.98 ms. Large-placement pauses remain.

One full large-blueprint candidate check/second averaged 1.80 MSPT. Eight checks/second averaged 3.95 MSPT, but 20/400 ticks exceeded 50 ms, maximum 69.92 ms. A low average does not eliminate search spikes.

## Workbench spawn protection

Stationary vanilla world crafting tables create horizontal protected areas, checked against the full blueprint footprint. Structure-carried tables are excluded. Index building is incremental, capped at 64 section probes / 8,192 block reads per tick in total. Completed queries use caches; placements/removals update counts directly.

| Scenario | Ticks | Mean MSPT | P95 | Additional scanning |
| --- | ---: | ---: | ---: | --- |
| Cold index, 625 chunks | 370 | 1.993 | 4.246 | 1,228,800 block reads total |
| Loaded idle baseline | 200 | 0.542 | 0.742 | 0 |
| Eight cached queries/second | 200 | 0.503 | 0.749 | 0 |
| 512 queries/tick, extreme load | 100 | 3.843 | 4.140 | 0 |
| 100 placements + 100 removals/tick | 200 | 1.006 | 1.650 | 0 |

40,000 real changes added no scanning. Cold indexing took about 18.5 seconds, with two ticks above 50 ms, maximum 51.003 ms. Other steady phases in this table stayed below 50 ms. Unfinished indexed areas temporarily reject spawning; protection queries do not force-load new chunks.

## Limits of the results

These short server runs do not establish long-term memory stability, client FPS, multiplayer network performance or every modpack's behavior. 64 spawn slots and 64 complex combatants are different workloads. Keep default caps initially and adjust density for actual creations and server hardware.
