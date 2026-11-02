# Changelog

This changelog highlights the major development milestones of Sable Life from its earliest documented prototypes to the current public release.

> **History note:** Sable Life was developed through a long private development cycle before its public release. The public Git history was intentionally reset at **0.1.313** during repository cleanup, so the entries below are a curated reconstruction of the project's development timeline rather than a complete commit-by-commit record.

## 0.1.315 — Open-ended Create and Sable dependency ranges

- Removed the upper version caps for Create and Sable while retaining minimums of Create 6.0.10 and Sable 2.0.5.
- Kept the Minecraft 1.21.1 requirement and pinned development dependencies unchanged.
- Clarified that higher dependency versions are allowed to load, with compatibility issues reported against exact versions; they are not all claimed as tested.

## 0.1.314 — Photomancy interface compatibility

- Arena automatic respawn now checks the required Photomancy blueprint API and trusted-template NBT hook instead of requiring version exactly 1.0.1.
- Compatible newer versions can enable the integration; missing/incompatible APIs disable it with a server-log explanation. Optional diagnostic hooks are checked separately.
- Documented the optional respawn dependency and its compatibility limits. Create/Sable dependency ranges are unchanged.
- Added interface-contract regression tests and retained existing native Arena coverage.

## Development Timeline

### 0.1.21–0.1.30 — Early Life Prototype

The earliest documented Sable Life builds already established the core idea that the **physical structure itself is the creature**.

Early playtests covered:

- Heart naming and Boss bar support.
- Physical-body health and damage.
- Arrow attack modules.
- Area pulse attacks.
- Target sensors and line-of-sight checks.
- Redstone-driven attacks.
- Detached-part behavior and shutdown rules.
- Early module configuration and in-world validation.

These builds formed the first playable foundation for turning a Sable structure into a Life.

### 0.1.31–0.1.40 — Heart Unification and Navigation

The project moved away from separate physical module blocks and toward a unified Heart-controlled architecture.

Key milestones included:

- **0.1.31:** Importing legacy physical modules into virtual Heart modules.
- **0.1.32:** Moving all active module configuration into the Heart.
- **0.1.33–0.1.34:** Expanded virtual projectile systems, including guns, arrows, mortars, explosions, trajectories, sounds, and projectile previews.
- **0.1.35:** Data Copier support.
- **0.1.36–0.1.39:** Battle music development.
- **0.1.40:** Target Navigation, allowing a Life to produce directional redstone signals toward tracked targets.

This period established the Heart as the central authoring system rather than a collection of independent module blocks.

### 0.1.41–0.1.59 — Wireless Control, Protection, and Arena Tools

The Heart system expanded into a more complete control and combat framework.

Development in this period included:

- Wireless emitters and internal wireless control.
- Core wireless channels.
- UI density improvements and paging.
- Projectile sound-folder support.
- Heart/Copycat compatibility work.
- Attack and particle effects.
- Navigation refinements.
- **0.1.52:** Physical-body block and explosion protection.
- **0.1.53:** Per-part immunity.
- **0.1.54:** Force Field mechanics.
- Triggered sounds.
- Arena Ground Strike tools.
- Projectile size controls.
- Ground Strike UI improvements.
- Arena coordinate systems.

By this point, a Life could be protected as a physical structure while still receiving combat damage through Sable Life's own systems.

### 0.1.61–0.1.80 — Combat Expansion and Health Phases

Combat authoring became substantially more flexible.

Major additions included:

- Vanilla sound importing.
- Armor and Arena return behavior.
- Photomancy integration.
- Direct-hit flame attacks.
- Laser attacks.
- Arrow projectile customization and residue.
- Pulse sequences.
- **0.1.73:** Health Phase signals based on remaining health percentage.
- Health-phase-driven pulse behavior.
- Attraction mechanics.
- Sequence-driven wireless output.
- Projectile color and sound controls.
- Mortar particle effects.
- Peaceful-mode blast handling.

This period introduced the foundations for multi-phase boss behavior.

### 0.1.81–0.1.105 — Advanced Targeting and Attack Logic

