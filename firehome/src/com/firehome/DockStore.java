package com.firehome;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Which apps are on the home dock, and how the dock is ordered. The apps are chosen by the user in edit mode (or at
 * install) and kept as package names; until then there is no list, and the dock shows the apps used last. A package
 * that is not installed at the moment (an app mid-update, or switched off) is skipped when the dock is shown but stays
 * in the list, so it comes back with the app; the list only changes when the user edits it.
 */
final class DockStore {
    /** Most apps the dock holds, and fewest. */
    static final int MAX = 6;
    static final int MIN = 1;

    private static final String KEY_APPS = "apps";
    private static final String KEY_ALPHABETICAL = "alphabetical";
    private static final String KEY_HINT_SEEN = "hint_seen";

    private final SharedPreferences preferences;

    DockStore(Context context) {
        preferences = context.getSharedPreferences("dock", Context.MODE_PRIVATE);
    }

    /** The chosen packages, in the order they were chosen; null until the user has chosen. */
    List<String> packages() {
        String saved = preferences.getString(KEY_APPS, null);
        if (saved == null) {
            return null;
        }
        return saved.isEmpty() ? new ArrayList<String>() : new ArrayList<>(Arrays.asList(saved.split(",")));
    }

    void save(List<String> packages) {
        preferences.edit().putString(KEY_APPS, TextUtils.join(",", packages)).apply();
    }

    /** True once the user has been told how to edit the dock. */
    boolean hintSeen() {
        return preferences.getBoolean(KEY_HINT_SEEN, false);
    }

    void setHintSeen() {
        preferences.edit().putBoolean(KEY_HINT_SEEN, true).apply();
    }

    /** True if the dock is ordered by name; false, the default, if the app used last comes first. */
    boolean alphabetical() {
        return preferences.getBoolean(KEY_ALPHABETICAL, false);
    }

    void setAlphabetical(boolean alphabetical) {
        preferences.edit().putBoolean(KEY_ALPHABETICAL, alphabetical).apply();
    }
}
