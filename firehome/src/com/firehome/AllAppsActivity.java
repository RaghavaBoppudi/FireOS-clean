package com.firehome;

import java.util.List;

/** Every app on the stick, by name, as a grid of discs. Back returns to the home screen. */
public class AllAppsActivity extends HomeActivity {
    @Override
    protected int titleRes() {
        return R.string.title_apps;
    }

    @Override
    protected int layout() {
        return R.layout.activity_plain;
    }

    @Override
    protected boolean grid() {
        return true;
    }

    @Override
    protected List<Tile> tiles() {
        return catalog().alphabetical();
    }

    @Override
    public void onBackPressed() {
        finish();
    }
}
