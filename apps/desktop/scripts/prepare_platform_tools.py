"""Fetch the pinned Google platform-tools distribution; never use host PATH tools."""

import argparse
import hashlib
import json
import platform
import shutil
import struct
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PACKAGES = {
    'aarch64-apple-darwin': ('darwin', '106a5d31fad8c1c0c5a180d06f5779767d129d7d5edbe629005c11a85eec5b4b'),
    'x86_64-pc-windows-msvc': ('win', 'b024d4f319d6ad3004de1ba7b96a5c7c5f3512e8b14126308d598b4ab93dcead'),
}


def prepare(target):
    host, digest = PACKAGES[target]
    url = f'https://dl.google.com/android/repository/platform-tools_r36.0.2-{host}.zip'
    cache = ROOT / 'build' / 'platform-tools'
    cache.mkdir(parents=True, exist_ok=True)
    archive = cache / f'{host}.zip'
    if not archive.exists():
        urllib.request.urlretrieve(url, archive)
    actual = hashlib.sha256(archive.read_bytes()).hexdigest()
    if actual != digest:
        raise ValueError(f'{archive}: SHA-256 {actual}, expected {digest}')
    destination = ROOT / 'src-tauri' / 'resources' / 'platform-tools'
    if destination.exists():
        shutil.rmtree(destination)
    destination.mkdir(parents=True, exist_ok=True)
    files = ['adb', 'fastboot'] if host == 'darwin' else ['adb.exe', 'fastboot.exe', 'AdbWinApi.dll', 'AdbWinUsbApi.dll']
    # Keep the upstream notices and package metadata beside the unmodified tools.
    with zipfile.ZipFile(archive) as package:
        for name in files + ['NOTICE.txt', 'source.properties']:
            data = package.read(f'platform-tools/{name}')
            (destination / name).write_bytes(data)
            if host == 'darwin' and name in files:
                (destination / name).chmod(0o755)
        for name in files:
            data = (destination / name).read_bytes()
            if host == 'darwin':
                # Google's universal Mach-O must include CPU_TYPE_ARM64.
                magic, count = struct.unpack_from('>II', data)
                if magic != 0xcafebabe or 0x100000c not in [struct.unpack_from('>I', data, 8 + index * 20)[0] for index in range(count)]:
                    raise ValueError(f'{name}: missing native arm64 Mach-O')
            else:
                pe = struct.unpack_from('<I', data, 0x3c)[0]
                machine = struct.unpack_from('<H', data, pe + 4)[0]
                # Google's Windows 36.0.2 tools/DLLs are x86, run via Windows x64 WOW64.
                if data[pe:pe + 4] != b'PE\0\0' or machine != 0x14c:
                    raise ValueError(f'{name}: unsupported PE architecture {machine:#x}')
    manifest = dict(version='36.0.2', target=target, url=url, archiveSha256=digest,
                    files={name: hashlib.sha256((destination / name).read_bytes()).hexdigest()
                           for name in files + ['NOTICE.txt', 'source.properties']})
    (destination / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(manifest, indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--target', choices=PACKAGES, default='aarch64-apple-darwin' if platform.system() == 'Darwin' else 'x86_64-pc-windows-msvc')
    prepare(parser.parse_args().target)
