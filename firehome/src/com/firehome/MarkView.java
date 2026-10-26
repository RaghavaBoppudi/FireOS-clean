package com.firehome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.drawable.Drawable;
import android.view.View;

/**
 * A white mark that blooms. The mark is drawn once, sharp; its own blurred shape is then added on top, so the mark
 * itself brightens and light spills out around it, as bloom does on a lit screen, with no second shape to see. The
 * bloom is made the first time it is wanted, since most marks are never selected. The view is larger than the mark by
 * the room the spill needs, and its parents must not clip it.
 */
final class MarkView extends View {
    // a tight halo that hugs the shape and a wide one that spreads, as shares of full white
    private static final float TIGHT_STRENGTH = 0.75f;
    private static final float WIDE_STRENGTH = 0.65f;
    // the blur reaches about this many radii past the shape
    private static final int REACH = 2;

    private final int width;
    private final int height;
    private final float tight;
    private final float wide;
    private final int pad;
    private final Paint spill = new Paint(Paint.FILTER_BITMAP_FLAG);
    private Drawable mark;
    private Bitmap bloom;
    private float amount;
    private float dark;       // 0 white, 1 near black: for a mark over a light photograph

    /** A mark that fits, centred, in a box of this size in pixels. */
    MarkView(Context context, Drawable mark, int width, int height) {
        super(context);
        this.width = width;
        this.height = height;
        tight = getResources().getDimension(R.dimen.bloom_tight);
        wide = getResources().getDimension(R.dimen.bloom_wide);
        pad = Math.round(wide * REACH);
        spill.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD));
        this.mark = Tile.copy(mark);
    }

    /** Swaps the picture, for a mark that changes (the Wi-Fi signal). */
    void setMark(Drawable drawable) {
        mark = Tile.copy(drawable);
        bloom = null;
        if (dark > 0f) {
            setDark(dark);
        }
        invalidate();
    }

    /** 0 draws the mark white, 1 near black; the bloom, which is light, fades out as it darkens. */
    void setDark(float dark) {
        this.dark = dark;
        int grey = Math.round(255 * (1f - dark) + 20 * dark);
        mark.setColorFilter(new PorterDuffColorFilter(Color.rgb(grey, grey, grey), PorterDuff.Mode.SRC_IN));
        bloom = null;      // made again from the mark as it now is, if it is wanted
        invalidate();
    }

    /** 0 is the mark alone; 1 is the mark in full bloom. */
    void setBloom(float amount) {
        this.amount = amount;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(width + 2 * pad, height + 2 * pad);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.save();
        canvas.translate(pad, pad);
        fit(canvas);
        canvas.restore();
        if (amount > 0f) {
            if (bloom == null) {
                bloom = make();
            }
            spill.setAlpha(Math.round(255 * amount * (1f - dark)));
            canvas.drawBitmap(bloom, 0f, 0f, spill);
        }
    }

    /** Draws the mark fitted and centred in its box. */
    private void fit(Canvas canvas) {
        int across = mark.getIntrinsicWidth() > 0 ? mark.getIntrinsicWidth() : width;
        int down = mark.getIntrinsicHeight() > 0 ? mark.getIntrinsicHeight() : height;
        float scale = Math.min(width / (float) across, height / (float) down);
        canvas.translate((width - across * scale) / 2f, (height - down * scale) / 2f);
        canvas.scale(scale, scale);
        mark.setBounds(0, 0, across, down);
        mark.draw(canvas);
    }

    /** The mark's own shape, blurred twice and in white, centred in a bitmap the size of this view. */
    private Bitmap make() {
        Bitmap shape = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        fit(new Canvas(shape));
        Bitmap made = Bitmap.createBitmap(width + 2 * pad, height + 2 * pad, Bitmap.Config.ARGB_8888);
        Canvas into = new Canvas(made);
        light(shape, into, tight, TIGHT_STRENGTH);
        light(shape, into, wide, WIDE_STRENGTH);
        shape.recycle();
        return made;
    }

    private void light(Bitmap shape, Canvas into, float radius, float strength) {
        Paint blur = new Paint();
        blur.setMaskFilter(new BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL));
        int[] offset = new int[2];
        Bitmap alpha = shape.extractAlpha(blur, offset);   // the blur makes it larger than the shape
        Paint white = new Paint(Paint.FILTER_BITMAP_FLAG);
        white.setColor(Color.argb(Math.round(255 * strength), 255, 255, 255));
        into.drawBitmap(alpha, pad + offset[0], pad + offset[1], white);
        alpha.recycle();
    }
}
