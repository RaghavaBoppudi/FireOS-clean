package com.firehome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.os.Handler;
import android.view.animation.LinearInterpolator;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
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
 * The home screen: the date and time across the top, up to six apps in a row in the middle of the screen, a greeting
 * at the bottom left and three buttons (apps, edit, settings) at the bottom right. Only Home has the date and time; the
 * screens it extends have their name at the top left instead. The user chooses the apps in edit mode (DockEditor). Apps
 * switched off by tools/disable.sh are not listed. HomeWatcher brings this up whenever Amazon's home is started.
 *
 * All apps and Settings share its shelf of discs and extend it.
 */
public class HomeActivity extends Activity {
    static final String AMAZON_HOME = "com.amazon.tv.launcher";
    // Menu on the remote opens Amazon's home this long, the way out if the stick needs it
    private static final long AMAZON_PAUSE_MS = 10 * 60 * 1000L;
    private static final long HINT_FADE_MS = 180;

    // An app was opened from one of these screens: when Home is next shown, select the first tile, which is now it
    private static boolean appOpened;
    private static final String STATE_SELECTED = "selected";

    private boolean cameUp;                // this screen has faded in once; coming back to it does not fade it again

    private final Map<String, Bubble> views = new LinkedHashMap<>();   // by Tile.key, in shelf order
    private final List<String> shown = new ArrayList<>();
    private int look;                     // the theme this screen was made in, and the wallpaper it shows
    private Bitmap pictured;
    private ValueAnimator fading;         // the wallpaper giving way to the next, if it is
    private final Handler handler = new Handler();
    private final Runnable shuffle = new Runnable() {
        @Override
        public void run() {
            Wallpaper.loadLater(HomeActivity.this, shuffled);
        }
    };
    private final Runnable shuffled = new Runnable() {
        @Override
        public void run() {
            if (!isFinishing() && !isDestroyed()) {
                showWallpaper();
                scheduleShuffle();
            }
        }
    };
    private AppCatalog catalog;
    private DockStore store;
    private DockEditor editor;            // only on Home itself
    private Screensaver screensaver;      // and this
    private List<String> manual;          // packages in the order the user last put them, until the next app is opened
    private LinearLayout shelf;
    private TextView hint;
    private View profile;                 // the greeting; only on Home itself
    private String selected;              // Tile.key; null selects the first tile

