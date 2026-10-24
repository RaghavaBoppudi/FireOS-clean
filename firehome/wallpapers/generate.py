#!/usr/bin/env python3
"""Generates the background gradients in assets/backgrounds/, with the index.json that lists them. Needs Pillow.

    python3 wallpapers/generate.py

Each wallpaper is a dark ground with a few large soft pools of colour, blurred until they melt together, under a fine
grain that stops the bands an 8-bit gradient would show. Seeded, so a run reproduces the files exactly.
"""
import json
import os
import random

from PIL import Image, ImageChops, ImageDraw, ImageFilter

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "assets", "backgrounds")
SIZE = (1920, 1080)
WORK = (480, 270)   # painted small and scaled up: the scaling is part of the blur

# name, ground, then pools of (colour, x, y, radius), positions and radii as fractions of the width
WALLPAPERS = [
    ("Ultramarine", (6, 8, 28), [((38, 70, 230), .25, .15, .75), ((120, 70, 240), .85, .55, .6), ((20, 140, 255), .5, 1.1, .7)]),
    ("Violet", (14, 6, 30), [((140, 60, 240), .2, .1, .7), ((230, 80, 200), .9, .7, .6), ((70, 50, 220), .45, 1.1, .65)]),
    ("Sunset", (24, 8, 14), [((255, 120, 60), .15, 1.0, .8), ((240, 60, 120), .8, .8, .65), ((120, 50, 200), .6, -.1, .7)]),
    ("Ember", (22, 6, 6), [((255, 80, 40), .3, 1.05, .75), ((255, 170, 60), .75, 1.0, .5), ((150, 30, 60), .8, .0, .7)]),
    ("Rose", (24, 8, 18), [((255, 110, 150), .2, .2, .7), ((190, 70, 230), .85, .5, .6), ((255, 170, 120), .5, 1.15, .6)]),
    ("Teal", (4, 16, 20), [((20, 190, 190), .2, .9, .75), ((30, 110, 230), .85, .25, .65), ((70, 230, 170), .6, 1.1, .5)]),
    ("Aurora", (4, 12, 18), [((40, 220, 150), .15, .3, .65), ((60, 120, 250), .75, .2, .7), ((170, 80, 240), .9, .95, .6)]),
    ("Lagoon", (4, 10, 26), [((30, 150, 255), .3, .5, .75), ((20, 220, 200), .9, .9, .55), ((60, 60, 200), .1, -.1, .7)]),
    ("Dusk", (12, 8, 26), [((90, 70, 230), .3, .1, .75), ((255, 120, 110), .85, 1.0, .65), ((40, 50, 140), .1, 1.0, .6)]),
    ("Mint", (6, 18, 16), [((90, 235, 190), .2, .2, .65), ((50, 160, 230), .8, .75, .65), ((190, 240, 130), .6, 1.15, .5)]),
    ("Amethyst", (10, 6, 22), [((170, 90, 255), .25, .6, .75), ((255, 100, 170), .9, .1, .55), ((60, 40, 170), .6, 1.1, .7)]),
    ("Graphite", (10, 11, 14), [((90, 110, 150), .2, .1, .75), ((60, 70, 100), .85, .8, .7), ((130, 150, 190), .55, 1.15, .5)]),
]


def paint(ground, pools, rng):
    img = Image.new("RGB", WORK, ground)
    w, h = WORK
    for colour, x, y, radius in pools:
        pool = Image.new("L", WORK, 0)
        r = radius * w
        cx, cy = x * w + rng.uniform(-.03, .03) * w, y * h
        ImageDraw.Draw(pool).ellipse((cx - r, cy - r * .8, cx + r, cy + r * .8), fill=255)
        pool = pool.filter(ImageFilter.GaussianBlur(r * .45))
        pool = pool.point(lambda v: int(v * .62))
        img = Image.composite(Image.new("RGB", WORK, colour), img, pool)
    return img.filter(ImageFilter.GaussianBlur(18))


def grain(rng):
    noise = Image.effect_noise(SIZE, 14).convert("RGB")   # mean 128, so it adds and subtracts equally
    return noise


def main():
    os.makedirs(OUT, exist_ok=True)
    for name in os.listdir(OUT):
        if name.endswith(".jpg"):
            os.remove(os.path.join(OUT, name))
    names = []
    for n, (title, ground, pools) in enumerate(WALLPAPERS, 1):
        rng = random.Random(n)
        img = paint(ground, pools, rng).resize(SIZE, Image.BICUBIC)
        noise = grain(rng)
        # overlay the grain at low strength: pull it towards mid-grey, then add its deviation from mid-grey
        faint = Image.blend(Image.new("RGB", SIZE, (128, 128, 128)), noise, .22)
        img = ImageChops.add(img, faint, scale=1, offset=-128)
        name = "%02d.jpg" % n
        img.save(os.path.join(OUT, name), "JPEG", quality=90, subsampling=0)
        names.append(name)
    with open(os.path.join(OUT, "index.json"), "w") as f:
        json.dump(names, f, indent=2)
    print(len(names), "wallpapers")


if __name__ == "__main__":
    main()
