package com.firehome;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.drawable.Drawable;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Random;

/**
 * A slot: a white mark that, while it is selected, brightens and blooms. A setting also carries its name under the
 * mark and, where it has one, its current value under that. Nothing is coloured.
 */
final class TileView extends ActiveView {
    // a jiggling mark rocks this far either way, one rock taking JIGGLE_MS
    private static final float JIGGLE_DEGREES = 1.8f;
    private static final long JIGGLE_MS = 120;
    private static final Random RANDOM = new Random();

    private final ViewGroup content;
    private ObjectAnimator jiggle;
    private boolean jiggling;      // wanted, whether or not it is running (it cannot while the mark is off screen)
    private MarkView mark;        // null where no mark could be cut from the artwork
    private TextView caption;     // the value under a setting's name

    TileView(Context context, Tile tile) {
        super(context, null);
        setContentDescription(tile.label);
        setClipChildren(false);   // the bloom reaches past the mark
        content = tile.named ? setting(context, tile) : app(context, tile);
        addView(content, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setActive(0f);
    }

    /** Swaps a setting's mark, for the one whose glyph changes (the Wi-Fi signal). */
    void setMark(Drawable drawable) {
        if (mark != null) {
            mark.setMark(drawable);
        }
    }

    /** Changes the value shown under a setting's name. */
    void setCaption(String text) {
        if (caption != null && text != null) {
            caption.setText(text);
        }
    }

    /** Starts or stops the mark rocking, as the icons of a phone do while they can be moved. */
    void jiggle(boolean on) {
        jiggling = on;
        rock();
    }

    private void rock() {
        if (jiggle != null) {
            jiggle.cancel();
            jiggle = null;
        }
        content.setRotation(0f);
        // with the system's animations switched off a repeating animator would spin without ever drawing a frame
        boolean allowed = Settings.Global.getFloat(getContext().getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f;
        if (jiggling && allowed && isAttachedToWindow()) {
            jiggle = ObjectAnimator.ofFloat(content, ROTATION, -JIGGLE_DEGREES, JIGGLE_DEGREES);
            jiggle.setDuration(JIGGLE_MS);
            jiggle.setRepeatCount(ObjectAnimator.INFINITE);
            jiggle.setRepeatMode(ObjectAnimator.REVERSE);
            jiggle.start();
            jiggle.setCurrentPlayTime((long) (RANDOM.nextFloat() * JIGGLE_MS * 2));   // no two marks rock in step
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        rock();       // a mark moved within its row is detached and attached again: it carries on rocking
    }

    @Override
    protected void onDetachedFromWindow() {
        if (jiggle != null) {
            jiggle.cancel();      // nothing to draw it on; it is started again if the mark comes back
            jiggle = null;
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onActive(float active) {
        content.setAlpha(RESTING_ALPHA + (1f - RESTING_ALPHA) * active);
        if (mark != null) {
            mark.setBloom(active);
        }
    }

    /** An app: its mark, fitted into one box so every mark is the same size whatever it shows. */
    private ViewGroup app(Context context, Tile tile) {
        Resources res = getResources();
        FrameLayout frame = new FrameLayout(context);
        frame.setClipChildren(false);
        if (tile.mark != null) {
            mark = new MarkView(context, tile.mark, res.getDimensionPixelSize(R.dimen.mark_width),
                    res.getDimensionPixelSize(R.dimen.mark_height));
            frame.addView(mark, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        } else {
            // nothing stood out from the artwork's backdrop: show the artwork itself, without its colour
            ImageView art = new ImageView(context);
            art.setScaleType(ImageView.ScaleType.FIT_CENTER);
            art.setImageDrawable(Tile.copy(tile.art));
            ColorMatrix grey = new ColorMatrix();
            grey.setSaturation(0f);
            art.setColorFilter(new ColorMatrixColorFilter(grey));
            int across = (res.getDimensionPixelSize(R.dimen.tile_width) - res.getDimensionPixelSize(R.dimen.mark_width)) / 2;
            int down = (res.getDimensionPixelSize(R.dimen.tile_height) - res.getDimensionPixelSize(R.dimen.mark_height)) / 2;
            art.setPadding(across, down, across, down);
            frame.addView(art, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        return frame;
    }

    /** A setting: the glyph, then its name, then its value if it has one. */
    private ViewGroup setting(Context context, Tile tile) {
        Resources res = getResources();
        LinearLayout column = new LinearLayout(context);
        column.setClipChildren(false);
        column.setOrientation(LinearLayout.VERTICAL);
        // glyph and name are centred in the tile on their own, so they line up across the grid; a value hangs below
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        column.setPadding(0, res.getDimensionPixelSize(R.dimen.setting_top), 0, 0);

        // the mark is larger than its glyph by the room its bloom needs, so it is given the glyph's own height
        // and the name is placed from there
        int size = res.getDimensionPixelSize(R.dimen.setting_glyph);
        mark = new MarkView(context, tile.mark, size, size);
        FrameLayout frame = new FrameLayout(context);
        frame.setClipChildren(false);
        frame.addView(mark, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        column.addView(frame, new LinearLayout.LayoutParams(size, size));

        TextView name = new TextView(context);
        name.setTextAppearance(context, R.style.Text_TileName);
        name.setText(tile.label);
        LinearLayout.LayoutParams below = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        below.topMargin = res.getDimensionPixelSize(R.dimen.setting_name_gap);
        column.addView(name, below);

        if (tile.caption != null) {
            caption = new TextView(context);
            caption.setTextAppearance(context, R.style.Text_TileCaption);
            caption.setText(tile.caption);
            column.addView(caption, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        return column;
    }
}
