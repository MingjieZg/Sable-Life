# Let it Walk death cleanup performance comparison

The dual-core Walker retained 18 leg segments after death with 443 and zero with 444. These single local runs showed no clear increase in sustained activity cost. They are small samples, not a full-TPS guarantee for complex servers or larger builds.

## Dual-core Walker

| JAR | Active mean / P95 / max MSPT | Death-window mean / P95 / max MSPT | Legs remaining |
|---|---|---|---|
| 0.1.443 | 1.735 / 2.629 / 11.931 | 2.189 / 3.103 / 31.371 | 18 |
| 0.1.444 | 1.806 / 2.578 / 14.992 | 1.495 / 2.470 / 35.199 | 0 |

One run per JAR: one six-legged demo Walker, 18 leg segments, a main core and an independent core on one segment, with a Contact Sensor on the foot and solid ground support. After 50 warm-up ticks, 200 active ticks were sampled. A lethal hit through the arrow-impact handler then started a 40-tick death window, including attachment capture and batched removal. The old JAR did not perform equivalent leg cleanup, so the death-window mean is not a pure algorithm speed comparison.

## Blueprint generation and multiple active creatures

| JAR | Batch generation tick window ms | Placement mean / max ms | Steady mean / P95 / max MSPT |
|---|---|---|---|
| 0.1.443 | 400.668 | 2.863 / 12.386 | 2.399 / 3.078 / 11.236 |
| 0.1.444 | 427.318 | 3.057 / 16.498 | 2.437 / 2.981 / 11.635 |

Matched native blueprint scene: 32 single-block core creatures with 256 Contact Sensors, plus one dormant template. The batch tick includes export, setup, chunk work, placement and activation; it is not a per-creature generation cost. Each run sampled 300 ticks after a 100-tick warm-up. Pose and velocity resets maintain the load after the timing endpoint and are excluded from the measured window.

Environment: Minecraft 1.21.1, NeoForge 21.1.235, Sable 2.0.6, Let it Walk 0.1.5, Create 6.0.10, Sable Photomancy 1.0.1, Java 21 and a 2 GiB heap. Separate game servers ran serially with matching dependencies and scenes, replacing only Sable Life. MSPT measures ServerTick Pre through Post. This does not cover long combat sessions, the full weapon matrix or client FPS.
