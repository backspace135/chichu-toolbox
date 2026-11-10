"""Check bundle hashes, native macOS linkage and relocated offline CLI startup."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import shutil
import subprocess
import tempfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("bundle", type=Path)
    args = parser.parse_args()
    bundle = args.bundle.resolve()
    manifest = json.loads((bundle / "manifest.json").read_text())
    native = 0
    for name, digest in manifest["files"].items():
        path = bundle / name
        with path.open("rb") as stream:
            if hashlib.file_digest(stream, "sha256").hexdigest() != digest:
                raise ValueError(f"SHA-256 mismatch: {name}")
        if platform.system() == "Darwin":
            kind = subprocess.check_output(["/usr/bin/file", "-b", path], text=True)
            if "Mach-O" in kind:
                subprocess.run(["/usr/bin/lipo", "-verify_arch", "arm64", path], check=True)
                links = subprocess.check_output(["/usr/bin/otool", "-L", path], text=True)
                for line in links.splitlines()[1:]:
                    dep = line.strip().split(" (", 1)[0]
                    if dep.startswith("/") and not dep.startswith(("/usr/lib/", "/System/Library/")):
                        raise ValueError(f"External native dependency: {name}: {dep}")
                native += 1
    # A path with spaces catches assumptions about shell invocation / current directory.
    with tempfile.TemporaryDirectory(prefix="runtime relocation ", dir=bundle.parent) as temp:
        relocated = Path(temp) / "bundle"
        shutil.copytree(bundle, relocated)
        executable = relocated / ("mtk-runtime.exe" if os.name == "nt" else "mtk-runtime")
        env = {"PATH": ""}
        if os.name == "nt":
            env["SYSTEMROOT"] = os.environ["SYSTEMROOT"]
            env["TEMP"] = temp
            env["TMP"] = temp
        for command in (["--runtime-check"], ["--help"], ["da", "seccfg", "--help"]):
            result = subprocess.run([str(executable), *command], cwd=temp, env=env,
                                    check=True, capture_output=True, text=True)
            print(result.stdout)
        result = subprocess.run([str(executable), "--invalid-runtime-option"], cwd=temp,
                                env=env, capture_output=True, text=True)
        if result.returncode == 0:
            raise AssertionError("Invalid CLI input incorrectly succeeded")
    print(json.dumps({"verified_files": len(manifest["files"]), "arm64_macho_files": native,
                      "relocated_startup": "passed", "invalid_input": "nonzero"}))


if __name__ == "__main__":
    main()
