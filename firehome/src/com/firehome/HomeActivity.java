package com.firehome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The home screen: date and time, buttons for all apps, settings and editing the dock, and a frosted dock of up to six
 * apps over a photograph that changes through the day. The user chooses the apps in edit mode (DockEditor). Apps switched off by
 * tools/disable.sh are not listed. HomeWatcher brings this up whenever Amazon's home is started.
 *
 * The screens that share this layout of a shelf of tiles under a wash, Settings and All apps, extend it.
 */
public class HomeActivity extends Activity {
    static final String AMAZON_HOME = "com.amazon.tv.launcher";
    // Menu on the remote opens Amazon's home this long, the way out if the stick needs it
    private static final long AMAZON_PAUSE_MS = 10 * 60 * 1000L;


    // An app was opened from one of these screens: when Home is next shown, select the first tile, which is now it
    private static boolean appOpened;

    private final Map<String, View> views = new LinkedHashMap<>();   // by Tile.key, in shelf order
    private final List<String> shown = new ArrayList<>();
    private AppCatalog catalog;
    private DockStore store;
    private DockEditor editor;            // only on Home itself
    private Screensaver screensaver;      // and this
    private List<String> manual;          // packages in the order the user last put them, until the next app is opened
    private WallpaperView wallpaper;
    private TextView credit;
    private TextView clock;
    private TextView title;               // the name of the screen, on the right of the top bar, where there is one
    private GlyphButton[] buttons = new GlyphButton[0];
    private LinearLayout row;
    private TextView caption;     // only where there is a line to write above the shelf
    private String selected;      // Tile.key; null selects the first tile

