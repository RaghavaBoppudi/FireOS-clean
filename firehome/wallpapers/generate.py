#!/usr/bin/env python3
"""Render the home screen's built-in wallpapers: minimalist monochrome sand dunes, 960x540.

Each image is a real height field: dunes with skewed crests and rounded troughs, rendered by marching a ray through
every sub-pixel, lit by a low sun with soft shadows, a cool skylight on the ridges and a trace of haze. It is one
continuous surface, so there are no seams, and every pixel averages ssaa x ssaa rays, so the edges are anti-aliased.
Nothing is downloaded or copied. Needs numpy and Pillow.

    pip3 install numpy pillow
    python3 generate.py                  # the built-in set, into ../assets/backgrounds, with its index.json
    python3 generate.py --all --out /tmp/dunes --width 1920   # all twelve, bigger, to look at
    python3 generate.py --only 3 --seed 5                     # one of them, other dunes

The set is mojave, graphite, sand, ink, dusk-rose, clay, mist, violet and charcoal. The dunes of each follow from its
place in the full list of twelve, so dropping one from the set leaves the rest as they were.
"""
import argparse
import colorsys
import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

ASPECT = 16 / 9
TMAX = 80.0                                                # how far the dunes extend before they dissolve into the sky

# name, hue, saturation, sky top / horizon lightness, dune shadow / light lightness
PALETTES = [
    ("mojave",    215, 0.20, 0.80, 0.92, 0.035, 0.56),
    ("graphite",  220, 0.07, 0.20, 0.36, 0.015, 0.62),
    ("sand",       33, 0.38, 0.52, 0.76, 0.09,  0.72),
    ("ink",       240, 0.28, 0.07, 0.19, 0.010, 0.46),
    ("dusk-rose", 345, 0.30, 0.30, 0.56, 0.04,  0.62),
    ("sage",      150, 0.16, 0.36, 0.62, 0.04,  0.62),
    ("clay",       18, 0.42, 0.24, 0.46, 0.035, 0.58),
    ("mist",      200, 0.10, 0.58, 0.80, 0.10,  0.82),
    ("violet",    265, 0.32, 0.11, 0.30, 0.015, 0.56),
    ("teal-fog",  185, 0.24, 0.30, 0.54, 0.035, 0.62),
    ("gold-hour",  42, 0.48, 0.19, 0.42, 0.025, 0.66),
    ("charcoal",    0, 0.00, 0.68, 0.88, 0.020, 0.52),
]

# the ones that ship with the app; the rest are kept so the dunes of these do not change
BUNDLED = ["mojave", "graphite", "sand", "ink", "dusk-rose", "clay", "mist", "violet", "charcoal"]


def hsl(h, s, l):
    return np.array(colorsys.hls_to_rgb(h / 360.0, l, s), np.float32)


def smoothstep(a, b, x):
    t = np.clip((x - a) / (b - a), 0.0, 1.0)
    return t * t * (3.0 - 2.0 * t)


