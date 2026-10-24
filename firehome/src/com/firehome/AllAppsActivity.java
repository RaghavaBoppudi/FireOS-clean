package com.firehome;

import android.view.View;
import android.widget.LinearLayout;

import java.util.List;

/** Every app on the stick, by name, in a centred grid inside a frosted panel. Back returns to the home screen. */
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
        int columns = getResources().getInteger(R.integer.grid_columns);
        int width = getResources().getDimensionPixelSize(R.dimen.tile_width);
        int height = getResources().getDimensionPixelSize(R.dimen.tile_height);
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
            View view = tileView(tiles.get(i));
            line.addView(view, params);
        }
    }

    @Override
    public void onBackPressed() {
        finish();
    }
}
