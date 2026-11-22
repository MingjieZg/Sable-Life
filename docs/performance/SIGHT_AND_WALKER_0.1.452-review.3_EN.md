# Sable Life 0.1.452-review.3 Server Performance Report

With Line of Sight enabled, mean MSPT fell from 16.62 to 16.34 with zero-second target memory and rose from 2.95 to 3.99 with three-second memory. The full tick that placed and activated 12 simplified cores rose from 355.83 to 409.69 ms. After the Walker fix, mean MSPT rose from 4.31 to 4.52 during activity and from 2.18 to 2.80 during death cleanup. Each section names the versions it compares.

## Line of sight and blueprint placement

| Active workload | Metric | 0.1.452 | 0.1.452-review.2 |
|---|---|---:|---:|
| Zero-second target memory | Mean / P95 / maximum MSPT | 16.62 / 46.08 / 94.59 | 16.34 / 48.22 / 60.37 |
| Three-second target memory | Mean / P95 / maximum MSPT | 2.95 / 6.26 / 56.24 | 3.99 / 9.82 / 34.23 |

| Placement measurement | Metric | 0.1.452 | 0.1.452-review.2 |
|---|---|---:|---:|
| Placement and activation of 12 cores in one tick | Full tick (ms) | 355.83 | 409.69 |
| Individual native blueprint placement calls | Mean / maximum (ms) | 6.77 / 21.88 | 5.60 / 18.24 |

Each version ran once with 12 single-block cores, 96 Target Sensors and one simulated Survival player. The phases always ran with zero-second memory first and three-second memory second. Each phase used 100 warmup ticks and 300 sampled ticks. Line of Sight stayed enabled, with cover added and removed on the same schedule. The full placement tick includes scene setup, blueprint export, chunk preparation, placement and activation. Individual placement calls were timed separately.

## Walker activity and death cleanup

| Phase | Metric | 0.1.452-review.2 | 0.1.452-review.3 |
|---|---|---:|---:|
| Assembly | Full tick (ms) | 290.45 | 198.40 |
| Assembly | Native assembly call (ms) | 277.93 | 188.38 |
| Activity | Mean / P95 / maximum MSPT | 4.31 / 11.52 / 22.33 | 4.52 / 8.08 / 38.46 |
| Death cleanup | Mean / P95 / maximum MSPT | 2.18 / 3.77 / 48.38 | 2.80 / 4.15 / 59.74 |

Each version ran once with the native six-legged Let it Walk! 0.1.5 demo, which has two active cores and 18 leg segments. After 50 warmup ticks, the run sampled 200 active ticks and 40 death-cleanup ticks. Both versions removed all 18 leg segments. The lethal-hit tick was the cleanup peak in both runs. The leg segments stayed loaded in this performance scenario.

## Test environment and scope

Both comparisons used fresh Linux server processes run in sequence with Minecraft 1.21.1, NeoForge 21.1.235, Create 6.0.10, Sable Photomancy 1.0.1, Java 21 with G1, a 3 GiB heap and four active processors configured for the JVM. The line-of-sight comparison used Sable 2.0.5. The Walker comparison used Sable 2.0.6. MSPT is server milliseconds per tick, and P95 is the 95th percentile. Peaks come from a single run of each version and apply to the workloads shown above.
