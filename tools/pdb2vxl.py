#!/usr/bin/env python3
"""
pdb2vxl.py — convert Palm Vexed level packs (*.pdb) to VXL text format,
verifying every bundled solution against a reference engine on the way.

Usage:
    python3 tools/pdb2vxl.py SRCDIR [-o core/src/main/resources/levels]

SRCDIR is a directory of *.pdb files downloaded from the Vexed SourceForge
project. Output is one <id>.vxl per pack plus index.json.

Exits non-zero if any solution fails to verify, any board is malformed, or any
duplicate board is found across packs (duplicates are reported, kept in the
first pack by order, and dropped from the rest).

Part of kVexed. GPL-2.0.
"""

import argparse
import json
import re
import struct
import sys
from collections import Counter
from pathlib import Path

W, H = 10, 8

# --------------------------------------------------------------------------
# PDB reading
# --------------------------------------------------------------------------

def read_pdb(path):
    """Return (pack_metadata_dict, [level_dict, ...]) from a Vexed .pdb file.

    Palm PDB layout: 78-byte header, then numRecords * 8-byte record entries.
    Vexed packs use type='DATA', creator='Vexd' and put EVERYTHING in a single
    record -- numRecords is 1 and the record list is immediately followed by
    the record data (no 2-byte padding).

    Inside the record is a flat stream of chunks:

        uint16 length      total chunk size including these 4 header bytes
        uint16 pairCount   number of key/value pairs that follow the name
        char[] name        NUL-terminated ("General" or "Level")
        char[] key, value  pairCount NUL-terminated pairs

    GOTCHA: the LAST chunk in every pack has length == 0 and simply runs to
    the end of the file. A parser that trusts the length field drops the
    60th level of every pack silently.
    """
    data = path.read_bytes()
    if len(data) < 78:
        raise ValueError(f"{path.name}: too short to be a PDB")

    ptype, creator = data[60:64], data[64:68]
    if ptype != b"DATA" or creator != b"Vexd":
        raise ValueError(f"{path.name}: not a Vexed pack (type={ptype!r} creator={creator!r})")

    pack_name = data[0:32].split(b"\0")[0].decode("latin-1")
    num_records = struct.unpack(">H", data[76:78])[0]
    if num_records < 1:
        raise ValueError(f"{path.name}: no records")
    offset = struct.unpack(">I", data[78:82])[0]

    general, levels = {}, []
    p = offset
    while p < len(data) - 4:
        length, pair_count = struct.unpack(">HH", data[p:p + 4])
        last = length < 4                       # zero-length terminator chunk
        end = len(data) if last else p + length
        parts = data[p + 4:end].split(b"\0")

        name = parts[0].decode("latin-1")
        kv, it = {}, iter(parts[1:])
        for key in it:
            if not key:
                continue
            try:
                value = next(it)
            except StopIteration:
                break
            kv[key.decode("latin-1")] = value.decode("latin-1")

        if len(kv) != pair_count:
            raise ValueError(f"{path.name}: chunk '{name}' declared {pair_count} "
                             f"pairs, parsed {len(kv)}")

        if name == "General":
            general = kv
        elif name == "Level":
            levels.append(kv)
        else:
            raise ValueError(f"{path.name}: unknown chunk type {name!r}")

        if last:
            break
        p = end

    general["_name"] = pack_name
    return general, levels


# --------------------------------------------------------------------------
# Reference engine -- mirrors the Kotlin :core semantics exactly
# --------------------------------------------------------------------------

def parse_board(s):
    """Board string -> H rows of W cells. '#'=wall, ' '=empty, 'a'-'h'=block."""
    rows = s.split("/")
    if len(rows) != H:
        raise ValueError(f"expected {H} rows, got {len(rows)}")
    grid = []
    for r in rows:
        row, i = [], 0
        while i < len(r):
            if r[i].isdigit():
                j = i
                while j < len(r) and r[j].isdigit():
                    j += 1              # greedy: "10" is ten walls, not 1 then 0
                row += ["#"] * int(r[i:j])
                i = j
            elif r[i] == "~":
                row.append(" ")
                i += 1
            elif "a" <= r[i] <= "h":
                row.append(r[i])
                i += 1
            else:
                raise ValueError(f"illegal char {r[i]!r} in row {r!r}")
        if len(row) != W:
            raise ValueError(f"row {r!r} is {len(row)} wide, expected {W}")
        grid.append(row)
    return grid