    private final BroadcastReceiver packagesChanged = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            catalog.invalidate();
            refresh();
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        startService(new Intent(this, HomeWatcher.class));
        setContentView(layout());
        catalog = new AppCatalog(this);
        wallpaper = (WallpaperView) findViewById(R.id.wallpaper);
        credit = (TextView) findViewById(R.id.credit);
        FrostedPanel panel = (FrostedPanel) findViewById(R.id.panel);
        if (panel != null) {
            panel.setWallpaper(wallpaper);
        }
        wallpaper.setListener(new WallpaperView.Listener() {
            @Override
            public void onPhoto(Wallpapers.Photo photo) {
                credit.setText(photo.credit != null ? getString(R.string.credit, photo.credit) : "");
                credit.setVisibility(photo.credit != null ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onTone(float dateLight, float buttonLight) {
                tone(dateLight, buttonLight);
            }
        });
        row =(LinearLayout) findViewById(R.id.row);
        caption = (TextView) findViewById(R.id.caption);
        open(R.id.button_all_apps, AllAppsActivity.class);
        open(R.id.button_settings, SettingsActivity.class);
        clock = (TextView) findViewById(R.id.clock);
        title = (TextView) findViewById(R.id.title);
        List<GlyphButton> glyphs = new ArrayList<>();
        for (int id : new int[]{R.id.button_all_apps, R.id.button_edit, R.id.button_settings}) {
            View glyph = findViewById(id);
            if (glyph != null) {
                glyphs.add((GlyphButton) glyph);
            }
        }
        buttons = glyphs.toArray(new GlyphButton[0]);
        store = new DockStore(this);
        View edit = findViewById(R.id.button_edit);
        if (edit != null) {
            editor = new DockEditor(this, store, new View[]{
                    findViewById(R.id.button_all_apps), edit, findViewById(R.id.button_settings)});
            edit.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    editor.enter();
                }
            });
            screensaver = new Screensaver(this, editor);
        }

        IntentFilter packages = new IntentFilter();
        packages.addAction(Intent.ACTION_PACKAGE_ADDED);
        packages.addAction(Intent.ACTION_PACKAGE_REMOVED);
        packages.addAction(Intent.ACTION_PACKAGE_CHANGED);
        packages.addDataScheme("package");
        registerReceiver(packagesChanged, packages);
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(packagesChanged);
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        HomeWatcher.pause(0);      // back from an app or Amazon's home: Home brings this screen up again
        if (screensaver != null) {
            screensaver.rearm();
            editor.hintOnce();
        }
        if (appOpened && getClass() == HomeActivity.class) {
            selected = null;       // the app just used is now first: one press of OK goes back to it
            appOpened = false;
            manual = null;         // and the dock goes back to its own order
        }
        refresh();
    }

    /**
     * The cover over Amazon's home comes down only once this window really has the screen. Being told to start (which
     * is when onNewIntent runs) says nothing about that: Android can take seconds to swap Amazon's home out while it
     * finishes drawing, and the cover has to stay up over all of it.
     */
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            HomeWatcher.homeShown();
        }
    }

    @Override
    protected void onPause() {
        if (editor != null) {
            editor.leave();        // what was done is saved as it is done
        }
        if (screensaver != null) {
            screensaver.stop();
        }
        super.onPause();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (screensaver != null && screensaver.consume(event)) {
            return true;
        }
        if (editor != null && editor.consume(event)) {
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_RUNNING_LOW) {
            // the system is short of memory (this app runs a foreground service, so it is never sent the "cached
            // process" levels): the pictures are the bulk of what it holds, and are cheap to remake
            wallpaper.release();
            Wallpapers.get(this).trim();
            // the pictures' memory is returned when they are collected, which would otherwise wait for a Java heap that
            // is nowhere near full
            System.gc();
        }
    }

    @Override
    public void onBackPressed() {
        // this is Home: Back has nowhere to go
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            openAmazonHome();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    protected int layout() {
        return R.layout.activity_home;
    }

    protected final AppCatalog catalog() {
        return catalog;
    }

    /**
     * The text and buttons across the top follow the photograph under them: white over a dark one, near black over a
     * light one, and in between while one fades into the other.
     */
    private void tone(float dateLight, float buttonLight) {
        clock.setTextColor(blend(getResources().getColor(R.color.ink), getResources().getColor(R.color.ink_on_light), dateLight));
        clock.setShadowLayer(8f, 0f, 1f, Color.argb(Math.round(0x99 * (1f - dateLight)), 0, 0, 0));   // a shadow lifts white text, not black
        if (title != null) {
            title.setTextColor(blend(getResources().getColor(R.color.ink_dim),
                    getResources().getColor(R.color.ink_dim_on_light), buttonLight));
            title.setShadowLayer(8f, 0f, 1f, Color.argb(Math.round(0x99 * (1f - buttonLight)), 0, 0, 0));
        }
        for (GlyphButton button : buttons) {
            button.setTone(buttonLight);
        }
    }

    private static int blend(int from, int to, float amount) {
        return Color.argb(Math.round(Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * amount),
                Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * amount),
                Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * amount),
                Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * amount));
    }

    /** The view of a tile, once the shelf is laid out; null if it is not shown. */
    protected final TileView viewOf(Tile tile) {
        View view = views.get(tile.key);
        return view instanceof TileView ? (TileView) view : null;
    }

    /** Writes a line above the dock, for the screens that have a caption. */
    protected final void say(String text) {
        if (caption != null) {
            caption.setText(text);
        }
    }

    protected final WallpaperView wallpaper() {
        return wallpaper;
    }

    /** The tiles to show, in order. */
    protected List<Tile> tiles() {
        return dockTiles();
    }

    /**
     * The apps on the dock, in the dock's order. They are the ones the user chose, shown by recency or by name as the
     * setting says (an order the user has just made by hand stands until the next app is opened); until the user has
     * chosen, the six used last, and the same if none of those chosen is installed.
     */
    List<Tile> dockTiles() {
        List<String> chosen = store.packages();
        List<Tile> dock = new ArrayList<>();
        if (chosen != null) {
            for (Tile tile : store.alphabetical() ? catalog.alphabetical() : catalog.byRecency(true)) {
                if (chosen.contains(tile.packageName())) {
                    dock.add(tile);
                }
            }
        }
        if (dock.isEmpty()) {
            List<Tile> recent = catalog.byRecency();
            dock.addAll(recent.size() > DockStore.MAX ? recent.subList(0, DockStore.MAX) : recent);
            if (store.alphabetical()) {
                Collections.sort(dock, BY_NAME);
            }
        }
        if (dock.size() > DockStore.MAX) {
            dock = new ArrayList<>(dock.subList(0, DockStore.MAX));
        }
        if (manual != null && !store.alphabetical()) {
            List<Tile> placed = new ArrayList<>();
            for (String name : manual) {
                for (Tile tile : dock) {
                    if (name.equals(tile.packageName())) {
                        placed.add(tile);
                    }
                }
            }
            if (placed.size() == dock.size()) {
                dock = placed;
            }
        }
        return dock;
    }

    /** Edit mode is over: show the dock again, with the order the user left it in, and select this tile. */
    void finishEditing(List<String> order, String focusKey) {
        if (screensaver != null) {
            screensaver.rearm();   // the idle time counts again from the end of editing
        }
        manual = order;
        selected = focusKey;
        shown.clear();
        refresh();
    }

    private static final Comparator<Tile> BY_NAME = new Comparator<Tile>() {
        @Override
        public int compare(Tile a, Tile b) {
            return a.label.compareToIgnoreCase(b.label);
        }
    };

    /** Lay the tiles out in the shelf; the default is one row. Each tile view comes from tileView(). */
    protected void show(LinearLayout shelf, List<Tile> tiles) {
        int width = getResources().getDimensionPixelSize(R.dimen.tile_width);
        int height = getResources().getDimensionPixelSize(R.dimen.tile_height);
        int gap = getResources().getDimensionPixelSize(R.dimen.tile_gap);
        for (int i = 0; i < tiles.size(); i++) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
            params.setMarginStart(i == 0 ? 0 : gap);
            shelf.addView(tileView(tiles.get(i)), params);
        }
    }

    /**
     * Lay the tiles out in rows of grid_columns, the first row filling from the left. The grid is always as wide as
     * its columns, whatever the count, so the panel around it does not change width with the number of tiles.
     */
    protected final void showGrid(LinearLayout shelf, List<Tile> tiles, int height) {
        int columns = getResources().getInteger(R.integer.grid_columns);
        int width = getResources().getDimensionPixelSize(R.dimen.tile_width);
        int gap = getResources().getDimensionPixelSize(R.dimen.tile_gap);
        int lineWidth = columns * width + (columns - 1) * gap;
        LinearLayout line = null;
        for (int i = 0; i < tiles.size(); i++) {
            if (i % columns == 0) {
                line = new LinearLayout(this);
                line.setClipChildren(false);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        lineWidth, LinearLayout.LayoutParams.WRAP_CONTENT);
                params.topMargin = i == 0 ? 0 : gap;
                shelf.addView(line, params);
            }
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
            params.setMarginStart(i % columns == 0 ? 0 : gap);
            line.addView(tileView(tiles.get(i)), params);
        }
    }

    protected final View tileView(final Tile tile) {
        TileView view = new TileView(this, tile);
        views.put(tile.key, view);
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (tile.intent == null && tile.action == null) {
                    return;
                }
                if (tile.action != null) {
                    ((TileView) v).setCaption(tile.action.run());
                } else {
                    launch(tile.intent, getString(R.string.error_open, tile.label),
                            !getPackageName().equals(tile.packageName()));
                }
            }
        });
        view.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean focused) {
                if (focused) {
                    selected = tile.key;
                }
            }
        });
        return view;
    }

    /** Brings the shelf up to date, laying it out again only when the tiles or their order changed. */
    private void refresh() {
        if (editor != null && editor.active()) {
            return;                // the editor has the dock
        }
        List<Tile> tiles = tiles();
        List<String> keys = new ArrayList<>();
        for (Tile tile : tiles) {
            keys.add(tile.key);
        }
        if (!keys.equals(shown)) {
            shown.clear();
            shown.addAll(keys);
            views.clear();
            row.removeAllViews();
            show(row, tiles);
        }
        View target = views.get(selected);
        if (target == null && !views.isEmpty()) {
            target = views.values().iterator().next();
        }
        if (target != null) {
            target.requestFocus();
        }
    }

    private void open(int buttonId, final Class<? extends Activity> screen) {
        View button = findViewById(buttonId);
        if (button != null) {
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    launch(new Intent(HomeActivity.this, screen),
                            getString(R.string.error_open, v.getContentDescription()), false);
                }
            });
        }
    }

    private void openAmazonHome() {
        HomeWatcher.pause(AMAZON_PAUSE_MS);
        Toast.makeText(this, R.string.amazon_home_notice, Toast.LENGTH_LONG).show();
        launch(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(AMAZON_HOME),
                getString(R.string.error_open, "Amazon's home"), false);
    }

    private void launch(Intent intent, String failure, boolean isApp) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            appOpened |= isApp;
        } catch (ActivityNotFoundException | SecurityException e) {
            HomeWatcher.pause(0);
            Toast.makeText(this, failure, Toast.LENGTH_LONG).show();
        }
    }
}
