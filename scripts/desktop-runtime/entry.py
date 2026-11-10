"""Frozen CLI entry; USB backend always comes from the bundled wheel."""
import json
from pathlib import Path
import runpy
import sys

import libusb_package
import usb.backend.libusb1


def main():
    library = libusb_package.get_library_path()
    if library is None:
        raise RuntimeError("Bundled libusb library is missing")
    backend = usb.backend.libusb1.get_backend(find_library=lambda name: str(library))
    if backend is None:
        raise RuntimeError(f"Cannot load bundled libusb: {library}")

    if sys.argv[1:] == ["--runtime-check"]:
        from Cryptodome.Cipher import AES
        from Crypto.Hash import SHA256
        import serial

        # Enumerates descriptors only; never opens, claims or writes a device.
        devices = list(backend.enumerate_devices())
        print(json.dumps({
            "python": sys.version,
            "libusb": str(library),
            "usb_device_count": len(devices),
            "aes_block_size": AES.block_size,
            "sha256": SHA256.new(b"runtime-check").hexdigest(),
            "serial": serial.VERSION,
        }))
        return

    # PyUSB otherwise searches the host's library paths on macOS.
    usb.backend.libusb1.get_backend = lambda **kwargs: backend
    runpy.run_path(str(Path(__file__).parent / "upstream" / "mtk.py"), run_name="__main__")


if __name__ == "__main__":
    main()
