# Arena respawn and Sable Photomancy

Starting with Sable Life **0.1.314**, Arena automatic respawn accepts Sable Photomancy **1.0.1 or newer** when its required blueprint interfaces remain compatible. **1.0.1** is the compilation and native-test baseline, not an exact runtime version gate.

Photomancy is optional for the mod, but required for recording Arena respawn templates and rebuilding the structure after death. Arena boundaries and other Sable Life features do not require automatic respawn. Recording still requires a creative operator.

## What is checked

Before registering the Heart blueprint mapper or enabling respawn, Sable Life inspects class files without initializing Photomancy classes. The check covers the blueprint members actually referenced by the mapper and respawn bridge, including names, JVM descriptors, public access and static/instance usage. It also verifies required interface callbacks and the trusted-template `BlueprintNbtPolicies.resolve` hook used to restore recorded module data and inventories.

Mapper registration, the required NBT hook and Arena controls share the same compatibility decision. A missing mod or failed contract check disables this integration; the server log identifies the missing/incompatible member. Existing respawn jobs pause when the integration is unavailable. Optional diagnostic hooks are inspected separately: changes to tool-error diagnostics do not by themselves disable respawn.

Compatible API additions are allowed. Removing a used method, changing its return/argument types or static status, removing a placement callback, or adding an abstract callback the mapper does not implement is rejected. The check does not use equality against Photomancy's version string.

## Limits

Matching interfaces cannot prove identical behavior. Future changes to blueprint semantics, coordinate mapping, events or NBT policy still require native regression tests. This is forward compatibility for preserved interfaces, not a guarantee for every future Photomancy release.

If an update disables respawn, retain the world and consult the server log before changing the encounter. Use a compatible dependency version or an updated Sable Life adapter. Do not bypass the trusted-template NBT check: it protects complete restoration and confines full NBT loading to administrator-recorded respawn templates.

Create and Sable participate in broader world/physics behavior and retain their declared version ranges. This change does not relax their compatibility requirements.

## Verification and maintenance

- `PhotomancyApiContractTest` checks the baseline JAR, compatible additions, missing dependencies/methods, changed descriptors/static members, callback changes and independent diagnostic hooks. Run the documented dependency setup before unit tests.
- Native Arena tests cover record/place, inventory and mark restoration, repeated generations and large templates. A separate optional-absence test confirms that a missing Photomancy installation does not prevent ordinary Life activation.
- Optional dependency checks cannot substitute for multiplayer, addon-specific or new-version behavior testing. Rerun the native Arena suite when updating the pinned Photomancy development dependency.
