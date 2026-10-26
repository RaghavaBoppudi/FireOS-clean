package com.firehome;

import android.content.Intent;
import android.graphics.drawable.Drawable;

/** One entry on a shelf: what it shows, and what selecting it does. */
final class Tile {
    /** Runs when the tile is selected; returns the caption the tile should now carry, or null to leave it. */
    interface Action {
        String run();
    }

    final String key;
    final String label;
    final Drawable icon;     // white shapes on transparency, tinted when shown; null for an app with no mark
    final Intent intent;     // what an app or a settings page opens; null for a tile with an action, or none
    final Action action;
    final String caption;    // a setting's current value, or null
    final boolean enabled;   // false: shown dimmed, and selecting it does nothing

    private Tile(String key, String label, Drawable icon, Intent intent, Action action, String caption,
                 boolean enabled) {
        this.key = key;
        this.label = label;
        this.icon = icon;
        this.intent = intent;
        this.action = action;
        this.caption = caption;
        this.enabled = enabled;
    }

    /** An app: its mark is cut from its icon, and is null if nothing there stands out. */
    static Tile app(String label, Drawable mark, Intent intent) {
        return new Tile(intent.toUri(0), label, mark, intent, null, null, true);
    }

    /** A setting that opens one of the system's pages. */
    static Tile page(String key, String label, Drawable glyph, Intent intent) {
        return new Tile(key, label, glyph, intent, null, null, true);
    }

    /** A setting this app handles itself; each press runs the action, whose result is the value shown under the name. */
    static Tile setting(String key, String label, Drawable glyph, String caption, Action action) {
        return new Tile(key, label, glyph, null, action, caption, true);
    }

    /** A disc that does nothing by itself; whoever shows it decides what selecting it does. */
    static Tile plain(String key, String label, Drawable glyph) {
        return new Tile(key, label, glyph, null, null, null, true);
    }

    /** The same tile, dimmed and doing nothing, with this value under its name. */
    Tile disabled(String reason) {
        return new Tile(key, label, icon, null, null, reason, false);
    }

    /** The app this opens, or null for a tile with an action. */
    String packageName() {
        if (intent == null) {
            return null;
        }
        return intent.getComponent() != null ? intent.getComponent().getPackageName() : intent.getPackage();
    }

    /** A drawable is shown by one view at a time; a tile can be shown again, so views get their own copies. */
    static Drawable copy(Drawable drawable) {
        Drawable.ConstantState state = drawable.getConstantState();
        return state != null ? state.newDrawable().mutate() : drawable;
    }
}
