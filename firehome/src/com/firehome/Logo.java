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
 * a mark that is white on a colour and one that is dark on white both come out white.
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

    private Logo() {
    }

    /** The mark cropped to its bounds, or null if the artwork has nothing that stands out from its backdrop. */
    static Drawable mono(Resources res, Drawable art) {
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
