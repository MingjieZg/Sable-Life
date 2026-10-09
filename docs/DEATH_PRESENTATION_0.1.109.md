# Core death presentation

Version 0.1.109 adds two pages to Life Core Settings for death effects. The current Move Set panel is retained as the normal panel.

Version 0.1.110 consolidates Core Settings into three compact pages. Death particles now use the same two-column searchable selector as module particles, with up to 16 selected types and individual lifetime and size properties. Previous single-particle settings migrate automatically. Count and spread apply to each selected particle type.

Select a death particle and sound, then adjust particle count, spread, duration and scale, and sound volume and hearing range. A particle duration of zero preserves that particle's native lifetime and motion. Preview plays the configured effects without changing health or starting death.

Effects play once at the lethal hit, at the core's world position, before the existing delayed physical breakup. These effects do not deal damage or destroy blocks. Both selections default to None, preserving existing creatures' appearance until configured. Settings persist through saving and configuration copying.
