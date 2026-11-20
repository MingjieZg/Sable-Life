# Auto Facing aim-offset performance

With 32 simplified active cores and moving targets, the zero-offset steady means were 2.850 versus 2.838 MSPT in phase 0, and 1.403 versus 1.394 MSPT in phase 3. No clear sustained increase appeared in this sample. The candidate had a higher single phase-0 maximum; these runs do not establish a speed improvement.

| Phase / candidate offsets | Before mean / P95 / maximum MSPT | Candidate mean / P95 / maximum MSPT |
|---|---:|---:|
| 0: zero | 2.850 / 3.689 / 4.465 | 2.838 / 3.601 / 11.881 |
| 1: left 0.375 | 2.372 / 3.264 / 4.013 | 1.973 / 2.520 / 2.810 |
| 2: left 0.375, up -0.625 | 1.863 / 2.318 / 3.769 | 1.605 / 1.998 / 3.378 |
| 3: zero | 1.403 / 1.808 / 2.981 | 1.394 / 1.904 / 3.478 |

Before used the actual 0.1.449-fix1 catalog-order JAR; candidate used the actual 0.1.450 aim-offset JAR with manual251, before the later wording and Melee controls. Before had no offset support and kept zero in every phase. This report measures aim-offset behavior, not the later Melee additions.

Each phase lasted 240 ticks: 120 warm-up ticks then 120 samples from the server's full-tick timing ring. Each core had Auto Facing with Collision check Off and Vertical tracking On. Targets were no-AI, no-gravity cows. Fixture pose/velocity maintenance and target movement were included in MSPT. Simplified bodies were held in place while retaining orientation.

Native assembly setup took 380.001 ms before and 320.720 ms for the candidate. This times synchronous fixture assembly, not a full generation tick or blueprint placement.

Environment: Minecraft1.21.1, NeoForge21.1.235, Create6.0.10, Sable2.0.5, Java21, 2GiB heap, same local Windows machine. Runs were serial and other test processes were stopped. One run per JAR; warm-up, GC and scheduling can affect small differences. Large blueprints, complex joints, multiplayer and client FPS were not measured.
