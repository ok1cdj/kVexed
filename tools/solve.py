#!/usr/bin/env python3
"""
solve.py — a forward search solver for Vexed boards.

Reuses the reference engine from pdb2vxl.py (parse_board / resolve / is_lost /
count_blocks) so the search obeys exactly the same rules the levels are verified
against — there is no second copy of the game semantics here.

Usage:
    python3 tools/solve.py --board "10/.../10" [--max-depth N] [--max-states N]
    python3 tools/solve.py --pack core/src/main/resources/levels/<id>.vxl --level N
    python3 tools/solve.py --selftest   # solve 5 Classic levels, assert <= par

Search is breadth-first (so the first solution found is the shortest — useful for
"best known" thresholds), with dead states pruned via is_lost(). On hitting a
limit it reports "not found to depth D / N states", NOT "no solution": the
difference between "unsolvable" and "not found within budget" matters. Only a
fully exhausted frontier is reported as genuinely unsolvable.

Part of kVexed. GPL-2.0.
"""

import argparse
import importlib.util
import sys
import time
from collections import deque
from pathlib import Path

W, H = 10, 8

# Import the reference engine from the sibling converter.
_spec = importlib.util.spec_from_file_location("pdb2vxl", Path(__file__).with_name("pdb2vxl.py"))
_eng = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_eng)
parse_board = _eng.parse_board
resolve = _eng.resolve
is_lost = _eng.is_lost
count_blocks = _eng.count_blocks


def _ser(g):
    return "".join("".join(r) for r in g)


def _deser(s):
    return [list(s[i * W:(i + 1) * W]) for i in range(H)]


def _encode_move(x, y, dx):
    """(x, y, dir) -> two-letter code. Uppercase X = left, uppercase Y = right."""
    xc, yc = chr(ord("a") + x), chr(ord("a") + y)
    return (xc.upper() + yc) if dx < 0 else (xc + yc.upper())


def _neighbours(g):
    """Yield ((x, y, dir), resolved_grid) for every legal move from grid g."""
    for y in range(H):
        for x in range(W):
            if g[y][x] in "# ":
                continue
            for dx in (-1, 1):
                nx = x + dx
                if 0 <= nx < W and g[y][nx] == " ":
                    g2 = [r[:] for r in g]
                    g2[y][nx], g2[y][x] = g2[y][x], " "
                    resolve(g2)
                    yield (x, y, dx), g2


class Result:
    def __init__(self, status, solution=None, states=0, seconds=0.0, depth=0):
        self.status = status          # "solved" | "not_found" | "unsolvable"
        self.solution = solution      # solution string when solved
        self.states = states
        self.seconds = seconds
        self.depth = depth            # deepest layer reached (not_found)


def solve(board_str, max_depth, max_states):
    """Breadth-first solve. Returns a Result."""
    start = parse_board(board_str)
    if count_blocks(start) == 0:
        return Result("solved", "", 0, 0.0, 0)

    t0 = time.time()
    s0 = _ser(start)
    came = {s0: None}                 # state -> (prev_state, move)
    depth = {s0: 0}
    dq = deque([s0])
    states = 0
    deepest = 0

    while dq:
        s = dq.popleft()
        d = depth[s]
        deepest = max(deepest, d)
        if d >= max_depth:
            continue
        g = _deser(s)
        for mv, g2 in _neighbours(g):
            s2 = _ser(g2)
            if s2 in came:
                continue
            came[s2] = (s, mv)
            depth[s2] = d + 1
            states += 1

            if count_blocks(g2) == 0:
                # reconstruct path
                path, cur = [], s2
                while came[cur] is not None:
                    ps, mv = came[cur]
                    path.append(mv)
                    cur = ps
                path.reverse()
                sol = "".join(_encode_move(*m) for m in path)
                return Result("solved", sol, states, time.time() - t0, len(path))

            if is_lost(g2):
                continue
            dq.append(s2)

            if states >= max_states or (time.time() - t0) > 900:
                return Result("not_found", None, states, time.time() - t0, deepest)

    # Frontier fully expanded within max_depth without a solution.
    return Result("unsolvable", None, states, time.time() - t0, deepest)


def _load_level(pack_path, index):
    """Return (board, shipped_solution, title) for level `index` in a .vxl file."""
    for line in Path(pack_path).read_text(encoding="utf-8").splitlines():
        if not line or line.startswith("#"):
            continue
        num, title, board, solution = line.split(";")
        if int(num) == index:
            return board, solution, title
    raise SystemExit(f"level {index} not found in {pack_path}")


def _report(res, shipped_par=None):
    if res.status == "solved":
        print(f"SOLVED  length={len(res.solution) // 2}  states={res.states}  time={res.seconds:.2f}s")
        print(f"solution={res.solution}")
        if shipped_par is not None:
            print(f"shipped par={shipped_par}  ->  {'<= par' if len(res.solution)//2 <= shipped_par else 'LONGER than par'}")
    elif res.status == "not_found":
        print(f"NOT FOUND to depth {res.depth} / {res.states} states in {res.seconds:.1f}s "
              f"(hit a limit — not a proof of unsolvability)")
    else:
        print(f"UNSOLVABLE — frontier exhausted after {res.states} states in {res.seconds:.1f}s")


def _selftest():
    """Solve five Classic levels that already verify; each must solve within par."""
    pack = Path(__file__).resolve().parents[1] / "core/src/main/resources/levels/01-classic-levels.vxl"
    if not pack.exists():
        raise SystemExit(f"selftest needs generated levels at {pack}")
    ok = True
    for idx in (0, 1, 2, 3, 4):
        board, sol, title = _load_level(pack, idx)
        par = len(sol) // 2
        res = solve(board, max_depth=par + 6, max_states=2_000_000)
        good = res.status == "solved" and len(res.solution) // 2 <= par
        ok = ok and good
        mark = "ok" if good else "FAIL"
        got = (len(res.solution) // 2) if res.status == "solved" else res.status
        print(f"  [{mark}] {title:22} par={par} solved={got}")
    print("selftest:", "PASS" if ok else "FAIL")
    return 0 if ok else 1


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--board")
    ap.add_argument("--pack")
    ap.add_argument("--level", type=int)
    ap.add_argument("--max-depth", type=int, default=None)
    ap.add_argument("--max-states", type=int, default=5_000_000)
    ap.add_argument("--selftest", action="store_true")
    args = ap.parse_args()

    if args.selftest:
        sys.exit(_selftest())

    shipped_par = None
    if args.board:
        board = args.board
    elif args.pack is not None and args.level is not None:
        board, sol, title = _load_level(args.pack, args.level)
        shipped_par = len(sol) // 2
        print(f"level {args.level} '{title}' (shipped par {shipped_par})")
    else:
        ap.error("give --board, or --pack with --level, or --selftest")

    max_depth = args.max_depth if args.max_depth is not None else ((shipped_par or 20) + 6)
    res = solve(board, max_depth=max_depth, max_states=args.max_states)
    _report(res, shipped_par)
    sys.exit(0 if res.status == "solved" else 1)


if __name__ == "__main__":
    main()
