"""Read-only M3 device evidence: kernel, KernelSU installation and visible UI.

No Root request, activation, migration, module change or device setting change.
"""

import argparse
import datetime
import json
import pathlib
import re
import subprocess
import xml.etree.ElementTree as ET


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--output", type=pathlib.Path, required=True)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)

    def adb(*command):
        result = subprocess.run(["adb", "-s", args.serial, *command], capture_output=True, check=True)
        return result.stdout

    result = {
        "time_jst": datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=9))).isoformat(),
        "serial": args.serial,
        "model": adb("shell", "getprop", "ro.product.model").decode().strip(),
        "device": adb("shell", "getprop", "ro.product.device").decode().strip(),
        "android": adb("shell", "getprop", "ro.build.version.release").decode().strip(),
        "kernel": adb("shell", "uname", "-r").decode().strip(),
        "original_manager": adb("shell", "pm", "list", "packages", "me.weishu.kernelsu").decode().strip(),
        "toolbox_apk": adb("shell", "pm", "path", "com.chichu.toolbox").decode().strip(),
    }
    text = adb("exec-out", "uiautomator", "dump", "/dev/tty").decode()
    match = re.search(r"<\?xml.*</hierarchy>", text, re.DOTALL)
    if match is None:
        raise RuntimeError(f"Device returned no UI hierarchy: {text}")
    xml = match.group()
    root = ET.fromstring(xml)
    result["visible_labels"] = [node.get("text") or node.get("content-desc") for node in root.iter("node")
                                if node.get("text") or node.get("content-desc")]
    args.output.joinpath("screen.xml").write_text(xml)
    args.output.joinpath("screen.png").write_bytes(adb("exec-out", "screencap", "-p"))
    args.output.joinpath("device.json").write_text(json.dumps(result, indent=2, ensure_ascii=False))
    print(json.dumps(result, indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()
