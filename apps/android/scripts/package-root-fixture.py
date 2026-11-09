import argparse
import pathlib
import zipfile

parser = argparse.ArgumentParser()
parser.add_argument("--version", type=int, choices=(1, 2), required=True)
args = parser.parse_args()
root = pathlib.Path(__file__).resolve().parents[1]
source = root / "testing/root-module"
output = root / "build/root-fixture"
output.mkdir(parents=True, exist_ok=True)
archive = output / f"chichu-m3-v{args.version}.zip"
with zipfile.ZipFile(archive, "w", compression=zipfile.ZIP_DEFLATED) as target:
    for path in source.rglob("*"):
        if path.is_file() and path.name != "README.md":
            data = path.read_bytes()
            if path.name == "module.prop":
                data = data.replace(b"version=1.0", f"version={args.version}.0".encode()).replace(b"versionCode=1", f"versionCode={args.version}".encode())
            target.writestr(str(path.relative_to(source)), data)
print(archive)
