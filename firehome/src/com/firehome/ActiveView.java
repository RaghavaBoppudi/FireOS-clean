package com.firehome;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Property;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;

/**
 * A focusable view that eases between resting and selected. One animated value, {@code active} (0 at rest, 1
 * selected), drives everything a subclass changes, so its parts can never fall out of step. Nothing here changes
 * the view's layer type: doing that while animating makes the view flicker as its layer is rebuilt.
 */
abstract class ActiveView extends FrameLayout {
    /** How bright a mark or glyph rests at; selected is full. Shared, so every control rests alike. */
    static final float RESTING_ALPHA = 0.8f;

    private static final PathInterpolator EASE = new PathInterpolator(0.4f, 0f, 0.2f, 1f);

    private static final Property<ActiveView, Float> ACTIVE = new Property<ActiveView, Float>(Float.class, "active") {
        @Override
        public Float get(ActiveView view) {
            return view.active;
        }

        @Override
        public void set(ActiveView view, Float value) {
            view.setActive(value);
        }
    };

    private final long durationMs;
    private ObjectAnimator animator;
    private float active;

    ActiveView(Context context, AttributeSet attrs) {
        super(context, attrs);
        durationMs = getResources().getInteger(R.integer.tile_animation_ms);
        setFocusable(true);
    }

    /** Apply the look for this amount of selection. A subclass calls setActive(0) once it has built its parts. */
    protected abstract void onActive(float active);

    protected final void setActive(float value) {
        active = value;
        onActive(value);
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        if (animator != null) {
            animator.cancel();
        }
        animator = ObjectAnimator.ofFloat(this, ACTIVE, active, gainFocus ? 1f : 0f);
        animator.setDuration(durationMs);
        animator.setInterpolator(EASE);
        animator.start();
    }
}
