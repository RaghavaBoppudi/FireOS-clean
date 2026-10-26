package com.firehome;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Property;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Random;

/**
 * A disc of frosted glass with a mark in it and its name under it: the one control of the shelf. (The buttons at the
 * bottom are Shortcuts.) Selecting it grows the disc, lifts its shadow and fades its name (and, for
 * a setting, its value) in. Editing the dock can also pick it up (raised 1) and take it off the dock (raised 2). Two
 * animated values, {@code selected} and {@code raised}, drive everything that changes, so its parts cannot fall out
 * of step.
 */
final class Bubble extends FrameLayout {
    /**
     * The curve for anything the user sets going. It leaves at once, so a press is answered in the first frame, and
     * settles without overshoot, as a critically damped spring does; a curve that eases in would feel late.
     */
    static final PathInterpolator OUT = new PathInterpolator(0.2f, 0f, 0f, 1f);

    private static final float GROWTH = 0.45f;            // a selected disc is this much larger than at rest
    private static final float RAISE_GROWTH = 0.15f;      // and each step of being raised makes it this much larger
    private static final float MARK_SHARE = 0.54f;        // the mark's share of the disc's width
    private static final float WIDE_SHARE = 0.72f;
    private static final float INK_SHARE = 0.16f;         // the share of the disc's area that a logo's ink covers, whatever its shape
    private static final float LOGO_REACH = 0.88f;        // and the most of the disc's width that a logo's corners may reach
    private static final int INK_ALPHA = 64;
    private static final int SAMPLE = 64;                 // a logo is drawn this wide, to see how much of its box is ink
    private static final float DISABLED_ALPHA = 0.4f;
    private static final float LETTER_SHARE = 0.4f;
    private static final float JIGGLE_DEGREES = 1.8f;
    private static final long JIGGLE_MS = 120;
    private static final Random RANDOM = new Random();

    private static final Property<Bubble, Float> SELECTED = new Property<Bubble, Float>(Float.class, "selected") {
        @Override
        public Float get(Bubble bubble) {
            return bubble.selected;
        }

        @Override
        public void set(Bubble bubble, Float value) {
            bubble.selected = value;
            bubble.apply();
        }
    };

    private static final Property<Bubble, Float> RAISED = new Property<Bubble, Float>(Float.class, "raised") {
        @Override
        public Float get(Bubble bubble) {
            return bubble.raised;
        }

        @Override
        public void set(Bubble bubble, Float value) {
            bubble.raised = value;
            bubble.apply();
        }
    };

    private final FrameLayout disc;
    private final ImageView glyph;      // null for an app that has no mark: a letter stands in
    private final GradientDrawable highlight;   // the tone that comes up over the disc while it is selected
    private final TextView name;
    private final TextView caption;     // null unless the tile has a value
    private final int diameter;
    private final float restElevation;
    private final float selectedElevation;
    private final float lift;
    private final long durationMs;
    private ObjectAnimator selecting;
    private ObjectAnimator jiggle;
    private boolean jiggling;           // wanted, whether or not it is running (it cannot while off screen)
    private float selected;
    private float raised;

