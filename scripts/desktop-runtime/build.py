"""Build an investigation-only onedir runtime on the native target host."""
import argparse
import hashlib
import json
from pathlib import Path
import platform
import shutil
import subprocess
import sys


COMMIT = "118a000a83ea31c4f427fe5225abfe964546e05e"
ROOT = Path(__file__).resolve().parent


def run(*args):
    subprocess.run([str(arg) for arg in args], check=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    source = args.source.resolve()
    output = args.output.resolve()
    if platform.python_version() != "3.13.11":
        raise ValueError("Build with CPython 3.13.11")
    target = {("Darwin", "arm64"): "aarch64-apple-darwin",
              ("Windows", "AMD64"): "x86_64-pc-windows-msvc"}[
                  (platform.system(), platform.machine())]
    actual = subprocess.check_output(["git", "-C", str(source), "rev-parse", "HEAD"], text=True).strip()
    if actual != COMMIT:
        raise ValueError(f"Expected upstream {COMMIT}, got {actual}")
    if subprocess.check_output(["git", "-C", str(source), "status", "--porcelain", "--untracked-files=no"], text=True):
        raise ValueError("Upstream tracked source has local changes")
    output.mkdir(parents=True, exist_ok=True)
    run(sys.executable, "-m", "PyInstaller", "--noconfirm", "--onedir", "--noupx",
        "--name", "mtk-runtime", "--distpath", output / "dist",
        "--workpath", output / "work", "--specpath", output,
        "--paths", source, "--collect-all", "libusb_package",
        "--collect-all", "Crypto", "--collect-all", "Cryptodome",
        "--hidden-import", "mtkclient.Library.mtk_main", "--exclude-module", "PySide6",
        "--exclude-module", "shiboken6", "--exclude-module", "mfusepy",
        "--add-data", f"{source / 'mtk.py'}:upstream",
        "--add-data", f"{source / 'mtkclient' / 'payloads'}:mtkclient/payloads",
        ROOT / "entry.py")
    bundle = output / "dist" / "mtk-runtime"
    licenses = bundle / "licenses"
    licenses.mkdir(exist_ok=True)
    shutil.copy2(source / "LICENSE", licenses / "mtkclient-GPL-3.0.txt")
    shutil.copy2(ROOT / "requirements.lock", bundle / "requirements.lock")
    run("git", "-C", source, "archive", "--format=tar", "--output",
        bundle / "mtkclient-source.tar", COMMIT)
    # Include metadata/license files from every installed distribution collected here.
    from importlib.metadata import distributions
    for dist in distributions():
        for file in dist.files or ():
            if ".dist-info/" in str(file) and ("license" in str(file).lower() or "copying" in str(file).lower()):
                dest = licenses / str(file)
                dest.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(str(dist.locate_file(file)), dest)
    files = {}
    for path in sorted(bundle.rglob("*")):
        if path.is_file():
            with path.open("rb") as stream:
                files[str(path.relative_to(bundle))] = hashlib.file_digest(stream, "sha256").hexdigest()
    manifest = {
        "status": "investigation-not-adopted",
        "target": target, "python": platform.python_version(),
        "upstream": {"url": "https://github.com/bkerler/mtkclient", "commit": COMMIT},
        "excluded": ["GUI", "FUSE", "unreviewed vendor loaders", "Windows upstream DLLs"],
        "files": files,
    }
    (bundle / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    print(bundle)


if __name__ == "__main__":
    main()
