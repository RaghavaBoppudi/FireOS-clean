package com.firehome;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.graphics.drawable.Drawable;
import android.text.InputFilter;
import android.text.InputType;
import android.view.WindowManager;
import android.widget.EditText;

import java.util.ArrayList;
import java.util.List;

/**
 * Settings as rows of discs (six to a row), each with its name under it. Display & Sounds is left out: Amazon locks it to its own
 * launcher with a signature permission, so no other app can open it. The system's own entry point
 * (Settings.ACTION_SETTINGS) goes through Amazon's launcher, so this opens the pages Amazon's Settings grid links to
 * straight from the Settings app instead. The last seven are this app's own: the wallpaper, fetching new ones, how often it changes, the
 * theme (which a wallpaper takes over), the order of the dock, the screensaver and the greeting, each showing its choice
 * under its name. Back returns to the home screen.
 */
public class SettingsActivity extends HomeActivity {
    private static final int[] LABELS = {
            R.string.settings_network, R.string.settings_controllers,
            R.string.settings_applications, R.string.settings_device,
    };
    private static final int[] ICONS = {
            R.drawable.ic_network_4, R.drawable.ic_controllers,
            R.drawable.ic_apps_window, R.drawable.ic_device,
    };
    private static final String[] ACTIONS = {
            "android.settings.WIFI_SETTINGS", "android.settings.CONTROLLERS_SETTINGS",
            "android.settings.MANAGE_APPLICATIONS_SETTINGS", "android.settings.DEVICE_INFO_SETTINGS",
    };

    private static final int NETWORK = 0;   // the first page: its glyph shows the signal

    private Tile network;
    private Tile greeting;
    private int shownLevel = -1;

