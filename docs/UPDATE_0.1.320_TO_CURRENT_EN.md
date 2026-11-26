# Updates from 0.1.320 to the current build

## Unreleased

### Core performance diagnostics

- Operators and the server console can run `/sablelife profile start [seconds]` to sample briefly, for 15 seconds by default or any duration from 1 to 60 seconds. Use `/sablelife profile report cost` for core tick times or `/sablelife profile report activity` for recorded operation rates. `/sablelife profile stop` ends the sample early.
- Timing measures server-side core ticks. Activity tracks Target Sensor scans, Block Interaction searches and successful interactions, homing checks and deployments, and Move Set selection passes and starts.
- Reports show core names, dimensions, world coordinates, and peak tick locations. `/sablelife profile locate <core UUID>` resolves a loaded core's current position and displays the last recorded position for unloaded cores. The chat teleport button fills in a command to go two blocks above that position for the operator to review and run. The report stays in memory for the current server session. Entry 140.

### Survival Heart Core liquid fuel

- Refuel has a narrow 40,000 mB tank aligned with the container slots on the right. It holds one registered fluid at a time. The gauge has a mark every 5,000 mB and a longer mark every 10,000 mB. Put a filled container in the input slot marked with a lava bucket and collect the resulting container from the output slot, or right-click the tank with a filled container to add fluid directly. Left-click the tank with an empty bucket to withdraw 1,000 mB when it holds enough of a fluid that can be bucketed. The tank tooltip shows the right-click and left-click instructions on separate lines. Click the small dump icon below the tank to show a check mark. Click again to empty the tank if its contents still match the first click.
- Compatible pipes can insert fluid through the input-only NeoForge fluid capability. The tank contents save with the core in the world. Cores placed from blueprints start with an empty tank.
- Set the Energy value per 1,000 mB in `survivalTokens.fluidDepositValues` using fluid IDs or tags prefixed with `#`. The default `minecraft:lava=60` gives 60 Energy per bucket of lava. Manual Refuel quotes the amount that can be converted, including partial buckets, and settles it with the existing two-click confirmation.
- Auto Refuel can convert one full 1,000 mB portion of priced fluid at each eight-tick interval and also processes solid fuels. It leaves amounts below 1,000 mB and unpriced fluids in the tank. Entry 136.

### Target Alignment detection inner radius

- Set Detection inner radius from 0 to 31 blocks, below Radius. It defaults to 0, so there is no minimum distance. Target Alignment measures from the marked block and excludes targets closer than the inner radius from its own search, a linked Target Sensor, and Move Set. Preview shows the outer sphere and adds an inner sphere when the setting is above 0. Explicit Command Wand targets and destinations still bypass the distance limits and follow the module's trigger and line-of-sight settings. Entry 137, manual revision 265.

### Block Interaction range

- Set each X, Y, and Z side of the interaction box independently from 1 to 30 blocks in steps of 1. Each defaults to 1. An existing single side length is copied to all three axes. World and Structure modes each simulate one empty-hand right-click on the nearest supported block to the reference point. Supported targets are vanilla wooden doors and trapdoors, fence gates, buttons, and levers. Manual revision 266.

### Priority Targets

- Open Priority Targets under the core’s Target Whitelist & Factions screen. Both the priority list and the mob whitelist accept individual mob types or all mobs from a mod. Each list holds up to 256 entries. The passive and neutral groups remain available in the mob whitelist.
- Automatic targeting applies eligibility, whitelist, and faction rules first. Eligible priority mobs come first when acquiring a new target. When none qualify, ordinary selection continues. Adding priority entries leaves an existing valid lock in place.
- Lock only listed targets defaults to Off in Target Sensor and Target Alignment. Turn it on to select eligible mobs from the priority list only. Choose a target mode that can select mobs. An empty list yields no target. Target Alignment still accepts explicit Command Wand targets. Entry 138.

### Hovering homing projectiles

- After reaching its hover point and completing its charge, each projectile acquires automatic targets from that point. Tracking range continues to use the same fixed point. Spread targets prefers different eligible targets within a salvo. Explicit targets from Command Wand and Move Set follow their existing rules. Manual revision 267.

### Square arena boundary

- Set Arena shape to Circle or Square. A square aligns with the world's X and Z axes. Arena half-side measures from the marked center to an edge. Preview, boss boundary checks, inherited stages, summon placement, and summon returns use the selected shape. The player warning range remains a separate circle. Existing arenas retain Circle. Record the first-stage arena again after changing its shape if future respawns should use the new shape. Entry 139, manual revision 267.

### Stability fixes

- The Survival Heart Core tank can be emptied once the confirmation check mark is visible. A change to the tank contents requires a fresh confirmation.
- Circular and square arenas check the configured blueprint spawn point, including offsets. Rotated blueprints still align to that point when using Bottom Center.
- A blueprint awaiting faction binding can retain its placed, dormant core during an automated spawn or respawn. Binding or skipping lets the same core finish activation, including after a restart.
- Redstone Cores have a dedicated Priority Targets editor. With Lock only listed targets enabled, an empty list prompts the player to add targets.
- Forward Target Sensors detect targets throughout short, wide cones, including when rotated. Linked Target Alignment and Move Sets can use the same target.
- Automatic hovering projectiles reuse deployment sight checks for matching hover positions. The configured projectile count still applies.
- Block Interaction modules on the same core share a per-tick scan budget and queue pending interactions. Each interaction checks for the nearest supported block when it runs.

## 0.1.458

- **Blueprint factions on first placement**. The pending entry shows the source faction's name. When there is no usable local faction, the setup opens on the Create tab with the source settings filled in. Players can confirm creation and binding or skip the faction. Manual revision 264.

- **Wireless Redstone release ramp**. Set Release ramp duration in Ramp mode to lower the current output gradually to zero when the trigger ends. A trigger during the fall continues toward the configured end strength from the current output. A fresh trigger starts from the configured start. It defaults to 0 seconds, which keeps the original Output buffer behavior. A positive duration takes over release in Ramp mode and retains the saved buffer setting. Entry 135, manual revision 263.

## 0.1.457

- **Honeycomb Shield**. Whole hexagonal tiles appear outward from the center of the marked face, forming a flat, roughly circular honeycomb. The shield holds while triggered and closes inward when the trigger stops. Increasing size adds tiles. Hexagon sides are normally 0.5 world blocks and gradually grow above size 3. Set its color and choose whether it follows the marked part or stays at its release position. Pattern animation speed controls both opening and closing. Preview duration applies to the manual preview. Entry 134, manual revision 262.

## 0.1.456

Adds four features to 0.1.452, entries 130–133. See the [release notes](releases/0.1.456_EN.md).

- **Melee knockback control**. At zero Extra knockback, you can turn off Vanilla hurt knockback to deal melee damage without knockback from the module. A positive Extra knockback value applies both kinds of knockback. Returning to zero restores the saved setting. Vanilla hurt knockback is checked by default and applies the small vanilla hurt knockback. Manual revision 259.

- **Melee shield disabling**. Melee can apply the vanilla five-second shield cooldown when a player blocks the hit. An optional shield-break sound plays at the defender for nearby players when the shield is disabled. The defender hears the vanilla cue. The setting defaults to Off for existing Melee modules.