class Dunes:
    """A height field built from three skewed crest trains, warped and modulated so they never repeat."""

    def __init__(self, rng):
        wind = math.pi / 2 + rng.uniform(-0.55, 0.55)
        self.trains = []
        for lam, amp in ((rng.uniform(15, 24), 9.0), (rng.uniform(8, 12), 3.0), (rng.uniform(4.0, 6.0), 0.55)):
            theta = wind + rng.uniform(-0.35, 0.35)
            self.trains.append(dict(
                k=2 * math.pi / lam, dx=math.cos(theta), dz=math.sin(theta), amp=amp,
                skew=rng.uniform(0.6, 0.95) * rng.choice([-1, 1]), ph=rng.uniform(0, 6.28),
                env=[(rng.uniform(0.05, 0.16), rng.uniform(0.05, 0.16), rng.uniform(0, 6.28)) for _ in range(2)]))
        self.warp = [(rng.uniform(0.04, 0.12), rng.uniform(-0.12, 0.12), rng.uniform(1.8, 4.5), rng.uniform(0, 6.28))
                     for _ in range(3)]
        self.swell = (rng.uniform(0.03, 0.07), rng.uniform(0.03, 0.07), rng.uniform(0, 6.28))

    def height(self, x, z):
        w = np.zeros_like(x)
        for kz, kx, a, ph in self.warp:
            w += a * np.sin(kz * z + kx * x + ph)
        h = 1.2 * np.sin(self.swell[0] * x + self.swell[1] * z + self.swell[2])
        for t in self.trains:
            e = sum(np.sin(a * x + b * z + p) for a, b, p in t["env"])
            env = 0.25 + 0.75 * smoothstep(-0.9, 0.9, e)
            q = t["k"] * (x * t["dx"] + z * t["dz"] + w) + t["ph"]
            q = q + t["skew"] * np.sin(q)                  # one face steep, the other long and gentle (skew < 1 keeps q monotonic, so no folds)
            crest = 0.12 * (1 - np.abs(np.sin(q / 2))) + 0.88 * (0.5 + 0.5 * np.cos(q))
            h += t["amp"] * env * (crest - 0.5)
        return h * (0.45 + 0.55 * smoothstep(2.0, 26.0, z))   # calmer up close, big dunes in the distance


def march(dunes, origin, d, hmax, tmax=TMAX, iters=4000):
    """Distance along each ray to the dunes, or -1 where the ray reaches the sky."""
    n = d.shape[0]
    t = np.full(n, 0.4, np.float32)
    result = np.full(n, -1.0, np.float32)
    idx = np.arange(n)
    for _ in range(iters):
        if idx.size == 0:
            break
        tt = t[idx]
        px, py, pz = origin[0] + tt * d[idx, 0], origin[1] + tt * d[idx, 1], origin[2] + tt * d[idx, 2]
        gap = py - dunes.height(px, pz)
        hit = gap < 0.00015 * tt + 0.0005
        result[idx[hit]] = tt[hit]
        sky = (~hit) & (((py > hmax) & (d[idx, 1] > 0)) | (tt > tmax))
        t[idx] = tt + np.clip(0.5 * gap, 0.006 * (1 + 0.03 * tt), 2.5)
        idx = idx[~(hit | sky)]
    result[idx] = t[idx]                                   # out of steps: a grazing ray skims the surface
    return result


def soft_shadow(dunes, p, light, steps=48):
    """1 in the open, down to 0 in shadow; the penumbra widens with distance from the occluder."""
    s = np.ones(p.shape[0], np.float32)
    t = 0.08
    for _ in range(steps):
        q = p + t * light
        s = np.minimum(s, np.clip(7.0 * (q[:, 1] - dunes.height(q[:, 0], q[:, 2])) / t, 0.0, 1.0))
        t *= 1.12
    return s


def camera(rng, dunes):
    """Above the ground in front of it, so a dune never fills the whole frame."""
    cam = np.zeros(3, np.float32)
    gx, gz = np.meshgrid(np.linspace(-5, 5, 9, dtype=np.float32), np.linspace(0, 9, 9, dtype=np.float32))
    cam[1] = max(dunes.height(np.zeros(1, np.float32), np.zeros(1, np.float32))[0],
                 dunes.height(gx.ravel(), gz.ravel()).max()) + rng.uniform(1.2, 2.4)
    pitch = rng.uniform(0.0, 0.10)                         # raises or lowers the horizon
    tanf = math.tan(math.radians(rng.uniform(24, 32)))
    return cam, pitch, tanf


def rays(w, h, pitch, tanf):
    sx = (2 * (np.arange(w) + 0.5) / w - 1) * ASPECT * tanf / 2
    sy = (1 - 2 * (np.arange(h) + 0.5) / h) * tanf / 2
    dx, dy = np.meshgrid(sx, sy)
    dz = np.ones_like(dx)
    cp, sp = math.cos(pitch), math.sin(pitch)
    r = np.stack([dx, dy * cp + dz * sp, dz * cp - dy * sp], -1).reshape(-1, 3).astype(np.float32)
    return r / np.linalg.norm(r, axis=1, keepdims=True)


