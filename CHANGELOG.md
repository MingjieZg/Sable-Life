## 0.1.450 — Auto Facing aim offsets

- Auto Facing gains decimal local left/right and up/down aim-origin offsets. Collision check defaults Off for new modules; saved settings remain intact.
- Wireless Redstone Output buffer scrolls by 1 second; typed decimals remain supported. Chinese pulse mode is labeled 单次脉冲.
- Melee gains independent debris/drop switches and protected-list inventory samples; block-breaking controls move to the end of properties.
- [English release notes](docs/releases/0.1.450_EN.md) / [中文更新说明](docs/releases/0.1.450.md).

## 0.1.449-fix1 — Module catalog order

- Signal Contact Sensor now appears beside Target Sensor in the purple signal group. Add catalogs group signals, weapons and functions consistently while preserving personal preset order.
- [English release notes](docs/releases/0.1.449-fix1_EN.md) / [中文更新说明](docs/releases/0.1.449-fix1.md).

## 0.1.449 — Russian localization and adaptive language list

- Added Russian localization by [Bill-Fasber](https://github.com/Bill-Fasber) ([PR #5](https://github.com/MingjieZg/Sable-Life/pull/5)), with separate maintainer corrections and translations for current-version additions.
- Language selection fits the window, paginates longer lists and preserves unsaved input after switching.
- [English release notes](docs/releases/0.1.449_EN.md) / [中文更新说明](docs/releases/0.1.449.md).

## 0.1.443 — Signal Contact Sensor preview

- Added the independent Signal Contact Sensor: check terrain, other Sable structures, players, mobs and projectiles; configure Start distance, local XYZ size and angles. Supports Move Set windows, Hold and timed contact pulses, excluding own connected parts and projectiles. Bilingual manual revision 243.
- [English release notes](docs/releases/0.1.443_EN.md) / [中文更新说明](docs/releases/0.1.443.md).

## 0.1.442 — Local command preview

- Blueprint spawn command with name completion, default surface placement, optional exact coordinates and an activation toggle.
- Bilingual `/sablelife help` lists command syntax and concise purposes.
- [English release notes](docs/releases/0.1.442_EN.md) / [中文更新说明](docs/releases/0.1.442.md).

## 0.1.440 — Changes since 0.1.403-fix1

- Categorized changelog: [English](docs/releases/0.1.440_EN.md) / [中文](docs/releases/0.1.440.md).
- Includes world-direction Propulsion, the authored Inspection Tool texture, and its shapeless spyglass + redstone recipe, alongside the module, arena, core, compatibility and bug-fix updates since 0.1.403-fix1.

## Unreleased

- Right-click a group to edit its members in the sidebar. Check modules to add them, uncheck members to remove them, then click Confirm. Ctrl-click and Shift-click also support batch grouping and ungrouping.
- Newly placed Heart Cores enable detached limb cleanup with a 3-second delay. Existing cores keep their saved setting.
- New Wireless Channels are enabled when added. Existing channels keep their saved settings.
- Added Passive Mobs and Neutral Mobs presets at the top of the mob whitelist. The damage-immunity notice is highlighted.
- Sneak and use the Core Remote in the air to activate a bound Heart Core. Survival Heart Cores use the usual Energy cost.
- The Part Break health-loss threshold now scrolls in steps of 5.
- The Damage Accumulation Trigger's Damage threshold now scrolls in steps of 5.
- Added a rebindable Core Remote for loaded cores in the same dimension. Craft it with a lever surrounded by four redstone dust in a cross. Remote access checks the current player's permissions and ends when the core unloads. Ordinary Heart Cores require Creative mode for remote use.
- Added the Core Remote's custom red and purple texture with the standard item holding pose.
- Increased the Datapack limit to eight numeric options per module. Existing format 1 definitions and saved settings continue to load. Catalog synchronization uses protocol 49.

## 0.1.404

- Added the Java module API, the JSON datapack module format, and the Marker Chime and Alarm examples.
- Added a searchable mob type whitelist to the core’s target settings.
- Entity Hitbox receivers ignore potion effects and share the hurt window for repeated contact attacks.
- Release notes: [English](docs/releases/0.1.404_EN.md) / [中文](docs/releases/0.1.404.md).

### Mob target whitelist

- Added a Mobs tab alongside Players and Factions in Target Whitelist & Factions. Search living entity names or registry IDs on the left; use >> / << or right-click a row to manage the excluded types on the right. Each selected type excludes all its instances from automatic acquisition, including Mobs and All. Existing incidental projectile and area-damage behavior is retained.
- The catalog uses registered living-entity attributes, including modded types, without scanning the world or spawning entities. Lists are capped at 256 types, validated by the server, retained through core saves and configuration copies, and preserve well-formed IDs from temporarily absent mods. Unknown saved IDs can be removed. Added English/Chinese labels and brief manual guidance; protocol 47 / manual 200.
- 84 unit checks, 34 actual-JAR native checks and 16 real-client UI scenes passed. Checks cover all three cores, existing target locks, modded entity registration, persistence, limits, permissions and faction regressions; the UI covers English/Chinese, four GUI scales, two densities, search/input, independent pagination and authoritative snapshots. Local source follow-up; the delivered 0.1.403-fix1 archive remains unchanged. Original diagnostic logs are retained without a standalone report.

### Shared contact-attack hurt resistance

- Follow-up verification on the current candidate: 152 native cases passed (73 damage/compatibility/artifact regressions, 8 geometry cases and 71 core/lifecycle cases), with 84 unit checks passing. Added ten numerical/cadence/lifecycle boundary cases. A 100-projectile burst at 7 damage through eight overlapping receivers removed exactly 700 HP; the real Cataclysm fixtures still matched the ordinary-mob control at 42 HP. An initial test incorrectly forced two end-of-tick settlements in one tick; corrected to separate ticks and retained the failure/source. No production changes in this verification and no sustained-MSPT claim or separate public report.

- Reproduced Cataclysm 3.33's Phantom Halberd and Wave repeatedly damaging Entity Hitboxes every tick. Native non-projectile contact damage now shares a bounded per-core hurt window by attacker and damage type, including overlapping attack entities from the same caster. During that window, a stronger contact contributes only its raw increase before the marked part's armor. Independent projectiles, explosions, explicit cooldown-bypass damage and caller-timed body APIs retain their existing paths. NeoForge incoming-damage invulnerability overrides are respected; Release clears the temporary windows.
- 84 unit checks and 63 actual-JAR native checks passed, including the real Cataclysm attacks, potion immunity, stronger-hit/armor behavior, independent attackers, rapid projectiles, event overrides, Hybrid routing and Artifice bullet regressions. In the controlled 21-tick fixture, one halberd or wave previously removed 294 HP and three overlapping halberds removed 882 HP, versus 42 HP for an ordinary mob. All three repaired fixtures removed 42 HP with one or five receivers. Updated both manuals to revision 199. Original failures retained locally; the delivered fix1 JAR is unchanged and no standalone report was created.

### Entity Hitbox potion immunity

- Entity Hitbox receivers now reject beneficial and harmful MobEffects, including forced additions. Native splash and lingering potions skip receivers, including instant potion effects. Weapon hits, tipped-arrow impact damage and ordinary direct magic damage retain their existing damage paths. This avoids separate status timers and repeated status damage across multiple receivers.
- 84 unit checks and 50 packaged-JAR native checks passed, including five overlapping receivers, finite/infinite statuses, native harming splash, native lingering poison, a real tipped arrow and direct magic damage. Updated both manuals. Local candidate only; the delivered 0.1.403-fix1 JAR is unchanged. Original diagnostic logs are retained; no standalone public report.

## 0.1.403-fix1 - Text encoding repackage

- Corrected the English module Add button to `+ Add` and repaired 159 English translation entries containing misdecoded punctuation, arithmetic operators and range/angle symbols. Preserved wording and format placeholders; updated the matching English paragraph reference.
- Restored the Arena respawn countdown's filled/empty squares using Java Unicode escapes. Updated both release changelogs; retained version 0.1.403-fix1 for the requested replacement package.
- Replacement package verified: 84 unit checks and both manuals passed; 472 source/resource/release-text files and 55 packaged text resources passed the encoding scan. All 74 processed resources match the archive. Relative to the earlier fix1, only the English language resource and ArenaRespawns class changed. 2,413,593 bytes; SHA-256 `29b4dc07ea7ca291c3138488b6657b30394249269dcf2352f94227f7fd8cfad6`. The earlier fix1 archive is retained locally.

## 0.1.403-fix1 - qModeling compatibility hotfix

- Fixed GitHub issue #3 locally: qModeling temporarily changes the player to Spectator when opening its Workspace editor. The body-interaction guard previously closed that menu even for an ordinary world block. Spectator restrictions now apply to managed living bodies; ordinary-world and unactivated structure editors can stay open. Active-body interaction protections remain enforced. No qModeling dependency or mod-specific bypass was added.
- Reproduced the original 0.1.403 failure in both Creative and Survival. With the candidate JAR, real client/server checks passed for opening, adding a model part, moving the editing camera and restoring the original game mode in both modes, on ordinary ground and an assembled Sable structure without an active core. A further 155 native regression cases passed, including the temporary-mode editor regression and existing body-interaction protections. Tested CurseForge file 7966962 (page label 1.0.6b; embedded qModeling version 1.0.5.1), Minecraft 1.21.1 / NeoForge 21.1.235.
- Prepared as a separate compatibility hotfix. The original 0.1.403 archive is retained; uploaded GitHub source has not been replaced. Original logs and screenshots are retained locally; no standalone public report for this focused fix.
- Packaged as 0.1.403-fix1, following the current public 403 release. All 672 production classes match the native-tested compatibility candidate; only version metadata differs. 84 unit checks passed. The earlier local Gyroscope test package that used the same suffix was archived before placing this upload-ready JAR in releases. Protocol 46 / manual 197; 2,413,768 bytes. SHA-256 `91e587e4a6482fbcfa88378dab04d633da6afb184968797ff93e5dc963c93acd`.

## Earlier local 0.1.403-fix1 - Gyroscope test build (included in public 403)

- Replaced Gyroscope preview particles with the shared solid-line renderer: cyan world-down arrow, orange calibrated body-down arrow, and a green 48-segment tilt boundary with cone guides. Different arrow lengths keep aligned tips readable; 0/180-degree limits remain visible. Preview-only change; correction behavior is unchanged. Focused checks: 40 native gyro/facing cases, three vanilla graphics modes and five shader packs, including expiry (16 scenes), passed. Original logs/screenshots retained; no standalone report for this small change. Included in 0.1.403-fix1.

- Packaged and verified: 672 production classes match the native-tested gyroscope candidate; compiled classes and 74 processed resources match the archive. Only Gyroscope code and version metadata differ from 0.1.403. Protocol 46 / manual 197; 2,413,747 bytes. Previous 0.1.403 retained. SHA-256 `b2176aae5bceebca12b6b53586949ce96bdf6acc734754f3e551e2f34836ad39`. Evidence: `diagnostics/package-0.1.403-fix1-verification.json`.

## 0.1.403 - Visual patterns source follow-up

- Added energy gathering, mechanical rings, shield fracture, exhaust plume, planar energy fissures, echo rings and camera-facing symbols (!, ?, !!, ?!, +, ...), using the existing Visual Effects module. Added preset/RGB color, size, lifetime, animation speed, start distance and following/fixed placement controls. Existing default patterns and settings retain their values.
- World patterns use bounded client geometry and existing Sable render poses. Iris uses the entity rendering stage to avoid Photon's missing-pattern behavior; real depth retains wall occlusion. Finite effects, client count/segment caps, distance culling and mark-removal cleanup are enforced. No new rendering entities or continuous vertex synchronization.
- 84 unit checks, 160 native checks, 12 vanilla scenes and 20 shader scenes passed, with additional actual part-following, range and cleanup checks. Original failures and bilingual evidence retained in docs/VISUAL_PATTERNS.md and docs/VISUAL_PATTERNS_EN.md. Protocol 46 / manual 197. The delivered fix13 archive remains unchanged.

## 0.1.402-fix13 - Test build

- Renamed Survival Release Shears to Survival Release Tool (生存解除工具), including the failure message, Chinese manual references and review-source text. Item ID and shapeless recipe are unchanged. Manual revision 196 clarifies the Survival tool's scope, the Creative tool's game-mode requirement and whole-core Data Copier permissions. Both locale catalogs passed manual validation. See docs/RELEASE_TOOL_MANUAL_REVIEW.md and its English counterpart.

- Build, 84 unit tests, manual validation and 666-class/73-resource archive checks passed. Production classes are byte-identical to fix12; only version metadata, both locale files and the manual catalog changed. No gameplay/visual rerun for this text-only package. fix12 retained. Protocol 45 / manual 196; 2,392,544 bytes. SHA-256 da83ce74f449362ae79b4b66c181c008fd04984b8dff0539f4a6b06465325cbd. See docs/TEST_BUILD_0.1.402-fix13.md and its English counterpart.

## 0.1.402-fix12 - Test build

- Imported the authored 16x16 Data Copier and both release-tool textures from MCreator: the Survival tool uses the red crystal with gray fittings, and the Creative tool uses the purple-accented crystal. All three use the ordinary generated-item holding pose. Checked PNG dimensions, exact source bytes and model texture references.

- Data Copier now supports all three core types using the clicked core's existing editor permissions for both copying and pasting. Ordinary Heart Cores still follow the server's Survival editing switch; active life cores still reject pastes. Energy and paid current health are not copied. Updated English/Chinese permission text.

- Build, 84 unit tests, bilingual manual and 666-class/73-resource archive checks passed. Final delivered JAR passed 8 native checks; its production classes are byte-identical to the permission candidate that also passed 103 core regressions. fix11 retained. Protocol 45 / manual 195; 2,392,264 bytes. See docs/TEST_BUILD_0.1.402-fix12.md and its English counterpart. SHA-256 bd451325abc5a20ad4550053152a25cff41bdfe07a50f5637f5c115845727d06.

## 0.1.402-fix11 - Verified release candidate

- Fixed a real multiplayer configuration-sync exception: the optional TOML formatter now skips FML configurations without a backing file. The initial fix10 candidate reproduced the exception on two actual network clients and is not released. Feature freeze remains in force.

- Final packaged-JAR verification: 84 unit tests, 373 native cases, two real network clients, fix9 fixture-world upgrade, 12 firing/release cycles and four natural-spawn server boots passed. Initial failures retained; original intermittent release report is not marked conclusively fixed. See docs/RELEASE_READINESS_0.1.402.md and its English counterpart.

## 0.1.402-fix10 - Unreleased verification candidate

- Feature freeze for release verification. Includes the naming and recipe follow-ups below; test-only changes may be added without expanding product features.

- Renamed the Chinese creative-tab title to Sable Life · 生命化. Kept vanilla creative-category tooltip behavior and removed the unshipped tool-specific hiding handler. Creature terminology elsewhere is unchanged. Included in fix11.

- Renamed the Chinese Wild Encounter Tool item and its manual reference to 野外蓝图生成工具. English name remains Wild Encounter Tool. Included in fix11.

- Renamed the Chinese workbench spawning notice to 工作台刷新保护, including its disabled message. Added a shapeless Data Copier recipe: one paper and one redstone dust, with recipe-book unlock. Confirmed Survival Release Shears already use shapeless crafting (shears + redstone dust). Included in fix11.

## 0.1.402-fix9 - Test build

- Changed the Wild Encounter Tool model parent from generated to handheld, giving the crystal wand the standard tool grip in first and third person. Previous packages retained.

## 0.1.402-fix8 - Test build

- Added the authored 16x16 red/purple crystal wand texture for the Wild Encounter Tool, replacing its spyglass icon. Includes the entity hitbox dimension/offset mouse-wheel step of one block. Previous test packages retained.

## Source follow-up after 0.1.402-fix7

- Entity hitbox width, height, depth and X/Y/Z offsets now change by one block per mouse-wheel step. Saved values, manual input and validation limits are unchanged. Not packaged.

## 0.1.402-fix7 - Test build

- Packaged receiver edge-contact attacks: nearby-volume navigation, mob sight and attraction range, retaining native reach/cooldowns and wall occlusion. Build, 84 unit tests, manual validation, archive integrity and 666-class / 68-resource comparisons passed. Includes the preceding eight new native scenarios and 43 existing regression cases; native tests were not repeated for packaging. Protocol 45 / manual 195; 2,389,004 bytes. fix6 preserved. SHA-256 b3c9b5d169cfdd2234c596e13d8a928299c20a7e1d4b9a622cdb3092b39a6df9. See docs/TEST_BUILD_0.1.402-fix7.md and its English counterpart. Earlier source-status entries below are historical.

## Source follow-up after 0.1.402-fix6

- Fixed large damage receivers attracting mobs to their center or failing attacks under overhead cover. Receiver-specific navigation, mob sight and attraction distance now use the nearby receiver volume; native reach, cooldowns, damage and wall occlusion remain in force. Eight new native scenarios passed independently, including four zombies attacking four outer faces; 43 existing receiver/retaliation cases passed in regression. Not packaged. See docs/HITBOX_MOB_CONTACT_2026-11-09.md and its English counterpart.

## 0.1.402-fix6 - Test build

- Packaged the shader health-bar name fix and bilingual announcement-tooltip example removal. Protocol 45 / manual 195; 2,385,569 bytes. Build, 84 unit tests, manual validation, CRC, metadata and compiled class/resource checks passed; only two relevant client classes differ from fix5. Earlier 24 visual cases passed across five shader packs and three vanilla graphics modes. Old package preserved. SHA-256 fc84a842104b617b7dbbc644eb922ba2fdab1793c8c40c3831ef0400268e4f39. See docs/TEST_BUILD_0.1.402-fix6.md and its English counterpart. Source-follow-up entries below retain their original development status.

## Source follow-up after 0.1.402-fix5

- Fixed shader-enabled health-bar names by drawing names after entities and retaining bars after particles. Vanilla stages and font/bar materials are preserved. All 24 actual visual cases passed across five local shader packs and three vanilla graphics modes, including Chinese/Latin text, independent sizes, shorelines and real-wall occlusion. Not yet packaged; see docs/HEALTH_BAR_NAME_SHADERS_2026-11-09.md and its English counterpart.

- Removed the final aqua italic example sentence from the natural-spawn announcement tooltip in both languages; retained the formatting help and yellow empty-text notice. Not yet packaged.

## 0.1.402-fix5 - Test build

- Packaged source follow-ups after fix4: Survival projectile aiming, large receiver queries, named Arena countdowns, review fixes and the workbench-protection footer. Protocol 45 / manual 195. Build, 84 unit tests, manual validation, archive integrity, version, 663 class and 68 resource comparisons passed. Development tests excluded; previous fix4 hash unchanged. SHA-256 ab52c720f225d94202a792fb65973eda9de63c270fff607f281b0c5673777975. See docs/TEST_BUILD_0.1.402-fix5.md and its English counterpart. Source-follow-up entries below describe their original development status.

## Source follow-up after 0.1.402-fix4

- Added a bilingual workbench-protection notice to the bottom of natural-spawn management, showing the actual server radius (including disabled protection) and excluding tables inside Sable structures. Reserved footer space. Validated 109 core/module pairs with 2,421 mode subchecks and 3,060 bilingual client cases / 6,178 parameter pages, plus 80 spawning regressions and 2 workbench tests. Initial broad-suite failures and isolated reruns retained; no new MSPT claim. Source only; see docs/ALL_CORE_PATTERNS_2026-11-09.md and its English counterpart.

- Added the creature name centered above the Arena respawn countdown/status, preserving its color formatting. The job snapshots the latest name at death and persists it through core removal and record reload; unnamed/legacy records use the localized default. Existing progress, cancellation and shader label identification retained. All 21 related native respawn tests passed; source only. See docs/ARENA_RESPAWN_NAME_2026-11-09.md.
- Stress-tested 64 real two-block cores with up to 2048 maximum-size entity receivers, movement, arrows, 128 live attacking zombies and production automatic spawning across 64 rules. Fixed large-receiver section-query misses; 397 related native checks passed, followed by preserved full-matrix evidence. Final averages: 512 large receivers 5.50 MSPT, 2048 receivers 15.14, moving 16.80, attacking zombies 24.14; retained a 217.63 ms tick coinciding with autosave. Natural spawning caps and cleanup verified. Source only, default limits unchanged; see docs/HITBOX_STRESS_2026-11-09.md.
- Restored Survival Heart Core target aiming and single/multiple-target controls for Arrow launcher, Gun, Mortar and Slash. Restricts direct target-position origins only, retaining the existing area-skill placement restrictions. Editing, saved-setting constraints and runtime gates agree; aimed projectiles still launch from the muzzle and pay Energy. Manual 195; 123 related native tests and 20 bilingual client cases / 44 parameter pages passed. Source only, not in fix4. See docs/SURVIVAL_AIM_2026-11-08.md.
- Refreshed the cumulative 320-to-current update list with all 82 independent features, later fixes, current behavior, report links and outstanding work: docs/UPDATE_0.1.320_TO_CURRENT.md. Next independent feature number remains 403.

## 0.1.402-fix4 - Test build

- Packaged Guide/Signal Usage simplification with protocol 45 and manual 194; 2,368,842 bytes. Archive, version, 58 brief Usage chapters, compiled screen class, development exclusion and preserved introductory/other-tab content checks passed. Previous package unchanged. SHA-256 50e2622712dc798ecc8197fe52b80d71b69319ec4d151ffdb6157f972d9027cf. Earlier 354 bilingual client chapter-tabs and six screenshots retained; no gameplay tests repeated for packaging. See docs/TEST_BUILD_0.1.402-fix4.md.

## Source follow-up after 0.1.402-fix3

- Manual revision 194: shortened bilingual Usage in 10 Guide and 11 Signal chapters to one brief functional paragraph each, and removed automatic parameter/default notes from Signal Usage. Getting Started, its illustrations, and the other tabs are unchanged. All 58 non-introduction chapters now have concise Usage. Static validation and 354 actual bilingual client chapter-tabs passed, with six screenshots. Source only; the existing fix3 package remains manual 193.

## 0.1.402-fix3 - Test build

- Packaged the collision, brief-manual and zero movement-interval follow-ups below. Internal version 0.1.402-fix3, protocol 45, manual 193; 2,365,938 bytes. Archive/version/resources/collision-bytecode/development-exclusion/hash checks passed. SHA-256 80949fb6e2e1299192467e95f4b2453aa75d6dd057ede6ea64dbc7183bafe20f. Earlier 197 related GameTests and 354 bilingual manual chapter-tabs retained without rerunning for packaging. Previous builds preserved; see docs/TEST_BUILD_0.1.402-fix3.md.

## Source follow-up after 0.1.402-fix2

- Linear movement Interval now accepts 0 seconds (default remains 5 seconds). Zero adds no cooldown; the existing once-per-game-tick guard and non-overlapping action flow remain in place. Clarified bilingual per-field help to describe the interval between action starts. Resource revision 193; compile and manual validation passed, no additional gameplay tests for this lower-bound change. Not packaged.

- Manual revision 192: removed Sound combinations / Combat compatibility guides and placed Server resource limits last among Guides. Rewrote all 37 attack/function chapters in both languages: one brief Usage paragraph, a simple setup Example and focused Troubleshooting. Old parameter essays are not moved to the other tabs; automatic defaults notes are omitted for these categories. Validated 59 chapters / 48 modules plus 354 actual bilingual client chapter-tabs and six screenshots. Source only; see docs/MANUAL_BRIEF_2026-11-08.md.

- Fixed false motion/rotation obstruction from the empty enclosing bounds of other Sable structures. Bounds now select candidates; a bounded loaded-voxel narrow phase checks actual transformed collision shapes. Real obstacles still block, including for shared Auto facing, Gyroscope, Signal turning and Linear movement checks. Own-body conservative bounds remain unchanged. Old guard failed three new empty-space cases; final source passed 197 related native tests. Manual revision 191; protocol 45 unchanged; not packaged. See docs/ROTATION_FRAME_COLLISION_2026-11-08.md.

## 0.1.402-fix2 - Test build

- Packaged the single-line activation illustration and plus alignment, retaining all fix1 changes. Protocol 45, manual 190; 2,353,827 bytes. Archive/version/resources/development-exclusion/hash checks passed; no functional tests rerun for packaging. SHA-256 7d47134945beaf24812b62ac1f9ce339e4d646e349d5ac7a92720cba6ab86513. Previous packages retained; see docs/TEST_BUILD_0.1.402-fix2.md.

## 0.1.402-fix1 - Test build

- Source-only manual follow-up (revision 190): the activation illustration now has a single Empty-hand right-click label, with the plus sign centered in the gap and aligned with the instruction row. Removed the redundant Empty hand caption, normalized the Chinese plus glyph, and updated bilingual authoring sources. Compile and manual validation passed; the existing fix1 JAR is unchanged.

- Packaged the current follow-ups with internal version 0.1.402-fix1, protocol 45 and manual revision 189. Archive/version/resources/development-exclusion/hash checks passed. 2,353,766 bytes; SHA-256 bceb036dffbded235053fec0c3f1559968cef10c03cac8fbbdeb071ebe536676. Preserved 402; multiplayer clients and server must use the matching build. Earlier 254 native and actual preview checks retained without rerunning for packaging. See docs/TEST_BUILD_0.1.402-fix1.md.

## 0.1.402 - Development

- Source follow-up (protocol 45 / manual 189; not packaged): Target alignment now previews six original direction colors as solid weighted lines, with a four-wing yellow front arrow. Linear movement previews client-side individual block shapes at the destination (clear green / blocked red), retaining a bounding-box fallback for unfinished or capped geometry. Viewer-only packets carry at most 64 body IDs and an offset; scans/edges are bounded. Existing conservative collision checks are unchanged. New movement defaults: Internal wireless trigger and 0.3-second step cooldown; saved explicit values remain intact. Requested wheel increments: interval, charge time, stopping gap and movement distance ±1; target search range ±8. Gyroscope's threshold and speed remain visible because both recovery modes use them; bilingual help explains this. Native tests: 254 passed; actual client checks covered L-shaped outlines, obstacle red, six colors, yellow arrow, expiration and visible pixels. See docs/MOVEMENT_ALIGNMENT_PREVIEW_2026-11-08.md. The previously packaged 402 JAR remains protocol 44 / manual 187.

- Follow-up validation: 218 bilingual three-core UI cases / 344 property pages, 20 focused tooltip samples / 44 pages, and illustrated manual checks across two locales and four densities passed. Source manual revision 188; see docs/MANUAL_READING_2026-11-08.md. The released 402 archive remains the earlier revision-187 snapshot.
- Source follow-up, after packaging: replaced shared module paragraphs on numeric fields, choices and apply buttons with dedicated localized property help (449 short entries per locale). Full mechanics remain in Manual. Removed the tutorial diagram label and centered captions on their associated items/actions. Target Sensor radius maximum is now 256 (default 12; loaded targets only). Manual revision 188. These changes are not in the 402 JAR.
- Packaged releases/sablelife-neoforge-1.21.1-v0.1.402.jar before the above follow-up, as requested: 2,322,669 bytes, protocol 44, manual 187, SHA-256 4d14348a0359c57bc93dd2f182d00851e0d96a3fda1068f4b1a1e0bd7be86b3c. Archive integrity, resources, version and development-exclusion checks passed. See docs/TEST_BUILD_0.1.402.md.
- Validation: 59 core-related GameTests, a real world with three independently sized/formatted names and GPU text-pixel checks, and nine Fast/Fancy/Fabulous water/occlusion scenes passed. Bilingual manual schema passed. Screenshots and correction history retained in docs/HEALTH_BAR_WATER_2026-11-08.md; third-party shader packs were not rerun.
- Added an optional creature name centered above marked health bars. Reads the owning core's authored name, preserves colors/formatting, and hides empty names. Name size is independent (0.05–2 blocks, default 0.2), with a separate gap (0–4 blocks, default 0.08). Display is on by default; switching it off hides its sizing controls. Names share bar triggers, damage visibility, distance and cleanup. Uses the bounded existing health snapshot and client text rendering, without spawned entities or frame-by-frame packets. Updated both manual locales and authoring overlays to revision 187. Source only; not packaged.

## 0.1.401 - Development

- Source follow-up (not packaged): aligned Manual and module-picker header/category/list right edges, aligned Loot Back with its controls, and made all three core dashboards use equal 11-pixel left/right content margins. Removed the green announcement save-instruction line while keeping Enter/checkmark saving. Actual client checks and screenshots passed; see the UI follow-up in docs/LOOT_MANUAL_ANNOUNCEMENT_2026-11-08.md.
- Packaged releases/sablelife-neoforge-1.21.1-v0.1.401.jar (2,319,898 bytes, protocol 44, manual revision 186). Includes Sensor 128/conditional front radius, single-copy loot gestures, the three-tab manual and formatted spawn announcements. Assemble and archive/version/resource/schema/bytecode/development-exclusion/hash checks passed; no tests rerun for packaging. SHA-256 7c54cc00b8df31685bb74c0a28f38ee08eba9fb8baeb62bbb61a6ff788a6de6b. Earlier packages retained. See docs/TEST_BUILD_0.1.401.md for validation boundaries.
- Target Sensor now shows Front end radius only in Forward cone mode and accepts detection Radius up to 128 blocks (default remains 12). Retains the ±1 front-radius wheel step. Updated both manual locales and authoring overlays; detection still queries loaded targets without loading remote chunks. Not repackaged.
- Changed Target Sensor's Front end radius (frontWidth) mouse-wheel increment to ±1 as requested. Defaults and bounds unchanged; direct UI step adjustment, not retested or repackaged.
- Fixed loot-template Shift-click copying by using one explicit revision-checked editor action per mouse gesture. Whole-body and part-loot menus reject vanilla bulk quick-move as a source of ghost samples; a held press, drag or release cannot add more copies. Separate deliberate clicks still work, without consuming the source stack. Core-related tests passed 58 cases; the actual client passed gesture and read-only checks at GUI scales 1–4.
- Simplified every manual chapter to Usage / Example / Troubleshooting. Merged all former Settings references into Usage without dropping text, retained six beginner illustrations, and moved editor tips and Example timelines to the corresponding new tabs. Manual revision 185, with bilingual coverage validation.
- Added actual spawn-announcement formatting through the shared bounded name formatter: &0–&f colors, &l bold, &o italic, &n underline, &m strikethrough, &k obfuscation, &r reset and hex colors. Expanded localized hover help with syntax, examples and the 256-character limit. Raw codes persist; broadcasts render styles and keep coordinate styling separate. Related spawn regression passed 47 tests with workbench protection disabled only in the isolated test config; an initial cold-index-interference run is retained.
- Version 401 adds one formatting capability under the cumulative policy; protocol remains 44. Actual bilingual manual checks passed all 366 chapter/tab cases; 10 targeted natural-spawn UI cases passed at GUI scales 1–4, including the full formatting tooltip. Not repackaged; the delivered 400 JAR is unchanged. Detailed evidence: docs/LOOT_MANUAL_ANNOUNCEMENT_2026-11-08.md.

## 0.1.400 - Development

- Renamed the unpublished Survival Heart Core registry ID and resource paths to sablelife:survival_heart_core at the user's request, deliberately without legacy aliases or item migration. Updated recipes/unlocks, loot, mining tags, models, language keys and manual semantic coloring. All 57 core-related GameTests passed after the rename, including actual registry/save/recipe/drop checks; 75 unit checks passed. Packaged releases/sablelife-neoforge-1.21.1-v0.1.400.jar (2,318,866 bytes, protocol 44), SHA-256 632bb01519dddccceb3f830f9f6aff9f83e4c147dcdaa7046dcdca35b5858957. Archive/resource/version/development-exclusion/hash checks passed. Includes manual revisions 181–183 and the laser/faction changes below. Previous packages retained; docs/TEST_BUILD_0.1.400.md.
- Added independent horizontal/vertical laser sweep speeds (0–720 degrees/second, 0 retains legacy automatic timing), shown only for a nonzero corresponding angle in fixed mode. Damage timing and costs remain unchanged; completed sweeps hold and slow ones may end partway. Sweep preview still displays both configured edges.
- Added configurable extra laser particle spacing (0.25–32 blocks, default 4), with bounded sampling and the existing shared player visual budget. Continuous-beam rendering now interpolates consecutive compatible frames without additional packets; stop/body/count/coordinate-space changes reset interpolation.
- Added server setting maximumFactionsPerPlayer (default 1, 0 removes the personal cap). Counts ownership, exempts permission-level-2 operators, retains existing factions and multiple memberships, and enforces the existing global 256-faction storage limit. Empty names and other creation failures now have explicit localized feedback; Enter submits the name.
- Manual revision 183, protocol 44. Added three independently configurable features under the existing cumulative-version policy. Validation: 75 JUnit tests, 173 related GameTests and 14 bilingual actual client UI cases passed; manual coverage validated. No JAR packaged. Evidence and practical limits: docs/LASER_FACTION_CONTROLS_2026-11-08.md.

## 0.1.397 - Development

- Manual revision 182 removes the redundant Sable definition paragraph in both locales and the Chinese physical-module comparison. Rewrites 19 Chinese manual entries and matching Summoned weapon dashboard help using player-facing language and actual UI names, including Release behavior, automation, receiver deduplication and compatibility limits. Corrects the English summoned-item/particle/sound defaults against VirtualModule. Bilingual validation and 488 actual client chapter/tab checks passed, with six screenshots retained. Behavior, version and protocol unchanged; not repackaged. Evidence: docs/MANUAL_CHINESE_READING_2026-11-08.md.
- Reordered the manual's first seven guides as requested, removed standalone Hurt counterattack / Death delay and chunk unloading chapters, and grouped All as Guides / Signals / Attacks / Functions. Appended guides such as Survival Heart Core now appear before modules, with runtime stable grouping for future additions. Titles remain white; only body terms use core-specific or shared signal-purple, weapon-red and function-yellow colors, preserved through wrapping. Renamed the English Wireless entry to Wireless Channel. Manual revision 181; 61 chapters / all 48 module chapters validated. Actual bilingual client checks passed 488 chapter/tab combinations and six representative screenshots. No version/protocol change or new package. Report: docs/MANUAL_COLORS_ORDER_2026-11-08.md.
- Packaged 0.1.397-fix2 (internal 0.1.397, protocol 44) with Linear movement solid-line previews and the latest 15% health-bar rim. Assemble and archive/bytecode/hash checks passed; no tests run. Artifact: releases/sablelife-neoforge-1.21.1-v0.1.397-fix2.jar, 2,307,982 bytes; SHA-256 e3b141e4c8d7131d8c938c0eb7ffb70787e79a6b2a23d4ce456a9fa072a40b3a. Previous packages preserved; see docs/TEST_BUILD_0.1.397-fix2.md.
- Reduced the health-bar base rim from 20% to 15% of the short side in both render paths at the user's request. No tests rerun; included in the pending fix2 package with Linear movement solid-line previews.
- Converted Linear movement Preview's particle route and destination boxes to shared continuous preview lines. Retained green-clear/red-blocked status and existing movement checks. Up to four parts retain individual destination boxes; larger connected machines use an enclosing destination frame to remain within the existing outline packet limit. No protocol change. Not yet repackaged.
- Packaged 0.1.397-fix1 (internal version 0.1.397, protocol 44), including Artifice damage extraction, shoreline depth repair, revised defaults and the latest 20% health-bar rim. Ran assemble and archive/bytecode/hash checks only, as requested; no tests rerun for this package. Earlier tests cover the previous 10% rim. Artifact: releases/sablelife-neoforge-1.21.1-v0.1.397-fix1.jar, 2,307,966 bytes; SHA-256 047f8cda21f96a5e1d500b458cd8b5e637bb84f74561c673fc736f74280a34c7. Prior package preserved; not publicly uploaded. See docs/TEST_BUILD_0.1.397-fix1.md.
- Increased the health-bar border again at the user's request: base rim thickness is now 20% of the short side in both ordinary and shader-pack rendering, twice the previous 10%. No tests run as requested; not repackaged. Earlier rendering reports describe the previously tested border thickness.
- Thickened both health-bar rendering paths' inventory-colored rim and changed newly created defaults to Rounded ends, 1.6 × 0.1 blocks, start distance 0.75, view distance 64, After taking damage, and a five-second hide delay. Explicit saved settings remain intact. Ordinary bars now write actual depth, fixing Fabulous water composition washing out the water-side portion of a shoreline bar while retaining wall occlusion. Bilingual help/examples and manual revision 180 updated. Report: docs/HEALTH_BAR_WATER_2026-11-08.md. Not yet repackaged; version/protocol unchanged.
- Validation: 24 final shoreline/wall/default-size samples across Fast/Fancy/Fabulous and five actual Iris shader packs, 117 core/weapon GameTests and 72 unit tests passed. Retained earlier fixture and pixel-threshold misclassifications; the targeted Complementary rerun changed only the test classifier, not production rendering. Existing packaged JAR and original shader copies remained unchanged.
- Fixed Iron’s Arms ’n Artifice body-hit damage extraction by accepting its public numeric resolveDamage accessor after existing getDamage/getBaseDamage accessors. The native BulletImpactEvent already reaches the existing listener. Reproduced three failures with actual Artifice 1.21.1-1.1.1 before the fix; all 61 real-mod and blast/bridge regression tests passed afterwards, covering flight, velocity/pellet scaling, armor, cancellation and deduplication. Optional test dependencies only; no new production dependency or protocol change. Report: docs/ARTIFICE_DAMAGE_2026-11-08.md. This fix is not in the previously packaged JAR.
- Packaged the 0.1.397 development test JAR on 2026-11-08 (protocol 44). Offline build/check and all 72 unit tests passed; verified archive integrity, version/protocol, required assets/classes, emerald catalog, removed command constants, no development test classes and release-copy SHA-256. Artifact: releases/sablelife-neoforge-1.21.1-v0.1.397.jar (2,307,913 bytes). Test instructions: docs/TEST_BUILD_0.1.397.md; verification: diagnostics/package-0.1.397-verification.json. Prior packages and reports retained. Earlier un-packaged notes below record their original completion state; this package includes those completed changes.
- Removed the legacy `/sablelife options` and `/sablelife configure` authoring commands, which duplicate the core editor and use outdated partial settings. Retained operator inspect/damage diagnostics, residue scan/apply and arena-respawn cancellation. Two new command-tree/execution/permission regressions and related suites passed all 85 tests in a fresh isolated world. Current references reviewed; underlying configuration APIs retained. Report: docs/COMMAND_REVIEW_2026-11-08.md. The user subsequently canceled proposed floating damage numbers and their display switch to avoid extra overhead; neither feature nor a placeholder command was implemented. No JAR packaged.
- Completed the three-core module audit: ordinary Heart Core 48 capabilities, Redstone Core 17, Survival Heart Core 44; all three cores traverse all 48 stored module types for capability boundaries, persistence and config gating. Added 21 cross-core/runtime tests, including unfunded/funded casts, server multipliers, healing and release. Final isolated runs passed 842 GameTests and 72 JUnit tests. The actual client passed 218 bilingual module-page cases and 342 parameter-page checks; representative screenshots inspected. Report: docs/CORE_MODULE_AUDIT_2026-11-08.md; per-module CSV and all initial/retry/final evidence retained. Not an exhaustive parameter, multiplayer soak or all-mod compatibility claim; rare release feedback remains unreproduced. No new JAR packaged.
- Initialize sound-library module fields as empty strings rather than numeric zero, making fresh and loaded configurations consistent. Updated historical test assumptions for dynamic wireless channels, traveling flame fronts, effect editing, solid/striped previews, Redstone presentation capabilities and the third core. Isolated global-cleanup tests from the oversized fixture; the blueprint deletion fixture temporarily raises its own nearby cap and restores it, retaining actual density-limit tests. Game version, protocol and feature count unchanged.
- Added Emerald (120 Energy) and Emerald Block (1,080 Energy, nine times the item value) to the default Refuel catalog, before Diamond and Diamond Block respectively. Unchanged historical default catalogs upgrade automatically; administrator-customized catalogs remain intact. All 54 core UI/config GameTests passed in a fresh isolated world; generated configuration confirmed all 12 entries and their order. Log: diagnostics/emerald-fuel-397-tests.log. No JAR packaged.
- Manual revision 179 adds the standalone Wireless Channels guide after Triggers and signals: five modes and four inside/outside routes, including RX-to-local bridging. Explicitly distinguishes local channel numbers, ordered external frequency items, RX input and internal output. Short examples cover direct module transmission, Follow input and feedback avoidance. Corrected the internal-repeat TX label and outdated pulse-sequence-only mode wording. English/Chinese validation and eight actual client reading/scroll cases passed; screenshots inspected. Final wording/shorter labels statically checked. 63 chapters / 48 modules; game version and feature count unchanged. Report: docs/WIRELESS_CHANNEL_GUIDE_2026-11-08.md. No JAR packaged.
- Manual content revision 178 retains the four tabs, 62 chapters, all 48 modules and six illustrated beginner steps. Rewrote 24 bilingual explanations into shorter titled paragraphs; the edited English text totals fell from 7,807 to 4,416 words (43.4%, not a whole-book metric). Removed the repeated generated property/default table from module Settings, retaining a concise editor-reference note and key behavioral limits in prose. Automation retains its three controls with a four-step channel example. Version and feature count unchanged.
- Validation: bilingual manual validation passed; actual isolated client checks passed both languages across four densities and GUI scales 2/3, including illustrations, separate example, automation, four long Settings chapters, no property dump and end scrolling. Screenshots inspected. Report: docs/MANUAL_READING_2026-11-08.md; log: diagnostics/manual-reading-178-ui-final.log. Existing reports retained; no JAR packaged.
- Added fixed-world vanilla crafting-table exclusion for natural spawning. Default horizontal radius 64, configurable with wildWorkbenchProtectionRadius (0–256; 0 disables), measured to the entire blueprint footprint at every height. Test spawn, bounds-skip and obstacle clearing obey the check before changing terrain. Existing creature movement and damage are unaffected.
- Added a persistent, bounded per-dimension column index. Chunk loading queues budgeted initial verification; actual table changes update counts directly, including multiple tables on one column. Unknown regions wait without forced chunk loads. Across dimensions, indexing visits at most 64 section palettes and 8,192 blocks per server tick. No periodic warm-cache rescans. English/Chinese reasons and manual revision 177 explain scope, unknown-data delays and config. Feature ledger now has 77 additions since 320; next addition 0.1.398. Protocol remains 44.
- Validation passed: the default-on protection scenario, including blocked actual placement and successful spawning after the last table was removed; 120 existing regression GameTests with protection disabled; six actual automatic spawns/activity/unload with protection enabled. The isolated benchmark used 625 forced chunks, 300 tables, mixed cache queries and 40,000 actual block changes. Warm phases added zero block/section scans; 512 queries in one batch averaged 3.665 ms, while 200 actual changes per tick averaged 1.006 total MSPT. Cold indexing had two ticks above 50 ms; this does not establish full-modpack or AI performance. Real reboot retained protection and known empty regions while the area remained unloaded. Full evidence: docs/WORKBENCH_PROTECTION_2026-11-08.md. No player worlds modified; no new JAR packaged.

## 0.1.396 - Development

- Completed the isolated natural-spawn lifecycle replay: six automatically spawned objects (three living, three coreless), autonomous movement, player departure, actual world/Sable unload, four server boots with three real restarts, expiry/absence/manual cleanup on reload, and a final reboot audit. All stages passed; residue scan, old plot cells, ownership, receivers and cleanup jobs were empty, and placement/mining worked at all former spawn sites. Report: docs/WILD_LIFECYCLE_2026-11-08.md. This is a small lifecycle correctness test, not a 64-creature load benchmark.
- Fixed coreless natural-spawn tracking treating a missing in-memory holding entry as permanent deletion. Disk-unloaded objects now retain their records and pending cleanup; native permanent-removal callbacks retire confirmed deleted bodies. Added saved unavailable-state and native deletion regressions; all 66 blueprint/lifecycle/residue GameTests passed in a fresh isolated world. No player worlds modified and no new JAR packaged.
- Manual introduction review, content revision 176: merged A Simple Start and Basic operation checklist into the existing guides, leaving Getting Started, Module management, Mark system and Preview at the front. The illustrated six-step walkthrough appears once; Example now supplies a minimal Arrow configuration. Consolidated troubleshooting, shortened UI/color/preset explanations and retained preset sorting/deletion and ordinary-core Survival editing warnings in the appropriate sections. English/Chinese content now has 62 chapters covering all 48 modules.
- Getting Started / Settings now introduces the three main automation controls: Move set for selection and action windows, Wireless Channel for per-core signal routing and external reception, and Wireless Redstone for marked, external or internal output. Includes an internal-channel particle example and distinguishes local channel numbers from red/blue wireless frequencies. Documentation changes retain game version 0.1.396 and do not count as another gameplay feature.
- Manual validation passed for 62 chapters / 48 modules / both languages. The isolated client checked both languages across four densities and GUI scales 2/3: six illustrations, separate Example, reachable automation text, clipping and end scrolling. Screenshots inspected; log diagnostics/manual-intro-176-final-ui.log. Existing player worlds/configuration were not used.
- Health bar display now has Visible distance (blocks), 1–256 with a default of 64. Each viewer's camera distance to the offset bar center determines whether it renders; outside the radius it hides, and inside it follows the normal trigger and Always / After taking damage rules. This applies to both ordinary display and Preview, before either vanilla or shader-pack rendering. Walls and structure loading still apply.
- The value persists in modules and presets and is included in cosmetic snapshots. Older modules and snapshots without the field use 64; client values are bounded and non-finite values are rejected. The existing network envelope and protocol 44 are retained. English/Chinese labels, help and manual revision 175 updated; feature ledger now has 76 additions since 0.1.320, next addition 0.1.397. No new JAR packaged.
- Validation: all 54 core UI GameTests passed in a fresh isolated world, including the new distance defaults, bounds, saved modules and presets. Actual client validation passed server-to-client distance changes: a one-block radius stopped drawing the three distant bars while retaining their snapshots, restoring 64 resumed drawing, and release cleared the cache. Both existing styles and screenshots passed. Logs: diagnostics/health-bar-396-range-tests.log and diagnostics/health-bar-396-range-world.log. Both-language manual validation passed.

## 0.1.395 - Development

- Health bar display now offers Visibility: Always (default) / After taking damage. The latter starts on actual health loss and resets on further health loss. Hide after no damage defaults to 5 seconds, accepts 0.1–600 seconds, and is shown only for the damage-triggered mode. Healing, zero damage and fully blocked hits do not refresh it. Reactivation uses a new creature identity and cannot retain the previous damage timer; Preview remains available independently.
- Recorded the existing viewing limit in English/Chinese help and manual revision 174: up to 256 blocks, with the structure loaded and normal block occlusion. The bar remains cosmetic and costs no Energy. Protocol remains 44. Feature ledger now has 75 additions since 0.1.320; next addition is 0.1.396.
- Fixed health bars disappearing under Iris. Active shader packs now use a supported vanilla text material with cached shape textures, vertex tint and depth writing; the fill sits slightly in front of the background to avoid coplanar depth rejection. Clients without active shaders retain the existing SDF renderer. No new entities or mandatory shader dependency. Pack post-processing can still change brightness, tint and bloom.
- Validation: all 106 core UI / blast GameTests passed in a fresh isolated world, including damage visibility, timer refresh/expiry, healing, presets and reactivation. Log: diagnostics/health-bar-395-final-tests.log. Native-world rectangle/rounded rendering, server-to-client delivery and release cleanup passed: diagnostics/health-bar-395-final-world.log. Manual validation passed, 64 chapters / 48 modules / both languages.
- Five local shader packs passed 25 actual Iris-pipeline samples covering rectangle, rounded, wall occlusion, night and damage-mode timeout. Final screenshots were inspected. Report: docs/HEALTH_BAR_SHADERS_2026-11-08.md; machine-readable results: diagnostics/health-bar-395-shaders-final-report.json. Original shader ZIP hashes and player Iris settings were unchanged. No new JAR packaged.

## 0.1.394 - Development

- Added a thin health-bar outline using the inventory slot's gray-purple #373542. The SDF renderer keeps soft outer/inner edges and the red fill inside the border, with no extra entity or geometry pass.
- Added Bar style: Rectangle (default) / Rounded ends. The latter uses semicircular ends on a horizontal bar. Style persists through modules/presets and travels in cosmetic snapshots; missing settings or older snapshots default to Rectangle. Existing size, positioning, health, triggers and release behavior are retained. Protocol remains 44.
- Validation: all 52 core UI GameTests passed in a fresh isolated world, including style persistence, snapshot export and legacy defaults. Log: diagnostics/health-bar-394-tests.log. English/Chinese help and manual revision 172 updated and validated. No new JAR packaged.
- Native-world comparison passed both server-to-client style states, rendering, screenshots and release cleanup. Visually checked diagnostics/marked-health-bars-rectangle-394.png and diagnostics/marked-health-bars-rounded-394.png. Log: diagnostics/health-bar-394-world.log.

## 0.1.393 - Development

- Added Health bar display for ordinary and Survival Heart Cores. Mark one connected block face; Width (0.25–16, default 2.5), Height (0.05–2, default 0.2) and Start distance (0–128, default 0.75) position a camera-facing red health bar. Distance begins at the block face and follows the marked part's local direction.
- The world renderer uses a textureless SDF quad with feathered, slightly rounded edges, a dark empty slot, normal depth occlusion and no names/numbers. It follows the part's interpolated render pose without creating entities or changing collision. The module reads its own core's current/maximum health and costs no Energy. Server module switches, enable/trigger controls, presets, Move sets and temporary Preview are supported; Redstone Cores have no health-bar capability.
- Live display is restricted to ALIVE/DYING. A zero-health bar can remain during the ordinary death countdown; release or invalid marks hide it. Client state is scoped to core identity/level and expires. Changed health/settings are sampled every four ticks, unchanged data gets a one-second heartbeat, and moving anchors are transformed locally. Snapshots cap at 64 bars/core and rendering at 128 bars/frame within 256 blocks. Reuses the existing optional-NBT cosmetic packet; protocol remains 44.
- Validation: all 52 core UI GameTests passed in a fresh isolated world, including the three new health-bar tests for health/lifecycle, all face offsets, presets/limits, destroyed marks and server switches. Two unrelated legacy XP tests failed when a prior test world was reused; the fresh run passed all tests. Log: diagnostics/health-bar-393-fresh-tests.log. Actual integrated-world network/render/screenshot/release cleanup passed: diagnostics/health-bar-393-world.log. Screenshot: diagnostics/marked-health-bars-393.png (100%, 55%, 20% from left to right).
- English/Chinese help and manual revision 171 cover the module; validation passed for 64 chapters and 48 modules. No new release JAR packaged.

## 0.1.392 - Development

- Faction lists now show Public in green and Private in gold. Invitation status remains separate and aqua, so an invitation does not hide privacy. Labels reserve space for both badges; full names and creator attribution remain available in the tooltip. The natural-spawn faction chooser uses the same privacy colors; None/Wilds retain their special labels.
- Arrow, Gun and Mortar now offer Custom item rotation X/Y/Z, each -180 to 180 degrees with a zero default. Offsets use the model's local axes relative to flight. Custom gun/mortar items follow their velocity, including the mortar arc; native default appearances remain unchanged. Arrow camera-facing mode applies offsets relative to the camera. These are cosmetic settings and do not alter trajectory, hitboxes, damage or Energy cost.
- Rotation is snapshotted at launch, synchronized to clients and persisted with projectile entities. Older module/projectile saves default to zero; invalid saved angles are sanitized. Protocol is now 44 for the additional entity metadata; client and server must update together. English/Chinese field help and manual revision 170 explain the controls.
- Validation: all 65 blast/sound GameTests passed, including item-rotation launch/sync/reload, legacy defaults, immutable in-flight settings and flight-frame transform checks. Log: diagnostics/projectile-rotation-392-tests.log. No new release JAR packaged.
- Real-client validation passed all four GUI scales: Public/Private text colors, retained invitation badge, existing alliance button colors, and all three rotation inputs for Arrow/Gun/Mortar, including defaults, limits and hover help. Log: diagnostics/faction-projectile-392-ui.log. English/Chinese manual validation also passed.

## 0.1.391 - Development

- Added a Coords checkbox beside the natural-spawn announcement input. Empty or whitespace-only text disables and clears it; a selected checkbox uses the existing green enabled style. The save checkmark remains separate.
- Successful spawns append aqua `[X: ..., Y: ..., Z: ...]` coordinates from the actual blueprint bounds' bottom center. Preview and failed placement remain silent. The choice persists per rule, survives ordinary edits/reloads, and defaults Off for older saves. English/Chinese help and manual revision 169 explain the option.
- Validation: all 45 blueprint/natural-spawn GameTests passed, including persistence, blank-text gating, old saves, cross-dimension broadcasts, plain/coordinate messages and released spawns. Real client checks passed four GUI scales, including checkbox state, green fill, draft retention and input/button alignment. Logs: diagnostics/announcement-coordinates-391-tests.log and diagnostics/announcement-coordinates-391-preview.log. Manual validation passed for 63 chapters and 47 modules in both locales. No new release JAR packaged.

## 0.1.390-fix1 - Local test build (internal version 0.1.390)

- Increased the shared world-preview line weight by 40 percent, retaining the existing window-resolution scaling. All MarkOutline categories and blueprint/entity/natural-spawn preview boxes use the same dedicated render type; their colors, geometry and depth behavior are unchanged. Weapon-preview muzzle diamonds use the same relative increase while retaining their foreground rendering.
- The dedicated render type resets line width after drawing and leaves Minecraft's own line renderer unchanged. GPU pixel-coverage comparison and subsequent ring/filter/trail rendering checks passed.
- GPU comparison measured 7696 versus 10781 lit pixels using identical geometry. Isolated native-world preview checks passed for blueprint, white entity box, natural-spawn projection/expiry and a yellow marked dispenser screenshot (diagnostics/preview-lines-yellow-thicker.png). Log: diagnostics/preview-lines-world.log.
- Packaged with the user-requested internal version 0.1.390 and unchanged protocol 43. The fix1 filename preserves the original package. Gradle build/check and 72 unit tests passed; archive/version/render-class/SHA-256 checks passed. See docs/TEST_BUILD_0.1.390-fix1.md.

## 0.1.390 - Development

- Incoming faction alliance requests now show Accept alliance with a green background and Cancel / decline with a red background. Permission checks and the existing cancel confirmation are unchanged.
- Incoming alliance colors passed real-screen assertions at four GUI scales (diagnostics/alliance-colors-ui.log). All 72 unit tests also passed after the pattern/protocol changes (diagnostics/radial-unit-tests.log).
- Added Radial blur as a Visual Effects spatial pattern beside the default Ring shockwave. It stretches the scene outward from the emission point's world-space projection and leaves a configurable clear center. Strength, duration and center radius are independent of the stored ring settings; irrelevant fields are hidden when switching modes.
- Reuses the existing transient world-position effects, triggers, range attenuation and client Distortion Effects setting. Behind-camera emitters do not blur; HUD rendering remains separate. The shader uses one bounded 12-tap radial pass, choosing the strongest applicable emitter per pixel rather than multiplying taps by event count.
- Retained legacy packet constructors and default ring settings. Network protocol is now 43; both client and server must update. English/Chinese help and manual entries updated.
- Server validation: 151 control/effect GameTests passed, including radial persistence, presets, packet round trip/validation, correct source and radial-only emission with ring strengths zero. Log: diagnostics/radial-pattern-01.log.
- GPU validation passed for projected emitter position, camera shifts, clear center, peripheral streaks, disabled/behind/expired cases and render-state restoration, alongside the existing ring/filter/trail checks. An isolated native world passed network receipt, rendering, camera/filter recovery and expired-event cleanup. Pattern selection/conditional fields passed four GUI scales. Logs: diagnostics/radial-gpu-01.log, diagnostics/radial-native-world.log and diagnostics/radial-pattern-ui.log.

### UI fixes included in 0.1.390

- Aligned the shared Heart/Redstone/Survival Core dashboard close button with the detail column's right edge. Recomputed header spacing from that edge, retaining gaps and distributing integer-width remainders across the navigation area.
- Validation: all three core headers passed exact right-edge, gap and viewport checks at four GUI scales (12 core/scale combinations), with screenshots. Natural-spawn E typing, Backspace/Enter, styled wrapped help and Test spawn alignment also passed. Log: diagnostics/core-header-and-input-ui.log. Existing release JAR unchanged.
- Focused text fields in the natural-spawn tool now consume editing keys before inventory/hotbar/drop shortcuts, fixing E closing the panel while typing. Enter confirmation, Tab navigation and Escape closing retain their existing behavior.
- Split announcement help into a gold bold heading, scope text, yellow bold empty-message guidance, green bold save guidance and an aqua example. Test spawn now uses the remaining row width so its right edge aligns with the controls above and biome panel below.
- Validation: real-screen checks passed at four GUI scales, including typing E in the announcement and biome search, Backspace, Enter, draft retention, colored/bold help styles and exact Test spawn row alignment at odd and even widths. Log: diagnostics/wild-input-tooltip-ui-02.log.

- Named the counted objects Natural Sable structures and clarified that the displayed tracked population cap is shared across all rules and dimensions, with a separate nearby cap; both values come from server config. Automatic spawning remains limited to at most one success per second server-wide. English and Chinese wording updated; no cap or behavior changed. The existing 0.1.389 JAR is preserved.
- Packaged local test build 0.1.390 with protocol 43. Gradle build/check passed; JAR version, protocol/shader/language resources, archive integrity, copied SHA-256 and exclusion of development test classes verified. See docs/TEST_BUILD_0.1.390.md.

## 0.1.389 - Development

- Added a per-blueprint Spawn Announcement input in the old Repeat position. Enter/checkmark saves it; empty disables it. Each successful natural or manual test spawn broadcasts plain text to all online players across dimensions, including released spawns. Previews and failed placements remain silent. Messages persist across reloads and ordinary rule edits; length is limited to 256 characters.
- Moved Release after spawning beside Preview/Test on the following row. Activate core: Yes now uses the same green fill as Enabled; No restores the normal fill.
- The Overworld biome group now excludes ocean types tagged minecraft:is_ocean, including tagged modded oceans. Explicitly added ocean biomes remain allowed; an empty biome list remains unrestricted. Existing rules using the group adopt the exclusion.
- Ocean-group validation: all 45 blueprint/natural-spawn GameTests passed; actual placement rejects every registered ocean-tagged biome through the group, while explicit oceans, plains and an unrestricted empty list remain valid. Log: diagnostics/ocean-biome-group-01.log.
- Measured natural-spawn performance on an isolated dedicated server in two runs; see docs/MSPT_2026-11-07.md. No population cap was raised or performance optimization applied. The benchmark covers 64 idle rules and one coreless Valkyrie, not 4096 live creatures.
- Validation: all 44 blueprint/natural-spawn GameTests passed, including message persistence, cross-dimension broadcast, successful/released spawns, silent preview/failure and blank-message disabling. Four UI scales passed announcement loading/Enter/page-switch checks and Yes/No green-fill assertions. Logs: diagnostics/spawn-announcement-02.log and diagnostics/spawn-announcement-ui-02.log.
- Packaged local test build 0.1.389: Gradle build/check and all 72 unit tests passed. JAR version, language resources, archive integrity and matching SHA-256 verified; development test/benchmark classes are excluded. See docs/TEST_BUILD_0.1.389.md.

- Renamed Skip full bounding box check to Skip full blueprint bounds check and added English/Chinese tooltips explaining full-box versus per-part checks without implying a size change.
- Renamed Max active for this rule to Max simultaneous blueprint spawns, with a tooltip clarifying current-dimension tracked population, coreless structures, released objects and separate server caps.
- Removed Repeat / Only one spawn per dimension. Existing Once/Spawned flags are ignored and omitted from new saves; automatic rules replenish when slots become free and the cooldown has elapsed. Manual tests can no longer be blocked by completed one-shot history. Population and placement checks remain.
- Validation: 44 GameTests passed, including an old completed/empty rule loading successfully, manual population-cap enforcement, automatic replenishment and another full Valkyrie placement. Revised Conditions/Release layout and Density page checks passed four GUI scales.

## 0.1.388 - Development

- Added per-blueprint Spawn settings / Density in Conditions: successful-spawn cooldown, failed-search retry (1-3600 seconds), candidate attempts (1-8), and automatic minimum/maximum distance. Legacy defaults remain 5 seconds, one candidate and 48-96 blocks. Settings persist, survive legacy edits and are validated by the server. Failure retry does not add to the success cooldown. Larger footprints shift the distance band outward; terrain and server population limits still apply.
- Displayed current server-wide and nearby population caps in the density panel. The global one-search-round-per-second throttle remains in place, with at most one successful spawn per round.
- Enabled Release after spawning now uses the same green fill as Enabled in the rule list. Property labels use 85 percent text size and fit the available width; the full cooldown label is shown in the new panel. Lifetime and unattended cleanup remain separate controls.
- Validation: 43 GameTests passed, covering density persistence, invalid ranges, configured distances, multiple search candidates, independent success/failure timing and full native Valkyrie placement. Density editing, invalid-distance controls, page-return drafts, Enter confirmation and the green release button passed real-screen checks at four GUI scales.

## Unreleased - Natural spawning over liquids

- Liquid surfaces are now valid height references. Water or lava below the final spawn volume no longer rejects an airborne blueprint; liquid overlapping checked placement volumes still rejects the candidate, including when obstacle clearing is enabled.
- Added Close at the top right of the natural-spawn management list, using the existing menu close behavior.
- Validation: 40 GameTests passed, including water/lava below placement, liquid overlap rejection, unchanged liquid and actual native placement. The original 63993-block, 22-part Valkyrie shuttle actually spawned over water in an isolated world in approximately 1.1 seconds; this is a single local measurement, not a performance guarantee. The Close layout passed four GUI scales.

## 0.1.387-fix2 - Local test build

- Read-only natural blueprint previews no longer require a valid spawn location. Failed checks show an approximate center-ground (or viewer-height) preview with a warning; test spawning and automatic spawning retain placement checks. Preview never clears terrain or forces chunks to load.
- Replaced the generic placement failure with specific reasons for unloaded footprint chunks, terrain scan limits, water/lava, height, biomes, sky, terrain/structure overlap, protected clearing, population limits and completed one-shot rules. Status text scales to its available width instead of being truncated.
- Replayed the user's original valkyrie shuttle blueprint in an isolated world with six local block-mod dependencies. Its 63993 blocks, 22 parts and approximately 188 by 147 footprint reproduced unloaded-chunk rejection at ground clearances 0, 32 and 128. A deliberately reduced scan budget reproduced scan-limit rejection separately. With the footprint loaded, the default 4194304 scan budget passed.
- Validation: 38 GameTests passed, including original-blueprint replay and protected-block read-only previews. Natural-spawn UI checks passed four GUI scales; the real blueprint boundary reached and expired on the integrated client. A second client replay forced scan-budget rejection and confirmed that the boundary and localized warning still appeared.

## 0.1.387-fix1 - Local test build

- Added maximumWildBlueprintParts, default 128 and configurable from 1 to 512, to remove the unintended 32-part Arena limit from natural template admission. Cached templates honor lowered limits. Templates above the 32-part projection protocol cap use boundary previews; ordinary Arena admission and projection packet limits remain unchanged.
- Release after spawning now disables and ignores Repeat/Only one spawn per dimension while preserving that selection. Released spawns do not consume the saved one-shot state; disabling release restores it. Spawn cooldown remains active.

- Validation: 37 blueprint/natural-spawn GameTests passed, including native placement and boundary preview for a 33-part blueprint, cached/file cap rejection and release overriding a previously completed one-shot rule. JUnit test passed. Release/one-shot disable-and-restore controls passed real-screen checks at four GUI scales.

## 0.1.387 - Local test build

- Added Clear obstacles before spawning, independent of Skip full bounding box check. It uses center-column surface height and clears the full blueprint volume, including raised terrain. Protected/unbreakable blocks, block entities, fluids, life-owned blocks and Sable bodies reject the candidate. Preview stays read-only; failed placement rolls back. Legacy tree-only settings are retained until the new toggle is explicitly changed.
- Added independent natural-spawn limits: 262144 blocks (configurable up to 1048576), 256 MiB template data, 256-block horizontal footprint, 4194304 scan visits and 131072 cleared blocks. Natural template admission no longer inherits Arena block/data limits. Large-template candidate distances expand with their footprint; projection limits stay separate.

- Validation: 36 blueprint/natural-spawn GameTests passed, including a 131073-block template with Arena limits lowered, cached-limit enforcement, protected obstacle rejection and read-only preview. Full-clearing controls passed four GUI scales. Native placement of the full large template was not benchmarked.

## 0.1.385 - Local test build

- Automatic natural encounters now consider living Creative players as well as Survival/Adventure players, while Spectators remain excluded. Density settings are still under discussion. Verified with 34 GameTests, including automatic Creative-player spawning and Spectator exclusion.
- Vertically centered the natural-spawn Current label with its row buttons and rendered only its numeric population in green.
- Restored UTF-8 characters in 161 English strings, including natural-spawn ranges and separators, without changing format placeholders or paragraph structure.

## 0.1.328 - Local test build

- Unified all refueling materials, including copper, in depositValues, with legacy fuel migration. Module-switch tables remain compact while other server settings retain spacing.
- Raised the blueprint projection default to 8192 blocks, added a server cap up to 32768, removed the yellow direction arrow and displayed the selected blueprint name. Network protocol is 42; clients and servers must update together.
- Replaced the natural-spawn Save rule button with an independent Release after spawning checkbox. Future released lives and coreless structures leave population tracking and rule cleanup; existing encounters stay managed. Lifetime, absence cleanup and per-rule population controls are disabled while release is selected. Numeric confirmations, switches, biome transfers and faction selection save conditions without the removed button. Validated with 33 GameTests and checkbox/disabled-field previews at four GUI scales.
- Natural encounter management now uses two columns, shows each template's current tracked population, and provides a confirmed Clear all current action. Cleanup includes plain structures and persists across reloads; generation pauses during cleanup and resumes after the configured cooldown when enabled. Verified with 31 blueprint/natural-spawn GameTests and real-screen previews at four GUI scales.
- Rule removal uses a red X followed by ? confirmation; removing a rule preserves the server blueprint file. Administrative population cleanup suppresses pending loot/XP and keeps unloaded objects tracked until cleanup can complete.
- Fixed arena preview displaying paragraph breaks as missing-glyph boxes in the action bar; use a dedicated single-line boundary color hint.
- Capped Survival Heart Core armor at 90 percent reduction. Default billing is now 1 Energy per percentage point per active second (90 percent = 90/s); synchronized server enforcement, legacy rule clamping, UI bounds, estimates and formula tooltips. Ordinary core armor limits are unchanged.

- Added public/private factions, UUID-based player invitations, acceptance, decline, revocation and member removal. Creators and server operators manage factions; players may join several while each creature retains one faction.
- Added faction Details pages for membership, invitations and multiple bilateral alliances. Either side may end an alliance; alliance relationships are not transitive and grant no editing, interaction or mining rights.
- Connected direct alliances to creature targeting and Sable Life damage. Same-faction Friendly Fire remains separate; cross-faction allied damage defaults off and requires both sides to opt in.
- Validated private faction assignment and configuration copying on the server. Existing factions remain public, including their original Friendly Fire settings; Wilds stays hidden.

- Validation: 85 GameTests passed across core UI, defense and faction namespaces; faction/member/alliance client previews passed four GUI scales. The full manual validator still reports the existing missing Block Interaction chapter; touched translations were checked separately.

## 0.1.327 - Local test build

- Added a Survival Heart Core recipe using the Redstone Core pattern with redstone dust in the center.
- Added Survival Release Shears, crafted from shears and redstone dust. They release Survival Heart Cores while preserving their structure, settings, remaining life and stored energy.
- Fixed a core's Block Interaction module being blocked by its own external interaction policy.
- Clarified the Blueprint generation limit label as the maximum spawns for that module.
- Renamed player-facing Token and Top Up wording to Energy and Refuel.
- Dropped the planned craftable server energy guide.

## 0.1.326 — Local test build

- Added gold, diamond and netherite blocks to default Token deposits at nine times their corresponding material values.
- Upgraded the original 0.1.325 default deposit catalog while preserving customized catalogs.

## 0.1.325 — Local test build

- Added default Token values for iron, gold, diamond, netherite, copper blocks and iron blocks. Blocks redeem for nine times their corresponding ingot value.
- Restored part armor on the Survival Heart Core with reduction-fraction and active-time billing. Insufficient funds suspend protection.
- Unified visible core names and configuration sections around Heart Core, with migration for legacy names and administrator values.
- Added blank lines between server settings while preserving parsed values and existing comments.

## 0.1.324 — Local test build

- Unified Sphere Blast, Ground Strike and Shockwave prices around radius. Shockwave includes configured knockback, and Ground Strike includes all scheduled impact points.
- Movement Freeze billing includes horizontal reach, height and active duration. Rectangular reach uses one quarter of length plus width.
- Updated English and Chinese cost tooltips and verified 123 related GameTests.

## 0.1.323 — Local test build

- Block Interaction can select world blocks or the marked structure part, with a one-block default search box expandable to three blocks per side.
- Gun billing includes range, and homing salvo billing includes projectile lifetime. Ground Strike estimates include all planned extensions, rays or random points. Actual charges use the scheduled valid-terrain points.
- Corrected speed-based attack prices and added enabled impact explosions to projectile costs. Unfunded flamethrowers stop producing new flames and launch audio.
- Removed potion effect properties and target-position casts from Survival Life Cores. Current life uses health-dependent colors, and the top Token balance uses the estimate's yellow number.
- Enabled all supported Redstone Core presentation modules by default for new configurations. Added localized beam style choices and kept the built-in laser visible.

## 0.1.322 — Local test build

- Added the Survival Life Core. Survival players with build permission can edit and recharge it. Its settings show current and maximum life plus the server's activation rate. Depletion plays the configured effect and releases the structure.
- Added server-configured exchange rates and Token billing for activation, healing, native weapons and crowd control. Summoned weapons charge for deployment and each attack. Normal mining drops carry a single-use balance claim redeemable on the same server. Blueprint copies start with zero balance and zero paid life.
- Added horizontal and vertical laser sweeps and a choice between the continuous beam and legacy particles. The continuous beam follows the marked part.
- Added Block Interaction for doors, trapdoors, fence gates, buttons and levers. Start distance and part-local XYZ offsets position the search box. Preview highlights the nearest eligible block.
- Added camera-facing item projectiles for Arrow and three mining modes for ordinary Life Cores. Active ordinary cores remain protected from survival mining. Explosion destruction has its own setting.
- Updated Wild Encounters with a three-column rule manager, server blueprint selection, optional core activation, biome and faction selectors, and double-confirm deletion. Blueprint generation previews can show a model projection for supported templates, together with the bounds and forward direction.

## 0.1.321 — Local development (unreleased)

- Added a server setting for the number of simultaneously active entity hitboxes per Life Core. The default is 8.
- Summoned Weapon previews show an orange muzzle marker through the preview model and nearby blocks.

- Marking selections and rectangular module previews now use continuous outlines in their existing colors. Generic electric spark markers use pale yellow. Wireless Redstone face previews use green diagonal stripes.

- Expanded Wild Encounters to 64 rules. Each rule can track multiple independently expiring creatures, subject to per-rule, nearby and server-wide caps. The automatic scheduler still attempts at most one placement per second.

- Wild encounters now clean up after 180 seconds without a player within 128 blocks by default, independently of forced chunk loading. Absence cleanup and fixed lifetime are configurable in Spawn Conditions.

- Added per-blueprint sky, biome and obstruction conditions to Wild Encounters. Forced forest placement can leave trees for a terrain-breaking core to handle, or optionally pre-clear overlapping logs and leaves. Saved rules can be copied to another blueprint, and server blueprint deletion requires confirmation.

- Added adjustable camera shake frequency to Visual Effects, from 0.1× to 2×. The default 1× preserves the previous shake speed.

- Added an opt-in Entity Hitbox setting that lets nearby hostile mobs and mobs with retaliation goals proactively target an active receiver.

- Added opt-in automatic cleanup for ordinary detached limbs, with a configurable delay. Limb Loot keeps its own timer, and releasing life cancels ordinary cleanup.

- Kept Visual Effects camera shake independent of the source's screen position and corrected shake delivery for nearby viewers using Sable plot coordinates.

- Added per-blueprint factions for Wild Encounters. New spawns default to the hidden Wilds faction with friendly fire off, or can join a server faction selected in the rule. The public faction editor now marks the current membership in green.

- Added an opt-in Life Core setting for normal block drops from player mining when block destruction is allowed. Fixed ownership transfer during Sable assembly so detached parts retain their owner's mining settings.

- Enabled Linear Movement by default on the Redstone Core, with a server switch under `[modules.redstoneCore]`. It supports collision-checked Travel and Move Set scheduling. Teleport and Force movement are locked on this core.

- Restored Move Set on the Redstone Core, enabled by default and configurable under `[modules.redstoneCore]`. It always skips target and health conditions. Priority, weight, timing and repeat settings still apply. Actions can schedule modules allowed on the Redstone Core. The repeat setting takes the Target type slot in the Redstone Core editor.

- Enabled normal mob retaliation after Life attacks when an active Entity hitbox supplies the missing living attacker.

- Added a per-core Owner username for following, facing, navigation and Target sensor, owner-safe attacks, and owner-labelled death messages.

- Added an experimental configurable Entity hitbox module with part-aware core damage, receiver deduplication and lifecycle cleanup.

- Added per-core target priorities while retaining target eligibility and lock rules.

- Added an initial administrator Blueprint generation module with core inspection, trigger/death jobs and optional Life activation.
- Player exclusions now accept offline usernames and match case-insensitively per core.

- Added a controller-wide Move set switch to ignore target/HP conditions during selection, with locked condition controls and preserved timing.

- Added per-core player exclusions that also apply to All targeting.
- Added shared server factions with per-core membership and friendly-fire settings.
- Added an Own death combat event with external wireless pulses that survive core removal.
- Added directional Attraction volumes and stable suction; existing spherical attraction remains the default.

# Changelog

This changelog highlights the major development milestones of Sable Life from its earliest documented prototypes to the current public release.

> **History note:** Sable Life was developed through a long private development cycle before its public release. The public Git history was intentionally reset at **0.1.313** during repository cleanup, so the entries below are a curated reconstruction of the project's development timeline rather than a complete commit-by-commit record.

## 0.1.320 — Module management

- Added References, Groups and Channels through the button next to module search.
- Added Particle density (0-128, default24) to Ongoing area damage. Zero hides particles while damage, burning and effects remain active; the existing shared particle budget still applies.
- Jump to referenced modules or exact Move Set moves; warn before deleting referenced modules.
- Organize modules into collapsible groups without changing execution order. Groups persist in saves, copies and presets; search reveals matching grouped modules.
- Inspect each module's actual internal channel contribution, configured idle sources and consumers, plus channel waveform/latch and external received strength.
- Highlighted Uses/Used by reference labels, added Channel Inspect wheel adjustment by one, and improved search-button alignment and bottom-frame spacing.

## 0.1.319 — Wireless Redstone signal ramps

- Added Ramp output strength mode with adjacent start/end values (0–15) and a configurable duration. Ramps can increase or decrease and hold their final strength while triggered.
- Ending activation immediately clears and resets the ramp, including Move Set action windows. Reverse and Hold/Pulse controls are disabled in Ramp mode; swap endpoints to reverse the ramp. Fixed and Follow input behavior is unchanged.
- Marked-surface, internal-channel and external-frequency outputs share the ramp. Available on Life Cores and permitted survival Redstone Cores.
- Updated the in-game help and manual.

- New Wireless Redstone modules default to selected-surface output instead of all six faces. Existing saved face selections are preserved.

## 0.1.318 — Blueprint placement on reused body storage

- Fixed stale Life protection records rejecting new blocks at empty coordinates after a deleted structure's storage plot is reused. This could affect ordinary Photomancy blueprints without Life Cores, producing missing parts or an `empty plot bounds` placement failure.
- New blocks placed into air discard the retired coordinate ownership before protection checks. Existing non-air living blocks retain deletion and replacement protection.
- Existing blueprint files remain usable without re-saving. Confirmed in the previously affected player world; regression coverage includes 13 consecutive imports on reused storage.

## 0.1.317 — Straight firing angles and optional presentation modules

- Added horizontal and vertical firing angles to Flamethrower, Arrow, Gun and fixed-mode Laser. Flamethrower/Arrow/Gun/Slash/Laser now position the muzzle along the original selected direction before firing angles, fans or sweeps are applied. Previews distinguish the positioning line and launch point; target aiming starts at that muzzle and summoned weapons retain their own muzzle settings.
- Added Visual Effects, Distortion trail, Particle effects, Item display and Trigger sound as optional survival Redstone Core modules, all disabled by default in server configuration.
- Enabled survival particle, item and trigger-sound editors when their server switches are on. Existing control modules keep their defaults; Move Set, ordinary Sound and BGM remain unavailable on the Redstone Core.

## 0.1.316 — Server module switches and survival controller restrictions

- Restricted the survival Redstone Core to nine signal/control modules, adding Signal logic and removing Move Set, sound, item display and visual-effect modules.
- Removed Move Set and Hurt triggers from the Redstone Core. Unsupported legacy module settings remain stored but cannot run or be copied on that core.
- Added fixed server-owned `[modules.lifeCore]` and `[modules.redstoneCore]` boolean switches. Disabled types are hidden from Add/preset catalogs, retain existing settings with a server-disabled status, and cannot trigger or bypass policy through copy/import/edit requests.
- Module policy changes clear loaded cores' transient runtimes; restoring a switch permits normal triggering without deleting configuration.
- Release JAR filenames now use `sablelife-neoforge-1.21.1-v<version>.jar`.

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

