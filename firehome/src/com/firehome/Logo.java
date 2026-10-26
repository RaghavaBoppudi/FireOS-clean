package com.firehome;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

/**
 * Cuts an app's mark out of its artwork as white shapes on transparency. App artwork is a logo on a flat or
 * gently graded backdrop, so each pixel counts as part of the mark by how far its colour is from the backdrop's;
 * a mark that is white on a colour and one that is dark on white both come out white. Artwork that is an icon with
 * its name beside it gives the icon; a name over a swoosh gives the swoosh; artwork that is a name alone gives
 * nothing, and the app's first letter stands in.
 */
final class Logo {
    private static final int MAX_SIDE = 256;
    // Where the backdrop is read: just inside each corner, clear of rounded corners and edge shading
    private static final float INSET = 0.08f;
    // Colour distance from the backdrop at which a pixel starts and finishes counting as part of the mark
    private static final float EDGE_LOW = 0.2f;
    private static final float EDGE_HIGH = 0.5f;
    private static final float PRESENT = 0.3f;
    private static final float MARGIN = 0.04f;
    private static final float MIN_COVER = 0.004f;
    // A wordmark beside an icon is dropped: the mark ends at the first gap this wide, as a share of its height ...
    private static final float GAP = 0.04f;
    private static final int MIN_GAP = 2;
    // ... and a mark still wider than this, as a multiple of its height, or much shorter than the artwork, is a name;
    // if a swoosh under it can be told apart it is the mark (up to the second limit), and if not there is no mark
    private static final float WIDE = 1.6f;
    private static final float SWOOSH = 6f;
    private static final float TALL = 0.85f;
    // a row narrower than this share of the artwork's width is a letter's tail, not part of a swoosh
    private static final float TAIL = 0.05f;

    private Logo() {
    }