- **Entity Pass-Through test build**. This Function module lets players and mobs pass through one marked substructure or the whole connected creature while triggered. It supports always-on, signal and Move Set triggers. Structure physics and attack hitboxes retain their usual behavior.

- **0.1.452-review.4** updates LittleTiles client mesh recovery. It lets pending rebuilds finish and reuses cached model geometry when restoring vertex buffers.

- **Blueprint faction mapping**. Photomancy blueprints carry faction settings and direct ally references. After placing a blueprint in another world, use the core’s faction tab to bind a local faction, create one, or skip the assignment. Confirmed player mappings are saved in the destination world, and local factions confirm alliances. [Candidate notes](releases/0.1.452_EN.md)

- The **0.1.452 review candidates** improve recovery and compatibility. `review.1` retains saved blueprint jobs when quotas decrease and releases ownership after confirming permanent removal. `review.2` fixes Move Set ordering and target inheritance, weapon previews, concurrent large Item Displays, editor drafts and Data Copier archive restore. `review.3` keeps pending cleanup for Let it Walk leg segments when their chunks unload. [Local candidate notes](releases/0.1.452_EN.md)

- **0.1.452 stability pass**: Weapons check visibility at the muzzle, previews read existing state and inherited targets keep the original expiry. Compatible clients receive large Item Display snapshots in parts and original models stay in saves. The Data Copier verifies saved data, applies a 64 MiB account quota and supports operator status, archive and restore commands. Optional integration contracts and editor draft handling were tightened. [Local candidate notes](releases/0.1.452_EN.md)

- Target Sensors check a player’s chest and shoulders from the marking. Tracking lasers aim at the upper chest and adjust for stance. Target Alignment and Move Set can use a linked Target Sensor. With Line of Sight enabled, sensors, alignment, move conditions, and independently aimed weapons can retain the last visible target for 0–60 seconds, defaulting to zero. A linked Target Sensor supplies its own timer.

- When Photomancy places a blueprint, the Heart Core loads its modules and marks first and then restores each Item Display’s original model automatically. The tool and command both accept existing .nbt files.

- Sable Life logs the exception type and full stack trace when the official Photomancy command fails to load a blueprint file.

- Data Copiers can copy and paste core configurations with nested model items, including grouped models from Yuushya Modelling.

- Data Copier clipboards live in `data/sablelife_clipboards` in the world save. Inventory sync carries references and usage. Identical clipboards from the same originating account share a file. The active world quota is 2 GiB. Existing disk references keep working, and legacy item-stored clipboards can be copied again.

- The server setting `allowSurvivalHeartCoreTerrainBreaking` defaults to On. Off disables terrain damage from Survival Heart Core melee, lasers, and blasts while preserving attacks and module settings.

- The Data Copier now accepts up to 2,000,000 bytes of compressed configuration, up from 1,000,000, to accommodate larger item data in modules such as Item Display. The item tooltip shows the stored size and the limit, starting at zero when empty. The size-limit message also shows the limit.

- **0.1.450 Melee breaking controls**: Grouped at the end of properties; independent flying-debris and block-drop switches, plus inventory samples for the protected list. Existing drop preferences migrate compatibly.
- Optional integrations check dependency versions and the interfaces they use. LittleTiles, weapon add-ons, Walker, claim protection and Toolgun accept compatible newer releases. Epic Fight and Iron’s Spells also verify native class bytecode. Iron’s Spells 3.16.4 is supported.

- Auto Facing adds decimal local left/right and up/down aim-origin offsets in blocks, defaulting to 0, with positive values for left/up and a 0.1-block wheel step. Vertical offset follows Vertical tracking. Collision check defaults Off for new modules while saved values, marks and sensor bindings remain intact. Wireless Redstone Output buffer now scrolls by 1 second while retaining typed decimal precision. Manual253.

- Fixed Signal Contact Sensor appearing at the end of the Add catalog. The All list groups purple signals, red weapons and yellow functions; contact detection sits beside Target Sensor with the same order in filtered categories. Personal presets retain their custom order. This changes only client catalog presentation, preserving capability bits, saves and execution order.

