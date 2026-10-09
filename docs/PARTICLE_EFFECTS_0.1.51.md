# 0.1.51: Target alignment and decorative particles

Navigation is now displayed as **Target alignment / 目标对准**. Horizontal and vertical correction remain independent outputs of one module. Alignment does not directly rotate the machine: wire its outputs to the corresponding actuators.

The new virtual **Particle effects / 粒子特效** module uses the same two-point, same-body cuboid marking and range preview as Melee. Its particle chooser has searchable available particles on the left, selected particles on the right, and add/remove buttons. Up to 16 distinct simple particle types can be selected, including supported types registered by other mods. Block/item/color particles needing extra configuration are excluded. Particle IDs appear in tooltips and can be searched.

Properties, in order: selected particles, emission interval (0.05–10 seconds), particles per type (1–32), motion trail, only while moving, drift speed (0–0.5). Trail is enabled by default. The runtime interpolates the marked volume's translation and rotation between emissions. Emission is capped at 256 particles per module per emission, shared evenly between selected types. Reloads, inactive triggers and teleports clear motion history. Particles have their native Minecraft lifetimes and behavior; this version does not override those lifetimes.

The module supports the existing always/anchor/internal wireless/external wireless triggers. It emits decoration only: no damage, potion effects or signal output. Configuration and selection are saved with the core and included in data copier snapshots.

For hand exhaust: mark a small region on the hand, select `minecraft:smoke` or `minecraft:flame`, enable motion trail and only while moving, and start with a 0.05–0.10 second interval and 4 particles per type. Larger regions scatter particles throughout that volume.

## Pending in-game checks

- [ ] Navigation name displays correctly in both languages; existing navigation settings survive.
- [ ] Particle marking waits for the second point, rejects a different body, and previews the complete moving region.
- [ ] Search, paging, add/remove, multiple particle selection and window resizing work; attack effect selectors still expose duration/level.
- [ ] Translation and rotation leave a continuous trail; stationary hands stop emitting with only-while-moving enabled.
- [ ] All trigger modes start/stop particle emission; disabling, releasing, destroying or unloading the core stops new emission.
- [ ] Particles do not damage entities or apply potion effects.
- [ ] Rejoining the world and data copying preserve particle selection and properties.
- [ ] Multiple particle types remain visible under the shared emission cap.

Validation: Gradle assemble only. No client launch or gameplay tests were requested for this batch.