    /**
     * The mark cropped to its bounds, or null if the artwork has nothing that stands out from its backdrop. With
     * {@code whole} everything that stands out is the mark, name included (the Appstore's is what tells it apart).
     */
    static Drawable mono(Resources res, Drawable art, boolean whole) {
        int width = art.getIntrinsicWidth();
        int height = art.getIntrinsicHeight();
        if (width <= 0 || height <= 0) {
            return null;
        }
        float scale = Math.min(1f, MAX_SIDE / (float) Math.max(width, height));
        int w = Math.max(1, Math.round(width * scale));
        int h = Math.max(1, Math.round(height * scale));
        int[] pixels = pixels(art, w, h);
        int[] backdrop = backdrop(pixels, w, h);

        float[] mask = new float[w * h];
        int left = w;
        int top = h;
        int right = -1;
        int bottom = -1;
        int covered = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int pixel = pixels[y * w + x];
                float alpha = Color.alpha(pixel) / 255f;
                float m = backdrop == null ? alpha : alpha * smoothstep(distance(pixel, backdrop));
                mask[y * w + x] = m;
                if (m >= PRESENT) {
                    covered++;
                    left = Math.min(left, x);
                    right = Math.max(right, x);
                    top = Math.min(top, y);
                    bottom = Math.max(bottom, y);
                }
            }
        }
        if (covered < MIN_COVER * w * h) {
            return null;
        }

        if (!whole) {
            int[] mark = leftmost(mask, w, new int[]{left, top, right, bottom});
            // an icon stands as tall as the artwork; the first letter of a name does not
            if (wide(mark, WIDE) || mark[3] - mark[1] < TALL * (bottom - top)) {
                // a name over a swoosh: the swoosh is the mark, if the name can be told apart from it
                int[] swoosh = lowest(mask, w, new int[]{left, top, right, bottom});
                if (swoosh[3] - swoosh[1] >= bottom - top || wide(swoosh, SWOOSH)) {
                    return null;
                }
                mark = swoosh;
            }
            left = mark[0];
            top = mark[1];
            right = mark[2];
            bottom = mark[3];
            left = mark[0];
            top = mark[1];
            right = mark[2];
            bottom = mark[3];
        }

        int pad = Math.round(Math.max(right - left, bottom - top) * MARGIN);
        left = Math.max(0, left - pad);
        top = Math.max(0, top - pad);
        right = Math.min(w - 1, right + pad);
        bottom = Math.min(h - 1, bottom + pad);
        int cropWidth = right - left + 1;
        int cropHeight = bottom - top + 1;
        int[] white = new int[cropWidth * cropHeight];
        for (int y = 0; y < cropHeight; y++) {
            for (int x = 0; x < cropWidth; x++) {
                white[y * cropWidth + x] = (Math.round(mask[(top + y) * w + left + x] * 255) << 24) | 0x00FFFFFF;
            }
        }
        return new BitmapDrawable(res, Bitmap.createBitmap(white, cropWidth, cropHeight, Bitmap.Config.ARGB_8888));
    }

    private static boolean wide(int[] box, float limit) {
        return box[2] - box[0] + 1 > limit * (box[3] - box[1] + 1);
    }

    /** The first part of the mark reading left to right: up to the first gap in its columns as wide as GAP of its height. */
    private static int[] leftmost(float[] mask, int w, int[] box) {
        int gap = Math.max(MIN_GAP, Math.round((box[3] - box[1] + 1) * GAP));
        int empty = 0;
        int end = box[0];
        for (int x = box[0]; x <= box[2] && empty < gap; x++) {
            boolean inked = false;
            for (int y = box[1]; y <= box[3] && !inked; y++) {
                inked = mask[y * w + x] >= PRESENT;
            }
            empty = inked ? 0 : empty + 1;
            end = inked ? x : end;
        }
        return tighten(mask, w, new int[]{box[0], box[1], end, box[3]});
    }

    /**
     * The lowest part of the mark: the rows up from the bottom while they have something in them, and once the part
     * has been seen to be broad, only while they stay broad, so a letter's tail that touches a swoosh is left out.
     */
    private static int[] lowest(float[] mask, int w, int[] box) {
        int minSpan = Math.round((box[2] - box[0] + 1) * TAIL);
        boolean broad = false;
        int start = box[3];
        for (int y = box[3]; y >= box[1]; y--) {
            int first = -1;
            int last = -1;
            for (int x = box[0]; x <= box[2]; x++) {
                if (mask[y * w + x] >= PRESENT) {
                    first = first < 0 ? x : first;
                    last = x;
                }
            }
            int span = first < 0 ? 0 : last - first + 1;
            broad |= span >= minSpan;
            if (span == 0 || broad && span < minSpan) {
                break;
            }
            start = y;
        }
        return tighten(mask, w, new int[]{box[0], start, box[2], box[3]});
    }

    private static int[] tighten(float[] mask, int w, int[] box) {
        int[] tight = {box[2], box[3], box[0], box[1]};
        for (int y = box[1]; y <= box[3]; y++) {
            for (int x = box[0]; x <= box[2]; x++) {
                if (mask[y * w + x] >= PRESENT) {
                    tight[0] = Math.min(tight[0], x);
                    tight[1] = Math.min(tight[1], y);
                    tight[2] = Math.max(tight[2], x);
                    tight[3] = Math.max(tight[3], y);
                }
            }
        }
        return tight;
    }

    private static int[] pixels(Drawable art, int w, int h) {
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Drawable copy = Tile.copy(art);
        copy.setBounds(0, 0, w, h);
        copy.draw(new Canvas(bitmap));
        int[] pixels = new int[w * h];
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h);
        bitmap.recycle();
        return pixels;
    }

    /** The average RGB of the four corner samples, or null when most are transparent: then alpha is the mark. */
    private static int[] backdrop(int[] pixels, int w, int h) {
        int ax = Math.round(w * INSET);
        int ay = Math.round(h * INSET);
        int[] samples = {
                pixels[ay * w + ax], pixels[ay * w + (w - 1 - ax)],
                pixels[(h - 1 - ay) * w + ax], pixels[(h - 1 - ay) * w + (w - 1 - ax)]};
        int[] sum = new int[3];
        int opaque = 0;
        for (int sample : samples) {
            if (Color.alpha(sample) >= 128) {
                sum[0] += Color.red(sample);
                sum[1] += Color.green(sample);
                sum[2] += Color.blue(sample);
                opaque++;
            }
        }
        if (opaque < 3) {
            return null;
        }
        return new int[]{sum[0] / opaque, sum[1] / opaque, sum[2] / opaque};
    }

    private static float distance(int pixel, int[] backdrop) {
        int d = Math.max(Math.abs(Color.red(pixel) - backdrop[0]),
                Math.max(Math.abs(Color.green(pixel) - backdrop[1]), Math.abs(Color.blue(pixel) - backdrop[2])));
        return d / 255f;
    }

    private static float smoothstep(float distance) {
        float t = Math.min(Math.max((distance - EDGE_LOW) / (EDGE_HIGH - EDGE_LOW), 0f), 1f);
        return t * t * (3f - 2f * t);
    }
}
