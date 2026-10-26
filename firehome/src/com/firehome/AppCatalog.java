package com.firehome;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The apps on the stick as tiles. Cutting a mark out of artwork and finding its colours is costly, so each app's
 * tile is made once and kept until a package changes.
 */
final class AppCatalog {
    private static final String APPSTORE = "com.amazon.venezia";
    // Amazon services that declare a TV launcher entry but are not apps anyone opens
    private static final Set<String> HIDDEN = new HashSet<>(Arrays.asList(
            "com.firehome", HomeActivity.AMAZON_HOME, "com.amazon.firetv.troubleshooting", "com.amazon.spiderpork"));

    private final Context context;
    private final PackageManager pm;
    private final RecentApps recents;
    private final Map<String, Tile> tiles = new HashMap<>();

    AppCatalog(Context context) {
        this.context = context;
        pm = context.getPackageManager();
        recents = new RecentApps(context);
    }

    void invalidate() {
        tiles.clear();
    }

    /** The apps, the one used last first; the Appstore is left to the full list. */
    List<Tile> byRecency() {
        return byRecency(false);
    }

    /** The apps, the one used last first. */
    List<Tile> byRecency(boolean withAppstore) {
        List<Tile> apps = launchable(withAppstore);
        final Map<String, Long> lastUsed = recents.lastUsed();
        Collections.sort(apps, new Comparator<Tile>() {
            @Override
            public int compare(Tile a, Tile b) {
                Long usedA = lastUsed.get(a.packageName());
                Long usedB = lastUsed.get(b.packageName());
                int byUse = Long.compare(usedB != null ? usedB : 0L, usedA != null ? usedA : 0L);
                return byUse != 0 ? byUse : a.label.compareToIgnoreCase(b.label);
            }
        });
        return apps;
    }

    /** Every app, by name, the Appstore included. */
    List<Tile> alphabetical() {
        List<Tile> apps = launchable(true);
        Collections.sort(apps, new Comparator<Tile>() {
            @Override
            public int compare(Tile a, Tile b) {
                return a.label.compareToIgnoreCase(b.label);
            }
        });
        return apps;
    }

    private List<Tile> launchable(boolean withAppstore) {
        List<Tile> apps = new ArrayList<>();
        Set<String> packages = new HashSet<>();
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        for (ResolveInfo info : pm.queryIntentActivities(query, 0)) {
            ActivityInfo activity = info.activityInfo;
            if (HIDDEN.contains(activity.packageName) || !packages.add(activity.packageName)) {
                continue;
            }
            Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                    .setComponent(new ComponentName(activity.packageName, activity.name));
            Tile tile = tiles.get(intent.toUri(0));
            if (tile == null) {
                Drawable banner = activity.loadBanner(pm);
                tile = tile(String.valueOf(info.loadLabel(pm)), banner != null ? banner : info.loadIcon(pm), intent);
            }
            apps.add(tile);
        }
        if (withAppstore) {
            // the Appstore only has a phone-style launcher entry
            Intent appstore = pm.getLaunchIntentForPackage(APPSTORE);
            if (appstore != null) {
                Tile tile = tiles.get(appstore.toUri(0));
                try {
                    apps.add(tile != null ? tile
                            : tile(context.getString(R.string.appstore), pm.getApplicationIcon(APPSTORE), appstore));
                } catch (PackageManager.NameNotFoundException e) {
                    // not installed after all
                }
            }
        }
        return apps;
    }

    private Tile tile(String label, Drawable art, Intent intent) {
        Drawable mark = Logo.mono(context.getResources(), art);
        // the banner is only kept when no mark could be cut from it: it is far larger than the mark
        Tile tile = Tile.opening(label, mark != null ? mark : art, mark, intent);
        tiles.put(tile.key, tile);
        return tile;
    }
}