    private final BroadcastReceiver packagesChanged = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            catalog.invalidate();
            refresh();
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        Wallpaper.load(this);
        Look.apply(this);
        look = Look.theme(this);
        super.onCreate(state);
        startService(new Intent(this, HomeWatcher.class));
        setContentView(layout());
        showWallpaper();
        showProfile();
        selected = state != null ? state.getString(STATE_SELECTED) : null;    // a look was changed: stay on the tile
        catalog = new AppCatalog(this);
        store = new DockStore(this);
        shelf = (LinearLayout) findViewById(R.id.shelf);
        hint = (TextView) findViewById(R.id.hint);
        profile = findViewById(R.id.profile);
        TextView title = (TextView) findViewById(R.id.title);
        if (title != null) {
            title.setText(titleRes());
        }
        LinearLayout buttons = (LinearLayout) findViewById(R.id.buttons);
        if (buttons != null) {
            addButton(buttons, R.string.all_apps, R.drawable.ic_applications);
            Shortcut edit = addButton(buttons, R.string.edit_dock, R.drawable.ic_edit);
            addButton(buttons, R.string.settings, R.drawable.ic_settings);
            editor = new DockEditor(this, store, buttons);
            screensaver = new Screensaver(this, editor);
            edit.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    editor.enter();
                }
            });
            open(buttons.getChildAt(0), AllAppsActivity.class);
            open(buttons.getChildAt(2), SettingsActivity.class);
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
        Wallpaper.load(this);
        if (look != Look.theme(this) || (pictured == null) != (Wallpaper.sharp() == null)) {
            recreate();            // the look changed, or the wallpaper went on or off, on another screen
            return;
        }
        showWallpaper();           // or it changed to the next one: the screen gives way to it
        comeUpFromBlack();
        findViewById(android.R.id.content).post(new Runnable() {
            @Override
            public void run() {
                tone();
            }
        });
        if (Look.wallpaper(this)) {
            RemoteWallpapers.syncIfDue(this);
        }
        showProfile();             // a sync may have brought a new picture
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
        scheduleShuffle();
    }

    /**
     * Home comes up out of the black of its window, so that nothing light flashes as it opens. It is only faded the
     * once: coming back to it (from All apps, say) finds it drawn, and fading it again would flash it black. The screens
     * that extend it are not faded at all, for their opening would flash black over Home.
     */
    private void comeUpFromBlack() {
        if (cameUp || getClass() != HomeActivity.class) {
            return;
        }
        cameUp = true;
        View root = ((ViewGroup) findViewById(android.R.id.content)).getChildAt(0);
        root.setAlpha(0f);
        root.animate().alpha(1f).setDuration(getResources().getInteger(R.integer.fade_in_ms)).withLayer();
    }

    /** Over a wallpaper, every line of text and every button along the bottom takes the ink that suits what is behind it. */
    private void tone() {
        if (Wallpaper.soft() == null) {
            return;
        }
        for (int id : new int[]{R.id.date, R.id.time, R.id.title, R.id.greeting}) {
            paint(id, false);
        }
        paint(R.id.credit, true);
        paint(R.id.hint, true);
        LinearLayout buttons = (LinearLayout) findViewById(R.id.buttons);
        if (buttons != null) {
            for (int i = 0; i < buttons.getChildCount(); i++) {
                ((Shortcut) buttons.getChildAt(i)).tone();
            }
        }
    }

    private void paint(int id, boolean dimmed) {
        TextView text = (TextView) findViewById(id);
        if (text != null) {
            Ink.paint(text, dimmed);
        }
    }

    private void scheduleShuffle() {
        handler.removeCallbacks(shuffle);
        if (Look.wallpaper(this) && Wallpaper.sharp() != null) {
            handler.postDelayed(shuffle, Wallpaper.untilNextMs(this) + 100);
        }
    }

    /** The shuffle setting changed: show the wallpaper that is now due, and wait for the one after. */
    final void shuffleNow() {
        handler.removeCallbacks(shuffle);
        Wallpaper.loadLater(this, shuffled);
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
        handler.removeCallbacks(shuffle);
        if (fading != null) {
            fading.end();          // the picture gets to its end, and the old one is let go
        }
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

    /** The name written at the top left of a screen that has one (the others have the date and time instead). */
    protected int titleRes() {
        return 0;
    }

    protected final AppCatalog catalog() {
        return catalog;
    }

    /** True for a screen that lays its tiles out in rows of grid_columns; false, one row. */
    protected boolean grid() {
        return false;
    }

    /** The tiles to show, in order. */
    protected List<Tile> tiles() {
        return dockTiles();
    }

    /** The view of a tile, once the shelf is laid out; null if it is not shown. */
    protected final Bubble viewOf(Tile tile) {
        return views.get(tile.key);
    }

    /** Shows the wallpaper that is on now: by fading to it from the one on show if that is the one just before. */
    private void showWallpaper() {
        final Bitmap next = Wallpaper.sharp();
        final ImageView backdrop = (ImageView) findViewById(R.id.backdrop);
        if (next != pictured) {
            if (fading != null) {
                fading.end();
            }
            if (pictured != null && next != null && pictured == Wallpaper.beforeSharp() && Wallpaper.mix() < 1f) {
                long ms = getResources().getInteger(R.integer.wallpaper_fade_ms);
                TransitionDrawable blend = new TransitionDrawable(new Drawable[]{
                        new BitmapDrawable(getResources(), pictured), new BitmapDrawable(getResources(), next)});
                backdrop.setImageDrawable(blend);
                blend.startTransition((int) ms);
                fading = ValueAnimator.ofFloat(0f, 1f);
                fading.setDuration(ms);
                fading.setInterpolator(new LinearInterpolator());
                fading.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator animation) {
                        Wallpaper.setMix((Float) animation.getAnimatedValue());
                        for (Bubble disc : views.values()) {
                            disc.repaintGlass();       // the discs are made of the picture behind them
                        }
                        tone();
                    }
                });
                fading.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        fading = null;
                        backdrop.setImageBitmap(next);
                        Wallpaper.settle();
                        for (Bubble disc : views.values()) {
                            disc.repaintGlass();
                        }
                        tone();
                    }
                });
                fading.start();
            } else {
                backdrop.setImageBitmap(next);
                Wallpaper.settle();
            }
            pictured = next;
            tone();
        }
        backdrop.setVisibility(pictured != null ? View.VISIBLE : View.GONE);
        TextView credit = (TextView) findViewById(R.id.credit);
        String photographer = Wallpaper.credit();
        credit.setText(photographer != null ? getString(R.string.credit, photographer) : "");
        credit.setVisibility(pictured != null && photographer != null ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString(STATE_SELECTED, selected);
    }

    /** The tile that has the selection, by Tile.key; null before anything has. */
    String selectedKey() {
        return selected;
    }

    /** Writes a line at the bottom left of the screen, in the place of the greeting, or clears it. */
    void say(String text) {
        if (text != null) {
            hint.setText(text);
        }
        hint.animate().alpha(text != null ? 1f : 0f).setDuration(HINT_FADE_MS).setInterpolator(Bubble.OUT);
        if (profile != null) {
            profile.animate().alpha(text != null ? 0f : 1f).setDuration(HINT_FADE_MS).setInterpolator(Bubble.OUT);
        }
    }

    /** The greeting and the picture beside it, as the user and the repository last had them. */
    private void showProfile() {
        if (profile == null) {
            return;
        }
        String words = Profile.greeting(this);
        TextView greeting = (TextView) findViewById(R.id.greeting);
        greeting.setText(words);
        greeting.setVisibility(words.isEmpty() ? View.GONE : View.VISIBLE);
        ImageView avatar = (ImageView) findViewById(R.id.avatar);
        Bitmap picture = Profile.picture(this);
        avatar.setOutlineProvider(Glass.CIRCLE);
        avatar.setClipToOutline(true);
        avatar.setImageBitmap(picture);
        avatar.setVisibility(picture != null ? View.VISIBLE : View.GONE);
        profile.setVisibility(words.isEmpty() && picture == null ? View.GONE : View.VISIBLE);
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
        screensaver.rearm();       // the idle time counts again from the end of editing
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

    /**
     * Lays the tiles out in lines of {@code columns} slots, the first line filling from the left, and keeps the block
     * in the middle of the screen: a single line is placed so that the middle of its discs is the middle of the
     * screen, and a grid of many lines keeps clear of the clock and the buttons on both sides and scrolls. Each line is
     * as wide as its columns, whatever the count, so the grid keeps its shape.
     */
    final void showGrid(List<Tile> tiles, int columns) {
        views.clear();
        shelf.removeAllViews();
        Resources res = getResources();
        int width = res.getDimensionPixelSize(R.dimen.slot_width);
        int height = res.getDimensionPixelSize(R.dimen.slot_height);
        int disc = res.getDimensionPixelSize(R.dimen.disc);
        int top = res.getDimensionPixelSize(R.dimen.slot_top);
        boolean single = tiles.size() <= columns;
        int inset = res.getDimensionPixelSize(R.dimen.grid_inset);
        // a slot is taller below its disc than above it: a single line's padding makes up the difference
        shelf.setPadding(0, single ? height - 2 * top - disc : inset, 0, single ? 0 : inset);
        LinearLayout line = null;
        for (int i = 0; i < tiles.size(); i++) {
            if (i % columns == 0) {
                line = new LinearLayout(this);
                line.setClipChildren(false);
                shelf.addView(line, new LinearLayout.LayoutParams(columns * width, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            line.addView(tileView(tiles.get(i)), new LinearLayout.LayoutParams(width, height));
        }
    }

    private View tileView(final Tile tile) {
        Bubble view = new Bubble(this, tile, getResources().getDimensionPixelSize(R.dimen.disc));
        views.put(tile.key, view);
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!tile.enabled) {
                    return;
                }
                if (tile.action != null) {
                    ((Bubble) v).setCaption(tile.action.run());
                } else if (tile.intent != null) {
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
            showGrid(tiles, Math.max(1, grid() ? Math.min(tiles.size(), getResources().getInteger(R.integer.grid_columns))
                    : tiles.size()));
        }
        View target = views.get(selected);
        if (target == null && !views.isEmpty()) {
            target = views.values().iterator().next();
        }
        if (target != null) {
            target.requestFocus();
        }
    }

    private Shortcut addButton(LinearLayout row, int label, int glyph) {
        Shortcut button = new Shortcut(this, getResources().getDrawable(glyph), getString(label));
        row.addView(button, new LinearLayout.LayoutParams(
                getResources().getDimensionPixelSize(R.dimen.button_slot), ViewGroup.LayoutParams.MATCH_PARENT));
        return button;
    }

    private void open(View button, final Class<? extends Activity> screen) {
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launch(new Intent(HomeActivity.this, screen),
                        getString(R.string.error_open, v.getContentDescription()), false);
            }
        });
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
