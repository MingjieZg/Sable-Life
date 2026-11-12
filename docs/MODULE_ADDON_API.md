# Creating a Sable Life module addon

An addon is a separate NeoForge mod. Declare Sable Life as a required dependency on both sides and compile against the matching Sable Life JAR. Install the addon and Sable Life on the server and all clients. Register definitions in your mod constructor on both sides. Only the server creates and runs behavior instances.

Use `dev.sablelife.api.module` for integration. The [Marker Chime example](../examples/module-addon/src/main/java/dev/sablelife/exampleaddon/ExampleModuleAddon.java) registers a function module with a numeric volume option and one marked block face. Give each module a stable `namespace:path` ID in your own namespace and supply the name, description and option translations in your own language resources.

## Try the example

From the project root, use Java 21 to build the matching Sable Life candidate and example addon.

```powershell
.\gradlew.bat exampleAddonJar
```

`build/libs/` contains the Sable Life JAR and `sablelife-example-addon-1.0.0.jar`. The example declares Sable Life `0.1.404` as its minimum version. Use the same Sable Life build on the server and all clients. When adapting the example to your own project, declare the version range you support in your addon’s `META-INF/neoforge.mods.toml`.

1. Install both files and Sable Life’s dependencies in an isolated test profile.
2. Open an assembled structure’s core dashboard. Use **+ Add** to select **Marker Chime**. Set **Chime volume** and mark a face on the structure.
3. Set the trigger to an internal wireless channel and give that channel a signal. The core must be operational. The chime plays once at the mark when the trigger becomes active. Let the signal turn off before triggering again.
4. Save and reopen the world. The module, option, mark and trigger remain in the core. If you remove the addon, its saved row is disabled and identified by its ID. Reinstall the addon to make that row usable again.

## Build your own addon

In your NeoForge 1.21.1 project, place the Sable Life JAR in `libs/` and add it to your compile classpath. Install Sable Life and its dependencies in your test profile.

```groovy
dependencies {
    compileOnly files('libs/sablelife-neoforge-1.21.1-v0.1.404.jar')
}
```

Use your own mod ID and copy the Sable Life dependency entry from the [example metadata](../examples/module-addon/src/main/resources/META-INF/neoforge.mods.toml). Register your definition from your `@Mod` constructor. Include your translations under `assets/<your_mod_id>/lang/` when you build your addon JAR.

## Module lifecycle

- `ModuleDefinition` declares the module’s display category, supported core types, mark mode and numeric options. The dashboard saves and validates option values on the server. Keep IDs and option keys stable across versions.
- `NONE` needs no mark. `POINT` uses one marked face. `AREA` uses the two corners accepted by the normal marking system. `ModuleContext` exposes the marks and their transformed world-space normals, plus the current core position.
- `ModuleBehavior.onStart` runs when an operational core’s module first has a valid mark and an active trigger. `onTick` also runs that tick and continues while those conditions hold. `onStop` runs when the activation ends or the core runtime is cleared. The next activation uses a fresh instance. Store persistent player settings as options and keep temporary state in the behavior instance. Use the context within the callback.
- Server administrators can disable individual IDs via `modules.disabledAddonModules` in `sablelife-server.toml`. The module picker and scheduler use the server’s decision. Addons inherit the core’s editing permissions and trigger modes, including Move Set action windows. Declare Survival support only when your behavior follows the server’s intended Energy and gameplay rules. Built-in Energy pricing applies to built-in skills.

## Verify the candidate

Use the project’s prepared NeoForge runtime with Java 21 and Python. Each script invocation makes a fresh isolated run directory and retains its logs.

```powershell
.\gradlew.bat exampleAddonJar gameTestClasses prepareGameTestServerRun prepareCoreModuleAuditClientRun
python scripts/test-module-addon.py
python scripts/test-module-addon.py --singleplayer
python scripts/test-module-addon.py --network
```

The default scenario saves an assembled structure and restarts the same world with the addon removed, then installed again. The client scenarios use the module picker, numeric editor, trigger selector and marking interaction. The multiplayer scenario runs an English author and a Simplified Chinese observer against the same dedicated server.
