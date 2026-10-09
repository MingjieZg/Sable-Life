# Heart crystal and Copycat casing — 0.1.48-dev

Each of the six faces has the same centered red crystal diamond. The default casing is a muted metal panel. The 32-pixel texture uses redstone reds, shaded facets and a small highlight; the crystal is a separate cutout surface slightly above the casing to prevent overlapping faces from flickering.

The Heart now inherits Create's Copycat block and block entity. Right-click with a supported block to apply its material to the casing. Right-click with a Create wrench to remove the material; Create handles consuming and returning the sample in survival. Right-clicking the same directional material cycles its orientation. Empty-hand right-click still opens the Heart dashboard in creative, and sneaking with an empty hand still activates the creature.

The custom baked model substitutes material quads for the casing only. Crystal quads remain untinted and render on all six faces, including the top and bottom. Material render layers and biome colors are retained. Disguise material and the Heart's existing life/module/wireless data are saved together and synchronized through Create's block entity update handling. Old Hearts without material data load with the default casing.

Sable's block transfer does not refund the disguise item. The Heart retains its active light emission. Sneak-wrenching only removes disguise material: it never extracts the core, regardless of life state or whether a disguise is present.

Validation: `scripts/dev.ps1 -Task assemble`, model/PNG resource checks and texture preview. No client launched and no gameplay tests run.

Preview: `previews/heart-face-0.1.48.png`.
