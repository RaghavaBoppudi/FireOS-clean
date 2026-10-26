package com.firehome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Sets the apps on the dock from install.sh: a comma-separated list of names (as the apps are named on screen) or
 * package names. Up to six keep the order they were given in; more than six, the first six by name. A dock the user has
 * already made is left alone unless the broadcast says to start again. The answer, which `am broadcast` prints, says
 * what was done and which names matched no app.
 */
public class DockReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        DockStore store = new DockStore(context);
        if (store.packages() != null && !intent.getBooleanExtra("reset", false)) {
            setResultData("kept: the dock has been customised; add --reset-dock to start again");
            return;
        }
        List<Tile> installed = new AppCatalog(context).alphabetical();
        List<Tile> chosen = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String name : TextUtils.split(String.valueOf(intent.getStringExtra("apps")), ",")) {
            Tile tile = find(installed, name.trim());
            if (tile == null) {
                if (!name.trim().isEmpty()) {
                    missing.add(name.trim());
                }
            } else if (!chosen.contains(tile)) {
                chosen.add(tile);
            }
        }
        if (chosen.isEmpty()) {
            setResultData("kept: none of those apps is installed");
            return;
        }
        if (chosen.size() > DockStore.MAX) {
            Collections.sort(chosen, new Comparator<Tile>() {
                @Override
                public int compare(Tile a, Tile b) {
                    int byName = a.label.compareToIgnoreCase(b.label);
                    return byName != 0 ? byName : a.packageName().compareTo(b.packageName());
                }
            });
            chosen = new ArrayList<>(chosen.subList(0, DockStore.MAX));
        }
        List<String> packages = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (Tile tile : chosen) {
            packages.add(tile.packageName());
            labels.add(tile.label);
        }
        store.save(packages);
        setResultData("dock: " + TextUtils.join(", ", labels)
                + (missing.isEmpty() ? "" : "; not installed: " + TextUtils.join(", ", missing)));
    }

    /** The app with this package name, else the one with this name; none if it matches no app, or two. */
    private static Tile find(List<Tile> apps, String name) {
        Tile found = null;
        for (Tile tile : apps) {
            if (name.equalsIgnoreCase(tile.packageName())) {
                return tile;
            }
        }
        for (Tile tile : apps) {
            if (name.equalsIgnoreCase(tile.label)) {
                if (found != null) {
                    return null;   // two apps have this name: better none than the wrong one
                }
                found = tile;
            }
        }
        return found;
    }
}
