# 0.1.53: Part immunity / 部位免伤

Add the virtual Part immunity module and mark one block anywhere on the desired Sable part. Protection resolves by that part's unique identity, not by the core or the complete connection chain. Every block in the marked part shares protection; other connected parts keep their own damage behavior. Preview draws a blue outline around the entire marked part.

Properties:

- Damage reduction: 0–100%, default 100%. At 100%, hits on that part cause no core health loss or successful-hurt sounds/BGM activation. At 50%, an otherwise 10 HP hit loses 5 HP.
- Protection active when: Signal on (default) or Signal off. With an anchor/wireless trigger and Signal off, applying redstone removes protection. With Always trigger, protection remains active regardless of this choice.
- Existing trigger choices: Always, anchor redstone, internal wireless and external wireless reception. Disable/delete the module to remove its protection.

Multiple active modules on one part use the highest reduction rather than stacking. Saved worlds and data copier preserve reduction/activation/trigger settings through the existing virtual module format. Re-mark after copying data to a different creature, as with other anchored modules.

Hit-aware combat now routes player melee, vanilla arrows/tridents and virtual gun/mortar impacts through `HeartBlockEntity.damageAt(hitBlock, amount)`. Administrative core damage and physical core-removal death handling remain locationless so lifecycle cleanup is not blocked. Physical block protection remains controlled by Core Settings: turn block destruction Off if parts must also be impossible to cut or delete. Part immunity itself controls health damage, not block replacement.

## Pending in-game checks

- [ ] Mark the body: punches/arrows/gun shots there lose no HP, while hits on an unprotected arm still do.
- [ ] Mark the arm instead: all arm blocks are protected; the body is not.
- [ ] 50% reduction halves the health loss of the same attack.
- [ ] 100% immune hits do not trigger hurt audio or first-hit combat BGM.
- [ ] Anchor redstone + Signal off: no power means immune; applying power makes the part vulnerable immediately.
- [ ] Internal wireless and external RX alternate protection correctly; Always does not depend on redstone.
- [ ] Disable/delete/re-mark works; duplicate modules use the highest active reduction.
- [ ] Preview follows the whole part during movement; disconnecting/unloading invalidates protection safely.
- [ ] Rejoining, data copying and Sable assembly transfers preserve configuration/markers appropriately.
- [ ] Core health damage/death cleanup and the separate block/explosion protection settings still work.

Validation: assemble only, no client launch or gameplay tests. Other mods' projectile compatibility depends on whether they use the supported impact path or the public hit-aware damage API.
