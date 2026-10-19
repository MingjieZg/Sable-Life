"""Create a recoverable snapshot without Gradle caches or generated runtime logs."""
from pathlib import Path
import hashlib, json, zipfile, argparse
from datetime import datetime, timezone, timedelta
root = Path(__file__).resolve().parents[1]
out = root / "backups"
out.mkdir(exist_ok=True)
version = next((s.split("=",1)[1].strip() for s in (root/"gradle.properties").read_text().splitlines() if s.startswith("mod_version=")), "unknown")
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument("--date",help="Client-local date YYYY-MM-DD, when different from the host")
args=parser.parse_args()
created=datetime.now(timezone(timedelta(hours=8)))
if args.date:
    day=datetime.strptime(args.date,"%Y-%m-%d")
    created=created.replace(year=day.year,month=day.month,day=day.day)
stable_file=root/"docs/STABLE_VERSION.json"
stable=stable_file.exists() and json.loads(stable_file.read_text(encoding="utf-8")).get("version")==version
archive = out / ("Sable-Life-"+version+("-stable" if stable else "")+"-"+created.strftime("%Y%m%d-%H%M%S")+".zip")
roots = ["src", "docs", "scripts", "gradle", ".git", ".dev/mods", ".dev/compile-libs", ".dev/test-mods", ".dev/epicfight", ".dev/cleanup-mods", ".dev/iron-spells-review", ".dev/iron-spells-testmods", "config", "defaultconfigs", "mods", "exports/stable-"+version, "exports/development-"+version, "releases/development-"+version, "build/libs/sablelife-"+version+".jar"]
# Optional compatibility regressions need their pinned private runtime and replay fixtures.
roots += [".dev/arena-addon-mods", ".dev/mounted-cleanup-mods", ".dev/dragon-replay-mods", ".dev/dragon-toolgun.jar", ".dev/dragon-template.nbt", ".dev/arena-replay/template.nbt"]
for runtime in ["client", "server", "gametest"]:
    for name in ["saves", "world", "config", "defaultconfigs", "schematics", "resourcepacks", ".sable", "mods"]:
        roots.append("runs/"+runtime+"/"+name)
    for name in ["options.txt", "servers.dat", "server.properties"]:
        roots.append("runs/"+runtime+"/"+name)
files = {p for p in root.iterdir() if p.is_file() and p.name not in {"local.properties", ".env"} and p.suffix.lower() not in {".log", ".hprof", ".key"} and not p.name.startswith(("hs_err_pid", "replay_pid"))}
for name in roots:
    path = root / name
    if path.is_file(): files.add(path)
    elif path.is_dir(): files.update(p for p in path.rglob("*") if p.is_file() and not p.is_symlink())
files = sorted((p for p in files if p.name != "session.lock" and "__pycache__" not in p.parts and not ("config/sablelife/minecraft_sounds/" in p.relative_to(root).as_posix())), key=lambda p: str(p))
manifest = []
with zipfile.ZipFile(archive, "w", zipfile.ZIP_DEFLATED, compresslevel=6) as z:
    for p in files:
        data=p.read_bytes(); rel=p.relative_to(root).as_posix()
        z.writestr(rel,data)
        manifest.append({"path":rel,"bytes":len(data),"sha256":hashlib.sha256(data).hexdigest()})
    z.writestr("BACKUP-MANIFEST.json", json.dumps({"version":version,"status":"stable" if stable else "development","created":created.isoformat(),"timezone":"Asia/Singapore","files":manifest},ensure_ascii=False,indent=2))
    z.writestr("RESTORE.txt", "Extract this ZIP to a new folder. Install Java 21 and set JAVA_HOME. Run gradlew.bat build, then gradlew.bat runClient. Gradle downloads omitted build caches on first run. Worlds and custom sounds are under runs/client. Exported vanilla minecraft_sounds are omitted; use the MC button to regenerate them. Keep your original folder until restoration is verified.\n")
with zipfile.ZipFile(archive) as z:
    bad=z.testzip()
    if bad: raise RuntimeError("Corrupt ZIP entry: "+bad)
digest=hashlib.sha256(archive.read_bytes()).hexdigest()
archive.with_suffix(".zip.sha256").write_text(digest+"  "+archive.name+"\n")
print(json.dumps({"archive":str(archive),"files":len(manifest),"bytes":archive.stat().st_size,"sha256":digest}))
