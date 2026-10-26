package com.firehome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;

/**
 * The small picture beside the greeting at the bottom left of the home. It is profile.jpg in the wallpaper repository,
 * which RemoteWallpapers cuts to a square of SIZE pixels (whatever its own size) and keeps in the app's files; this reads
 * that copy. With none, the greeting stands alone. This also keeps the greeting's words: the user's own, set in Settings,
 * or "Hello, NAME" with the name from config.xml.
 */
final class Profile {
    /** The name it has in the repository and, cut down, in the app's files. */
    static final String FILE = "profile.jpg";
    /** Its side in pixels: twice what the screen shows, so it stays sharp. */
    static final int SIZE = 128;

    private static final String PREFERENCES = "profile";
    private static final String KEY_GREETING = "greeting";
    private static final int MAX_LENGTH = 32;

    private static Bitmap picture;
    private static boolean read;      // false until the file has been looked for, and again after a sync

    private Profile() {
    }

    /** The picture, or null if the repository has none. */
    static synchronized Bitmap picture(Context context) {
        if (!read) {
            File file = new File(new File(context.getFilesDir(), "remote"), FILE);
            picture = file.isFile() ? BitmapFactory.decodeFile(file.getPath()) : null;
            read = true;
        }
        return picture;
    }

    /** The words at the bottom left: what the user wrote, or by default "Hello, NAME". Empty means no words. */
    static String greeting(Context context) {
        String saved = prefs(context).getString(KEY_GREETING, null);
        return saved != null ? saved : standard(context);
    }

    /** The words the greeting has until the user writes their own; none if config.xml has no name. */
    static String standard(Context context) {
        String name = context.getString(R.string.profile_name).trim();
        return name.isEmpty() ? "" : context.getString(R.string.greeting, name);
    }

    /** Keeps the user's words; null goes back to the standard ones. */
    static void setGreeting(Context context, String text) {
        SharedPreferences.Editor edit = prefs(context).edit();
        if (text == null) {
            edit.remove(KEY_GREETING);
        } else {
            edit.putString(KEY_GREETING, text.trim().substring(0, Math.min(text.trim().length(), MAX_LENGTH)));
        }
        edit.apply();
    }

    static int maxLength() {
        return MAX_LENGTH;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    /** A sync may have changed it: read the file again next time. */
    static synchronized void stale() {
        read = false;
    }
}
