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
    final Drawable art;      // the app's artwork; only shown, greyed, if no mark could be cut from it
    final Drawable mark;     // white, shown on the tile; null if the artwork has none to cut out
    final Intent intent;     // what an app or settings page tile opens; null for a tile with an action
    final Action action;
    final boolean named;     // a setting: its name sits under the mark, and so may a caption with its value
    final String caption;    // the setting's current value, or null

    private Tile(String key, String label, Drawable art, Drawable mark, Intent intent, Action action, boolean named,
                 String caption) {
        this.key = key;
        this.label = label;
        this.art = art;
        this.mark = mark;
        this.intent = intent;
        this.action = action;
        this.named = named;
        this.caption = caption;
    }

    /** A tile that opens an app: just its mark. */
    static Tile opening(String label, Drawable art, Drawable mark, Intent intent) {
        return new Tile(intent.toUri(0), label, art, mark, intent, null, false, null);
    }

    /** The tile at the end of the dock in edit mode, which opens the apps that can be added. */
    static Tile adding(String label, Drawable glyph) {
        return new Tile("add", label, glyph, glyph, null, null, false, null);
    }

    /** A setting that opens one of the system's pages. */
    static Tile setting(String key, String label, Drawable glyph, Intent intent) {
        return new Tile(key, label, glyph, glyph, intent, null, true, null);
    }

    /** A setting this app handles itself; each press runs the action, whose result is the value shown under the name. */
    static Tile setting(String key, String label, Drawable glyph, String caption, Action action) {
        return new Tile(key, label, glyph, glyph, null, action, true, caption);
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
