# Wireless redstone output-buffer performance

In one comparison with 32 simplified creatures and 256 Wireless redstone modules, steady means were 1.535, 1.590 and 1.613 MSPT for 446 Off, 447 Off and 447 with a 0.2-second buffer. No clear sustained increase appeared. These small samples cannot attribute minor differences to buffer code or establish complex-server capacity.

| JAR / buffer | Steady mean MSPT | P95 MSPT | Maximum MSPT | Tick window with 32 spawns ms | Mean placement ms | Maximum placement ms |
|---|---:|---:|---:|---:|---:|---:|
| 446 / Off | 1.535 | 1.885 | 14.940 | 451.936 | 3.478 | 25.318 |
| 447 / Off | 1.590 | 1.904 | 17.289 | 454.103 | 2.848 | 14.246 |
| 447 / 0.2 s | 1.613 | 1.900 | 2.485 | 435.552 | 2.884 | 13.452 |

Each scenario ran once, serially in its own JVM on the same machine, replacing only the actual Sable Life JAR and buffer duration. There were 32 single-block cores with eight Wireless redstone modules each, plus one inactive blueprint template. Each module followed an internal input channel and published to another local channel and a marked face. All eight modules on a core shared that face. External frequencies and additional receiving machinery were absent.

Input alternated ten ticks On and ten ticks Off at strength7. The enabled buffer lasted four ticks, or0.2seconds, shorter than the Off gap. After200ticks, a200-tick steady window included input publication, actual core/module execution, neighbor notifications and native physics. MSPT measures ServerTick Pre to Post. Fixture pose and velocity maintenance occurred after the timing endpoint and was excluded.

The generation window includes export, chunk preparation,32native blueprint placements and activation, rather than one creature's cost. All synchronous batch windows exceeded50ms; they are not a stable full-TPS guarantee. Individual placement timing excludes subsequent activation.

Environment: Minecraft1.21.1, NeoForge21.1.235, Sable2.0.6, Create6.0.10, Sable Photomancy1.0.1, Java21,2GiB heap, same Windows machine. Optional weapons and Let it Walk were absent. Each row is one run; JVM warm-up, GC and scheduling can affect it. Complex receiving blocks, large face counts, multiplayer load and client FPS are outside this fixture. External-frequency consistency and save-upgrade functional evidence remain local.
