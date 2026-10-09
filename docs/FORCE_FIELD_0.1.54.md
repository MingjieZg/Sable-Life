# 0.1.54: target-gated force field

The virtual range pulse (force field) has a new Force field activation property and a target filter. Periodic release preserves existing behavior. When target is in range waits silently until an eligible living target enters the existing spherical radius, then releases at the configured interval while targets remain. Players, non-player creatures, or all may be selected; creative/spectator/dead entities are excluded. Detection uses the same radius and wall visibility test as the actual pulse, so a blocked target does not produce an ineffective visual pulse.

Activation requires BOTH the existing trigger and the additional presence condition:

| Trigger | When target is in range |
|---|---|
| Always | Continuously armed; pulse only when targets are present. |
| Anchor power | Armed while the marked anchor receives power; pulse only when targets are present. |
| Internal wireless | Armed during the selected channel's on phase; pulse only when targets are present. |
| External RX | Armed while the selected external frequency receives a signal; pulse only when targets are present. |

No targets means no pulse particles, sound, damage or attached effects. Exit/re-entry does not reset the attack interval and cannot force rapid pulses. The UI distinguishes armed/waiting from target-present. Existing modules keep Periodic release and All targets defaults; the new settings persist and are copied with module data.

## Pending in-game checks

- [ ] Presence mode + Always stays silent in an empty range, activates on approach and stops when targets leave.
- [ ] Player/creature/all filter changes activation and affected targets consistently; creative players do not trigger it.
- [ ] Walls prevent activation for targets that the pulse cannot reach.
- [ ] Anchor power and both wireless modes require signal AND target presence.
- [ ] Re-entering during cooldown does not bypass the interval.
- [ ] Periodic mode keeps the original repeating visuals and sound even with no target.
- [ ] Attached effects still apply after successful pulse damage; saved/copied settings survive.

Validation: assemble only; client not launched and gameplay not tested.
