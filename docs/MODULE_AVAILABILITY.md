# Module availability by core

The server's `sablelife-server.toml` controls module availability. Clients follow server snapshots.

- **Heart Core:** the full module catalog, enabled by default.
- **Survival Heart Core:** its supported catalog, enabled by default. Command, Limb Loot, Arena and Blueprint Spawner remain unavailable. Energy costs and restrictions still apply. Buff properties are unavailable.
- **Redstone Core:** Move Set, Signal Logic, Pulse Sequence, Wireless Redstone, Target Sensor, Target Alignment, Auto Facing, Signal Turning, Gyroscope, Hover Anchor, Linear Movement, Visual Effects, Distortion Trail, Particle Effects, Item Display, Trigger Sound and Block Interaction. All supported types default to enabled.

Redstone Core Linear Movement uses collision-checked Travel; Teleport and Force Movement are locked. Its Move Set always ignores target and health conditions while retaining priority, weight, timing and repeat rules. Switches cannot grant capabilities outside a core's catalog.

## Configuration

```toml
# Ordinary Heart Core editing in Survival; independent of the Survival Heart Core.
allowEditHeartCoreUnderSurvivalMode = false

# Natural blueprint spawning master switch.
enableWildEncounters = true

[modules.heartCore]
melee = true
health_phase = true
sound = true

[modules.survivalHeartCore]
melee = true
armor = true

[modules.redstoneCore]
move_set = true
linear_movement = true
wireless_redstone = true
visual_shockwave = true
block_interaction = true
```

These are examples, not the complete generated config. Ordinary Heart Core Survival editing includes loot, experience and terrain destruction; enable it when that behavior is intended. Command authoring and server blueprint selection retain their operator requirements.

Energy settings retain the internal `[survivalTokens]` table and `Token` key names for configuration compatibility. The UI calls this resource **Energy**. `depositValues` contains all fuel entries as `item_id=value`; there is no separate primary copper entry. Module multipliers are under `[survivalTokens.moduleMultipliers]`.

## Disabling and restoring

- `false` removes a type from Add/preset catalogs and suspends existing instances without deleting settings. Instances can be inspected and deleted; forged add/copy/edit requests are rejected.
- Restoring `true` preserves saved enable states and bindings. A permitted enabled module with an active trigger may run immediately.
- Loaded config changes clear transient runtimes on the next core tick. Persistent signals, loops, held control effects and summoned weapons stop. Allowed modules restart; in-progress Move Sets and counters restart.
- Independent projectiles already launched and vanilla effects already applied are not rolled back. Arena countdowns pause while Arena is disabled.

`enableWildEncounters = false` stops automatic/test spawning and suspends encounter lifetime cleanup while retaining rules and tracked records. It does not delete spawned objects or stop an independently active creature's AI.