    // the signal changes while this screen is up: redraw the glyph when its level does
    private final BroadcastReceiver signal = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            showSignal();
        }
    };

    private void showSignal() {
        int level = WifiSignal.level(this);
        Bubble view = network != null ? viewOf(network) : null;
        if (view != null && level != shownLevel) {
            shownLevel = level;
            view.setGlyph(WifiSignal.glyph(this, level));
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter changes = new IntentFilter(WifiManager.RSSI_CHANGED_ACTION);
        changes.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        changes.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        registerReceiver(signal, changes);
    }

    @Override
    protected void onStop() {
        unregisterReceiver(signal);
        super.onStop();
    }

    @Override
    protected int titleRes() {
        return R.string.settings;
    }

    @Override
    protected int layout() {
        return R.layout.activity_plain;
    }

    @Override
    protected boolean grid() {
        return true;      // more than a row's worth: the rest go on a second row, not into one long one
    }

    @Override
    protected List<Tile> tiles() {
        List<Tile> tiles = new ArrayList<>();
        for (int i = 0; i < LABELS.length; i++) {
            Drawable glyph = i == NETWORK ? WifiSignal.glyph(this, WifiSignal.level(this))
                    : getResources().getDrawable(ICONS[i]);
            Tile tile = Tile.page(ACTIONS[i], getString(LABELS[i]), glyph, new Intent(ACTIONS[i]));
            if (i == NETWORK) {
                network = tile;
            }
            tiles.add(tile);
        }
        tiles.add(Tile.setting("wallpaper", getString(R.string.settings_wallpaper),
                getResources().getDrawable(R.drawable.ic_wallpaper), wallpaperCaption(), new Tile.Action() {
                    @Override
                    public String run() {
                        Look.toggleWallpaper(SettingsActivity.this);
                        recreate();
                        return null;
                    }
                }));
        // always there, so the row does not shift when the wallpaper goes on or off; dimmed while there is nothing to fetch
        Tile refresh = Tile.setting("refresh", getString(R.string.settings_refresh),
                getResources().getDrawable(R.drawable.ic_refresh), null, new Tile.Action() {
                    @Override
                    public String run() {
                        say(getString(R.string.wallpapers_checking));
                        RemoteWallpapers.syncNow(SettingsActivity.this, new RemoteWallpapers.Callback() {
                            @Override
                            public void onDone(RemoteWallpapers.Outcome outcome, int count) {
                                if (!isFinishing()) {
                                    say(refreshResult(outcome, count));
                                }
                            }
                        });
                        return null;
                    }
                });
        tiles.add(Look.wallpaper(this) ? refresh : refresh.disabled(getString(R.string.wallpaper_off)));
        Tile shuffle = Tile.setting("shuffle", getString(R.string.settings_shuffle),
                getResources().getDrawable(R.drawable.ic_shuffle), Wallpaper.shuffleCaption(this), new Tile.Action() {
                    @Override
                    public String run() {
                        Wallpaper.nextShuffle(SettingsActivity.this);
                        shuffleNow();
                        return Wallpaper.shuffleCaption(SettingsActivity.this);
                    }
                });
        tiles.add(Look.wallpaper(this) ? shuffle : shuffle.disabled(getString(R.string.wallpaper_off)));
        Tile theme = Tile.setting("theme", getString(R.string.settings_theme),
                getResources().getDrawable(R.drawable.ic_theme), themeCaption(), new Tile.Action() {
                    @Override
                    public String run() {
                        Look.toggle(SettingsActivity.this);
                        recreate();
                        return null;
                    }
                });
        // a wallpaper brings its own look: the theme has nothing to choose until it is off
        tiles.add(Look.wallpaper(this) ? theme.disabled(getString(R.string.theme_fixed)) : theme);
        tiles.add(Tile.setting("order", getString(R.string.settings_dock_order),
                getResources().getDrawable(R.drawable.ic_dock_order), orderCaption(), new Tile.Action() {
                    @Override
                    public String run() {
                        DockStore store = new DockStore(SettingsActivity.this);
                        store.setAlphabetical(!store.alphabetical());
                        return orderCaption();
                    }
                }));
        tiles.add(Tile.setting("screensaver", getString(R.string.settings_screensaver),
                getResources().getDrawable(R.drawable.ic_screensaver), Screensaver.caption(this), new Tile.Action() {
                    @Override
                    public String run() {
                        Screensaver.next(SettingsActivity.this);
                        return Screensaver.caption(SettingsActivity.this);
                    }
                }));
        greeting = Tile.setting("greeting", getString(R.string.settings_greeting),
                getResources().getDrawable(R.drawable.ic_greeting), greetingCaption(), new Tile.Action() {
                    @Override
                    public String run() {
                        askGreeting();
                        return null;
                    }
                });
        tiles.add(greeting);
        return tiles;
    }

    /** Asks for the words at the bottom left, with the remote's keyboard; the tile shows them when they are saved. */
    private void askGreeting() {
        final EditText field = new EditText(this);
        field.setInputType(InputType.TYPE_CLASS_TEXT);
        field.setSingleLine(true);
        field.setFilters(new InputFilter[]{new InputFilter.LengthFilter(Profile.maxLength())});
        field.setHint(R.string.greeting_hint);
        field.setText(Profile.greeting(this));
        field.setSelection(field.getText().length());
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.settings_greeting)
                .setView(field)
                .setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Profile.setGreeting(SettingsActivity.this, field.getText().toString());
                        showGreeting();
                    }
                })
                .setNeutralButton(R.string.greeting_reset, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Profile.setGreeting(SettingsActivity.this, null);
                        showGreeting();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        dialog.show();
    }

    private void showGreeting() {
        Bubble view = viewOf(greeting);
        if (view != null) {
            view.setCaption(greetingCaption());
        }
    }

    private String greetingCaption() {
        String words = Profile.greeting(this);
        return words.isEmpty() ? getString(R.string.greeting_none) : words;
    }

    private String refreshResult(RemoteWallpapers.Outcome outcome, int count) {
        switch (outcome) {
            case SYNCED:
                return getResources().getQuantityString(R.plurals.wallpapers_synced, count, count);
            case EMPTY:
                return getString(R.string.wallpapers_empty);
            case CURRENT:
                return getString(R.string.wallpapers_current);
            case DENIED:
                return getString(R.string.wallpapers_denied);
            case NOT_SET:
                return getString(R.string.wallpapers_not_set);
            default:
                return getString(R.string.wallpapers_offline);
        }
    }

    private String wallpaperCaption() {
        return getString(Look.wallpaper(this) ? R.string.wallpaper_on : R.string.wallpaper_off);
    }

    private String themeCaption() {
        return getString(Look.dark(this) ? R.string.theme_dark : R.string.theme_light);
    }

    private String orderCaption() {
        return getString(new DockStore(this).alphabetical() ? R.string.order_alphabetical : R.string.order_recent);
    }

    @Override
    public void onBackPressed() {
        finish();
    }
}
