package com.firehome;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;

/**
 * Covers Amazon's home with a plain "Launching Home" screen for the moment between Amazon's home starting and
 * HomeActivity being on screen, so Amazon's home is never seen. It is drawn over other apps, which install.sh
 * allows with `appops set ... SYSTEM_ALERT_WINDOW allow`; without that it simply does not appear. All calls
 * come on the main thread.
 */
final class LaunchOverlay {
    private final Context context;
    private final Handler handler;
    private final WindowManager windows;
    private final long minimumMs;
    private final long settleMs;
    private final long fadeMs;
    private final long timeoutMs;
    private View view;
    private long shownAt;

    private final Runnable hide = new Runnable() {
        @Override
        public void run() {
            fadeOut();
        }
    };

    LaunchOverlay(Context context, Handler handler) {
        this.context = context;
        this.handler = handler;
        windows = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        minimumMs = context.getResources().getInteger(R.integer.overlay_min_ms);
        settleMs = context.getResources().getInteger(R.integer.overlay_settle_ms);
        fadeMs = context.getResources().getInteger(R.integer.overlay_fade_ms);
        timeoutMs = context.getResources().getInteger(R.integer.overlay_timeout_ms);
    }

    void show() {
        handler.removeCallbacks(hide);
        if (view != null) {
            view.animate().cancel();
            view.setAlpha(1f);
        } else {
            View created = LayoutInflater.from(context).inflate(R.layout.overlay_launching, null);
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.OPAQUE);
            try {
                windows.addView(created, params);
            } catch (RuntimeException e) {
                return;   // not allowed to draw over other apps
            }
            view = created;
            shownAt = SystemClock.elapsedRealtime();
        }
        handler.postDelayed(hide, timeoutMs);   // only a safety net, long enough to outlast Android swapping homes
    }

    /**
     * HomeActivity has the screen: take the overlay down once it has been up long enough to read and our screen has
     * had a moment to draw. A later start of Amazon's home brings it straight back up (see show).
     */
    void dismiss() {
        if (view == null) {
            return;
        }
        handler.removeCallbacks(hide);
        handler.postDelayed(hide, Math.max(settleMs, minimumMs - (SystemClock.elapsedRealtime() - shownAt)));
    }

    private void fadeOut() {
        final View leaving = view;
        if (leaving == null) {
            return;
        }
        view = null;
        leaving.animate().alpha(0f).setDuration(fadeMs).withEndAction(new Runnable() {
            @Override
            public void run() {
                try {
                    windows.removeView(leaving);
                } catch (RuntimeException e) {
                    // already gone
                }
            }
        }).start();
    }
}
