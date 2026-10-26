package com.firehome;

import android.widget.LinearLayout;

import java.util.List;

/** Every app on the stick, by name, in a grid of six columns inside a frosted panel. Back returns to the home screen. */
public class AllAppsActivity extends HomeActivity {
    @Override
    protected int layout() {
        return R.layout.activity_all_apps;
    }

    @Override
    protected List<Tile> tiles() {
        return catalog().alphabetical();
    }

    @Override
    protected void show(LinearLayout shelf, List<Tile> tiles) {
        showGrid(shelf, tiles, getResources().getDimensionPixelSize(R.dimen.tile_height));
    }

    @Override
    public void onBackPressed() {
        finish();
    }
}