- Russian localization from Bill-Fasber (PR #5), with maintainer terminology corrections and translations for current modules/settings. The language picker discovers available translations, marks the active language, fits the window and paginates long lists. Russian keys, formatting and manual references join the existing validators. Manual250.

- Core Remote now defaults allowCoreRemoteWithUnsupportedClaims to true for new configurations. Existing true/false values remain unchanged; use false to reject unavailable claim integrations. Supported claim permissions, failed checks and other core access rules are unchanged. Manual249.

- Wireless redstone adds Output buffer, Off at 0 seconds. Final output retains its last nonzero strength after falling to zero; short gaps can merge. Works with Hold, Pulse, Ramp and inverted output at all destinations. Disable/lifecycle cleanup clears buffers; old data retains its behavior. Manual 248.

- Melee terrain breaking now has an independent protected list per module. An empty list permits breakable terrain. Enabled 445 modules copy their previous core list once, then save it independently; edits do not change the core or other melee lists. Manual revision 247.

- Core collision breaking now detects outline-only decorations such as grass and flowers, without treating them as solid support. The optional surface protection preset adds common plants and flower/sapling/leaf tags; applying it merges entries while old custom lists remain unchanged.
- Melee gains Break blocks, Off by default: clears loaded terrain in its attack box and sweep, using independent protections and drop settings independently of body collision breaking, delay and floor protection. Other Sable structures remain intact; Survival cores pay the melee rate per successfully removed block. Older modules stay Off. Manual revision 246.

- Removed Let it Walk relationship notes from the in-game manual at the author's request. Verified cleanup behavior and independent damage ownership remain unchanged. Manual revision 245.

- Fixed Let it Walk! 0.1.5 leg segments remaining after the main creature dies. Death jobs remember the Walker's exact segment IDs and remove them in bounded batches after the main body. Attached independent cores are retired too; defeating a foot core alone does not remove the main body. Normal release preserves legs, and a foot reclaimed under a new creature ID during the delay is protected. Pending attachment cleanup survives world saves and restarts.

- Added the independent Signal Contact Sensor: check terrain, other Sable structures, players, mobs and projectiles; configure Start distance, local XYZ size and angles. Supports Move Set windows, Hold and timed contact pulses, excluding own connected parts and projectiles. Bilingual manual revision 243.

- Added an independent administrator blueprint command: `/sablelife blueprint spawn <name> <true|false> [x y z]`, with name completion, default surface placement and optional exact coordinates. Its queue and failure cleanup are separate from module and Arena records.
- Added `/sablelife help`; bare `/sablelife` also lists every command and its concise purpose. Both manuals document the command workflow. Manual revision 242.

Current source version: `0.1.452`.
- Historical 0.1.440 release preparation includes MIT/template licenses and source dependency lockfiles. At that time, production classes and gameplay resources matched the original 440 test build. Public materials include performance reports; detailed functional evidence stays local.

- Propulsion adds marked-face or fixed world direction selection. Choose North, South, East, West, Up or Down and apply angle offsets. Existing modules retain marked-face behavior. The force point follows the mark, and speed control and both previews use the same direction. Manual revision 240.
- The Inspection Tool uses the authored 16×16 purple inspection-ring texture with the ordinary handheld item model.
- The Inspection Tool can be crafted with one spyglass and one redstone dust, shapeless. Obtaining a spyglass unlocks the recipe book entry. Both manuals explain crafting, binding and persistent previews. Manual revision 241.
- The 0.1.440 crafting release is retained. Earlier 439 world-direction and 438 damping-editor test builds are retained.

[中文版](UPDATE_0.1.320_TO_CURRENT.md)

Categorized changes since 0.1.403-fix1: [0.1.403-fix1 → 0.1.440](releases/0.1.440_EN.md).

Included in 0.1.403-fix1: Gyroscope now uses cyan/orange arrows and a green tilt-boundary wireframe through the shared solid-line renderer. Focused native and vanilla/five-shader checks passed; this does not increment the independent feature count.

## Current source changes


- Motion Damping uses a compact single-page editor for all six directions, with smaller number fields and one confirmation button per row. Three-decimal precision, wheel controls and server validation are retained.

Previous published build: `0.1.440`, including the compact damping editor, world-direction propulsion and Inspection Tool recipe.

- Wireless Channels and Wireless Redstone modules default to 16 newly authored output faces each. The world/server setting maximumRedstoneOutputFaces supports 1–64. Editors display the server limit; marking start and confirmation enforce it. Existing saved faces up to 64 remain readable, operational and editable. Only additions are limited. Manual revision 239.
- Added independent Propulsion and Motion Damping modules for all three cores, with per-core configuration switches and Move Set support. Propulsion applies a marked-point impulse or sustained thrust with optional directional speed control. Damping applies center-of-mass braking in six world/local directions. Both can run together; runtime state is not saved and existing motion settings are retained. Manual revision 238.

- Included an isolated, optional client adapter for LittleTiles mesh recovery. It activates only for verified dependency versions and interfaces, and makes bounded native rebuild requests for confirmed mesh losses on Sable structures. It does not scan the world or change saved data. The `littleTilesMeshRecovery` option in the client's `config/sablelife-client.toml` defaults to On. The built-in adapter skips itself if the earlier standalone recovery addon is installed; remove that addon after upgrading.
- Fixed Arrow Launcher entity collision queries against Sable structures. Both world-space and plot-space endpoints are converted to world coordinates before applying the arrow's width, avoiding oversized queries that could skip a mob in front of a structure. Wide-arrow collisions, block impacts and existing save formats are retained. This is independent of LittleTiles mesh recovery.
- The earlier standalone LittleTiles mesh recovery client test addon `1.0.0-test1` restores bounded native rebuild requests after an existing mesh is discarded as invalid or a nonempty mesh upload fails on a Sable structure. That addon leaves the older main JAR, saved data and server logic unchanged. New builds include the optional recovery adapter; the original combat scene still needs continued observation. Instructions.
- A removable LittleTiles rendering diagnostic client addon 1.0.0 is provided separately. It can capture one host block's cache state and manually request a Sable section rebuild or a LittleTiles mesh rebuild. Main-mod rendering and save logic are unchanged; this is not a confirmed fix for invisibility. Instructions.
- Added world/server option allowCoreRemoteWithUnsupportedClaims, default Off. It allows Core Remote and remote inspection access when a claim integration is unavailable. Supported claim permission checks and other core access checks remain enforced. Denials identify the mod IDs and setting. Existing configs retain protected access. Manual revision 237.
- Core Remote denial messages distinguish binding reach, game mode and editing permissions, world protection, unsupported claim mods or AeroClaims APIs, and denied or failed claim checks. Default authorization rules are unchanged, making it possible to identify why a Creative player cannot access a Survival Heart Core.
- Auto facing can select a Target sensor on the same core by module name and number. It follows the target selected within that sensor's detection area and sight settings, using core target priority, and stops turning when the sensor loses its target. Bindings survive saves, blueprints, full configuration copies, renaming and reordering. Existing modules keep Self targeting by default. Manual revision 236.
- Signal turning can run alongside the Gyroscope with Turn during gyro recovery enabled. This works with both directional signals and random horizontal turns. Existing saves default to Off. Manual revision 234.
- Signal turning adds a Random horizontal turn mode. Each trigger or Move Set action picks one angle, from −90° to 90° by default. Random mode hides the six directional inputs and keeps their channel settings for when you switch back. Manual revision 233.

## Local test build 0.1.425

This build uses multiplayer protocol 50 and manual revision 236. It includes the changes below and those from the previous 0.1.425 test build.

- Wireless Redstone raises the output face limit from 8 to 64 per module. All faces share the module settings. Face 1 uses the original Mark and Preview buttons at the bottom. Add faces 2 and up in the properties, with each face's Remark, Preview, and Remove controls available through the property pages. Previews show face numbers, and existing marks keep their numbers.
- Radial blur also works when facing away from the source. It uses the center of the screen and transitions smoothly as the player turns. Distance falloff still applies. Manual revision 231.
- The default module limit per core is now 256, configurable from 16 to 1024. Existing config values and saved modules are preserved. An inline config note explains that cores with more modules may take longer to load, including from blueprints. Large dashboards sync details for the selected module, and the Data Copier compresses large configurations. Manual revision 230.
- Arena battle timer reminders can appear as screen titles or chat messages. Players receive them within the configured range in the same dimension. Existing arenas default to titles. Manual revision 228.

## Previous 0.1.425 test build

This build uses multiplayer protocol 50 and manual revision 227. It includes items 106–112 below, covering Survival Heart Core auto-refueling, multi-stage arena fights and timers, Let it Walk! leg damage compatibility, and separate modifiers for general, melee, and direct projectile damage.

- Arena Boundary initializes its respawn and defeat announcements as text. Both fields stay consistent when modules are copied, saved, or reloaded, and existing announcements continue to work.
- Structure cleanup falls back to native Sable removal if the Toolgun 0.3.6 interface is unavailable. Failed death cleanup jobs retain their pending records and retry after 100 ticks.
- Java addon linkage errors during startup, ticking, and shutdown are isolated to the affected module. Its saved settings remain available, and other modules and cores keep running.
- The packaged JAR cleared all nine parts of the affected blueprint on death and successfully respawned it in the arena. The replacement structure and its wireless signal still worked after saving and reopening the world. The cleanup timeout in the combined tests came from the dragon blueprint test removing shared chunk tickets. After correcting ticket ownership in the test, the affected case passed both reruns. The final run passed all 47 checks. A separate cleanup retry test timed out on the first run and passed on rerun.

This build also includes the **0.1.425-breaching-test** fix for missed damage when TaCZ bullets hit non-core blocks on a living structure with Tactical Breaching installed. Sable Life applies the bullet's damage at impact when Tactical Breaching cancels a valid structure hit. Tactical Breaching still handles block destruction and penetration. In Hybrid mode, each bullet damages the same creature only once across both hit paths.

- The test JAR passed 11 GameTests in each of the four combinations of TaCZ `1.1.8-hotfix-r7` and `1.2.0` with Tactical Breaching `1.0.5` and `1.0.7`. The tests covered structure hits, penetration and block destruction, single and overlapping Entity Hitboxes, and Hybrid mode.
- TaCZ alone and a simulated unsupported Tactical Breaching version passed 11 tests each. With TaCZ absent, 67 Entity Hitbox regression tests passed. The integration skips its hook if a dependency is missing, the version is unsupported, or the hook signature has changed.

**Interface and preview changes**

- Shortened the natural spawn announcement placeholder so it fits in smaller windows.
- The particle picker includes seven new tintable particles (item 105). Attraction's Capture length adjusts by one block per scroll step.
- Removed the long action-bar instructions from Wireless Redstone, Target Sensor, Signal Turning and Auto Facing previews. Attraction shows a short legend for the capture area, pull destination and stopping distance. All five previews passed in-game checks in English and Simplified Chinese. All 16 related server GameTests passed.
- Part Break can preview the watched Sable part and the blocks to break together. Cyan outlines the entire watched part, and pale yellow marks the blocks to break. Hover over Preview for the legend. The watched part can also be previewed on its own. Damage Accumulation Trigger shows a Preview button in Marked part mode and outlines the entire watched Sable part in cyan.
- Refined the exclamation mark, question mark, lightning bolt and fault spark textures. The exclamation mark's stem matches the width of its dot and follows the same lower curve. The exclamation and question marks have symmetrical dots with transparency along the edges.

## Recent feature follow-ups

Servers can enable Message Display on Survival Heart Cores. It defaults to off. When enabled, chat, titles and action bar messages reach players within the configured radius in the same dimension. Server-wide broadcast is exclusive to ordinary Heart Cores. Each core type can now disable Java addon and Datapack modules by ID, and existing server-wide exclusions still apply. Manual revision 220.

After a multi-part blueprint spawns, the core gives joints five ticks to reconnect before marking modules as permanently detached or processing detached limb loot and cleanup.

Blueprint modules, natural spawning and Arena respawns wait for placement, connections, marks and Linear Movement destinations to be ready before activating a core. Mark a new Arena center after loading a blueprint. Arena respawns use the recorded center.

The original **0.1.404 build (protocol 48, manual revision 200)** added custom modules and mob-type targeting exclusions and fixed potion effects and repeated contact damage on entity hitboxes. See the [0.1.404 release notes](releases/0.1.404_EN.md).

Datapack modules support up to eight numeric options each. Definitions still use `format: 1`, and existing saved settings carry over. See the [Datapack module guide](DATAPACK_MODULES.md).

The Wireless Redstone ramp arrow is centered horizontally and vertically between the start and end strength fields.

New Wireless Channels are enabled when added. Existing channels keep their saved settings.

Fixed a disconnect when marking the watched part in Part Break and Damage Accumulation. Both modules can track actual health lost on a marked part, then break blocks or emit a signal when the threshold is reached.

Heart Core and Survival Heart Core can customize the particles at a structure hit, with a shared particle count and adjustable spread. Existing cores default to the original dark hearts.

After a tracking laser starts firing, losing its target holds the beam's last direction for the rest of the burst. The beam can still hit targets in its path.

Persistent Area Damage can detonate once after a delay. The blast uses the existing circular or rectangular footprint and supports optional terrain destruction, particles, and sound. Repeated impacts that refresh the same area leave its blast timer alone.

Removed two unused core textures, shared the block geometry between the Heart Core and Redstone Core, and let the vanilla atlas load the Redstone Core crystal.

Gun, Arrow Launcher, Mortar and Hovering Homing Salvo can switch between particles and a tracer with configurable color, length and width. Homing tracers appear after launch. Existing modules keep their particle settings. The current source uses multiplayer protocol 50.

The local test build adds damage compatibility for Create: Gunsmithing 1.4.9 with NTGL 3.2.0. Projectiles, rockets, grenades and melee can damage the structure itself. Entity Hitbox and Hybrid modes settle each impact once. The integration checks dependency versions and hook signatures before enabling.

Source follow-up **0.1.403 / protocol 46 / manual 197** adds six world-space Visual Effects patterns and symbols. Color, size, lifetime, animation speed, placement and following are configurable. This is feature addition 83. See visual pattern verification.

Included in fix7: large receiver mob navigation, sight and attraction distance now use the nearby receiver volume. Four zombies attacked four outer faces successfully; eight new native scenarios and 43 existing regressions passed. See verification.

**fix5 includes** Survival Heart Core target aiming, large-hitbox query fixes, Arena respawn names, review fixes and the natural-spawn workbench notice. See package notes.

fix6 additionally fixes shader-enabled health-bar names and removes the aqua announcement example. All 24 visual checks across five shader packs and three vanilla graphics modes passed. See package notes.

The current source contains **120 independent feature additions** since 320, giving version **0.1.440**. The original **0.1.404** release covers the first 85.

## Already present in 320

- Module references, groups and channel management, including navigation to referenced modules and individual Move Set actions, with warnings before deleting referenced entries.
- Collapsible module groups, search, and group preservation when copying or using presets.
- Channel inspection showing contributors, receivers, consumers and signal waveforms.
- Particle density for Persistent Area Damage; setting it to zero hides the visual effect while retaining damage.

## 120 independent additions after 320

1. Per-core player name whitelist, supporting offline names, case-insensitive matching and the All target category.
2. Per-core target priority.
3. Owner binding for following, facing, navigation, sensing, owner protection and kill attribution.
4. Server-wide factions, one faction per creature, and same-faction friendly-fire settings.
5. An administrator master switch allowing ordinary Heart Cores to be edited in Survival mode.
6. A separate Survival Heart Core with its own Survival settings and crafting recipe.
7. An Energy Refuel screen with accepted materials, value previews and confirmation before conversion.
8. A server-configurable Energy value list for multiple materials and their storage blocks.
9. Retention of purchased health, with activation charging only for the missing health that needs to be restored.
10. Energy costs based on the amount of health actually regenerated.
11. Activation using the remaining funded health when Energy is insufficient, and automatic deactivation when health reaches zero.
12. A single-use Energy transfer record on the core item to prevent copied balances from being claimed repeatedly.
13. Resetting Energy and funded health in blueprint copies to prevent duplication of stored resources.
14. Live module Energy estimates and formula tooltips.
15. Administrator-configurable Energy rates for individual modules.
16. Server-side Energy charging for attack and powerful control modules.
17. Survival Core part damage reduction charged by reduction percentage and active duration.
18. Move Set selection that can ignore target and health conditions.
19. Move Set support for Redstone Cores.
20. Safe Linear Movement support for Redstone Cores.
21. Entity Hitbox modules with configurable dimensions, offsets and marked body parts.
22. Hybrid structure/entity damage entry points, shared creature health and deduplication of identifiable repeated hits.
23. A server setting for the maximum number of simultaneously active entity hitboxes per creature.
24. Selection of the nearest valid hitbox when mobs retaliate after being hurt.
25. An optional hitbox setting that attracts hostile mobs and mobs with retaliation AI.
26. Deactivation tools that can operate through applicable entity hitboxes.
27. Craftable Survival Release Tool for releasing Survival Heart Cores.
28. A Block Interaction module with single-use and repeated right-click behavior.
29. World and marked-structure interaction modes, with interaction area and position controls.
30. A continuous laser visual that follows moving parts and supports custom colors.
31. A vertical laser sweep angle that can be combined with horizontal sweeping.
32. An option to keep custom projectile items facing the camera.
33. A Fan pattern for Flamethrower, with an adjustable opening angle and end thickness.
34. Directional Attraction areas.
35. A stable intake movement mode for Attraction.
36. Adjustable screen-shake frequency.
37. Continuous outline previews for marks and entity hitboxes.
38. Diagonal stripe previews on marked Wireless Redstone faces.
39. A self-death signal and external pulses that can continue after the original body disappears.
40. Three player-mining modes: Prohibited, Always Allowed and After Death Only.
41. A switch controlling block drops when players mine a creature.
42. Automatic cleanup of ordinary detached limbs, with a configurable delay.
43. Cleanup of ownership, loading records and queued work when an external system permanently deletes a structure.
44. Administrator commands for scanning and cleaning up identifiable world remnants.
45. A Blueprint Spawner module with core validation.
46. Fixed-world-point and follow-mark blueprint spawning modes.
47. Blueprint placement origin, start distance, XYZ offsets and rotation controls.
48. Blueprint selection retained across deactivation.
49. Actual blueprint model projection previews.
50. A server-configurable size limit for blueprint projections.
51. A natural-spawning management tool with per-blueprint rules.
52. Optional core activation during natural spawning, including support for ordinary structures without a core.
53. A searchable two-panel biome selector and an Overworld biome group that excludes ocean types; oceans can be added separately.
54. Surface placement and ground clearance for naturally spawned blueprints.
55. Open-sky requirements, blueprint-bounds clearance checks and optional obstacle removal, later expanded to cover obstacles throughout the full bounds.
56. Faction selection for natural spawning and a hidden Wilds faction.
57. Copying natural-spawning rules between blueprints.
58. Multiple tracked objects per rule, population limits, and per-object lifetime and no-player cleanup records.
59. The current tracked population shown beside each blueprint in the natural-spawning list.
60. Clearing all currently tracked objects for a blueprint, backed by a persistent cleanup queue.
61. Release After Spawning, which removes new objects from rule counts and cleanup management.
62. Public and private factions.
63. UUID-based invitations, acceptance and member management; players may join multiple factions.
64. Multiple mutual alliances, without automatic alliance inheritance; allied friendly fire requires agreement from both sides.
65. Automatic natural spawning around Creative players, while still excluding Spectators.
66. Obstacle and raised-terrain removal within the full blueprint bounds, while protecting unbreakable blocks, containers and other protected blocks.
67. Separate server limits for naturally spawned blueprint blocks, parts, data size, footprint dimensions, placement searches and obstacle removal.
68. A per-blueprint Spawn Density panel controlling successful-spawn cooldown, failed-search retry, candidate positions per search and automatic spawn distances. Settings are saved with the world rules and applied at runtime.
69. Per-blueprint server-wide spawn announcements, sent after successful spawning. Empty text disables them. Test spawns and released objects are supported, and announcements survive world restarts.
70. A radial blur pattern in Visual Effects, centered on the actual emitter's screen projection, with strength, duration and a clear center radius. The existing ring effect remains the default.
71. Optional actual spawn coordinates appended to announcements. This is saved per rule and disabled and deselected when the announcement is empty.
72. Local X/Y/Z model rotation for custom Arrow Launcher, Gun and Mortar projectiles, following flight orientation and retained through synchronization and saving.
73. A marked-point Health Bar module that follows its part and faces the viewer, with a soft-edged red health fill, adjustable width, height and start distance, preview support and lifecycle cleanup.
74. Rectangle and rounded-end Health Bar styles with a thin gray-purple inventory-colored border.
75. Health Bar visibility set to Always or After Taking Damage, with an adjustable hide delay measured from the last actual health loss.
76. An independent visible distance for each Health Bar module: 64 blocks by default, adjustable from 1 to 256, with immediate hiding and restoration based on distance from the bar's center.
77. Workbench protection for natural spawning: a default horizontal radius of 64 blocks, checked against the entire blueprint footprint at all heights. A persistent chunk index is built in batches; block changes update the cache directly, unknown areas wait, and administrators can change or disable the radius.
78. Independent horizontal and vertical laser sweep speeds in degrees per second. Each speed appears when its corresponding angle is nonzero. Auto retains the old timing behavior, and continuous beams interpolate smoothly between frames.
79. Adjustable spacing for selected extra particles along a laser beam, with bounded sampling and a shared visual budget.
80. A server-configurable faction creation limit per player: default 1, with 0 meaning unlimited and permission level 2+ exempt. This is separate from joining multiple factions. Existing factions are retained, and rejected actions show specific reasons.
81. Spawn announcement formatting with & color codes, bold, italic, other standard styles and hex colors. Raw codes are saved and converted into styled broadcast text; the tooltip lists the syntax and 256-character limit.
82. Creature names above marked-point Health Bars, preserving name colors and formatting, with independent name size, spacing and visibility controls. Names follow the bar's trigger, visibility timing, distance and cleanup.
83. Six world-space Visual Effects patterns and configurable symbols, with color, size, lifetime, animation speed and fixed or part-following placement. Click the spatial effect pattern to pick an effect from a two-column list.
84. Custom module support through Java addons and JSON datapacks with `/reload`, including the Marker Chime and Alarm examples.
85. A searchable mob type whitelist for excluding vanilla or modded types from automatic targeting, including the All target category.
86. Part Break can remove one block or a two-point area on the same Sable part. It can follow a module trigger or react to accumulated part damage. Moves that depend on an invalid attack mark automatically leave the Move Set draw. New modules default to the sound of a zombie breaking a wooden door. The mix can be replaced or cleared and plays once at the center of the marked selection when blocks break.
87. Damage Accumulation can track the whole creature or a marked Sable part. Existing configurations continue to track the whole creature by default.
88. Gun, Arrow Launcher, Mortar and Hovering Homing Salvo support tinted tracers with adjustable length and width.
89. Entity Hitbox modules can let individual whitelisted mobs that deal damage become eligible targets for 15 seconds. Existing target locks and selection rules still apply.
90. Heart Cores and Survival Heart Cores can customize hit particle types, count and spread.
91. Persistent Area Damage can detonate once after a delay using the configured area shape, with optional terrain destruction, particles and sound.
92. The rebindable Core Remote has a custom red and purple texture and opens loaded cores in the same dimension. Craft it with a lever surrounded by four redstone dust in a cross. Ordinary Heart Cores require Creative mode for binding and remote access. The server checks the current player's permissions and closes the remote panel when the core unloads.
93. Passive Mobs and Neutral Mobs appear first in the mob whitelist. The presets use entity tags that server datapacks can extend. The damage-immunity notice is highlighted.
94. Sneak and right-click in the air with a Core Remote to activate the bound Heart Core or Survival Heart Core. The server checks permissions, assembly and the usual Survival Energy cost. Sneak and right-click a core to bind it.
95. Right-click a group heading to edit its members in the sidebar. Members start checked. Uncheck a member to remove it, check another module to add it, then click Confirm next to the group name. Ctrl-click selects individual modules, Shift-click selects a range, and right-clicking a grouped module removes it from its group.
96. Linear Movement can take the core to a marked world position. Gravity uses the horizontal coordinates. The editor shows the settings that apply to the current movement and destination modes.
97. The Command Wand has a custom red-and-purple texture, binds to one core and sets a temporary creature target or world destination for Auto Facing, Target Alignment and Linear Movement. Each module still follows its configured trigger. Tracked creatures and loaded terrain can be selected up to 128 blocks away. Tracked creatures under the crosshair get a spectral outline even beyond vanilla entity render distance. Terrain gets a thin green outline that hugs the block and is hidden where obstructed. Command feedback uses colors for the result, target and duration. Left-click can also select a block of another active Life. Explicit orders bypass module search ranges while respecting triggers and line of sight. Target Alignment now supports a search range of up to 256 blocks, matching Auto Facing and Linear Movement. The manual covers temporary targeting, module priority and troubleshooting. Craft it with redstone dust, an amethyst shard and a stick, placed diagonally from the top-right to the bottom-left of a crafting table.
98. Mortar gains a Line of sight toggle in Target position mode. It filters obstructed targets for firing and preview, and defaults to Off for existing modules. The Move set sight condition applies when selecting a move.
99. Arrow Launcher, Gun, Slash, Laser Beam, Ground Strike, Earthquake Wave, Sphere Blast, Movement Freeze, Shockwave, Grab / Restraint, and Summoned Weapon gain Line of sight settings for targeting. Line of sight defaults to On for newly added modules, including Mortar and Hovering Homing Salvo. Existing settings and targeting behavior in older saves are preserved. With the setting enabled, a laser that has already fired continues in its last direction if the tracked target moves behind cover.
100. Use move's target lets directly scheduled aimed attacks use the target chosen when their move starts. Multi-target attacks put that target first and fill the remaining slots as usual. If it fails the module's own target, range or sight checks, that cast is skipped and the timeline continues. The setting defaults to On for new moves and Off for older moves without a saved value. It is unavailable when conditions are ignored.
101. Sneak-right-click the air with a Command Wand to choose whether Target Alignment, Auto Facing and Linear Movement follow your next order. All three are enabled by default, including on existing wands. Hover over a toggle to see how its module runs. Scrolling the duration field adjusts it by five seconds. You can type any whole number from 1 to 120.
102. Weapons follow the left-click target by default on new Command Wands. Existing wands keep their settings. When enabled, supported weapons prioritize the wand target, then the Move Set target, then their own targeting. Multi-target weapons fill remaining slots as usual. Weapon triggers, target rules, range and line of sight still apply. A valid right-click destination order can replace the target order. The item tooltip and manual explain the setting. Manual revision 216.
103. Arena Boundary has a separate Player death countdown, shown when player death is enabled. It defaults to 5 seconds on new modules and inherits the previous countdown on existing modules. Nearby Survival and Adventure players each get a full countdown while the creature is outside the arena. Leaving range or the creature returning cancels it. Immediate full-HP return takes priority. Scrolling adjusts the player death countdown by 5 seconds and the arena radius and nearby player range by 8 blocks. Manual revision 214.
104. Message Display sends up to eight messages per trigger. The first goes out immediately. Each message can appear in Chat, as a Title or in the Action Bar. Chat uses `[Creature Name] Message`. The editor supports text formatting, title duration and optional subtitles. Message Display is under Functions. Each message has its own wait before the next message, defaulting to one second and preserving existing intervals. The mouse wheel adjusts recipient radius by eight blocks and title duration by one second. The default recipient radius is 64 blocks. On ordinary Heart Cores, Creative-mode operators can edit messages and enable server-wide broadcast. The editor keeps its controls visually stable while saving and marks the selected message with ▶.
105. The particle picker adds seven animated particles: an exclamation mark, a question mark, an ellipsis, a cross, a lightning bolt, metal chips and fault sparks. Each has a default tint and supports custom RGB, size and lifetime.
106. Hoppers and compatible item pipes can fill a Survival Heart Core's Refuel slots. Convert fuel manually or enable Auto Refuel. The slots move with the core, and mining and placing it again in the same world restores the stored fuel.
107. Arena Boundary and Blueprint Generation support two-stage boss fights. The first stage can spawn the second through its Own death signal. The arena waits for the fight to finish before starting its respawn countdown. Both stages can spawn arena summons, which are cleared when the arena resets.
108. Arena Boundary can send separate respawn and defeat announcements to all online players. Both support colors and formatting. Leave either field blank to disable it. In a two-stage fight, the defeat announcement goes out after the final stage is defeated.
109. Arenas can use a battle timer with a five-minute default. It begins on the first player hit that reduces HP and spans all boss stages. On timeout, the arena clears the fight and starts restoring the first form. Blueprint Generation settings group blueprint selection and role, placement, and timing.
110. Arena battle timers can show nearby players customizable titles at battle start and at 10 minutes, 5 minutes, 1 minute, and 10 seconds remaining. The edit fields come prefilled with examples.
111. Optional Let it Walk! 0.1.5 compatibility routes hits on attached legs to the walker’s Heart Core. A projectile or explosion counts once per core across leg segments, the hull, and overlapping Hybrid hitboxes.
112. Each phase rule in Part damage modifier can set general, melee and direct projectile modifiers separately. Melee and projectile hits inherit the general value by default. Explosions use the general value. This works on the structure, Entity Hitboxes and Hybrid. Survival Heart Cores retain the 90% cap and pay for the strongest active reduction in each module. Old rules and enabled module-wide projectile filters retain their behavior. Manual revision 227.
    - Fire Arrow from Iron’s Spells ’n Spellbooks applies its direct-hit damage to the walker core once. TaCZ Tactical Breaching also preserves bullet damage when it cancels the original hit on a glass leg segment.

113. Wireless Redstone supports multiple faces sharing one module's settings, with individual marking, previews and removal. The current default is 16 faces per channel/module, configurable from 1 to 64, while older saved faces are retained. Wireless Channels can also bind output faces directly.
114. Signal Turning supports random horizontal turns, choosing an angle within a configurable range on each activation or Move Set action.
115. Signal Turning can optionally run alongside Gyroscope correction in directional and random modes; off by default.
116. A world/server setting can allow the Core Remote and remote inspection tools with unsupported claim APIs. Since0.1.448, new configurations default On; existing values are retained. Supported permission checks remain effective.
117. Independent Propulsion module: marked-point impulses or continuous thrust, optional directional speed control and retained physical inertia.
118. Independent Motion Damping module: six world/local directions with separate strengths and speed thresholds, controlled by signals or timed activation.
119. Propulsion supports fixed world directions with six axes and angle offsets, retaining marked-face mode and existing settings.
120. Survival crafting access to the Inspection Tool: one spyglass and one redstone dust, shapeless, with recipe-book unlocking.

Command Wand target and destination orders share a four-tick cooldown per player. The first order runs immediately. Repeated orders during the cooldown are dropped before raycasting.

Command Wands without saved module toggles default to Weapons follow left-click target enabled. Saved toggles retain their values. See item 102.

Message Display can use the same internal channel number as an Own death output. The first message goes out at death and the rest can play after the body is removed. Local delivery uses the death location. Pending rows last for the current server session. See item 104 and manual revision 219.

The two-column Visual Effects picker shows the current page and total pages in the footer, with left and right buttons for browsing. Both buttons are disabled when there is only one page.

The Shield Fracture effect now starts with a curved shield face. Cracks spread from the impact point, and individual shards tumble, scatter, and fade. The speed setting adjusts when the shield breaks and how far the shards scatter.

Part Break's Required health loss field adjusts by 5 per scroll step.

The Damage Accumulation Trigger's Damage threshold adjusts by 5 per scroll step.

## Current behavior and important adjustments

### Cores, factions and Energy

- The three core types are Heart Core, Redstone Core and Survival Heart Core. The unpublished Survival core item ID is now `sablelife:survival_heart_core`; migration aliases for its earlier item IDs were intentionally omitted.
- The ordinary Heart Core's Survival editing switch is `allowEditHeartCoreUnderSurvivalMode`, default false. The Survival Heart Core is independently available in Survival mode and does not depend on this switch.
- Module configuration tables are `[modules.heartCore]`, `[modules.redstoneCore]` and `[modules.survivalHeartCore]`. Configuration comments use blank lines between settings, while module switch lists remain compact.
- The player whitelist excludes automatic targets; it does not grant general immunity to stray attacks. Owner and faction relationships do not grant editing, mining or interaction permissions.
- The faction list shows Public/Private status, creator and joined status. Players can create one faction by default, with administrator exemption, and can join multiple factions. Each creature belongs to one faction. Alliances require mutual acceptance and are not transitive.
- UI wording has changed from Token/Top Up to Energy/Refuel. Some internal configuration and save keys still use Token names.
- The Survival Heart Core uses the Redstone Core recipe with redstone dust replacing the central amethyst. The Survival Release Tool uses a shapeless recipe with shears and redstone dust.
- Inapplicable Survival Core settings are hidden, including experience, Loot, adaptive damage reduction, ordinary death-delay/removal behavior, void-death height, detached-limb cleanup and forced chunk loading. Survival interaction and receiving damage from other creatures are always enabled; faction damage rules still apply.
- At zero health, the health-depletion effect plays and the creature deactivates, leaving its structure. Manual deactivation retains remaining health and Energy. Mining the core can transfer a single-use Energy record to its item; this does not yet provide full item-based transport of settings and health.
- Refuel fixes cover Shift-click transfers blocking later manual placement, missing value tooltips and missing item balance display. Material values and actual conversion use the same server settings. Copper is included in `depositValues`, with migration preserving customized legacy settings.
- The accepted-material list, current value and two-step confirmation use the mod's UI style. The balance, estimated module cost and health costs have distinct colors and formula tooltips.

Default material values:

| Material | Energy per item | Energy per storage block |
| --- | ---: | ---: |
| Copper Ingot | 10 | 90 |
| Iron Ingot | 30 | 270 |
| Gold Ingot | 60 | 540 |
| Emerald | 120 | 1,080 |
| Diamond | 300 | 2,700 |
| Netherite Ingot | 3,000 | 27,000 |

### Attack costs and Survival restrictions

- Server rates determine costs, and estimates use the same formulas as charging. Melee charges for hits that successfully deal damage; Arrow Launcher charges for arrows successfully fired; Summoned Weapon charges for deployment and subsequent attacks.
- Cost calculations account for Homing Salvo damage and flight range, Gun range, Slash and Earthquake Wave speed, and related parameters. Faster travel, longer reach, more projectiles and more impact points affect the corresponding estimates.
- Impact explosions include radius × damage. Sphere Blast, Ground Strike and Shockwave use a consistent radius scale, correcting disproportionate prices caused by mixing sphere-volume and radius calculations.
- Ground Strike accounts for extended segments, rays and random impact points, charging for valid scheduled points. Shockwave includes knockback; Movement Freeze includes horizontal range, height and duration.
- Persistent damage areas created on impact have a separate charge. Extending an area pays only for the added duration. Insufficient Energy prevents new paid attacks; attacks already paid for and launched may complete their normal lifecycles.
- Part damage reduction is capped at 90%. By default, each percentage point of reduction costs 1 Energy per active second: 50% costs 50/s and 90% costs 90/s. Charging does not require an incoming hit. An active module trigger alone does not mean its internal phase rule matches; an unmet internal-channel condition gives neither reduction nor a charge.
- On the Survival Heart Core, Arrow Launcher, Gun, Mortar and Slash can aim at one or multiple targets from the muzzle. Saving, importing and activation preserve those aim settings. Survival restrictions still apply to releases at the target position for Sphere Blast, Attraction, Movement Freeze, Shockwave, Earthquake Wave and Ground Strike.

### Redstone Cores and movement

- The Redstone Core enables its 17 supported abilities by default while preserving existing administrator settings. It does not gain the full combat feature set of a Heart Core.
- Redstone Core Move Sets always ignore target and health conditions. Priority, weight, cooldown, move duration, rest and repeat behavior remain active. Heart Cores can enable Ignore Conditions themselves; skipped conditions become unavailable for editing.
- Safe Linear Movement supports Travel, gravity and step assistance. Redstone Cores do not offer inapplicable options such as teleporting or forced movement through obstacles.
- Linear Movement previews outline actual block shapes, green when clear and red when blocked, with bounded scanning and drawing. Large previews may temporarily fall back to an overall box while processing is incomplete.
- The interval, charge time, stop distance and movement distance use the requested integer scroll steps; target search range uses steps of 8. New Linear Movement modules default to an internal wireless trigger and a 0.3-second step cooldown.
- The minimum Interval is now 0 seconds; the default remains 5 seconds. Zero adds no cooldown, while game ticks and action occupancy still determine execution order. It does not create an unlimited loop within one tick.
- Target Alignment uses thick solid lines for all six directions, with an arrow on the yellow forward direction.
- Rotation and movement no longer treat empty portions of distant Sable structures' bounding boxes as solid obstacles. Candidate overlaps are checked against nearby actual collision shapes. Real obstacles and scanning budgets remain enforced; the moving creature's own conservative bounds have not been replaced with fully precise per-block collision.
- Gyroscope's tilt threshold and righting speed are also relevant in Fully Level mode, so they remain visible with corrected explanations.

### Module visuals, interaction and damage compatibility

- Fixed missed contacts at the edges of maximum-size entity hitboxes spanning section boundaries. The query coverage is tracked per section storage and resets when large hitboxes are removed. All 397 related native checks passed. See the hitbox stress report.
- Block Interaction supports World and Marked Structure modes, single and repeated interaction, start distance and offsets. Each axis of the box can be 1–30 blocks and defaults to one block. It interacts only with the nearest supported block and skips UI interactions such as chests and crafting tables. The creature's own interaction protection allows internal module interactions.
- Continuous laser beams follow moving parts and support colors. Particle visuals remain available; the old default visual and untranslated style labels were fixed. The two sweep axes have separate speeds, extra-particle spacing is adjustable, and continuous beams interpolate between compatible frames.
- Flamethrower supports Cone/Fan patterns, fan angle and end thickness. Attraction supports a directional funnel and stable intake. Screen shake still works when facing away from its source and has an adjustable frequency.
- Radial blur has adjustable strength, duration, and clear-center radius. It centers on the emitter while visible and on the screen when the emitter is behind the camera.
- Custom projectile items for Arrow Launcher, Gun and Mortar support local XYZ rotation. Arrow projectiles can also be set to always face the camera.
- Entity Hitbox defaults to Hybrid and supports dimensions and offsets. Hitboxes are normally invisible; their previews are white. The default simultaneous limit is 8 per core, configurable by administrators from 1 to 32.
- Entity Hitboxes cannot catch fire. Flaming arrow impacts and direct fire damage can still reduce core health.
- Fire Breath from Iron’s Spells ’n Spellbooks 3.16.3 can hit Entity Hitboxes again on later damage pulses. Overlapping hitboxes take one hit per pulse.
- Mob retaliation selects the nearest valid hitbox. Attracting hostile mobs and mobs with retaliation AI is optional; passive cows and chickens do not acquire attack AI from this feature.
- The structure damage bridge for ordinary Artifice bullets supports `resolveDamage()`, reading damage that varies with bullet speed and pellet count. Part reduction, cancelled damage events and identifiable repeated-hit checks remain in place. The native entity-hitbox damage path already worked; this does not establish compatibility with every external damage source.
- SlashBlade currently has a diagnosis of its target-filtering behavior. It is not listed as an implemented and tested compatibility fix.

### Blueprint spawning, natural spawning and cleanup

- Arena respawn displays show the creature's name above the countdown with its colors and formatting. The name is saved when death starts and remains available after core cleanup and reload. All 21 related native checks passed. See the change record.
- The Blueprint Spawner displays “Server Blueprint: name” and retains its selection across deactivation. It supports fixed world points and following marks, with placement origin, start distance, XYZ offsets and rotation controls.
- The Blueprint Spawner's Server Blueprint screen uses a three-column grid and fits the number of rows to the available height. Status and help text use a smaller size and align with nearby controls. The Activate core? toggle is green when On and defaults to On for new modules. Existing modules keep their saved setting. Automatic activation requires exactly one ordinary Heart Core and no Redstone Core. Loading a blueprint that fails this check switches the option Off. Manual revision 215.
- The standard Trigger controls spawning; the separate spawn-event selector was removed. Delay, cooldown and the module's maximum spawn count are independent settings. Arena record and clear controls share a row, with a reminder to record the latest structure.
- The mod renders blueprint model projections; blueprint reading and actual placement still use the Photomancy interface. The projection limit defaults to 8,192 and is administrator-configurable. Large templates can fall back to bounds. The old yellow blueprint direction arrow was removed.
- Natural-spawning management uses two columns of template cards, a centered title and a top-right Close button. Each card has a green Current count, Conditions, Enable, Clear All Current, and a red X that changes to ? for confirmation. Deleting a rule does not delete the server blueprint file.
- Loading a blueprint offers Activate Core: Yes/No, with Yes shown in green. No allows plain structures. The biome panels, search, transfer arrows, faction selection and return-to-page behavior after preview were adjusted.
- Automatic spawning includes Creative players and excludes Spectators. Density controls cooldown after each spawn, retry after a failed search, candidate positions per search, and minimum/maximum spawn distance. There is no separate percentage-probability setting.
- Rules share a scheduler. By default, all rules and dimensions together may track 64 objects, with 4 within a nearby 128-block area; both caps are configurable. A per-rule cap does not override these shared limits.
- Only One Spawn per Dimension was removed. Lifetime 0 only disables lifetime cleanup. Release After Spawning is a separate option affecting new objects; released objects leave rule counts, lifetime/no-player cleanup and Clear All Current management.
- Both creatures and coreless structures retain tracking records. Unloading is not treated as deletion, and tracking continues after a restart. Expired or manually cleared objects wait for the data needed for cleanup to load. Coreless structures no longer lose their records merely because they unload.
- Obstacle removal can clear terrain inside the blueprint bounds, while still respecting permissions, protected/unbreakable blocks, containers and work budgets. Blueprint blocks, parts, dimensions, data size, search and obstacle-removal capacities have separate settings. Natural blueprints currently allow 262,144 blocks and 128 parts by default.
- Workbench protection defaults to a horizontal radius of 64 blocks, measured from the whole blueprint footprint and covering all heights. Test spawns and obstacle removal also respect it. Only stationary vanilla crafting tables in the world count; crafting tables carried inside Sable structures do not create moving protection areas.
- The workbench index is built and saved through batched scans, then maintained from actual block changes rather than periodic rescanning. Unknown regions wait instead of being forcibly loaded.
- Spawn announcements support custom text, colors, bold, italic, standard styles and hex colors, with optional actual spawn coordinates. Empty text disables announcements. Successful test spawns and released objects also announce.
- `enableWildEncounters=false` disables automatic and test spawning and pauses this rule system's lifetime cleanup. Rules remain saved; disabling the feature does not automatically delete existing objects.
- External self-death pulses may outlive the original body. There is no forced activation that keeps the dead body attacking.
- Ordinary Heart Cores have Prohibited/Always Allowed/After Death Only mining modes. Mining drops and explosion destruction are separate settings, with additional protection for the core itself. Newly placed Heart Cores enable detached limb cleanup with a 3-second delay. Existing cores keep their saved setting. Deactivation cancels pending limb cleanup.
- Permanent structure deletion revokes related ownership, queued work and loading records. Administrator remnant-scan and cleanup commands can handle identifiable leftovers. The old options/configure commands were removed; inspect, damage, cleanup and respawn cancel remain.

### Health Bars, the manual and UI

- Marked-point Health Bars support width, height, start distance, rectangle/rounded ends, soft edges, a gray-purple border, Always/After Taking Damage visibility, a no-damage hide delay, and visible distance (64 by default, range 1–256).
- Creature names can appear above the bar with independent size and spacing, preserving name colors and formatting. Names share the bar's visibility timing and distance.
- The Fabulous-mode water-edge depth-compositing issue was fixed. Rounded-end defaults, dimensions and border thickness were adjusted while retaining occlusion.
- Loot template Shift-click now copies a sample only once per explicit click, fixing repeated insertion of ghost samples. Source items are not consumed.
- Alignment fixes cover all three cores' top-right X buttons, main-screen margins, Boss Bar controls, status lines and natural-spawning controls. Fixes also cover E closing focused input fields, corrupted English text and several unresolved dashboard translation keys.
- Target Sensor Radius supports up to 256. Front End Radius appears only for Forward Cone and uses scroll steps of 1. Detection still uses loaded targets and does not load distant chunks.
- Manual pages use Usage/Example/Troubleshooting. Getting Started retains its six illustrated steps; “Diagram · not clickable” was removed, diagram text and plus signs were centered, and the activation diagram shows the empty-hand right-click instruction once.
- The first seven chapters follow the requested order. The All view organizes chapters into Guides/Signals/Attacks/Functions, with Server Resource Limits last in Guides. The standalone Hurt Counter, Death Delay, Sound Combinations and Combat Compatibility guides were removed; the Hit Counter module chapter remains.
- All 58 non-introduction chapters have brief English and Chinese Usage sections. Attack and Function chapters also include simple setup Examples and Troubleshooting. Property tooltips are split by field. Titles remain white, while body terms use colors for cores, signals, attacks and functions.
