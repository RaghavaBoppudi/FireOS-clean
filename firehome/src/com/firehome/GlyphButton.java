package com.firehome;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

/** An icon button for the top of the screen: a white glyph that brightens, on a plate that appears when selected. */
public class GlyphButton extends ActiveView {
    // half the button: the glow reaches nothing at the button's edge, so there is no square where it would be cut off
    private static final float HALO_RADIUS = 0.5f;   // of the button's size

    private final View halo;      // a soft dark glow behind the glyph while it rests, so it reads on a bright photo
    private final ImageView glyph;
    private final GradientDrawable plate = new GradientDrawable();

    public GlyphButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        Resources res = getResources();
        TypedArray array = context.obtainStyledAttributes(attrs, new int[]{android.R.attr.src});
        halo = new View(context);
        GradientDrawable glow = new GradientDrawable();
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glow.setGradientRadius(res.getDimension(R.dimen.button_size) * HALO_RADIUS);
        glow.setColors(new int[]{res.getColor(R.color.halo), res.getColor(R.color.halo_edge), 0});
        halo.setBackground(glow);
        addView(halo, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        glyph = new ImageView(context);
        glyph.setImageDrawable(array.getDrawable(0));
        array.recycle();
        int size = res.getDimensionPixelSize(R.dimen.button_glyph);
        addView(glyph, new LayoutParams(size, size, Gravity.CENTER));

        plate.setCornerRadius(res.getDimension(R.dimen.tile_radius));
        plate.setColor(res.getColor(R.color.plate));
        setBackground(plate);
        setActive(0f);
    }

    @Override
    protected void onActive(float active) {
        plate.setAlpha(Math.round(255 * active));
        halo.setAlpha(1f - active);   // the plate takes over when selected
        glyph.setAlpha(RESTING_ALPHA + (1f - RESTING_ALPHA) * active);
    }
}
