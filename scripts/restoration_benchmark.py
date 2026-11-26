#!/usr/bin/env python3
"""Deterministic synthetic restoration oracle; not a learned model or device benchmark."""
import argparse
import hashlib
import json
import math
from pathlib import Path

SIZE = 96
PATTERNS = ("edges", "texture", "lines", "gradient")


def reference(name):
    if name not in PATTERNS:
        raise ValueError("unknown pattern")
    def pixel(x, y):
        if name == "edges":
            return 24 if x < 24 else (230 if x < 64 else 95)
        if name == "texture":
            return 35 + 180 * ((x // 4 + y // 4) % 2)
        if name == "lines":
            return 220 if (x % 13 < 2 or y % 19 < 2) else 28
        return (2 * x + y) % 256
    return bytes(pixel(x, y) for y in range(SIZE) for x in range(SIZE))


def blur(data):
    return bytes((sum(data[y * SIZE + min(SIZE-1, max(0, x+d))]
                      for d in range(-3, 4)) + 3) // 7
                 for y in range(SIZE) for x in range(SIZE))


def downsample(data):
    return bytes((sum(data[(2*y+dy)*SIZE+2*x+dx] for dx in (0, 1) for dy in (0, 1))+2)//4
                 for y in range(SIZE//2) for x in range(SIZE//2))


def bilinear(data):
    side = SIZE//2
    result = bytearray()
    for y in range(SIZE):
        fy = min(side-1, max(0, (y+.5)/2-.5))
        y0 = int(fy)
        y1 = min(y0+1, side-1)
        for x in range(SIZE):
            fx = min(side-1, max(0, (x+.5)/2-.5))
            x0 = int(fx)
            x1 = min(x0+1, side-1)
            a = data[y0*side+x0]*(1-(fx-x0))+data[y0*side+x1]*(fx-x0)
            b = data[y1*side+x0]*(1-(fx-x0))+data[y1*side+x1]*(fx-x0)
            result.append(round(a*(1-(fy-y0))+b*(fy-y0)))
    return bytes(result)


def metrics(truth, output):
    if len(truth) != SIZE*SIZE or len(output) != len(truth):
        raise ValueError("incorrect output shape")
    mse = sum((a-b)**2 for a, b in zip(truth, output))/len(truth)
    # Non-overlapping 8x8 SSIM blocks, population covariance; not Gaussian-window SSIM.
    scores = []
    for y in range(0, SIZE, 8):
        for x in range(0, SIZE, 8):
            a = [truth[(y+dy)*SIZE+x+dx] for dy in range(8) for dx in range(8)]
            b = [output[(y+dy)*SIZE+x+dx] for dy in range(8) for dx in range(8)]
            ma, mb = sum(a)/64, sum(b)/64
            va = sum((v-ma)**2 for v in a)/64
            vb = sum((v-mb)**2 for v in b)/64
            cov = sum((u-ma)*(v-mb) for u, v in zip(a, b))/64
            c1, c2 = (0.01*255)**2, (0.03*255)**2
            scores.append((2*ma*mb+c1)*(2*cov+c2)/((ma*ma+mb*mb+c1)*(va+vb+c2)))
    return {"mse": mse, "psnr_db": None if mse == 0 else 10*math.log10(255**2/mse),
            "ssim_block8": sum(scores)/len(scores), "exact": mse == 0}


def pgm(data, side):
    if len(data) != side*side:
        raise ValueError("incorrect PGM shape")
    return f"P5\n{side} {side}\n255\n".encode("ascii") + data


def read_candidate(path):
    # Deliberately narrow P5 format, bounded read; no arbitrary image parsing.
    prefix = f"P5\n{SIZE} {SIZE}\n255\n".encode("ascii")
    with path.open("rb") as stream:
        raw = stream.read(len(prefix)+SIZE*SIZE+1)
    if not raw.startswith(prefix) or len(raw) != len(prefix)+SIZE*SIZE:
        raise ValueError(f"invalid candidate PGM: {path.name}")
    return raw[len(prefix):], hashlib.sha256(raw).hexdigest()


def run(out, candidates=None):
    out.mkdir(parents=True, exist_ok=True)
    if candidates is not None and out.resolve() == candidates.resolve():
        raise ValueError("candidate directory must differ from generated fixtures")
    report = {"scope": "synthetic grayscale baseline only", "size": SIZE,
              "ssim": "non-overlapping 8x8 blocks", "cases": []}
    for pattern in PATTERNS:
        truth = reference(pattern)
        for task, inp, side, baseline in (("deblur", blur(truth), SIZE, blur(truth)),
                ("sr2x", downsample(truth), SIZE//2, bilinear(downsample(truth)))):
            name = f"{task}-{pattern}"
            truth_bytes = pgm(truth, SIZE)
            input_bytes = pgm(inp, side)
            (out/(name+"-truth.pgm")).write_bytes(truth_bytes)
            (out/(name+"-input.pgm")).write_bytes(input_bytes)
            (out/(name+"-baseline.pgm")).write_bytes(pgm(baseline, SIZE))
            row = {"case": name, "input_sha256": hashlib.sha256(input_bytes).hexdigest(),
                   "truth_sha256": hashlib.sha256(truth_bytes).hexdigest(),
                   "baseline": metrics(truth, baseline)}
            if candidates is not None:
                pixels, digest = read_candidate(candidates/(name+".pgm"))
                row["candidate_sha256"] = digest
                row["candidate"] = metrics(truth, pixels)
            report["cases"].append(row)
    (out/"report.json").write_text(json.dumps(report, indent=2, allow_nan=False)+"\n", encoding="utf-8")
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--candidates", type=Path)
    args = parser.parse_args()
    print(json.dumps(run(args.out, args.candidates), allow_nan=False))


if __name__ == "__main__":
    main()
