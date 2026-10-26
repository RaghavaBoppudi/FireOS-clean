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
 * screen behind it, cut to a circle. The disc moves and grows, so it works out each time where it is.
 */
final class Frost extends Drawable {
    private final View disc;
    private final BitmapShader picture;
    private final Matrix placing = new Matrix();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF circle = new RectF();
    private final int[] at = new int[2];
    private final float spreadX;
    private final float spreadY;

    Frost(Bitmap soft, View disc) {
        this.disc = disc;
        picture = new BitmapShader(soft, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        paint.setShader(picture);
        paint.setFilterBitmap(true);
        DisplayMetrics screen = disc.getResources().getDisplayMetrics();
        spreadX = screen.widthPixels / (float) soft.getWidth();
        spreadY = screen.heightPixels / (float) soft.getHeight();
    }

    @Override
    public void draw(Canvas canvas) {
        disc.getLocationOnScreen(at);     // where its top left corner is on the screen, as it is now
        float scale = disc.getScaleX();
        placing.setScale(spreadX, spreadY);
        placing.postTranslate(-at[0], -at[1]);
        placing.postScale(1f / scale, 1f / scale);
        picture.setLocalMatrix(placing);
        circle.set(getBounds());
        canvas.drawOval(circle, paint);
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
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
