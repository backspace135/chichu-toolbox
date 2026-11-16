import argparse
import pathlib
import zipfile

parser = argparse.ArgumentParser()
parser.add_argument("--version", type=int, choices=(1, 2, 3), required=True)
args = parser.parse_args()
root = pathlib.Path(__file__).resolve().parents[1]
source = root / "testing/root-module"
output = root / "build/root-fixture"
output.mkdir(parents=True, exist_ok=True)
archive = output / f"chichu-m3-v{args.version}.zip"
with zipfile.ZipFile(archive, "w", compression=zipfile.ZIP_DEFLATED) as target:
    for path in source.rglob("*"):
        if not path.is_file() or path.name == "README.md":
            continue
        # Versions 1 and 2 stay boot-inert; v3 adds observable boot-stage markers.
        if args.version < 3 and path.name in ("service.sh", "post-fs-data.sh"):
            continue
        data = path.read_bytes()
        if path.name == "module.prop":
            data = data.replace(b"version=1.0", f"version={args.version}.0".encode()).replace(b"versionCode=1", f"versionCode={args.version}".encode())
            if args.version >= 3:
                data = data.replace(
                    b"no boot scripts, mounts, properties or reboot.",
                    b"post-fs-data and service stage markers for cross-reboot lifecycle.")
        target.writestr(str(path.relative_to(source)), data)
print(archive)
