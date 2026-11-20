# Melee debris and drop performance

With 32 simplified creatures, disabled new options measured 2.153 mean MSPT versus 2.057 before. Enabling both debris and item drops measured 3.473 MSPT, with a 222.234 ms maximum during simultaneous block clearing. These options add work under this load; new modules default both Off. This single comparison cannot isolate debris cost from collectible-item cost.

| Scenario | Mean / P95 / max steady MSPT | Clearing burst mean / max ms | 32-spawn tick window ms | Placement mean / max ms |
|---|---:|---:|---:|---:|
| before | 2.057 / 2.659 / 12.371 | 6.704 / 68.201 | 454.090 | 2.898 / 15.565 |
| after-off | 2.153 / 2.837 / 3.303 | 8.378 / 97.010 | 464.000 | 3.045 / 12.535 |
| after-on | 3.473 / 4.366 / 5.504 | 31.279 / 222.234 | 465.218 | 3.752 / 24.447 |

Before used the previously delivered 0.1.450 aim-offset JAR. After used the final 0.1.450 candidate with manual253. Each serial run used 32 one-block active cores, one Melee module per core, Break blocks On, and body collision breaking Off. The enabled case turned on both new switches. At tick100, each attack box received a 3×3×3 dirt volume, 864 blocks total. The 20-tick burst covers this event; steady samples span ticks201–400. Dropped items remain present during the steady window.

The generation tick window includes setup, template export, chunk preparation, 32 native blueprint placements and activation. It exceeds50ms in every case; it is not one blueprint's cost or a full-TPS guarantee. Placement timing excludes later activation.

Environment: Minecraft1.21.1, NeoForge21.1.235, Create6.0.10, Sable2.0.6, Sable Photomancy1.0.1, Java21, 2GiB heap, same Windows machine. No players were connected, so debris packet delivery and client rendering were not measured. Measurements cover ServerTick Pre to Post; fixture pose/velocity maintenance runs after the endpoint and is excluded. One run per case; no maximum-capacity, complex-machine or multiplayer conclusion.
