With 32 creatures and 256 active Contact Sensors, the two sample windows averaged 3.532 and 2.412 MSPT. Their combined 240 samples averaged 2.972 MSPT, peaking at 4.843 ms. Without sensors, 0.1.442 and 0.1.443 averaged 1.392 and 1.450 MSPT. Warm-up and noise remain visible in this single comparison; other modpacks need their own measurements.

## Sustained activity

Minecraft 1.21.1, NeoForge 21.1.235, Create 6.0.10, Sable 2.0.5, Java 21, maximum heap 2 GiB. Actual JARs were used with other test processes stopped. The scene had 32 single-block creatures; the sensor scene used eight 2×2×2-block sensors per creature, with all five contact categories selected. Initially spawned 32 no-AI/no-gravity cows and 128 no-gravity arrows. Arrows were stationary, with no continuous firing replenishment.

Full tick times came from the server timing ring. Each window used 100 warm-up ticks and 120 samples, in Off/On/On/Off order. Pose maintenance ran every tick to preserve the fixture and is included in these times. The following windows all use 0.1.443 with 256 installed sensors.

| Window | Sensors | Mean MSPT | P95 (ms) | Maximum (ms) |
|---|---|---:|---:|---:|
| 1 | Off | 2.426 | 3.015 | 4.385 |
| 2 | On | 3.532 | 4.153 | 4.843 |
| 3 | On | 2.412 | 3.194 | 4.495 |
| 4 | Off | 0.932 | 1.092 | 1.200 |

A separate base comparison used the same 32 creatures, initial cows and arrows, and zero modules, with 480 samples per version: 0.1.442 averaged 1.392 MSPT and peaked at 11.164 ms; 0.1.443 averaged 1.450 MSPT and peaked at 10.876 ms. Cores without a sensor perform no contact queries.

## Blueprint placement spike and sustained activity

This comparison used Sable 2.0.6 and Photomancy 1.0.1, with the other main dependencies unchanged. The native placer generated and activated 32 single-block blueprint creatures in one tick, with one additional dormant template core. The old blueprint had no sensors; the new one had eight per creature. After placement, 100 warm-up ticks preceded 300 samples. Timing spans server Pre to Post, with pose maintenance after the measurement.

| Metric | 0.1.442, zero sensors | 0.1.443, 256 sensors |
|---|---:|---:|
| Placement tick Pre→Post window (ms) | 403.962 | 446.406 |
| Batch preparation and activation (ms) | 394.155 | 432.202 |
| Mean native placement call (ms) | 2.450 | 3.067 |
| Peak native placement call (ms) | 14.089 | 16.311 |
| Sustained mean MSPT | 1.155 | 2.382 |
| Sustained P95 (ms) | 1.448 | 2.979 |
| Sustained maximum (ms) | 8.020 | 9.522 |

The batch window includes fixture preparation, template export, chunk loading, 32 placements and activation. It does not establish a fixed per-creature or per-module placement cost. The new result includes sensor configuration copying and runtime work; the equal-module-count base comparison is above. Large structures, articulated machines, moving bullet clouds and multiplayer servers require separate tests.

## Delivery file

0.1.443 JAR SHA-256: `df06d22dce822953e8903be69098d9ffe3e8c02cac739d3f34dd0cffde56542e`. Old 0.1.442 comparison JAR SHA-256: `fa32bb889f4af4a6f6349199c03fe630cdd726d4d14193ab277bda9ba41a67db`. Measurements used these actual files without the development production classes.
