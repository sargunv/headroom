#!/usr/bin/env python3
"""Render Headroom's quota-ring icon without external imaging dependencies."""

import math
from pathlib import Path
import struct
import zlib


def png(size, *, template=False):
    samples = 4
    rows = bytearray()
    for y in range(size):
        rows.append(0)
        for x in range(size):
            red = green = blue = alpha = 0.0
            for sy in range(samples):
                for sx in range(samples):
                    px = (x + (sx + 0.5) / samples) / size - 0.5
                    py = (y + (sy + 0.5) / samples) / size - 0.5
                    radius = math.hypot(px, py)
                    angle = math.atan2(py, px)
                    in_ring = 0.245 <= radius <= 0.355
                    in_gap = -math.pi <= angle < -math.pi / 2
                    in_center = radius <= 0.085
                    if template:
                        color = (0, 0, 0, 255 if in_center or (in_ring and not in_gap) else 0)
                    else:
                        rounded = max(abs(px) - 0.30, 0) ** 2 + max(abs(py) - 0.30, 0) ** 2 <= 0.18**2
                        color = (48, 94, 183, 255) if rounded else (0, 0, 0, 0)
                        if in_ring or in_center:
                            color = (121, 151, 208, 255) if in_ring and in_gap else (255, 255, 255, 255)
                    red += color[0]
                    green += color[1]
                    blue += color[2]
                    alpha += color[3]
            rows.extend(round(value / samples**2) for value in (red, green, blue, alpha))

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))

    return (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0))
        + chunk(b"IDAT", zlib.compress(rows, 9))
        + chunk(b"IEND", b"")
    )


if __name__ == "__main__":
    root = Path(__file__).resolve().parents[1]
    resources = root / "src/main/resources"
    resources.mkdir(parents=True, exist_ok=True)
    (resources / "status-icon.png").write_bytes(png(44, template=True))
    (resources / "app-icon.png").write_bytes(png(256))
    icons = bytearray()
    for kind, size in ((b"icp4", 16), (b"icp5", 32), (b"icp6", 64), (b"ic07", 128), (b"ic08", 256), (b"ic09", 512), (b"ic10", 1024)):
        image = png(size)
        icons.extend(kind + struct.pack(">I", len(image) + 8) + image)
    (resources / "Headroom.icns").write_bytes(b"icns" + struct.pack(">I", len(icons) + 8) + icons)
