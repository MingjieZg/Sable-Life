# Multiple attack effects — 0.1.49-dev

Melee, Area pulse, Arrow, Gun and Mortar now expose Attached effects. The button opens a two-column selector. The left side lists registered vanilla and mod effects by localized name, with name/ID search and paging. Select a left row and press Add selected to copy it into the right list; duplicates are disallowed. Select a right row to remove it or edit its duration and level. Returning restores the selected attack module.

Each effect has its own duration (0–60 seconds, initially 3) and level (1–5, initially 1). Duration 0 disables that entry. Inputs support Enter, apply buttons and wheel steps (0.1 seconds / one level). Inventory hotkeys are consumed during text input. Active cores allow viewing but cannot change authoring data. The server verifies menu identity, creative permissions, core proximity, module identity, effect registry membership and numeric limits. At most 128 distinct effects can be attached.

Melee and pulse apply the list on successful hits. Arrows use vanilla custom arrow effects, whose durations are preserved without the ordinary potion-type duration reduction. Gun/Mortar copy effects into their projectile data on launch. Direct hits and visible victims in the blast radius receive the effects; an entity hit directly by a shell receives the list once rather than also repeating instant effects in that shell's explosion. Instant effects use their immediate application path for melee, pulse and Gun/Mortar; arrows retain vanilla arrow behavior. Effects retain vanilla/mod resistance and immunity behavior.

Effect lists save with modules and participate in existing Data Copier configuration copies. Saved Gun/Mortar shots retain their launch settings. Old Melee single-effect saves (including LegacyEffect IDs) migrate to one selected entry with the original level/duration. An explicitly cleared new list stays empty on reload.

Validation: assemble and static integration/layout review. No gameplay tests and no client launch.

Playtest checklist:
- Add two different effects to each of the five supported attacks, set different durations/levels, and verify both on a surviving target.
- Verify instant effects and mod effects where installed; immune targets should retain their normal behavior.
- Verify Gun/Mortar explosion victims away from the center receive effects, and direct hit victims do not receive an instant effect twice from one shell.
- Search, page both lists, add/remove, Enter/wheel edit, return to the original module, and try viewing an active core.
- Save/reload and Data Copier transfer should preserve the list; an old single-effect Melee should migrate, and removing its effect should stay cleared after reload.
