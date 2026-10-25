package com.firehome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The home screen: date and time, buttons for all apps and settings, and a frosted dock with one slot per TV app,
 * the one used last first, over a photograph that changes through the day. Apps switched off by
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
    private WallpaperView wallpaper;
    private TextView credit;
    private TileRail rail;        // only where the shelf scrolls sideways
    private LinearLayout row;
    private TextView caption;     // only where tiles have no name of their own to see
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
        });
        rail = (TileRail) findViewById(R.id.rail);
        row = (LinearLayout) findViewById(R.id.row);
        caption = (TextView) findViewById(R.id.caption);
        open(R.id.button_all_apps, AllAppsActivity.class);
        open(R.id.button_settings, SettingsActivity.class);

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
        if (appOpened && getClass() == HomeActivity.class) {
            selected = null;       // the app just used is now first: one press of OK goes back to it
            appOpened = false;
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
        return catalog.byRecency();
    }

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

    protected final View tileView(final Tile tile) {
        TileView view = new TileView(this, tile);
        views.put(tile.key, view);
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (tile.action != null) {
                    ((TileView) v).setBadge(tile.action.run());
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
                    select(tile, v);
                }
            }
        });
        return view;
    }

    /** Brings the shelf up to date, laying it out again only when the tiles or their order changed. */
    private void refresh() {
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

    private void select(Tile tile, View view) {
        selected = tile.key;
        if (rail != null) {
            rail.reveal(view);
        }
        if (caption != null) {
            caption.setText(tile.label);
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
