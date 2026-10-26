package com.firehome;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;

/** An icon button for the top of the screen: a white glyph that brightens and blooms while it is selected. */
public class GlyphButton extends ActiveView {
    // half the button: the halo reaches nothing at the button's edge, so there is no square where it would be cut off
    private static final float HALO_RADIUS = 0.5f;   // of the button's size

    private final MarkView glyph;
    private final View halo;

    public GlyphButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        Resources res = getResources();
        TypedArray array = context.obtainStyledAttributes(attrs, new int[]{android.R.attr.src});
        setClipChildren(false);   // the bloom reaches past the button
        // a soft dark halo behind the glyph, so it reads on a bright photo
        halo = new View(context);
        GradientDrawable shade = new GradientDrawable();
        shade.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        shade.setGradientRadius(res.getDimension(R.dimen.button_size) * HALO_RADIUS);
        shade.setColors(new int[]{res.getColor(R.color.halo), res.getColor(R.color.halo_edge), 0});
        halo.setBackground(shade);
        addView(halo, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        int size = res.getDimensionPixelSize(R.dimen.button_glyph);
        glyph = new MarkView(context, array.getDrawable(0), size, size);
        array.recycle();
        addView(glyph, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));
        setActive(0f);
    }

    /** 0 for a dark photograph behind the button, which takes a white glyph, to 1 for a light one, which takes black. */
    void setTone(float light) {
        glyph.setDark(light);
        halo.setAlpha(1f - light);     // the dark halo that helps a white glyph is not wanted under a black one
    }

    @Override
    protected void onActive(float active) {
        glyph.setBloom(active);
        glyph.setAlpha(RESTING_ALPHA + (1f - RESTING_ALPHA) * active);
    }
}
