package com.firehome;

import android.content.res.Resources;
import android.view.View;
import android.widget.TextView;

/**
 * Over a wallpaper the ink of the text and of the buttons along the bottom is chosen by what is behind it: dark where
 * the picture is light, white where it is dark, so they can be read on any photograph. (The discs, their marks and their names stay white.) The picture is read from the blurred copy the discs are made
 * of, which is the average of what lies behind, and a view changes ink only when the picture is clearly on the other
 * side of the line, so one on the line does not flicker. Without a wallpaper nothing here does anything: the theme's ink
 * stands.
 */
final class Ink {
    private static final float LINE = 0.56f;          // lighter than this behind it, and the ink is dark
    private static final float HYSTERESIS = 0.04f;

    private Ink() {
    }

    /**
     * True if dark ink is wanted on {@code view}: the picture behind it is light. {@code was} is what it had until now.
     */
    static boolean dark(View view, boolean was) {
        View root = view.getRootView();
        int w = root.getWidth();
        int h = root.getHeight();
        if (w == 0 || h == 0 || view.getWidth() == 0 || !view.isShown()) {
            return was;
        }
        int[] at = new int[2];
        view.getLocationOnScreen(at);
        float lightness = Wallpaper.lightness(at[0] / (float) w, at[1] / (float) h,
                (at[0] + view.getWidth()) / (float) w, (at[1] + view.getHeight()) / (float) h);
        if (lightness < 0) {
            return was;          // no picture: nothing to go by
        }
        return lightness > LINE + (was ? -HYSTERESIS : HYSTERESIS);
    }

    /** Gives a line of text the ink that suits what is behind it; {@code dimmed} for a caption or a credit. */
    static void paint(TextView text, boolean dimmed) {
        if (Wallpaper.soft() == null) {
            return;
        }
        Boolean was = (Boolean) text.getTag(R.id.ink_tone);
        boolean dark = dark(text, was != null && was);
        if (was != null && was == dark) {
            return;
        }
        text.setTag(R.id.ink_tone, dark);
        Resources res = text.getResources();
        text.setTextColor(res.getColor(dark ? (dimmed ? R.color.ink_dim_light : R.color.ink_light)
                : (dimmed ? R.color.ink_dim_photo : R.color.ink_photo)));
        text.setShadowLayer(8f, 0f, 1f, res.getColor(dark ? R.color.shadow_text_none : R.color.shadow_text_photo));
    }

    /** The colour of a mark: dark or white. */
    static int mark(Resources res, boolean dark) {
        return res.getColor(dark ? R.color.ink_light : R.color.ink_photo);
    }
}
