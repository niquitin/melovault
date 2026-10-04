from pathlib import Path
import tempfile
import unittest

from streaming import (
    RangeNotSatisfiable,
    iter_file,
    parse_range,
)


class RangeTests(unittest.TestCase):
    def test_full_range(self):
        self.assertEqual(
            (0, 999, 200),
            parse_range(None, 1000),
        )

    def test_open_ended_range(self):
        self.assertEqual(
            (100, 999, 206),
            parse_range("bytes=100-", 1000),
        )

    def test_bounded_range(self):
        self.assertEqual(
            (100, 199, 206),
            parse_range("bytes=100-199", 1000),
        )

    def test_suffix_range(self):
        self.assertEqual(
            (900, 999, 206),
            parse_range("bytes=-100", 1000),
        )

    def test_invalid_range(self):
        with self.assertRaises(RangeNotSatisfiable):
            parse_range("bytes=2000-", 1000)

    def test_iter_file_respects_window(self):
        with tempfile.TemporaryDirectory() as td:
            path = Path(td) / "sample.bin"
            path.write_bytes(bytes(range(100)))
            data = b"".join(
                iter_file(path, start=10, length=20)
            )
            self.assertEqual(bytes(range(10, 30)), data)
