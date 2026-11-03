"""脚本界面 icon 组件的图集：12 个白色图标排成一行，每格 16×16 RGBA。

    python3 docs/make_script_icons.py

顺序从 IconAtlas.NAMES 读，不在这里另写一份。这些是占位图：路径、格子大小与顺序是契约，
替换时覆盖同名文件、保持每格 16×16 与白色即可（IconAtlas 按颜色染色）。
每个图标按 8×8 画、放大两倍：size 为 8 与 16 时像素对齐，这两档最常用。
"""
import os
import re
import struct
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ATLAS = os.path.join(ROOT, 'shared/src/main/java/com/november/mcphone/core/script/client/render/IconAtlas.java')
OUT = os.path.join(ROOT, 'shared/src/main/resources/assets/mcphone/textures/script/icons.png')
CELL = 16

ICONS = {
    'back': """
        ........
        ...#....
        ..##....
        .######.
        .######.
        ..##....
        ...#....
        ........""",
    'forward': """
        ........
        ....#...
        ....##..
        .######.
        .######.
        ....##..
        ....#...
        ........""",
    'up': """
        ........
        ...##...
        ..####..
        .######.
        ...##...
        ...##...
        ...##...
        ........""",
    'down': """
        ........
        ...##...
        ...##...
        ...##...
        .######.
        ..####..
        ...##...
        ........""",
    'check': """
        ........
        ......#.
        .....##.
        #...##..
        ##.##...
        .###....
        ..#.....
        ........""",
    'cross': """
        ........
        .#....#.
        ..#..#..
        ...##...
        ...##...
        ..#..#..
        .#....#.
        ........""",
    'plus': """
        ........
        ...##...
        ...##...
        .######.
        .######.
        ...##...
        ...##...
        ........""",
    'minus': """
        ........
        ........
        ........
        .######.
        .######.
        ........
        ........
        ........""",
    'gear': """
        ........
        .#.##.#.
        ..####..
        .##..##.
        .##..##.
        ..####..
        .#.##.#.
        ........""",
    'search': """
        .###....
        #...#...
        #...#...
        #...#...
        .####...
        .....#..
        ......#.
        .......#""",
    'info': """
        ...##...
        ...##...
        ........
        ..###...
        ...##...
        ...##...
        ...##...
        ..####..""",
    'warn': """
        ...##...
        ..#..#..
        ..#..#..
        .#.##.#.
        .#.##.#.
        #......#
        #..##..#
        ########""",
}


def names():
    src = open(ATLAS, encoding='utf-8').read()
    body = re.search(r'NAMES = List\.of\((.*?)\);', src, re.S).group(1)
    return re.findall(r'"([a-z]+)"', body)


def mask(name):
    rows = [r.strip() for r in ICONS[name].strip().splitlines()]
    assert len(rows) == 8 and all(len(r) == 8 for r in rows), name
    return rows


def write(order):
    width = CELL * len(order)
    pixels = [[(0, 0, 0, 0)] * width for _ in range(CELL)]
    for i, name in enumerate(order):
        for y, row in enumerate(mask(name)):
            for x, ch in enumerate(row):
                if ch == '#':
                    for dy in (0, 1):
                        for dx in (0, 1):
                            pixels[y * 2 + dy][i * CELL + x * 2 + dx] = (255, 255, 255, 255)

    def chunk(tag, data):
        return (struct.pack('>I', len(data)) + tag + data
                + struct.pack('>I', zlib.crc32(tag + data) & 0xFFFFFFFF))

    raw = bytearray()
    for row in pixels:
        raw.append(0)
        for px in row:
            raw.extend(px)
    png = (b'\x89PNG\r\n\x1a\n'
           + chunk(b'IHDR', struct.pack('>IIBBBBB', width, CELL, 8, 6, 0, 0, 0))
           + chunk(b'IDAT', zlib.compress(bytes(raw), 9))
           + chunk(b'IEND', b''))
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    open(OUT, 'wb').write(png)
    return width, len(png)


order = names()
missing = set(order) ^ set(ICONS)
assert not missing, f'IconAtlas.NAMES 与本脚本的图标对不上：{sorted(missing)}'
w, size = write(order)
print(f'{os.path.relpath(OUT, ROOT)}  {w}×{CELL}  {size} 字节')
for name in order:
    print(f'\n{name}')
    for row in mask(name):
        print('  ' + row.replace('.', ' '))