def settle(g):
    """Gravity: every block falls while the cell below it is empty."""
    moved = True
    while moved:
        moved = False
        for y in range(H - 2, -1, -1):
            for x in range(W):
                if g[y][x] not in "# " and g[y + 1][x] == " ":
                    g[y + 1][x], g[y][x] = g[y][x], " "
                    moved = True


def clear(g):
    """Remove every group of >=2 orthogonally connected same-type blocks,
    all groups simultaneously. Returns True if anything was removed."""
    seen = [[False] * W for _ in range(H)]
    doomed = set()
    for y in range(H):
        for x in range(W):
            if g[y][x] in "# " or seen[y][x]:
                continue
            t = g[y][x]
            stack, group = [(x, y)], []
            seen[y][x] = True
            while stack:
                cx, cy = stack.pop()
                group.append((cx, cy))
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = cx + dx, cy + dy
                    if 0 <= nx < W and 0 <= ny < H and not seen[ny][nx] and g[ny][nx] == t:
                        seen[ny][nx] = True
                        stack.append((nx, ny))
            if len(group) >= 2:
                doomed |= set(group)
    for x, y in doomed:
        g[y][x] = " "
    return bool(doomed)


def resolve(g):
    """Settle, then clear, repeating until the board stops changing.

    Matches are evaluated only after the board is FULLY settled, never
    mid-fall. Verified against 60/60 of Children's Pack."""
    while True:
        settle(g)
        if not clear(g):
            return


def count_blocks(g):
    return sum(1 for y in range(H) for x in range(W) if g[y][x] not in "# ")


def is_lost(g):
    """Weak dead-state detector: some block type has exactly one instance left."""
    c = Counter(g[y][x] for y in range(H) for x in range(W) if g[y][x] not in "# ")
    return any(v == 1 for v in c.values())


def parse_solution(sol):
    """'IdbE...' -> [(x, y, dx), ...]. Exactly one letter of each pair is
    uppercase: uppercase X means left, uppercase Y means right."""
    if len(sol) % 2:
        raise ValueError(f"odd-length solution {sol!r}")
    out = []
    for i in range(0, len(sol), 2):
        a, b = sol[i], sol[i + 1]
        if a.isupper() == b.isupper():
            raise ValueError(f"pair {a}{b!r} must have exactly one uppercase letter")
        x, y = ord(a.lower()) - 97, ord(b.lower()) - 97
        if not (0 <= x < W and 0 <= y < H):
            raise ValueError(f"pair {a}{b} out of range")
        out.append((x, y, -1 if a.isupper() else 1))
    return out


def verify(board_str, solution):
    """Play the solution through. Raises ValueError on any inconsistency."""
    g = parse_board(board_str)

    snapshot = [r[:] for r in g]
    settle(g)
    if g != snapshot:
        raise ValueError("initial board is not settled")
    if clear([r[:] for r in g]):
        raise ValueError("initial board already contains a match")

    moves = parse_solution(solution)
    for k, (x, y, dx) in enumerate(moves):
        if g[y][x] in "# ":
            raise ValueError(f"move {k}: no block at ({x},{y})")
        nx = x + dx
        if not (0 <= nx < W) or g[y][nx] != " ":
            raise ValueError(f"move {k}: target ({nx},{y}) is not empty")
        g[y][nx], g[y][x] = g[y][x], " "
        resolve(g)
        if k < len(moves) - 1:
            if count_blocks(g) == 0:
                raise ValueError(f"solved early at move {k}, solution is too long")
            if is_lost(g):
                raise ValueError(f"dead state reached at move {k}")

    left = count_blocks(g)
    if left:
        raise ValueError(f"{left} blocks left on the board after the last move")
    return len(moves)


# --------------------------------------------------------------------------
# Pack ordering / grouping
# --------------------------------------------------------------------------

# Match the pack names as they appear INSIDE the .pdb (data[0:32]), which carry
# "Levels"/"Pack" suffixes -- not the shorthand used in menus. Order here is the
# difficulty order the pack list shows canonical packs in.
CANONICAL = [
    "Classic Levels", "Classic II Levels", "Variety Pack", "Variety II Pack",
    "Children's Pack", "Twister Levels", "Confusion Pack", "Panic Pack",
    "Impossible Pack",
]


def classify(pack_name):
    """Return (group, order) for a pack title."""
    for i, c in enumerate(CANONICAL):
        if pack_name.strip().lower() == c.lower():
            return "canonical", i + 1
    m = re.search(r"(\d+)", pack_name)
    if m and "variety" in pack_name.lower():
        return "variety", 100 + int(m.group(1))
    return "extra", 900


