# Independent Melee protection-list performance

With terrain breaking enabled on 32 simplified creatures, steady means were 1.685／1.610 MSPT for 445／446. Each build ran once; small differences cannot establish an improvement or regression. The table also retains terrain-processing and synchronous blueprint-generation peaks.

| JAR | Steady mean MSPT | P95 MSPT | Maximum MSPT | Breaking-window mean MSPT | Breaking-window maximum ms | Tick window with 32 spawns ms | Mean placement ms | Maximum placement ms |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| 0.1.445 | 1.685 | 2.175 | 2.623 | 6.472 | 70.060 | 417.734 | 2.508 | 12.495 |
| 0.1.446 | 1.610 | 2.104 | 2.384 | 5.740 | 59.916 | 427.276 | 2.458 | 10.909 |

Both runs used the same scenario and dependencies, replacing only the actual Sable Life JAR, and ran serially. There were 32 single-block cores with one Melee module each, no entity targets, and one inactive blueprint template. Both the old core list and new independent lists were empty. Body collision breaking and block drops were Off, and melee terrain breaking On, giving both builds the same terrain workload.

Every attack box was 3×3×3 with a three-block start offset. After 100 ticks, the fixture filled all boxes with 864 dirt blocks total. The 20-tick burst window includes placement, breaking and updates; a later 200-tick sample measures steady activity. This does not isolate individual list lookups. MSPT measures ServerTick Pre to Post; fixture pose/velocity maintenance occurs after the timing endpoint and is excluded.

The generation window includes export, chunk preparation, 32 native blueprint placements and activation. It is neither one creature's spawn cost nor the actual natural-spawn queue workload. Individual placement timing excludes subsequent activation. Synchronous generation and simultaneous breaking can still exceed 50 ms, so these results do not imply stable full TPS in all scenarios.

Environment: Minecraft 1.21.1, NeoForge 21.1.235, Sable 2.0.6, Create 6.0.10, Sable Photomancy 1.0.1, Java 21, 2 GiB heap, same Windows machine. Optional weapon mods and Let it Walk were absent. Large first-time legacy-list migrations, complex joints, replenished terrain, multiplayer traffic and client FPS are outside this fixture; functional upgrade evidence remains local.