Sable Life gained more complex combat coordination and targeting systems.

Development included:

- Dispatch Redstone.
- Quake attacks.
- Marker-based redstone.
- Random Strike.
- Laser tracking.
- Multi-target attacks.
- Combat UI refinements.
- Particle-based quake presentation.
- Homing salvos.
- Homing deployment.
- Target categories.
- Homing formations, shapes, blasts, and rotation.
- Handheld projectile support.
- Projectile aiming.
- Core regeneration.
- Internal repeat behavior.

The project was increasingly capable of coordinating large custom attacks without relying on a traditional Minecraft mob AI.

### 0.1.108–0.1.138 — Move Sets, Melee, and Adaptive Defense

This was a major shift toward fully authored boss behavior.

Key milestones included:

- **0.1.108:** Introduction of the dedicated **Move Set** system.
  - Weighted moves.
  - Cooldowns and rest periods.
  - Target conditions.
  - Timed action windows.
  - Multiple overlapping module actions.
- Death presentation and death effects.
- Configurable attack start distance.
- Hurt-triggered behavior.
- Virtual Commands.
- Linear Movement.
- Expanded particle patterns.
- Melee system review and refinements.
- Sound timelines.
- Melee offsets and hit limits.
- **0.1.136:** Global high-DPS Adaptive Defense.
  - Configurable DPS threshold.
  - Maximum damage reduction.
  - Detection window.
  - Shared protection across body parts and attackers.
- Manual and authoring documentation improvements.

Move Sets became the primary tool for combining independent modules into larger attacks and boss mechanics.

### 0.1.150–0.1.159 — Architecture Cleanup and Runtime Safety

A major cleanup pass removed obsolete systems and strengthened the current architecture.

Highlights included:

- Input and UI audits.
- Legacy-size auditing.
- **0.1.155:** Complete removal of the old seven physical module block types.
  - Heart and Redstone Core remained.
  - Current virtual modules remained.
  - Legacy entity-module registrations and resources were removed.
- Removal of obsolete Move Dispatcher infrastructure where no longer required.
- **0.1.159:** Optional Life Core chunk loading.
  - Per-Life loading ownership.
  - Chunk budgets.
  - Lifecycle-aware cleanup.
  - Death-delay limits.

This period reduced legacy code and moved the project more firmly onto the modern Heart/module architecture.

### 0.1.194–0.1.216 — Audit, Security, and Combat Integrations

The project entered a more formal validation and compatibility phase.

#### 0.1.194

- Established an audited source baseline.

#### 0.1.195

- Hardened authoring permissions and runtime safety.
- Added ownership, core, and signal indexes.
- Added bounded cleanup, visual, projectile, and collision workloads.
- Improved synchronization and multiplayer safety.

#### 0.1.197–0.1.200 — Epic Fight

- Added optional Epic Fight physical-body melee integration.
- Used Epic Fight's native attack geometry against actual Life body blocks.
- Avoided proxy LivingEntity targets.
- Added shared hit limits and native combat behavior.
- Expanded guard/parry and protected-body combat.
- **0.1.200** became a stable checkpoint for this compatibility work.

#### 0.1.202–0.1.206 — Iron's Spells 'n Spellbooks

- Added the first physical-body damage interfaces for Iron's Spells.
- Supported projectile, ray, area, and selected native spell damage paths.
- Added bounded body sphere queries and reusable combat bridge infrastructure.
- Expanded support across additional physical spell mechanics.

#### 0.1.206 — Arena Respawn

- Added optional Arena blueprint respawn using the Sable schematic API.
- Added persistent respawn tasks and incremental reconstruction.
- Added respawn countdown/progress displays.
- Preserved Life ownership, module configuration, wireless state, and activation behavior.

#### 0.1.207–0.1.214

- Continued Arena reconstruction, ownership, cleanup, capture-budget, placement, and respawn-display improvements.
- **0.1.214:** Separated ground respawn display positioning from the actual structure anchor and clarified large-structure placement behavior.

### 0.1.218–0.1.243 — Stable Lifecycle and Authoring Expansion

The project continued refining lifecycle behavior and player-facing authoring systems.

