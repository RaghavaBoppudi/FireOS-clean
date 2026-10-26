package com.firehome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** A ring with a bright head and a tail that fades away behind it, turning steadily. It runs only while it is on screen. */
public class SpinnerView extends View {
    private static final long TURN_MS = 1000;

    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();
    private final float stroke;
    private final ValueAnimator turn = ValueAnimator.ofFloat(0f, 360f);
    private float angle;

    public SpinnerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        stroke = getResources().getDimension(R.dimen.spinner_stroke);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(stroke);
        ring.setStrokeCap(Paint.Cap.ROUND);
        turn.setDuration(TURN_MS);
        turn.setInterpolator(new LinearInterpolator());
        turn.setRepeatCount(ValueAnimator.INFINITE);
        turn.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                angle = (Float) animation.getAnimatedValue();
                invalidate();
            }
        });
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float inset = stroke / 2f;
        bounds.set(inset, inset, w - inset, h - inset);
        int ink = Look.colour(getContext(), R.attr.ink);
        ring.setShader(new SweepGradient(w / 2f, h / 2f, new int[]{Color.TRANSPARENT, ink},
                new float[]{0.05f, 1f}));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        turn.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        turn.cancel();   // an endless animator would otherwise outlive the view
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.save();
        canvas.rotate(angle, getWidth() / 2f, getHeight() / 2f);
        canvas.drawArc(bounds, 0, 359, false, ring);
        canvas.restore();
    }
}
