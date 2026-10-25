package com.firehome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.widget.FrameLayout;

/** A panel of soft glass: the blurred wallpaper behind it under a light, flat veil. No grain, rim, sheen or shadow. */
public class FrostedPanel extends FrameLayout {
    private final Paint veil = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();
    private final float radius;
    private WallpaperView wallpaper;

    public FrostedPanel(Context context, AttributeSet attrs) {
        super(context, attrs);
        radius = getResources().getDimension(R.dimen.panel_radius);
        veil.setColor(getResources().getColor(R.color.veil));
        setWillNotDraw(false);
    }

    void setWallpaper(WallpaperView wallpaper) {
        this.wallpaper = wallpaper;
        wallpaper.follow(this);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (wallpaper != null && wallpaper.hasPicture()) {
            wallpaper.drawBlurred(canvas, this, radius, veil.getColor());
            return;
        }
        bounds.set(0, 0, getWidth(), getHeight());
        canvas.drawRoundRect(bounds, radius, radius, veil);   // no picture yet: the veil alone
    }
}
