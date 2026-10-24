package com.firehome;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.ImageView;

/** An icon button for the top of the screen: a white glyph that brightens, on a plate that appears when selected. */
public class GlyphButton extends ActiveView {
    private final ImageView glyph;
    private final GradientDrawable plate = new GradientDrawable();

    public GlyphButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        Resources res = getResources();
        TypedArray array = context.obtainStyledAttributes(attrs, new int[]{android.R.attr.src});
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
        glyph.setAlpha(RESTING_ALPHA + (1f - RESTING_ALPHA) * active);
    }
}
