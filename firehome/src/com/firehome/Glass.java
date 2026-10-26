package com.firehome;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.view.View;
import android.view.ViewOutlineProvider;

/**
 * The glass of a disc, for every disc on the home (Bubble's, and the buttons' along the bottom). On the flat ground it
 * is a rim of light, the glass, and a tone that comes up while the disc is selected; over a wallpaper it is the soft
 * picture behind the disc under an even veil, with no rim and no shadow.
 */
final class Glass {
    /** A circle the size of the view: what a disc's shadow, and a picture cut to a disc, follow. */
    static final ViewOutlineProvider CIRCLE = new ViewOutlineProvider() {
        @Override
        public void getOutline(View view, Outline outline) {
            outline.setOval(0, 0, view.getWidth(), view.getHeight());
        }
    };

    private Glass() {
    }

    /** The tone that comes up over a disc while it is selected; the caller sets how much, with the alpha. */
    static GradientDrawable lift(Context context) {
        int tone = Look.colour(context, R.attr.discLift);
        return oval(tone, tone);
    }

    /** Makes {@code disc} a disc of glass, with {@code lift} over it and a shadow that follows its circle. */
    static void shape(Context context, View disc, GradientDrawable lift) {
        Resources res = context.getResources();
        disc.setBackground(Wallpaper.soft() != null ? frosted(res, disc, lift) : flat(context, res, lift));
        disc.setOutlineProvider(CIRCLE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            disc.setOutlineAmbientShadowColor(Look.colour(context, R.attr.shadowAmbient));
            disc.setOutlineSpotShadowColor(Look.colour(context, R.attr.shadowSpot));
        }
    }

    /** On the flat ground, from the outside in: a rim of light fading down from the top, then the glass, then the lift. */
    private static Drawable flat(Context context, Resources res, GradientDrawable lift) {
        LayerDrawable layers = new LayerDrawable(new Drawable[]{
                oval(Look.colour(context, R.attr.discRimTop), Look.colour(context, R.attr.discRimBottom)),
                oval(Look.colour(context, R.attr.discTop), Look.colour(context, R.attr.discBottom)), lift});
        int rim = res.getDimensionPixelSize(R.dimen.rim);
        layers.setLayerInset(1, rim, rim, rim, rim);
        layers.setLayerInset(2, rim, rim, rim, rim);
        return layers;
    }

    /** Over a wallpaper, flat frosted glass: the soft picture behind the disc, cut to a circle, under one even veil. */
    private static Drawable frosted(Resources res, View disc, GradientDrawable lift) {
        int veil = res.getColor(R.color.glass_veil);
        return new LayerDrawable(new Drawable[]{new Frost(disc), oval(veil, veil), lift});
    }

    private static GradientDrawable oval(int top, int bottom) {
        GradientDrawable shape = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{top, bottom});
        shape.setShape(GradientDrawable.OVAL);
        return shape;
    }
}