Notable work across this period included:

- Whole-Life removal and cleanup improvements.
- Arena respawn lifecycle fixes.
- Owned decoration cleanup.
- Interaction protection.
- Signal and sound improvements.
- Loot UI improvements.
- Multi-row phase and defense rules.
- Hover behavior.
- Hit-counter mechanics.
- Self-health adjustment rules.

#### 0.1.243 — Stable Checkpoint

- Promoted a new stable version.
- Added configurable hover.
- Added hit counters.
- Added self-health adjustment.
- Consolidated post-0.1.218 lifecycle and control improvements.

### 0.1.244–0.1.253 — Combat Presentation and Control Polish

The next development series focused heavily on visual feedback and attack presentation.

Examples included:

- Continued control-system and attack-preview refinement.
- Spatial-wave preview improvements.
- Marked-area visualization.
- Contact-mode preview geometry.
- Quake presentation improvements.
- **0.1.249:** Spatial-wave previews began showing existing marked geometry correctly.
- **0.1.253:** Quake Ring rendering was redistributed around the full circumference so display limits no longer exhausted one side of the ring.

### 0.1.273 — Travel and Facing Integration

A major movement integration milestone combined:

- Travel movement.
- Auto Facing.
- Signal Turn.

Because these systems directly affect moving Sable structures, this version became an observation checkpoint for movement, turning, distance tracking, gravity, gyro behavior, and Move Set interaction.

### 0.1.278 — Damage API and Combat Expansion Milestone

The 0.1.278 development snapshot included:

- Expanded particle areas.
- Module search and copy improvements.
- Slash activation and sustained-flight sounds.
- Multi-target area release.
- Continued movement and combat work.
- A formal acceptance target for the public physical-body damage API.

This became an important checkpoint for later third-party combat compatibility work.

### 0.1.279–0.1.307 — Modern Combat and Authoring Development

Development continued rapidly across the 0.1.279–0.1.307 series.

Major themes included:

- Physical-body damage bridge refinement.
- Impact-area and persistent-area attack systems.
- Blueprint/reference remapping.
- Attack and movement behavior refinements.
- Compatibility hardening.
- Visual and preview improvements.
- Summoned Weapon development.
- Continued Move Set integration and authoring improvements.

#### 0.1.284

- Fixed Sable schematic remapping for persistent impact-area references.
- Preserved attack-to-impact-field references when a Life was exported and placed again.
- Covered multiple attack families using the same remapping system.

### 0.1.308 — Summoned Weapon Preview Alignment

- Reworked Summoned Weapon previews to use the same native ItemDisplay rendering path as the actual spawned weapon.
- Corrected orientation differences between preview and real weapon models.
- Preserved shared positioning, formation, and target-tracking calculations.
- Continued polishing Summoned Weapon configuration and presentation.

### 0.1.313 — Public Source Milestone

Version 0.1.313 became the source snapshot used for the public repository.

Major changes included:

- Added **Summoned Weapon** to the Move Set action system.
- Improved the 11 bundled Sable Life particle types for Iris/shader environments.
- Preserved the existing non-Iris rendering path when shaders are disabled.
- Added a shader-compatible world-projected respawn label/progress display path.
- Verified behavior with Iris, Sodium, and Bliss in an isolated test environment.
- Finalized the project branding/icon used for the public repository.

A remaining known visual limitation was recorded for some Bliss cloud/particle interactions.

## Public Repository Reset

Before the project was made public, the repository was cleaned to remove internal development records, local-machine information, historical development JARs, and other material that was not appropriate for a public source repository.

The public Git history therefore starts at the clean **0.1.313** source snapshot rather than exposing the complete private development commit chain.

This changelog preserves the major technical evolution of the project without restoring those internal development records.

## Going Forward

Future public development can be recorded normally through Git commits and new changelog entries.

Public release entries should focus on:

- New player-facing features.
- Major technical changes.
- Compatibility additions.
- Important fixes.
- Breaking changes or migration notes.
- Stable release milestones.

Minor internal experiments and temporary development notes do not need to be preserved here.