def render(index, width, seed, ssaa):
    name, hue, sat, sky_top, sky_hor, dune_dark, dune_light = PALETTES[index]
    Wo, Ho = width, int(round(width * 9 / 16))
    W, H = Wo * ssaa, Ho * ssaa

    for attempt in range(16):                              # reject compositions that are all sky or all dune
        rng = np.random.default_rng(seed * 1000 + index + attempt * 7919)
        dunes = Dunes(rng)
        cam, pitch, tanf = camera(rng, dunes)
        hmax = sum(t["amp"] for t in dunes.trains) * 0.5 + 1.2 + 0.4
        sky = float((march(dunes, cam, rays(96, 54, pitch, tanf), hmax) < 0).mean())
        if 0.22 <= sky <= 0.62:
            break

    d = rays(W, H, pitch, tanf)
    t = march(dunes, cam, d, hmax)
    hit = t > 0

    c_top, c_hor = hsl(hue, sat, sky_top), hsl(hue, sat, sky_hor)
    c_dark, c_light = hsl(hue, sat * 0.9, dune_dark), hsl(hue, sat * 0.7, dune_light)
    sk = smoothstep(0.0, 0.55, np.clip(d[:, 1], 0, None)) ** 0.7
    img = c_hor[None, :] * (1 - sk[:, None]) + c_top[None, :] * sk[:, None]

    dh = d[hit]
    tt = t[hit]
    for _ in range(3):                                     # Newton steps put each hit on the surface: no contour rings
        p = cam[None, :] + tt[:, None] * dh
        e = 0.01 + 0.0015 * tt
        hx = (dunes.height(p[:, 0] + e, p[:, 2]) - dunes.height(p[:, 0] - e, p[:, 2])) / (2 * e)
        hz = (dunes.height(p[:, 0], p[:, 2] + e) - dunes.height(p[:, 0], p[:, 2] - e)) / (2 * e)
        slope = dh[:, 1] - hx * dh[:, 0] - hz * dh[:, 2]
        step = (p[:, 1] - dunes.height(p[:, 0], p[:, 2])) / np.where(slope < -0.02, slope, -0.02)
        tt = tt + np.clip(-step, -0.3 - 0.03 * tt, 0.3 + 0.03 * tt)
    p = cam[None, :] + tt[:, None] * dh
    n = np.stack([-hx, np.ones_like(hx), -hz], -1)
    n /= np.linalg.norm(n, axis=1, keepdims=True)

    elev = math.radians(rng.uniform(6, 18))
    az = rng.choice([-1, 1]) * rng.uniform(0.6, 1.5)
    light = np.array([math.cos(elev) * math.sin(az), math.sin(elev), math.cos(elev) * math.cos(az)], np.float32)
    lit = np.clip(n @ light, 0, 1) * soft_shadow(dunes, p + n * (0.04 + 0.002 * tt[:, None]), light)
    rim = (1 - np.clip(np.sum(n * -dh, -1), 0, 1)) ** 3 * (0.5 + 0.5 * n[:, 1])
    sky_amb = 0.10 + 0.14 * (0.5 + 0.5 * n[:, 1])
    shade = np.clip(sky_amb * 0.3 + 1.1 * lit + 0.30 * rim, 0, 1) ** 1.15
    shade = np.clip(shade * (1 + rng.normal(0, 0.012, shade.shape)), 0, 1)           # fine sand grain
    col = c_dark[None, :] * (1 - shade[:, None]) + c_light[None, :] * shade[:, None]
    haze = np.maximum(1 - np.exp(-tt / rng.uniform(45, 85)), smoothstep(0.45 * TMAX, TMAX, tt))
    col = col * (1 - haze[:, None]) + img[hit] * haze[:, None]     # far ridges melt into the sky, so the cut-off never shows
    img[hit] = col

    img = img.reshape(Ho, ssaa, Wo, ssaa, 3).mean((1, 3))  # average the sub-pixels: anti-aliasing
    yy = np.linspace(0, 1, Ho, dtype=np.float32)[:, None]
    img = img * (1 - 0.30 * smoothstep(0.80, 1.0, yy))[..., None]                    # dock edge
    img = img + rng.normal(0, 0.7 / 255, img.shape).astype(np.float32)               # no banding
    return Image.fromarray((np.clip(img, 0, 1) * 255 + 0.5).astype(np.uint8)), name


