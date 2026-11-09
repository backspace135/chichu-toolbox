"""Read the exact v2 certificate identity checked by the pinned KernelSU kernel."""

import hashlib
import struct


def identity(data):
    def fail(message):
        raise ValueError(message)

    def u32(offset):
        return struct.unpack_from("<I", data, offset)[0]

    def field(offset, end):
        if offset + 4 > end:
            fail("truncated APK v2 length")
        size = u32(offset)
        if offset + 4 + size > end:
            fail("APK v2 field exceeds container")
        return offset + 4, offset + 4 + size

    # ZIP EOCD may contain a comment; check its length rather than guessing an offset.
    eocd = -1
    for offset in range(len(data) - 22, max(-1, len(data) - 65558), -1):
        if data[offset:offset + 4] == b"PK\x05\x06" and offset + 22 + struct.unpack_from("<H", data, offset + 20)[0] == len(data):
            eocd = offset
            break
    if eocd < 0:
        fail("ZIP EOCD missing")
    central = u32(eocd + 16)
    if central < 24 or data[central - 16:central] != b"APK Sig Block 42":
        fail("APK signing block missing")
    size = struct.unpack_from("<Q", data, central - 24)[0]
    start = central - size - 8
    if start < 0 or struct.unpack_from("<Q", data, start)[0] != size:
        fail("APK signing block size mismatch")
    cursor, end = start + 8, central - 24
    v2 = []
    while cursor < end:
        if cursor + 12 > end:
            fail("truncated signing pair")
        length = struct.unpack_from("<Q", data, cursor)[0]
        pair_end = cursor + 8 + length
        if length < 4 or pair_end > end:
            fail("signing pair exceeds block")
        block_id = u32(cursor + 8)
        if block_id == 0x7109871A:
            v2.append((cursor + 12, pair_end))
        elif block_id != 0x42726577:
            fail(f"KernelSU rejects signing block 0x{block_id:08x}; v2-only signing required")
        cursor = pair_end
    if len(v2) != 1:
        fail("KernelSU requires exactly one APK v2 block")
    cursor, end = v2[0]
    cursor, signers_end = field(cursor, end)
    cursor, signer_end = field(cursor, signers_end)
    cursor, signed_end = field(cursor, signer_end)
    _, digests_end = field(cursor, signed_end)
    cursor, certificates_end = field(digests_end, signed_end)
    certificate_start, certificate_end = field(cursor, certificates_end)
    certificate = data[certificate_start:certificate_end]
    if not 0 < len(certificate) <= 1024:
        fail("certificate exceeds pinned KernelSU limit")
    return {"package": "com.chichu.toolbox", "certificate_size": len(certificate), "sha256": hashlib.sha256(certificate).hexdigest(), "uapi": 6}


if __name__ == "__main__":
    import argparse
    import json
    import pathlib

    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=pathlib.Path)
    args = parser.parse_args()
    value = identity(args.apk.read_bytes())
    print(json.dumps(value, indent=2))
    print(f"KSU_PACKAGE_NAME={value['package']} KSU_EXPECTED_SIZE=0x{value['certificate_size']:04x} KSU_EXPECTED_HASH={value['sha256']}")
    print("These are build inputs, not proof that the running kernel recognizes this manager.")
