package com.firehome;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.View;

import java.util.Random;

/**
 * The screensaver of the home screen: after the home has sat unused for a while, the bare ground, and nothing on it
 * but the date and time, which move to a new place every few seconds so that no pixel of the screen is held in one
 * place. Any button wakes it, and that button does nothing else.
 *
 * It belongs to the home screen alone: it counts idle time while Home is on screen, and not while the dock is being
 * edited. How long it waits (or never) is a setting; the choices are in integers.xml.
 */
final class Screensaver {
    private static final String PREFERENCES = "screensaver";
    private static final String KEY_INDEX = "index";
    private static final long MINUTE_MS = 60 * 1000L;

    private final HomeActivity home;
    private final DockEditor editor;
    private final Handler handler = new Handler();
    private final Random random = new Random();
    private final View screen;
    private final View clock;
    private final long moveMs;
    private final long glideMs;
    private final long wakeMs;
    private boolean showing;
    private boolean swallowUp;      // the key that woke the screen has not been released yet
    private int lastX = -1;
    private int lastY = -1;

    private final Runnable start = new Runnable() {
        @Override
        public void run() {
            // the press that opens edit mode re-arms the timer a moment before the dock goes into edit mode, so
            // the timer can come due while the dock is being edited: it then waits for edit mode to end
            if (!editor.active()) {
                show();
            }
        }
    };
    private final Runnable move = new Runnable() {
        @Override
        public void run() {
            glide();
            handler.postDelayed(this, moveMs);
        }
    };

    Screensaver(HomeActivity home, DockEditor editor) {
        this.home = home;
        this.editor = editor;
        screen = home.findViewById(R.id.screensaver);
        clock = home.findViewById(R.id.saver_clock);
        moveMs = home.getResources().getInteger(R.integer.screensaver_move_ms);
        glideMs = home.getResources().getInteger(R.integer.screensaver_glide_ms);
        wakeMs = home.getResources().getInteger(R.integer.screensaver_wake_ms);
    }

    // --- the setting

    /** How many minutes the home waits, 0 for never. */
    static int minutes(Context context) {
        int[] choices = context.getResources().getIntArray(R.array.screensaver_minutes);
        return choices[index(context)];
    }

    /** The choice, in words, for the caption under its setting. */
    static String caption(Context context) {
        return context.getResources().getStringArray(R.array.screensaver_captions)[index(context)];
    }

    /** Moves to the next choice; after the last it wraps round to Off. */
    static void next(Context context) {
        int count = context.getResources().getIntArray(R.array.screensaver_minutes).length;
        prefs(context).edit().putInt(KEY_INDEX, (index(context) + 1) % count).apply();
    }

    private static int index(Context context) {
        int count = context.getResources().getIntArray(R.array.screensaver_minutes).length;
        int saved = prefs(context).getInt(KEY_INDEX, context.getResources().getInteger(R.integer.screensaver_default_index));
        return saved >= 0 && saved < count ? saved : 0;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    // --- idle time

    /** Starts counting from now (or stops, if the setting is Off, or the dock is being edited). */
    void rearm() {
        handler.removeCallbacks(start);
        int minutes = minutes(home);
        if (minutes > 0 && !showing && !editor.active()) {
            handler.postDelayed(start, minutes * MINUTE_MS);
        }
    }

    /** The home is leaving the screen. */
    void stop() {
        handler.removeCallbacks(start);
        hide();
    }

    /** True if the key was the screensaver's: it wakes the screen, and nothing else happens. */
    boolean consume(KeyEvent event) {
        if (showing) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                swallowUp = true;
                fadeOut();
                rearm();
            }
            return true;
        }
        if (swallowUp && event.getAction() == KeyEvent.ACTION_UP) {
            swallowUp = false;
            return true;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            rearm();
        }
        return false;
    }

    // --- showing it

    private void show() {
        showing = true;
        screen.animate().cancel();     // a fade out that is still running must not hide it
        screen.setAlpha(0f);
        screen.setVisibility(View.VISIBLE);
        screen.animate().alpha(1f).setDuration(glideMs);
        clock.setAlpha(0f);
        screen.post(new Runnable() {
            @Override
            public void run() {
                place(false);
                clock.animate().alpha(1f).setDuration(glideMs);
                handler.postDelayed(move, moveMs);
            }
        });
    }

    private void hide() {
        if (!showing) {
            return;
        }
        end();
        screen.animate().cancel();
        screen.setVisibility(View.GONE);
    }

    /** Wakes the screen: the black fades away, quickly, so that a lit screen does not jump out of the dark. */
    private void fadeOut() {
        end();
        screen.animate().alpha(0f).setDuration(wakeMs).withEndAction(new Runnable() {
            @Override
            public void run() {
                if (!showing) {
                    screen.setVisibility(View.GONE);
                }
            }
        });
    }

    private void end() {
        showing = false;
        handler.removeCallbacks(move);
        clock.animate().cancel();
    }

    /** Moves the clock to a new place: it fades out where it was, and in where it goes. */
    private void glide() {
        clock.animate().alpha(0f).setDuration(glideMs / 2).withEndAction(new Runnable() {
            @Override
            public void run() {
                if (showing) {
                    place(true);
                    clock.animate().alpha(1f).setDuration(glideMs / 2);
                }
            }
        });
    }

    /** A place for the clock inside the margins, not near the last one. */
    private void place(boolean differ) {
        int margin = home.getResources().getDimensionPixelSize(R.dimen.saver_margin);
        int roomX = Math.max(1, screen.getWidth() - clock.getWidth() - 2 * margin);
        int roomY = Math.max(1, screen.getHeight() - clock.getHeight() - 2 * margin);
        int x;
        int y;
        int tries = 0;
        do {
            x = margin + random.nextInt(roomX);
            y = margin + random.nextInt(roomY);
        } while (differ && lastX >= 0 && tries++ < 8
                && Math.abs(x - lastX) < roomX / 4 && Math.abs(y - lastY) < roomY / 4);
        lastX = x;
        lastY = y;
        clock.setX(x);
        clock.setY(y);
    }
}
