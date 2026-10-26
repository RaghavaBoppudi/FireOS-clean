package com.firehome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.PathInterpolator;

import java.util.ArrayList;
import java.util.List;

/**
 * The background photograph, filling the screen, under a soft dark gradient at the top and bottom so the clock and
 * the credit stay legible. It changes photo on the schedule in Wallpapers, with a slow cross-fade, and hands the
 * blurred copy of what it shows to the frosted panels over it.
 */
public class WallpaperView extends View {
    /** Told which photo is showing, for the credit. */
    interface Listener {
        void onPhoto(Wallpapers.Photo photo);

        /**
         * How light the picture is behind the date and behind the buttons: 0 for dark, so white, to 1 for light, so
         * black. They are told apart because a photograph can be bright where one is and dark where the other is.
         */
        void onTone(float dateLight, float buttonLight);
    }

    private static final int FADE_STEPS = 24;
    private static final float SCRIM_TOP_HEIGHT = 0.4f;
    private static final float SCRIM_BOTTOM_START = 0.7f;
    // a top strip lighter than this takes dark text; the scrim that keeps white text readable goes with it
    private static final float LIGHT_ABOVE = 0.5f;

    private final Wallpapers wallpapers;
    private final Handler handler = new Handler();
    private final Paint image = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint scrim = new Paint();
    private final Paint backdrop = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Matrix matrix = new Matrix();
    private final Matrix place = new Matrix();
    private PorterDuffColorFilter veilFilter;
    private int veilColor;
    private final RectF panel = new RectF();
    private final int[] here = new int[2];
    private final int[] there = new int[2];
    private final ValueAnimator fade = ValueAnimator.ofFloat(0f, 1f);
    private final List<View> followers = new ArrayList<>();
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            check();
        }
    };
    private Shader topScrim;
    private Shader bottomScrim;
    private final Runnable onChanged = new Runnable() {
        @Override
        public void run() {
            refresh();
        }
    };
    private Wallpapers.Frame from;
    private Wallpapers.Frame to;
    private Wallpapers.Photo wanted;
    private Listener listener;
    private float mix = 1f;
    private int step;

    public WallpaperView(Context context, AttributeSet attrs) {
        super(context, attrs);
        wallpapers = Wallpapers.get(context);
        fade.setDuration(getResources().getInteger(R.integer.photo_fade_ms));
        fade.setInterpolator(new PathInterpolator(0.4f, 0f, 0.2f, 1f));
        fade.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                // in steps: a slow fade does not need every frame, and each repaint of a full-screen photo is costly
                int now = Math.round(animation.getAnimatedFraction() * FADE_STEPS);
                if (now != step) {
                    step = now;
                    mix = animation.getAnimatedFraction();
                    changed();
                }
            }
        });
    }

    void setListener(Listener listener) {
        this.listener = listener;
        if (to != null) {
            listener.onPhoto(to.photo);
            notifyTone();
        }
    }

    /** A view that draws what this one shows (frosted glass) and must repaint when it changes. */
    void follow(View view) {
        followers.add(view);
    }

    /** Checks now whether the photo should change, and again when it next should. */
    void refresh() {
        handler.removeCallbacks(tick);
        check();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE) {
            wallpapers.addOnChanged(onChanged);
            RemoteWallpapers.syncIfDue(getContext());
            refresh();
        } else {
            wallpapers.removeOnChanged(onChanged);
            handler.removeCallbacks(tick);   // nothing to change while another app is on screen
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        handler.removeCallbacks(tick);
        wallpapers.removeOnChanged(onChanged);
        super.onDetachedFromWindow();
    }

    private void check() {
        long now = System.currentTimeMillis();
        Wallpapers.Photo photo = wallpapers.photoAt(now);
        if (photo != null && !photo.equals(wanted)) {
            wanted = photo;
            wallpapers.load(photo, new Wallpapers.Callback() {
                @Override
                public void onFrame(Wallpapers.Frame frame) {
                    if (frame.photo.equals(wanted)) {
                        show(frame);
                    }
                }
            });
        }
        handler.postDelayed(tick, wallpapers.untilNext(now) + 100);
    }

    private void show(Wallpapers.Frame frame) {
        if (frame == to) {
            return;
        }
        fade.cancel();
        from = to != null ? to : frame;
        to = frame;
        mix = to == from ? 1f : 0f;
        step = 0;
        if (listener != null) {
            listener.onPhoto(frame.photo);
        }
        if (mix < 1f) {
            fade.start();
        }
        changed();
    }

    /** 0 to 1 between a dark place and a light one, following the cross-fade between two photos. */
    private float tone(boolean forDate) {
        float now = from != null && (forDate ? from.dateLight : from.buttonLight) > LIGHT_ABOVE ? 1f : 0f;
        float next = to != null && (forDate ? to.dateLight : to.buttonLight) > LIGHT_ABOVE ? 1f : 0f;
        return now + (next - now) * mix;
    }

    private void notifyTone() {
        if (listener != null) {
            listener.onTone(tone(true), tone(false));
        }
    }

    /**
     * The photo on show reduced to a handful of colours and brought back up in steps, each smoothing the one before,
     * for the screensaver: no shape is left, only a gradient of where the light and dark were. Null before there is a
     * photo.
     */
    Bitmap softest() {
        if (to == null) {
            return null;
        }
        Bitmap few = Bitmap.createScaledBitmap(to.blur, 5, 3, true);
        Bitmap some = Bitmap.createScaledBitmap(few, 20, 12, true);
        Bitmap many = Bitmap.createScaledBitmap(some, 80, 45, true);
        few.recycle();
        some.recycle();
        return many;
    }

    private void changed() {
        notifyTone();
        invalidate();
        for (View follower : followers) {
            follower.invalidate();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        int dark = getResources().getColor(R.color.scrim);
        topScrim = new LinearGradient(0, 0, 0, h * SCRIM_TOP_HEIGHT, dark, Color.TRANSPARENT, Shader.TileMode.CLAMP);
        bottomScrim = new LinearGradient(0, h * SCRIM_BOTTOM_START, 0, h, Color.TRANSPARENT, dark,
                Shader.TileMode.CLAMP);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (from != null) {
            draw(canvas, from.image, 255);
            if (mix > 0f && to != from) {
                draw(canvas, to.image, Math.round(255 * mix));
            }
        }
        scrim.setShader(topScrim);
        // the wash that helps white text is needed only where white text sits on something light: not when either end of
        // the bar has a light picture behind it, which has black text, nor when the picture is dark already
        scrim.setAlpha(Math.round(255 * (1f - Math.max(tone(true), tone(false)))));
        canvas.drawRect(0, 0, getWidth(), getHeight() * SCRIM_TOP_HEIGHT, scrim);
        scrim.setShader(bottomScrim);
        scrim.setAlpha(255);
        canvas.drawRect(0, getHeight() * SCRIM_BOTTOM_START, getWidth(), getHeight(), scrim);
    }

    private void draw(Canvas canvas, Bitmap bitmap, int alpha) {
        image.setAlpha(alpha);
        canvas.drawBitmap(bitmap, fill(bitmap), image);
    }

    /** Drops the pictures held for drawing; they come back the next time the screen is shown. */
    void release() {
        from = null;
        to = null;
        wanted = null;
        invalidate();
        if (getWindowVisibility() == VISIBLE) {
            refresh();   // still on screen after all: make the picture again at once
        }
    }

    boolean hasPicture() {
        return from != null;
    }

    /** Draws the blurred photo as it lies behind a panel, clipped to the panel's rounded outline. */
    void drawBlurred(Canvas canvas, View panelView, float radius, int veil) {
        if (from == null) {
            return;
        }
        getLocationInWindow(here);
        panelView.getLocationInWindow(there);
        panel.set(0, 0, panelView.getWidth(), panelView.getHeight());
        // the veil is laid over the photo by a colour filter inside the same pass: painting it as a second, separate
        // shape would anti-alias the rounded edge twice and leave a faint halo there
        if (veilFilter == null || veilColor != veil) {
            veilColor = veil;
            veilFilter = new PorterDuffColorFilter(veil, PorterDuff.Mode.SRC_OVER);
        }
        backdrop.setColorFilter(veilFilter);
        paintBlurred(canvas, from, 255, there[0] - here[0], there[1] - here[1], radius);
        if (mix > 0f && to != from) {
            paintBlurred(canvas, to, Math.round(255 * mix), there[0] - here[0], there[1] - here[1], radius);
        }
    }

    private void paintBlurred(Canvas canvas, Wallpapers.Frame frame, int alpha, int dx, int dy, float radius) {
        place.set(fill(frame.blur));
        place.postTranslate(-dx, -dy);   // the shader works in the panel's own coordinates
        BitmapShader shader = frame.blurShader();
        shader.setLocalMatrix(place);
        backdrop.setShader(shader);
        backdrop.setAlpha(alpha);
        canvas.drawRoundRect(panel, radius, radius, backdrop);
    }

    /** Scales and centres a picture to cover this view, cropping what overhangs. */
    private Matrix fill(Bitmap bitmap) {
        float scale = Math.max(getWidth() / (float) bitmap.getWidth(), getHeight() / (float) bitmap.getHeight());
        matrix.setScale(scale, scale);
        matrix.postTranslate((getWidth() - bitmap.getWidth() * scale) / 2f, (getHeight() - bitmap.getHeight() * scale) / 2f);
        return matrix;
    }
}
