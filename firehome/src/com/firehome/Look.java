package com.firehome;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.TypedArray;

/**
 * The looks of the home: Light (the default) and Dark, which the user chooses between, and the one a wallpaper gives
 * it. The colours are attributes of the themes in styles.xml, so a view asks the theme it is in for them and nothing
 * else knows which look is on.
 */
final class Look {
    private static final String PREFERENCES = "look";
    private static final String KEY_DARK = "dark";
    private static final String KEY_WALLPAPER = "wallpaper";

    private Look() {
    }

    /** Gives an activity the chosen look; call it before the content is set. */
    static void apply(Activity activity) {
        activity.setTheme(theme(activity));
    }

    /** A wallpaper has one look of its own; without one, the light or the dark look the user chose. */
    static int theme(Context context) {
        return wallpaper(context) ? R.style.Photo : dark(context) ? R.style.Dark : R.style.Light;
    }

    static boolean dark(Context context) {
        return prefs(context).getBoolean(KEY_DARK, false);
    }

    static void toggle(Context context) {
        prefs(context).edit().putBoolean(KEY_DARK, !dark(context)).apply();
    }

    /** True (the default) if a wallpaper is shown behind the discs; false, the flat ground. */
    static boolean wallpaper(Context context) {
        return prefs(context).getBoolean(KEY_WALLPAPER, true);
    }

    static void toggleWallpaper(Context context) {
        prefs(context).edit().putBoolean(KEY_WALLPAPER, !wallpaper(context)).apply();
    }

    /** One of the theme's colours (R.attr.ground, disc, ink or inkDim) in this context's theme. */
    static int colour(Context context, int attr) {
        TypedArray values = context.obtainStyledAttributes(new int[]{attr});
        try {
            return values.getColor(0, 0);
        } finally {
            values.recycle();
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }
}
