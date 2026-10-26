package com.firehome;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.TypedArray;

/**
 * The two looks of the home, Light (the default) and Dark, and which of them the user has chosen. The colours are
 * attributes of the themes in styles.xml, so a view asks the theme it is in for them and nothing else knows which look
 * is on.
 */
final class Look {
    private static final String PREFERENCES = "look";
    private static final String KEY_DARK = "dark";

    private Look() {
    }

    /** Gives an activity the chosen look; call it before the content is set. */
    static void apply(Activity activity) {
        activity.setTheme(theme(activity));
    }

    static int theme(Context context) {
        return dark(context) ? R.style.Dark : R.style.Light;
    }

    static boolean dark(Context context) {
        return prefs(context).getBoolean(KEY_DARK, false);
    }

    static void toggle(Context context) {
        prefs(context).edit().putBoolean(KEY_DARK, !dark(context)).apply();
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
