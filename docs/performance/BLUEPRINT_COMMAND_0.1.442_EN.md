With 32 active cores under matching conditions, the measured mean was 1.243 ms/tick on 0.1.440 and 1.170 ms/tick on 0.1.442; P95 was 1.436 and 1.395 ms/tick. This single comparison did not show an increase in sustained cost from the new empty command queue. It does not establish a performance improvement.

[中文](BLUEPRINT_COMMAND_0.1.442.md)

| Measurement | 0.1.440 | 0.1.442 |
| --- | ---: | ---: |
| Mean measured window (ms/tick) | 1.243157 | 1.170029 |
| Median (ms/tick) | 1.090599 | 1.061599 |
| P95 (ms/tick) | 1.436000 | 1.395099 |
| Maximum (ms/tick) | 21.933999 | 20.989800 |
| Mean of 32 native placement calls (ms) | 2.428631 | 2.505850 |
| Maximum single placement call (ms) | 16.016399 | 16.257001 |
| Total scene-setup call (ms) | 356.332200 | 403.997900 |

Environment: Minecraft 1.21.1, NeoForge 21.1.235, Java 21, a 2 GiB heap, Create 6.0.10, Sable 2.0.6 and Photomancy 1.0.1. Each version ran in a separate server with the same scene and settings.

The scene contains 32 active, single-block Heart Cores without modules. It warms up for 100 ticks and samples 300 ticks. Timing covers the server Pre-to-Post event window. Test-body velocity and pose are reset after each measurement; that reset cost is excluded. This checks sustained cost while the new command queue is empty, not free flight, articulated motion or a heavy module workload.

Generation timings in the table use the native placement entry point shared by both versions. They do not include all file-reading and space-validation costs of the new command. The placement-call maximum and setup-call time are not full server-tick generation peaks.

Separately, a native regression on Sable 2.0.5 measured eight two-block blueprint commands: mean admission-call time 1.668612 ms; mean placement-step time 0.628300 ms; maximum placement-step time 0.872400 ms. That case also checks the eight-job limit, rejection of a ninth job and a command-block source. These are call measurements within a small functional regression, not an independent large stress test.

There is one comparison run per version. Maximum-size blueprints, sustained rapid command-block spawning, extreme jointed builds and client frame rates were not tested. Configuration limits are not recommended workloads.
