# Sable Life datapack modules

Copy the [Alarm example](../examples/datapack-alarm/) into the world's `datapacks` directory, with `pack.mcmeta` at the pack root. Enable the pack and run `/reload`. Add **Alarm** in the core's module picker, select an internal wireless channel as the trigger, then mark a block face. The alarm plays once when the trigger becomes active. Let the channel go low to rearm it.

The server loads the pack and sends the available module names and options to clients. Use the same Sable Life build on both sides. The example runs with Sable Life and its usual dependencies.

## Define a module

Place a UTF-8 JSON file at `data/<namespace>/sablelife/modules/<name>.json` in your own namespace. Its saved ID is `<namespace>:datapack/<name>`. Keep the ID and option IDs stable to preserve player settings. Datapack priority applies when two packs provide the same file.

- `format` is `1`. `name`, `description` and option `label` accept a string or an object with `en_us` and optional `zh_cn`. The English text is the fallback.
- `cores` lists one or more of `heart`, `redstone` and `survival`. `mark` is `none`, `point` or `area`.
- `options` is optional and supports up to four numeric options with `id`, `label`, `min`, `max` and `default`. Options added later use their default until edited.
- `on_start` supports `play_sound` with a registered sound ID. The sound uses the first marked face or the core for `none`. Volume is 0–2 and pitch is 0.5–2. Both default to 1. Use a number or a reference such as `{"option": "volume"}`. A referenced option must stay inside the same bounds.

The server reads up to 64 valid definitions, each up to 8 KiB. Bad definitions log the file, pack and error and are skipped individually. Removing a definition stops the saved module. Its settings, mark and trigger stay in the core and are used again when the definition returns. An unchanged definition continues its current activation through `/reload`. Server administrators can also disable a module by ID in `modules.disabledAddonModules`.

## Verify the example

Build the test JAR with Java 21. The three commands run an integrated server, a dedicated server with two clients, and three dedicated server boots on the same world. The client tests add the alarm through the dashboard and use `/reload` to test a malformed file, an unknown action, removal and restoration.

```powershell
.\gradlew.bat jar gameTestClasses prepareGameTestServerRun prepareCoreModuleAuditClientRun
python scripts/test-module-addon.py --datapack --singleplayer
python scripts/test-module-addon.py --datapack --network
python scripts/test-module-addon.py --datapack
```
