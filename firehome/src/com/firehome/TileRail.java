package com.firehome;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.widget.HorizontalScrollView;

/** The dock's row, which scrolls only when there are more tiles than fit, and only as far as the selected one needs. */
public class TileRail extends HorizontalScrollView {
    private final int inset;

    public TileRail(Context context, AttributeSet attrs) {
        super(context, attrs);
        inset = getResources().getDimensionPixelSize(R.dimen.dock_padding);
    }

    void reveal(View tile) {
        int shown = getScrollX();
        int dx = 0;
        if (tile.getLeft() - inset < shown) {
            dx = tile.getLeft() - inset - shown;
        } else if (tile.getRight() + inset > shown + getWidth()) {
            dx = tile.getRight() + inset - shown - getWidth();
        }
        if (dx != 0) {
            smoothScrollBy(dx, 0);
        }
    }

    @Override
    protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect) {
        return 0;   // reveal() scrolls instead, keeping the dock's inset and animating
    }
}
