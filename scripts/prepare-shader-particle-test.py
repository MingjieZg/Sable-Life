"""Copy a local Iris/Sodium/Bliss installation into an isolated test fixture; never edit the source instance."""
from pathlib import Path
import argparse, shutil, zipfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("instance", type=Path)
parser.add_argument("--pack", default="Bliss_v2.1.2_(Chocapic13_Shaders_edit) + Clrwl_1.0.5.zip")
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
mods = root / ".dev/shader-test-mods"
runtime = root / ".dev/shader-runtime"
merged = root / ".dev/shader-merged"
for folder in (mods, runtime, merged): folder.mkdir(parents=True, exist_ok=True)
for pattern in ("iris-neoforge-*.jar", "sodium-neoforge-*.jar"):
    matches = list((args.instance / "mods").glob(pattern))
    if len(matches) != 1: raise ValueError(f"Expected one installed {pattern}")
    shutil.copy2(matches[0], mods / matches[0].name)

# Sodium 0.8 ships a bootstrap LIBRARY and a nested game jar. A dev classpath needs
# both sets of classes in a game-layer jar, without the bootstrap's service locator.
with zipfile.ZipFile(next(mods.glob("sodium-neoforge-*.jar"))) as outer:
    for name in outer.namelist():
        if name.endswith(".jar"): (runtime / Path(name).name).write_bytes(outer.read(name))
    data = {n: outer.read(n) for n in outer.namelist() if not n.endswith("/")
            and not n.startswith(("META-INF/jarjar/", "META-INF/services/"))}
with zipfile.ZipFile(next(runtime.glob("*-mod.jar"))) as inner:
    data.update({n: inner.read(n) for n in inner.namelist() if not n.endswith("/")})
data["META-INF/MANIFEST.MF"] = b"Manifest-Version: 1.0\r\n\r\n"
with zipfile.ZipFile(merged / "sodium-dev.jar", "w", zipfile.ZIP_DEFLATED) as archive:
    for name, contents in data.items(): archive.writestr(name, contents)

run = root / "runs/bundled-particle-world-preview"
for folder in (run / "shaderpacks", run / "config"): folder.mkdir(parents=True, exist_ok=True)
shutil.copy2(args.instance / "shaderpacks" / args.pack, run / "shaderpacks" / args.pack)
(run / "config/iris.properties").write_text(
    f"allowUnknownShaders=false\nenableShaders=true\nshaderPack={args.pack}\n", encoding="utf-8")
print("Prepared isolated shader fixture. Run gradlew runBundledParticleWorldPreview -PshaderParticleTests=true")
