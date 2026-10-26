package com.firehome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.widget.FrameLayout;

/**
 * A panel of soft glass: the blurred wallpaper behind it under a light, flat veil, with a shimmer along its top edge
 * that is brightest in the middle and gone by the ends. A panel one tile high is a full pill; a taller one keeps the
 * pill's corner. No grain, sheen or shadow.
 */
public class FrostedPanel extends FrameLayout {
    // the shimmer's brightness across the panel, as a share of its full strength, at these shares of the width
    private static final float[] SHIMMER_AT = {0f, 0.15f, 0.5f, 0.85f, 1f};
    private static final float[] SHIMMER_STRENGTH = {0f, 0.3f, 1f, 0.3f, 0f};

    private final Paint veil = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shimmer = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();
    private final RectF edge = new RectF();
    private final float radius;
    private WallpaperView wallpaper;

    public FrostedPanel(Context context, AttributeSet attrs) {
        super(context, attrs);
        radius = getResources().getDimension(R.dimen.panel_radius);
        veil.setColor(getResources().getColor(R.color.veil));
        shimmer.setStyle(Paint.Style.STROKE);
        shimmer.setStrokeWidth(getResources().getDimension(R.dimen.shimmer_width));
        setWillNotDraw(false);
    }

    void setWallpaper(WallpaperView wallpaper) {
        this.wallpaper = wallpaper;
        wallpaper.follow(this);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        int light = getResources().getColor(R.color.shimmer);
        int[] colours = new int[SHIMMER_AT.length];
        for (int i = 0; i < colours.length; i++) {
            colours[i] = Color.argb(Math.round(Color.alpha(light) * SHIMMER_STRENGTH[i]),
                    Color.red(light), Color.green(light), Color.blue(light));
        }
        shimmer.setShader(new LinearGradient(0, 0, w, 0, colours, SHIMMER_AT, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int width = getWidth();
        int height = getHeight();
        float corner = Math.min(radius, Math.min(width, height) / 2f);
        if (wallpaper != null && wallpaper.hasPicture()) {
            wallpaper.drawBlurred(canvas, this, corner, veil.getColor());
        } else {
            bounds.set(0, 0, width, height);
            canvas.drawRoundRect(bounds, corner, corner, veil);   // no picture yet: the veil alone
        }
        // only the top edge is drawn: the gradient fades to nothing before the sides, so cutting the outline off
        // below the corners leaves no visible end
        float inset = shimmer.getStrokeWidth() / 2f;
        edge.set(inset, inset, width - inset, height - inset);
        canvas.save();
        canvas.clipRect(0, 0, width, Math.min(height / 2f, corner));
        canvas.drawRoundRect(edge, corner - inset, corner - inset, shimmer);
        canvas.restore();
    }
}
