"""检查 app_process 与 JNI 反射入口确实存在于最终 DEX，而非只有构建成功。"""

import struct
import sys
import zipfile


def classes(data):
    def uint(offset):
        return struct.unpack_from("<I", data, offset)[0]

    strings = []
    count, offset = uint(56), uint(60)
    for index in range(count):
        start = uint(offset + index * 4)
        while data[start] & 128:
            start += 1
        start += 1
        strings.append(data[start:data.index(0, start)].decode("utf-8", errors="replace"))
    types = [strings[uint(uint(68) + index * 4)] for index in range(uint(64))]
    return {types[uint(uint(100) + index * 32)] for index in range(uint(96))}


with zipfile.ZipFile(sys.argv[1]) as apk:
    present = set()
    for name in apk.namelist():
        if name.startswith("classes") and name.endswith(".dex"):
            present.update(classes(apk.read(name)))
    required = {
        "Lrikka/shizuku/server/ShizukuService;",
        "Lmoe/shizuku/starter/ServiceStarter;",
        "Lrikka/rish/RishHost;",
        "Lrikka/rish/RishTerminal;",
        "Lmoe/shizuku/manager/adb/PairingContext;",
    }
    missing = required - present
    if missing:
        raise SystemExit(f"缺少启动或 JNI 入口：{sorted(missing)}")
    for abi in ("arm64-v8a", "armeabi-v7a", "x86", "x86_64"):
        for library in ("chichu_starter", "rish", "adb"):
            assert f"lib/{abi}/lib{library}.so" in apk.namelist()
    assert apk.read("assets/OPEN_SOURCE_NOTICES.txt")
    print("APK 入口、四 ABI native 和许可证检查通过。")
