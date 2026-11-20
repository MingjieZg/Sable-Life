# Decoration and melee terrain-breaking performance

In a single comparison with 32 simplified creatures, steady-state means with melee breaking Off were 1.619／1.685 MSPT for 444／445. With the new option On, 445 averaged 1.860 MSPT. One simultaneous terrain-processing burst reached **63.630 ms**, exceeding a 50 ms tick. These results do not establish stable full TPS in complex combat builds.

## Active creatures and breaking window

| Scenario | Steady mean MSPT | P95 MSPT | Maximum MSPT | Burst-window mean MSPT | Burst-window maximum ms |
|---|---:|---:|---:|---:|---:|
| 444, melee breaking Off | 1.619 | 2.111 | 2.889 | 4.036 | 15.679 |
| 445, melee breaking Off | 1.685 | 2.193 | 2.439 | 4.395 | 22.358 |
| 444, new switch requested (unsupported) | 1.549 | 1.936 | 2.314 | 3.087 | 12.539 |
| 445, melee breaking On | 1.860 | 2.192 | 23.143 | 6.164 | 63.630 |
| 444, core collision breaking On | 1.942 | 2.312 | 22.496 | 3.034 | 8.381 |
| 445, core collision breaking On | 1.773 | 2.272 | 2.602 | 4.159 | 27.628 |

Each scenario ran once, serially, in its own JVM. There were 32 single-block core creatures, each with one melee module and no entity targets, plus one inactive blueprint template. Each melee box was 3×3×3 with a three-block offset. After 100 ticks, the fixture placed 27 dirt blocks in every attack box, 864 blocks total. The 20-tick burst window includes that placement and subsequent processing, followed by a later 200-tick steady sample. The old build ignores the unsupported switch and retains its Off behavior. On and Off perform different work, so timing alone does not compare algorithmic efficiency.

For the two core-collision rows, the fixture instead placed one tall-grass block at each core contact position, 32 total, with melee breaking Off. At the end, 444 retained all 32 and 445 retained none. The plant protection preset was not applied. Both burst windows include the same placement work and whatever detection, removal, neighbor updates and drops each version actually performed. Individual shape-query and block-removal costs were not isolated.

## Blueprint batch generation

| Scenario | Tick window containing 32 placements ms | Mean placement ms | Maximum placement ms |
|---|---:|---:|---:|
| 444, melee breaking Off | 457.796 | 2.526 | 15.831 |
| 445, melee breaking Off | 368.828 | 2.420 | 11.333 |
| 444, new switch requested (unsupported) | 434.959 | 2.420 | 13.489 |
| 445, melee breaking On | 429.017 | 2.510 | 12.164 |
| 444, core collision breaking On | 395.111 | 2.420 | 11.815 |
| 445, core collision breaking On | 428.894 | 2.411 | 12.115 |

The generation window includes export, chunk preparation, 32 native blueprint placements and activation. It is not the cost of one creature; synchronous batch generation exceeded 50 ms in every scenario. Individual placement measurements exclude the subsequent core activation. These batch spikes are not attributed to the terrain change, and this fixture does not test asynchronous spawning or the actual natural-spawn queue.

## Environment and limits

Minecraft 1.21.1, NeoForge 21.1.235, Sable 2.0.6, Create 6.0.10, Sable Photomancy 1.0.1, Java 21, 2 GiB heap. Same Windows machine, dependencies and scenario, replacing only the actual 444／445 JAR. Let it Walk and optional weapon mods were absent. MSPT measures the ServerTick Pre-to-Post window. Structure poses and velocities were restored after the timing endpoint to keep the workload fixed; that maintenance is excluded.

Each row is one small run with 200 steady ticks. JVM warm-up, garbage collection and scheduling can affect the results; small differences cannot establish a causal code regression. The fixture excludes complex joints, continuously replenished terrain, multiplayer traffic, third-party decorations and client FPS. Work budgets bound scanning and removal, but large areas or fast sweeps can still leave blocks.
