package com.firehome;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * A slot: a white mark, and under the selected one a brighter rectangle that fills the slot, a few pixels inside the
 * dock. A tile with a timer writes it inside its frame, in the same box as every other mark, so all marks are the same
 * size. Nothing else changes, and nothing is coloured.
 */
final class TileView extends ActiveView {
    private final View plate;
    private final View content;
    private TextView timer;       // the number inside the frame of a tile with a timer
    private TextView timerUnit;   // and its unit under it

    TileView(Context context, Tile tile) {
        super(context, null);
        Resources res = getResources();
        setContentDescription(tile.label);

        plate = new View(context);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(res.getColor(R.color.plate));
        shape.setCornerRadius(res.getDimension(R.dimen.tile_radius));
        plate.setBackground(shape);
        addView(plate, fill());

        content = tile.badge != null ? framedTimer(context, tile) : mark(context, tile);
        addView(content, fill());
        setActive(0f);
    }

    /** Swaps the mark's picture, for a tile whose glyph changes (the Wi-Fi signal). */
    void setMark(Drawable drawable) {
        if (content instanceof ImageView) {
            ((ImageView) content).setImageDrawable(drawable);
        }
    }

    /** Changes the timer in the frame: "30 min" is a 30 over a "min", "Daily" a word alone. */
    void setBadge(String text) {
        if (timer == null || text == null) {
            return;
        }
        int space = text.indexOf(' ');
        timer.setText(space < 0 ? text : text.substring(0, space));
        timerUnit.setText(space < 0 ? "" : text.substring(space + 1));
        timerUnit.setVisibility(space < 0 ? GONE : VISIBLE);
    }

    @Override
    protected void onActive(float active) {
        plate.setAlpha(active);
        content.setAlpha(RESTING_ALPHA + (1f - RESTING_ALPHA) * active);
    }

    private ImageView mark(Context context, Tile tile) {
        Resources res = getResources();
        ImageView mark = new ImageView(context);
        mark.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (tile.mark != null) {
            mark.setImageDrawable(Tile.copy(tile.mark));
            int across = (res.getDimensionPixelSize(R.dimen.tile_width) - res.getDimensionPixelSize(R.dimen.mark_width)) / 2;
            int down = (res.getDimensionPixelSize(R.dimen.tile_height) - res.getDimensionPixelSize(R.dimen.mark_height)) / 2;
            mark.setPadding(across, down, across, down);
        } else {
            // nothing stood out from the artwork's backdrop: show the artwork itself, without its colour
            mark.setImageDrawable(Tile.copy(tile.art));
            ColorMatrix grey = new ColorMatrix();
            grey.setSaturation(0f);
            mark.setColorFilter(new ColorMatrixColorFilter(grey));
        }
        return mark;
    }

    /** The frame glyph, as any other mark, with the timer written in its middle. */
    private FrameLayout framedTimer(Context context, Tile tile) {
        FrameLayout frame = new FrameLayout(context);
        frame.addView(mark(context, tile), fill());

        LinearLayout text = new LinearLayout(context);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setGravity(Gravity.CENTER);
        timer = new TextView(context);
        timer.setTextAppearance(context, R.style.Text_Timer);
        timerUnit = new TextView(context);
        timerUnit.setTextAppearance(context, R.style.Text_TimerUnit);
        text.addView(timer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        text.addView(timerUnit, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        frame.addView(text, fill());
        setBadge(tile.badge);
        return frame;
    }

    private static LayoutParams fill() {
        return new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }
}
