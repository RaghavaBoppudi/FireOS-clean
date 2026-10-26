package com.firehome;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;
import android.view.View;

/**
 * The glass of a disc: the soft picture, drawn where it lies behind the disc so the disc seems to be made of the
 * screen behind it, cut to a circle. The disc moves and grows, so it works out each time where it is. While the
 * wallpaper changes it draws the picture that was, and over it the new one, as far as the fade has come, so the discs
 * change with the screen behind them.
 */
final class Frost extends Drawable {
    private final View disc;
    private final Matrix placing = new Matrix();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF circle = new RectF();
    private final int[] at = new int[2];
    private int alpha = 255;
    private Bitmap nowBitmap;
    private BitmapShader nowShader;
    private Bitmap beforeBitmap;
    private BitmapShader beforeShader;

    Frost(View disc) {
        this.disc = disc;
        paint.setFilterBitmap(true);
    }

    @Override
    public void draw(Canvas canvas) {
        Bitmap now = Wallpaper.soft();
        if (now == null) {
            return;
        }
        if (now != nowBitmap) {
            nowBitmap = now;
            nowShader = new BitmapShader(now, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        }
        disc.getLocationOnScreen(at);     // where its top left corner is on the screen, as it is now
        circle.set(getBounds());
        float mix = Wallpaper.mix();
        Bitmap before = mix < 1f ? Wallpaper.beforeSoft() : null;
        if (before != null) {
            if (before != beforeBitmap) {
                beforeBitmap = before;
                beforeShader = new BitmapShader(before, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            }
            drawPicture(canvas, beforeShader, before, alpha);
            drawPicture(canvas, nowShader, now, Math.round(alpha * mix));
        } else {
            drawPicture(canvas, nowShader, now, alpha);
        }
    }

    private void drawPicture(Canvas canvas, BitmapShader shader, Bitmap picture, int strength) {
        DisplayMetrics screen = disc.getResources().getDisplayMetrics();
        float scale = disc.getScaleX();
        placing.setScale(screen.widthPixels / (float) picture.getWidth(), screen.heightPixels / (float) picture.getHeight());
        placing.postTranslate(-at[0], -at[1]);
        placing.postScale(1f / scale, 1f / scale);
        shader.setLocalMatrix(placing);
        paint.setShader(shader);
        paint.setAlpha(strength);
        canvas.drawOval(circle, paint);
    }

    @Override
    public void setAlpha(int alpha) {
        this.alpha = alpha;
    }

    @Override
    public void setColorFilter(ColorFilter filter) {
        paint.setColorFilter(filter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
