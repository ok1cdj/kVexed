#!/usr/bin/env python3
"""Sweep all levels for best-known (sub-par) solutions.

Phase 1: bounded BFS -> proven-optimal length where it completes.
Phase 2: weighted A* fallback -> a (near-optimal) solution for deep levels;
         if shorter than par, the level is confirmed beatable.

Writes results incrementally to /tmp/bestknown.csv. Read-only w.r.t. the repo.
"""
import importlib.util, time, glob, os, heapq, csv, sys
from collections import deque

spec = importlib.util.spec_from_file_location("p", "tools/pdb2vxl.py")
p = importlib.util.module_from_spec(spec); spec.loader.exec_module(p)
W, H = 10, 8

def ser(g): return "".join("".join(r) for r in g)
def deser(s): return [list(s[i*W:(i+1)*W]) for i in range(H)]
def enc(x, y, d):
    X, Y = chr(ord('a')+x), chr(ord('a')+y)
    return (X.upper()+Y) if d < 0 else (X+Y.upper())

def moves(g):
    for y in range(H):
        for x in range(W):
            if g[y][x] in '# ': continue
            for d in (-1, 1):
                nx = x+d
                if 0 <= nx < W and g[y][nx] == ' ':
                    g2 = [r[:] for r in g]; g2[y][nx], g2[y][x] = g2[y][x], ' '; p.resolve(g2)
                    yield (x, y, d), g2

def blocks(g): return sum(1 for r in g for c in r if c not in '# ')

def bfs_optimal(board, max_depth, cap, tlimit):
    """Return ('optimal', length, path) if proven, else ('budget', None, None)."""
    start = p.parse_board(board); t0 = time.time()
    s0 = ser(start); came = {s0: None}; depth = {s0: 0}; dq = deque([s0]); n = 0
    while dq:
        s = dq.popleft(); d = depth[s]
        if d >= max_depth: continue
        for mv, g2 in moves(deser(s)):
            s2 = ser(g2)
            if s2 in came: continue
            came[s2] = (s, mv); depth[s2] = d+1; n += 1
            if blocks(g2) == 0:
                path = []; cur = s2
                while came[cur] is not None:
                    ps, m = came[cur]; path.append(m); cur = ps
                path.reverse(); return 'optimal', len(path), path
            if p.is_lost(g2): continue
            dq.append(s2)
            if n >= cap or time.time()-t0 > tlimit: return 'budget', None, None
    return 'exhausted', None, None

def astar(board, max_depth, cap, tlimit, w=2.0):
    """Weighted A* (f = g + w*blocks). Returns ('found', length, path) or ('budget', None, None)."""
    start = p.parse_board(board); t0 = time.time()
    s0 = ser(start); g0 = deser(s0)
    came = {s0: None}; gcost = {s0: 0}
    pq = [(w*blocks(g0), 0, s0)]; n = 0; ctr = 0
    while pq:
        f, gc, s = heapq.heappop(pq)
        if gc > gcost.get(s, 1e9): continue
        if gc >= max_depth: continue
        for mv, g2 in moves(deser(s)):
            s2 = ser(g2); ng = gc+1
            if ng >= gcost.get(s2, 1e9): continue
            came[s2] = (s, mv); gcost[s2] = ng; n += 1
            if blocks(g2) == 0:
                path = []; cur = s2
                while came[cur] is not None:
                    ps, m = came[cur]; path.append(m); cur = ps
                path.reverse(); return 'found', len(path), path
            if p.is_lost(g2): continue
            ctr += 1
            heapq.heappush(pq, (ng + w*blocks(g2), ng, s2))
            if n >= cap or time.time()-t0 > tlimit: return 'budget', None, None
    return 'budget', None, None

def main():
    levels = []
    for f in sorted(glob.glob("core/src/main/resources/levels/*.vxl")):
        pid = os.path.basename(f)[:-4]
        for line in open(f):
            if line.startswith('#') or ';' not in line: continue
            num, title, board, sol = line.rstrip('\n').split(';')
            levels.append((pid, int(num), title, board, len(sol)//2))

    out = open("/tmp/bestknown.csv", "w", newline="")
    wr = csv.writer(out); wr.writerow(["pack", "level", "title", "par", "best", "status", "solution"])
    opt_under = opt_at = beatable = undet = 0
    t0 = time.time()
    for i, (pid, num, title, board, par) in enumerate(levels, 1):
        st, ln, path = bfs_optimal(board, par, cap=800_000, tlimit=4.0)
        if st == 'optimal':
            status = 'optimal_under' if ln < par else 'optimal_at'
            if ln < par: opt_under += 1
            else: opt_at += 1
        else:
            st2, ln, path = astar(board, max_depth=par-1, cap=1_200_000, tlimit=8.0)
            if st2 == 'found' and ln < par:
                status = 'beatable'; beatable += 1
            else:
                status = 'undetermined'; undet += 1; ln = ''
        sol = "".join(enc(*m) for m in path) if path else ""
        wr.writerow([pid, num, title, par, ln, status, sol]); out.flush()
        if i % 100 == 0:
            under = opt_under + beatable
            print(f"...{i}/{len(levels)}  under={under} (opt {opt_under}+beat {beatable})  "
                  f"opt_at={opt_at}  undet={undet}  ({time.time()-t0:.0f}s)", flush=True)
    out.close()
    under = opt_under + beatable
    print("\n===== BEST-KNOWN RESULT =====")
    print(f"levels total                 {len(levels)}")
    print(f"UNDER par (beatable)         {under}   = proven-optimal-under {opt_under} + a*-found-shorter {beatable}")
    print(f"proven OPTIMAL at par        {opt_at}")
    print(f"undetermined                 {undet}")
    print(f"under-par share of total     {100*under/len(levels):.0f}%")
    print(f"elapsed                      {time.time()-t0:.0f}s")
    print("CSV: /tmp/bestknown.csv")

if __name__ == "__main__":
    main()