def luminance(im, box):
    w, h = im.size
    x0, y0, x1, y1 = (int(box[0] * w), int(box[1] * h), int(box[2] * w), int(box[3] * h))
    return float((np.asarray(im.crop((x0, y0, x1, y1)).convert("L"), np.float32) / 255).mean())


def contact_sheet(images, names, path):
    cols, tw = 3, 640
    th = tw * 9 // 16
    rows = math.ceil(len(images) / cols)
    sheet = Image.new("RGB", (cols * tw + (cols + 1) * 12, rows * th + (rows + 1) * 12), (18, 18, 18))
    draw = ImageDraw.Draw(sheet)
    for i, (im, name) in enumerate(zip(images, names)):
        x, y = 12 + (i % cols) * (tw + 12), 12 + (i // cols) * (th + 12)
        sheet.paste(im.resize((tw, th), Image.LANCZOS), (x, y))
        draw.text((x + 10, y + th - 20), "%d %s" % (i + 1, name), fill=(255, 255, 255))
    sheet.save(path, quality=90)


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--out", default=os.path.join(here, "..", "assets", "backgrounds"))
    ap.add_argument("--width", type=int, default=960, help="image width; height is 9/16 of it")
    ap.add_argument("--seed", type=int, default=1, help="change for different dunes")
    ap.add_argument("--all", action="store_true", help="all twelve, not just the built-in set")
    ap.add_argument("--only", type=int, help="render just this one of the twelve (1-12)")
    ap.add_argument("--quality", type=int, default=90)
    ap.add_argument("--sheet", help="also write a contact sheet of what was rendered to this file")
    ap.add_argument("--ssaa", type=int, default=3, help="anti-aliasing: samples per pixel side (3 = 9 rays per pixel)")
    args = ap.parse_args()
    if args.width < 320 or args.width % 8:
        sys.exit("--width must be a multiple of 8 and at least 320")
    names = [p[0] for p in PALETTES]
    if args.only:
        if not 1 <= args.only <= len(PALETTES):
            sys.exit("--only must be 1-%d" % len(PALETTES))
        picks = [args.only - 1]
    elif args.all:
        picks = range(len(PALETTES))
    else:
        picks = [names.index(name) for name in BUNDLED]
    out = os.path.abspath(args.out)
    os.makedirs(out, exist_ok=True)
    if not args.only and not args.all:
        for old in os.listdir(out):                      # the set replaces whatever was there
            if old.endswith(".jpg") or old == "index.json":
                os.remove(os.path.join(out, old))

    images, files = [], []
    for number, i in enumerate(picks, 1):
        im, name = render(i, args.width, args.seed, args.ssaa)
        file = "%d-%s.jpg" % (number, name)
        im.save(os.path.join(out, file), quality=args.quality, optimize=True)
        top = luminance(im, (0.0, 0.0, 1.0, 0.16))
        print("%-18s %4d KB  top-bar brightness %.2f" % (file, os.path.getsize(os.path.join(out, file)) // 1024, top))
        images.append(im)
        files.append(file)
    if not args.only and not args.all:
        with open(os.path.join(out, "index.json"), "w") as index:
            json.dump(files, index, indent=1)
            index.write("\n")
    if args.sheet and len(images) > 1:
        contact_sheet(images, [os.path.splitext(f)[0] for f in files], args.sheet)


if __name__ == "__main__":
    main()
