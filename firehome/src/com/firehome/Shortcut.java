package com.firehome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * One of the buttons along the bottom right of the home (Apps, Edit, Settings): the mark alone, flat, with no disc. It
 * rests a little dimmed; selecting it grows the mark, brings it to full strength and fades its name in below it.
 */
final class Shortcut extends FrameLayout {
    private static final float GROWTH = 0.3f;        // a selected mark is this much larger than at rest
    private static final float DIMMED = 0.7f;        // the mark's strength until it is selected

    private final ImageView mark;
    private final TextView name;
    private final long durationMs;
    private ValueAnimator selecting;
    private float selected;
    private boolean dark;

    /** {@code label} is its name, shown while it is selected, and what a screen reader says. */
    Shortcut(Context context, Drawable glyph, String label) {
        super(context);
        Resources res = getResources();
        durationMs = res.getInteger(R.integer.select_ms);
        setFocusable(true);
        setClipChildren(false);
        setContentDescription(label);

        int size = res.getDimensionPixelSize(R.dimen.button_glyph);
        mark = new ImageView(context);
        mark.setScaleType(ImageView.ScaleType.FIT_CENTER);
        mark.setColorFilter(Look.colour(context, R.attr.ink), PorterDuff.Mode.SRC_IN);
        mark.setImageDrawable(Tile.copy(glyph));
        addView(mark, new LayoutParams(size, size, Gravity.CENTER));

        name = new TextView(context);
        name.setTextAppearance(context, R.style.Text_Label);
        name.setGravity(Gravity.CENTER_HORIZONTAL);
        LayoutParams below = new LayoutParams(res.getDimensionPixelSize(R.dimen.button_name_width),
                LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        below.topMargin = res.getDimensionPixelSize(R.dimen.button_name_top);
        name.setText(label);
        addView(name, below);
        apply();
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        if (selecting != null) {
            selecting.cancel();
        }
        selecting = ValueAnimator.ofFloat(selected, gainFocus ? 1f : 0f);
        selecting.setDuration(durationMs);
        selecting.setInterpolator(Bubble.OUT);
        selecting.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                selected = (Float) animation.getAnimatedValue();
                apply();
            }
        });
        selecting.start();
    }

    /** Over a wallpaper, the mark and the name take the ink that suits what is behind them. */
    void tone() {
        if (Wallpaper.soft() == null) {
            return;
        }
        boolean now = Ink.dark(mark, dark);
        if (now != dark || mark.getTag(R.id.ink_tone) == null) {
            dark = now;
            mark.setTag(R.id.ink_tone, now);
            mark.setColorFilter(Ink.mark(getResources(), now), PorterDuff.Mode.SRC_IN);
        }
        Ink.paint(name, false);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        post(new Runnable() {
            @Override
            public void run() {
                tone();
            }
        });
    }

    private void apply() {
        float scale = 1f + GROWTH * selected;
        mark.setScaleX(scale);
        mark.setScaleY(scale);
        mark.setAlpha(DIMMED + (1f - DIMMED) * selected);
        name.setAlpha(selected);
    }
}
