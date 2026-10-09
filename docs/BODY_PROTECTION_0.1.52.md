# 0.1.52: block and explosion protection

Core Settings now has three named toggles: Allow block destruction, Allow explosion destruction, and Boss bar. Both destruction toggles default to Off for new/reset cores. Existing cores retain their saved block-destruction setting; the new explosion option defaults to Off. Disable both on existing creatures for full protection through the intercepted game APIs.

| Allow block destruction | Allow explosion destruction | Result |
|---|---|---|
| Off | Off or On | Reject mining, destroyBlock and replacement by a different block, including air. |
| On | Off | Permit cutting/mining; remove creature blocks from standard explosion destruction lists. |
| On | On | Permit block destruction and explosions. |

Protection applies to all captured creature blocks, including the core and owned detached limbs. It does not make creature health invulnerable. State updates of the same block type remain allowed so redstone, machinery and the active core work normally. Controlled Sable assembly transfers and lifecycle death/limb cleanup have scoped bypasses; normal release removes ownership and protection. Copying data includes both toggles, and lifecycle protection is saved across world reloads.

The additional chunk-level interception closes the previous bypass where another mod called setBlock/setBlockAndUpdate/removeBlock or chunk.setBlockState directly. It returns failure before replacement and its ownership/anchor callbacks. Mods using standard Minecraft world/chunk mutation APIs are covered by block destruction Off. A mod rewriting chunk sections or removing block entities directly without those APIs is not guaranteed covered; compatibility with a specific gun/explosive mod still needs an in-game check.

## Pending in-game checks

- [ ] Existing core: turn both destruction options Off; survival and creative breaking leave core/body intact.
- [ ] TNT and modded explosives leave creature blocks intact; health damage remains possible.
- [ ] Modded guns with forced block destruction do not remove protected blocks.
- [ ] `/setblock` replacement and `/fill ... air` reject protected positions via standard world APIs.
- [ ] Block destruction On + explosion Off allows limb cutting but protects against TNT.
- [ ] Both On restores normal explosion destruction.
- [ ] Rejoining and data copying preserve both toggles.
- [ ] Sable assembly/disassembly, redstone state updates and copycat material changes still work.
- [ ] Death and detached-limb reward cleanup complete despite protection, without ordinary material drops.
- [ ] Releasing life removes protection and ordinary blocks become destructible.

Validation: assemble only; client not launched and gameplay compatibility remains untested.
