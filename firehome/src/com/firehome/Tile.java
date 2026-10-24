package com.firehome;

import android.content.Intent;
import android.graphics.drawable.Drawable;

/** One entry on a shelf: what it shows, and what selecting it does. */
final class Tile {
    /** Runs when the tile is selected; returns the tile's badge as it should now read. */
    interface Action {
        String run();
    }

    final String key;
    final String label;
    final Drawable art;      // the app's artwork; only shown, greyed, if no mark could be cut from it
    final Drawable mark;     // white, shown on the tile; null if the artwork has none to cut out
    final Intent intent;     // what an app tile opens; null for a tile with an action
    final Action action;
    final String badge;      // short text shown next to the mark, or null

    private Tile(String key, String label, Drawable art, Drawable mark, Intent intent, Action action, String badge) {
        this.key = key;
        this.label = label;
        this.art = art;
        this.mark = mark;
        this.intent = intent;
        this.action = action;
        this.badge = badge;
    }

    /** A tile that opens an app or a screen. */
    static Tile opening(String label, Drawable art, Drawable mark, Intent intent) {
        return new Tile(intent.toUri(0), label, art, mark, intent, null, null);
    }

    /** A tile that does something in this app, with a glyph for its mark and a badge beside it. */
    static Tile running(String key, String label, Drawable glyph, String badge, Action action) {
        return new Tile(key, label, glyph, glyph, null, action, badge);
    }

    /** The app this opens, or null for a screen of this app or a tile with an action. */
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
