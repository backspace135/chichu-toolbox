import hashlib
import struct
import unittest

from ksu_identity import identity


def field(value):
    return struct.pack("<I", len(value)) + value


def apk(blocks):
    pairs = b"".join(struct.pack("<QI", len(value) + 4, block_id) + value for block_id, value in blocks)
    size = len(pairs) + 24
    signing = struct.pack("<Q", size) + pairs + struct.pack("<Q", size) + b"APK Sig Block 42"
    eocd = struct.pack("<4s4H2IH", b"PK\x05\x06", 0, 0, 0, 0, 0, len(signing), 0)
    return signing + eocd


class IdentityTest(unittest.TestCase):
    def setUp(self):
        self.cert = b"certificate-fixture"
        signed = field(b"") + field(field(self.cert)) + field(b"")
        self.v2 = field(field(field(signed) + field(b"") + field(b"")))

    def test_extracts_exact_certificate_length_and_digest(self):
        value = identity(apk([(0x7109871A, self.v2), (0x42726577, b"padding")]))
        self.assertEqual(value["certificate_size"], len(self.cert))
        self.assertEqual(value["sha256"], hashlib.sha256(self.cert).hexdigest())

    def test_rejects_v3_and_agp_dependency_metadata(self):
        for block_id in (0xF05368C0, 0x1B93AD61, 0x504B4453):
            with self.assertRaises(ValueError):
                identity(apk([(0x7109871A, self.v2), (block_id, b"extra")]))

    def test_rejects_missing_duplicate_and_truncated_v2(self):
        for blocks in ([], [(0x7109871A, self.v2)] * 2, [(0x7109871A, self.v2[:-3])]):
            with self.assertRaises(ValueError):
                identity(apk(blocks))


if __name__ == "__main__":
    unittest.main()
