package com.firehome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.List;

/**
 * Settings as a dock of solid icons, the selected one named above it. Display & Sounds is left out: Amazon locks it to
 * its own launcher with a signature permission, so no other app can open it. The system's own entry point
 * (Settings.ACTION_SETTINGS) goes through Amazon's launcher, so this opens the pages Amazon's Settings grid links
 * to straight from the Settings app instead. The last tile is this app's own: it steps through how often the
 * background changes, and one that fetches new wallpapers now. Back returns to the home screen.
 */
public class SettingsActivity extends HomeActivity {
    private static final int[] LABELS = {
            R.string.settings_network, R.string.settings_controllers,
            R.string.settings_applications, R.string.settings_device,
    };
    private static final int[] ICONS = {
            R.drawable.ic_network_4, R.drawable.ic_controllers,
            R.drawable.ic_applications, R.drawable.ic_device,
    };
    private static final String[] ACTIONS = {
            "android.settings.WIFI_SETTINGS", "android.settings.CONTROLLERS_SETTINGS",
            "android.settings.MANAGE_APPLICATIONS_SETTINGS", "android.settings.DEVICE_INFO_SETTINGS",
    };

    private static final int NETWORK = 0;   // the first page: its glyph shows the signal

    private Tile network;
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
        TileView view = network != null ? viewOf(network) : null;
        if (view != null && level != shownLevel) {
            shownLevel = level;
            view.setMark(WifiSignal.glyph(this, level));
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
    protected int layout() {
        return R.layout.activity_settings;
    }

    @Override
    protected List<Tile> tiles() {
        List<Tile> tiles = new ArrayList<>();
        for (int i = 0; i < LABELS.length; i++) {
            Drawable glyph = i == NETWORK ? WifiSignal.glyph(this, WifiSignal.level(this))
                    : getResources().getDrawable(ICONS[i]);
            Tile tile = Tile.opening(getString(LABELS[i]), glyph, glyph, new Intent(ACTIONS[i]));
            if (i == NETWORK) {
                network = tile;
            }
            tiles.add(tile);
        }
        tiles.add(Tile.running("background", getString(R.string.settings_background),
                getResources().getDrawable(R.drawable.ic_background), Wallpapers.get(this).intervalBadge(),
                new Tile.Action() {
                    @Override
                    public String run() {
                        Wallpapers.get(SettingsActivity.this).nextInterval();
                        wallpaper().refresh();
                        return Wallpapers.get(SettingsActivity.this).intervalBadge();
                    }
                }));
        tiles.add(Tile.running("refresh", getString(R.string.settings_refresh),
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
                }));
        return tiles;
    }

    @Override
    public void onBackPressed() {
        finish();
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
}