    /** A disc of frosted glass, {@code diameter} wide, with the tile's mark in it. */
    Bubble(Context context, Tile tile, int diameter) {
        super(context);
        this.diameter = diameter;
        Resources res = getResources();
        restElevation = res.getDimension(R.dimen.elevation_rest);
        selectedElevation = res.getDimension(R.dimen.elevation_selected);
        lift = res.getDimension(R.dimen.lift);
        durationMs = res.getInteger(R.integer.select_ms);
        setFocusable(true);
        setClipChildren(false);
        setContentDescription(tile.label);

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        column.setClipChildren(false);
        column.setClipToPadding(false);   // or the padding would cut off a disc that has grown
        column.setPadding(0, res.getDimensionPixelSize(R.dimen.slot_top), 0, 0);
        addView(column, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        disc = new FrameLayout(context);
        highlight = Glass.lift(context);
        Glass.shape(context, disc, highlight);
        column.addView(disc, new LinearLayout.LayoutParams(diameter, diameter));

        int ink = Look.colour(context, R.attr.ink);
        if (tile.icon != null) {
            Drawable art = Tile.copy(tile.icon);
            int size = markSize(art, diameter);
            glyph = new ImageView(context);
            glyph.setScaleType(ImageView.ScaleType.FIT_CENTER);
            glyph.setColorFilter(ink, PorterDuff.Mode.SRC_IN);
            glyph.setImageDrawable(art);
            glyph.setAlpha(tile.enabled ? 1f : DISABLED_ALPHA);     // a mark that does nothing is dimmed, not its name
            disc.addView(glyph, new LayoutParams(size, size, Gravity.CENTER));
        } else {
            glyph = null;
            TextView letter = new TextView(context);
            letter.setTextAppearance(context, R.style.Text_Label);
            letter.setTextSize(TypedValue.COMPLEX_UNIT_PX, diameter * LETTER_SHARE);
            letter.setText(tile.label.substring(0, Math.min(1, tile.label.length())).toUpperCase());
            disc.addView(letter, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        }

        name = line(context, R.style.Text_Label, tile.label);
        LinearLayout.LayoutParams below = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        below.topMargin = res.getDimensionPixelSize(R.dimen.name_gap);
        column.addView(name, below);
        if (tile.caption != null) {
            caption = line(context, R.style.Text_Caption, tile.caption);
            column.addView(caption, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        } else {
            caption = null;
        }
        apply();
    }

    /**
     * The width of the box a mark is drawn in. Every mark, an app's logo or a setting's glyph, is sized by its ink: the
     * same area of the disc whether it is a solid block, a thin curve or a ring, so that none looks heavier or smaller
     * than the others, and not so large that its corners leave the disc. (One with no ink to measure gets a fixed share
     * of the width, more if it is long and low, or it would be a sliver.)
     */
    private static int markSize(Drawable art, int diameter) {
        int width = art.getIntrinsicWidth();
        int height = art.getIntrinsicHeight();
        float aspect = width / (float) Math.max(1, height);
        float share = MARK_SHARE + (WIDE_SHARE - MARK_SHARE) * Math.min(1f, Math.max(0f, (aspect - 2f) / 2f));
        if (width <= 0 || height <= 0) {
            return Math.round(diameter * share);
        }
        int w = aspect >= 1f ? SAMPLE : Math.max(1, Math.round(SAMPLE * aspect));
        int h = aspect >= 1f ? Math.max(1, Math.round(SAMPLE / aspect)) : SAMPLE;
        Bitmap sample = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        art.setBounds(0, 0, w, h);
        art.draw(new Canvas(sample));
        int[] pixels = new int[w * h];
        sample.getPixels(pixels, 0, w, 0, 0, w, h);
        sample.recycle();
        int ink = 0;
        int left = w;
        int right = -1;
        int top = h;
        int bottom = -1;
        for (int i = 0; i < pixels.length; i++) {
            if ((pixels[i] >>> 24) > INK_ALPHA) {
                ink++;
                left = Math.min(left, i % w);
                right = Math.max(right, i % w);
                top = Math.min(top, i / w);
                bottom = Math.max(bottom, i / w);
            }
        }
        if (ink == 0) {
            return Math.round(diameter * share);
        }
        float scale = (float) Math.sqrt(INK_SHARE / ink);         // the disc's widths in one sample pixel
        float reach = (float) Math.hypot(right - left + 1, bottom - top + 1) * scale;
        if (reach > LOGO_REACH) {
            scale *= LOGO_REACH / reach;
        }
        return Math.round(diameter * scale * Math.max(w, h));
    }

    private static TextView line(Context context, int style, String text) {
        TextView line = new TextView(context);
        line.setTextAppearance(context, style);
        line.setSingleLine(true);
        line.setEllipsize(TextUtils.TruncateAt.END);
        line.setGravity(Gravity.CENTER_HORIZONTAL);
        line.setText(text);
        return line;
    }

    /** The picture behind the disc has changed: draw its glass again. */
    void repaintGlass() {
        disc.invalidate();
    }

    /** Changes the value shown under a setting's name. */
    void setCaption(String text) {
        if (caption != null && text != null) {
            caption.setText(text);
        }
    }

    /** Swaps the mark, for the one setting whose glyph changes (the Wi-Fi signal). */
    void setGlyph(Drawable drawable) {
        if (glyph != null) {
            glyph.setImageDrawable(Tile.copy(drawable));
        }
    }

    /** 0 at rest, 1 picked up, 2 taken off the dock. */
    void raise(float level) {
        ObjectAnimator move = ObjectAnimator.ofFloat(this, RAISED, raised, level);
        move.setDuration(durationMs);
        move.setInterpolator(OUT);
        move.start();
    }

    /** Starts or stops the disc rocking, as the icons of a phone do while they can be moved. */
    void jiggle(boolean on) {
        jiggling = on;
        rock();
    }

    private void rock() {
        if (jiggle != null) {
            jiggle.cancel();
            jiggle = null;
        }
        disc.setRotation(0f);
        // with the system's animations switched off a repeating animator would spin without ever drawing a frame
        boolean allowed = Settings.Global.getFloat(getContext().getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f;
        if (jiggling && allowed && isAttachedToWindow()) {
            jiggle = ObjectAnimator.ofFloat(disc, ROTATION, -JIGGLE_DEGREES, JIGGLE_DEGREES);
            jiggle.setDuration(JIGGLE_MS);
            jiggle.setRepeatCount(ObjectAnimator.INFINITE);
            jiggle.setRepeatMode(ObjectAnimator.REVERSE);
            jiggle.start();
            jiggle.setCurrentPlayTime((long) (RANDOM.nextFloat() * JIGGLE_MS * 2));   // no two rock in step
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        rock();       // a disc moved within its row is detached and attached again: it carries on rocking
    }

    @Override
    protected void onDetachedFromWindow() {
        if (jiggle != null) {
            jiggle.cancel();      // nothing to draw it on; it is started again if the disc comes back
            jiggle = null;
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        if (selecting != null) {
            selecting.cancel();
        }
        selecting = ObjectAnimator.ofFloat(this, SELECTED, selected, gainFocus ? 1f : 0f);
        selecting.setDuration(durationMs);
        selecting.setInterpolator(OUT);
        selecting.start();
    }

    private void apply() {
        float scale = (1f + GROWTH * selected) * (1f + RAISE_GROWTH * raised);
        float away = Math.max(raised - 1f, 0f);
        highlight.setAlpha(Math.round(255 * selected));
        disc.setScaleX(scale);
        disc.setScaleY(scale);
        disc.setTranslationY(-lift * away);
        disc.setElevation(restElevation + (selectedElevation - restElevation) * Math.max(selected, Math.min(raised, 1f)));
        // the name keeps clear of a disc that has grown, and is shown only while the disc is selected and on the dock
        float shown = selected * (1f - away);
        float push = diameter * (scale - 1f) / 2f;
        name.setTranslationY(push);
        name.setAlpha(shown);
        if (caption != null) {
            caption.setTranslationY(push);
            caption.setAlpha(shown);
        }
    }
}
