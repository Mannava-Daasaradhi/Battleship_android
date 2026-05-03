#!/usr/bin/env python3
"""
fix_icons.py — replaces placeholder text files in mipmap-* with valid 1×1 PNGs.

Run from the repo root:
    python3 fix_icons.py

The 1×1 PNG is navy-coloured (#0A1628) to match ic_launcher_background.
For production, replace these with proper icon PNGs at the correct densities.
"""

import os
import struct
import zlib

def make_1x1_png(r: int, g: int, b: int) -> bytes:
    """Generate a minimal valid 1×1 RGB PNG."""
    def chunk(tag: bytes, data: bytes) -> bytes:
        return (
            struct.pack(">I", len(data))
            + tag
            + data
            + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)
        )

    sig    = bytes([137, 80, 78, 71, 13, 10, 26, 10])
    ihdr   = chunk(b"IHDR", struct.pack(">IIBBBBB", 1, 1, 8, 2, 0, 0, 0))
    idat   = chunk(b"IDAT", zlib.compress(bytes([0, r, g, b])))
    iend   = chunk(b"IEND", b"")
    return sig + ihdr + idat + iend


NAVY_PNG = make_1x1_png(0x0A, 0x16, 0x28)   # #0A1628

DENSITIES = ["mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"]
NAMES     = ["ic_launcher.png", "ic_launcher_round.png"]
BASE      = os.path.join("app", "src", "main", "res")


def main():
    fixed = 0
    for density in DENSITIES:
        for name in NAMES:
            path = os.path.join(BASE, f"mipmap-{density}", name)
            if not os.path.exists(path):
                print(f"  SKIP (not found): {path}")
                continue
            with open(path, "wb") as f:
                f.write(NAVY_PNG)
            print(f"  OK: {path}")
            fixed += 1
    print(f"\nDone — {fixed} icon file(s) replaced with valid 1×1 PNGs.")
    print("Replace them with real artwork before release.")


if __name__ == "__main__":
    main()