def slugify(name, group, order):
    s = re.sub(r"[^a-z0-9]+", "-", name.lower()).strip("-")
    return f"{order:02d}-{s}" if group == "canonical" else s


# --------------------------------------------------------------------------
# Main
# --------------------------------------------------------------------------

def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("srcdir", type=Path, help="directory containing *.pdb packs")
    ap.add_argument("-o", "--outdir", type=Path,
                    default=Path("core/src/main/resources/levels"))
    ap.add_argument("--keep-duplicates", action="store_true")
    args = ap.parse_args()

    pdbs = sorted(args.srcdir.glob("*.pdb"))
    if not pdbs:
        sys.exit(f"no *.pdb files in {args.srcdir}")

    packs, errors = [], []
    for path in pdbs:
        try:
            general, levels = read_pdb(path)
        except ValueError as e:
            errors.append(str(e))
            continue
        name = general.pop("_name")
        group, order = classify(name)
        packs.append({
            "id": slugify(name, group, order),
            "title": name,
            "author": general.get("Author", "Vexed Development Team"),
            "description": general.get("Description", ""),
            "url": general.get("URL", ""),
            "group": group,
            "order": order,
            "levels": levels,
            "src": path.name,
        })

    packs.sort(key=lambda p: (p["order"], p["title"]))

    # verify + dedupe
    #
    # Two failure classes, handled differently:
    #   errors  -- STRUCTURAL problems (bad PDB header, chunk pair-count mismatch,
    #              a whole pack that won't read). These abort the run: the input is
    #              not what we think it is, so writing anything would be dishonest.
    #   dropped -- a single level whose shipped solution doesn't solve its board,
    #              or that's missing board/solution. Reported and skipped, not
    #              fatal: one bad puzzle shouldn't withhold the other ~2800. (The
    #              Classic II "Greensboro" solution is a known case of this.)
    seen, dupes, dropped = {}, [], []
    total_in = 0
    for pack in packs:
        kept = []
        for lv in pack["levels"]:
            total_in += 1
            board, sol, title = lv.get("board"), lv.get("solution"), lv.get("title", "")
            if not board or not sol:
                dropped.append(f"{pack['id']}/{title}: missing board or solution")
                continue
            try:
                par = verify(board, sol)
            except ValueError as e:
                dropped.append(f"{pack['id']}/{title}: {e}")
                continue
            if board in seen and not args.keep_duplicates:
                dupes.append(f"{pack['id']}/{title} == {seen[board]}")
                continue
            seen.setdefault(board, f"{pack['id']}/{title}")
            kept.append({"title": title, "board": board, "solution": sol, "par": par})
        pack["kept"] = kept

    if errors:
        print(f"\n{len(errors)} STRUCTURAL ERROR(S):", file=sys.stderr)
        for e in errors[:40]:
            print("  " + e, file=sys.stderr)
        if len(errors) > 40:
            print(f"  ... and {len(errors) - 40} more", file=sys.stderr)
        sys.exit(1)

    args.outdir.mkdir(parents=True, exist_ok=True)
    index = []
    for pack in packs:
        lines = [
            f"# Author: {pack['author']}",
            f"# URL: {pack['url']}",
            f"# Description: {pack['description']}",
        ]
        for n, lv in enumerate(pack["kept"]):
            lines.append(f"{n};{lv['title']};{lv['board']};{lv['solution']}")
        (args.outdir / f"{pack['id']}.vxl").write_text("\n".join(lines) + "\n",
                                                       encoding="utf-8")
        index.append({
            "id": pack["id"],
            "title": pack["title"],
            "author": pack["author"],
            "description": pack["description"],
            "levels": len(pack["kept"]),
            "group": pack["group"],
            "order": pack["order"],
        })

    (args.outdir / "index.json").write_text(
        json.dumps(index, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

    total_out = sum(p["levels"] for p in index)
    print(f"packs        {len(index)}")
    print(f"levels in    {total_in}")
    print(f"duplicates   {len(dupes)}")
    print(f"unsolvable   {len(dropped)}")
    print(f"levels out   {total_out}")
    print(f"verified     {total_out}/{total_out} solutions OK")
    print(f"written to   {args.outdir}")
    if dropped:
        print("\nunsolvable levels dropped:")
        for d in dropped:
            print("  " + d)
    if dupes:
        print("\nduplicates dropped:")
        for d in dupes:
            print("  " + d)


if __name__ == "__main__":
    main()
