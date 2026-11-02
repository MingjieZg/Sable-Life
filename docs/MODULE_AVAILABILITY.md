# Module availability by core

The survival Redstone Core is a controller, not a living construct. Its hard capability list is `RedstoneCorePolicy.TYPES`:

- Signal logic
- Pulse sequence
- Wireless redstone
- Target sensor
- Target alignment
- Auto facing
- Signal turning
- Gyroscope
- Hover anchor

It supports Always, anchor power, internal wireless and external RX triggers. Move Set and Hurt triggers are unavailable. Its server runtime does not dispatch Move Sets, audio, item displays or visual effects. Legacy unsupported modules retain their configuration, but are disabled and cannot execute or be copied on a Redstone Core. They may be selected or deleted. Life Core availability is unchanged.

## Administrator configuration

The server generates `sablelife-server.toml` in its config directory. Both tables contain fixed module IDs with boolean switches; every supported entry defaults to `true`.

```toml
[modules.lifeCore]
melee = true
health_phase = true
sound = true

[modules.redstoneCore]
signal_logic = true
sequence = true
wireless_redstone = true
```

The Life Core table contains all 44 module types. The Redstone Core table contains only its nine supported types. These switches cannot grant unsupported capabilities: adding `health_phase = true` under Redstone Core does not enable it.

- `true`: the type appears in the Add catalog and may run, subject to its normal trigger, permission and Mark requirements.
- `false`: the type disappears from the Add/preset catalog. Existing modules remain stored and display **Disabled by server**. They cannot trigger, be duplicated or accept edits through an already-open module editor. They can be inspected and deleted.
- Restoring `true` preserves the original authored enable state, settings and bindings. An enabled module with a currently active trigger may run again immediately; a module the author disabled stays disabled.
- Multiplayer availability comes from server snapshots. A client's local configuration cannot override a server restriction. Singleplayer uses its integrated server's configuration.

When loaded config values change, each loaded core clears its transient module runtimes on its next tick. This stops persistent signals, audio loops, held visual effects, bindings/freezes and summoned weapons, and restarts permitted modules under the new policy. Runtime counters and in-progress Move Sets also restart; configuration is not rewritten. Arena respawn countdowns pause while Arena is disabled. Already-launched independent projectiles and already-applied vanilla effects are not rolled back.

Add/copy/preset requests, client catalogs, previews, sub-editors and runtime checks use the same policy. Imported core configurations and saved modules may retain disabled types but cannot execute them. Command module operator checks remain required even when its switch is true.

## Verification

`ModuleAvailabilityGameTests` exercises the real server config values and generated client snapshots: separate core tables, true/false catalogs, rejecting forged add/copy/preset requests, an open editor after disabling, clearing an existing logic output, save/load retention and restoration after re-enabling. Its isolated namespace is `sablelife_module_policy_tests`.
