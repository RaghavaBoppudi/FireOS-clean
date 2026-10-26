package com.firehome;

import android.animation.ObjectAnimator;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Edit mode for the home dock. The discs rock. OK picks one up and the arrows move it; OK sets it down. Up lifts it
 * off the dock, and OK then removes it. The + at the end opens the apps that are not on the dock, and OK on one adds
 * it. Back steps out one level at a time. The dock holds one app to six; asking for more or less shakes it and says
 * why. Every change is saved as it is made.
 *
 * It takes over the shelf while it runs, and gives it back to HomeActivity, rebuilt, when it ends.
 */
final class DockEditor {
    private enum Mode {OFF, IDLE, HOLDING, LIFTED, ADDING, LEAVING}

    private static final long SLIDE_MS = 180;
    private static final long LEAVE_MS = 200;
    private static final long SHAKE_MS = 320;
    private static final long MESSAGE_MS = 2500;
    private static final float ENTER_SCALE = 0.4f;

    private final HomeActivity home;
    private final DockStore store;
    private final ViewGroup buttons;      // the buttons at the bottom, which cannot be reached while editing
    private final Handler handler = new Handler();
    private final List<Tile> dock = new ArrayList<>();
    private final Tile plus;
    private Mode mode = Mode.OFF;
    private Tile held;
    private int heldFrom;                 // where the disc in hand was when it was picked up
    private int instruction;              // the line for the mode, which a message replaces for a moment
    private boolean hinting;              // the first-run line is up

    private final Runnable restore = new Runnable() {
        @Override
        public void run() {
            home.say(home.getString(instruction));
        }
    };
    private final Runnable endHint = new Runnable() {
        @Override
        public void run() {
            closeHint();
        }
    };

    DockEditor(HomeActivity home, DockStore store, ViewGroup buttons) {
        this.home = home;
        this.store = store;
        this.buttons = buttons;
        plus = Tile.plain("add", home.getString(R.string.add_app), home.getResources().getDrawable(R.drawable.ic_add));
    }

    boolean active() {
        return mode != Mode.OFF;
    }

    /** Tells the user, once, how to edit the dock: a line that goes at the first button or after a while. */
    void hintOnce() {
        if (store.hintSeen() || mode != Mode.OFF || hinting) {
            return;
        }
        store.setHintSeen();
        hinting = true;
        home.say(home.getString(R.string.first_run_hint));
        handler.postDelayed(endHint, home.getResources().getInteger(R.integer.hint_ms));
    }

    private void closeHint() {
        if (hinting) {
            hinting = false;
            handler.removeCallbacks(endHint);
            home.say(null);
        }
    }

    // --- starting and stopping

    void enter() {
        if (mode != Mode.OFF) {
            return;
        }
        closeHint();
        dock.clear();
        dock.addAll(home.dockTiles());
        if (store.packages() == null) {
            save();   // the first edit turns the apps the dock shows into a list of its own
        }
        for (int i = 0; i < buttons.getChildCount(); i++) {
            buttons.getChildAt(i).setFocusable(false);
        }
        mode = Mode.IDLE;
        showDock(dock.isEmpty() ? plus.key : dock.get(0).key, false);
    }

    /** Edit mode is over: the dock goes back to HomeActivity, with the order it was left in. */
    private void finish() {
        mode = Mode.OFF;
        held = null;
        handler.removeCallbacks(restore);
        for (int i = 0; i < buttons.getChildCount(); i++) {
            buttons.getChildAt(i).setFocusable(true);
        }
        home.say(null);
        home.finishEditing(packages(), home.selectedKey());
    }

    /** The home is leaving the screen. */
    void leave() {
        if (mode != Mode.OFF) {
            finish();
        }
    }

    // --- keys

    /** True if the key was the editor's. OK is not: it reaches the selected disc, which acts on it (see press). */
    boolean consume(KeyEvent event) {
        if (mode == Mode.OFF) {
            return false;
        }
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        int key = event.getKeyCode();
        if (mode == Mode.LEAVING) {
            return true;          // a disc is shrinking away: the keys wait for it
        }
        if (key == KeyEvent.KEYCODE_BACK) {
            if (down) {
                back();
            }
            return true;
        }
        boolean arrow = key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT
                || key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN;
        if (arrow && (mode == Mode.HOLDING || mode == Mode.LIFTED)) {
            if (down) {
                arrow(key);       // the disc in hand takes the arrows; the selection stays on it
            }
            return true;
        }
        return false;
    }

    private void arrow(int key) {
        if (mode == Mode.LIFTED) {
            if (key == KeyEvent.KEYCODE_DPAD_DOWN) {
                held().raise(1f);
                mode = Mode.HOLDING;
                instruct(R.string.edit_holding);
            }
        } else if (key == KeyEvent.KEYCODE_DPAD_UP) {
            if (dock.size() <= DockStore.MIN) {
                refuse(R.string.edit_last);
            } else {
                held().raise(2f);
                mode = Mode.LIFTED;
                instruct(R.string.edit_lifted);
            }
        } else if (key == KeyEvent.KEYCODE_DPAD_LEFT) {
            move(-1);
        } else if (key == KeyEvent.KEYCODE_DPAD_RIGHT) {
            move(1);
        }
    }

    private void back() {
        switch (mode) {
            case LIFTED:
                held().raise(1f);
                mode = Mode.HOLDING;
                instruct(R.string.edit_holding);
                break;
            case HOLDING:
                dock.remove(held);                  // put back where it was picked up
                dock.add(heldFrom, held);
                mode = Mode.IDLE;
                showDock(held.key, false);
                break;
            case ADDING:
                mode = Mode.IDLE;
                showDock(plusShown() ? plus.key : dock.get(dock.size() - 1).key, false);
                break;
            default:
                finish();
        }
    }

    /** OK on a disc. */
    private void press(Tile tile) {
        switch (mode) {
            case IDLE:
                if (tile == plus) {
                    openPicker();
                } else {
                    held = tile;
                    heldFrom = dock.indexOf(tile);
                    mode = Mode.HOLDING;
                    held().raise(1f);
                    instruct(R.string.edit_holding);
                }
                break;
            case HOLDING:
                held().raise(0f);
                mode = Mode.IDLE;
                save();
                instruct(R.string.edit_idle);
                break;
            case LIFTED:
                remove();
                break;
            case ADDING:
                dock.add(tile);
                save();
                mode = Mode.IDLE;
                showDock(tile.key, true);
                break;
            default:
        }
    }

    // --- changing the dock

    /** Swaps the disc in hand with its neighbour: the neighbour hops over it and both slide into place. */
    private void move(int step) {
        int from = dock.indexOf(held);
        int to = from + step;
        if (to < 0 || to >= dock.size()) {
            refuse(0);
            return;
        }
        Collections.swap(dock, from, to);
        final Bubble mine = held();
        final Bubble neighbour = home.viewOf(dock.get(from));
        final ViewGroup line = (ViewGroup) mine.getParent();
        final float mineAt = mine.getLeft() + mine.getTranslationX();
        final float neighbourAt = neighbour.getLeft() + neighbour.getTranslationX();
        ViewGroup.LayoutParams params = neighbour.getLayoutParams();
        line.removeView(neighbour);
        line.addView(neighbour, line.indexOfChild(mine) + (step < 0 ? 1 : 0), params);
        line.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                line.getViewTreeObserver().removeOnPreDrawListener(this);
                slide(mine, mineAt);
                slide(neighbour, neighbourAt);
                return true;
            }
        });
    }

    private static void slide(View view, float from) {
        view.setTranslationX(from - view.getLeft());
        view.animate().translationX(0f).setDuration(SLIDE_MS).setInterpolator(Bubble.OUT);
    }

    /** The disc in hand, taken off the dock. */
    private void remove() {
        final Tile gone = held;
        final int at = dock.indexOf(gone);
        mode = Mode.LEAVING;
        held().animate().scaleX(0f).scaleY(0f).alpha(0f).setDuration(LEAVE_MS).setInterpolator(Bubble.OUT)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        dock.remove(gone);
                        save();
                        mode = Mode.IDLE;
                        showDock(dock.get(Math.min(at, dock.size() - 1)).key, false);
                    }
                });
    }

    private void openPicker() {
        List<String> chosen = packages();
        List<Tile> available = new ArrayList<>();
        for (Tile tile : home.catalog().alphabetical()) {
            if (!chosen.contains(tile.packageName())) {
                available.add(tile);
            }
        }
        if (available.isEmpty()) {
            refuse(R.string.edit_adding_none);
            return;
        }
        mode = Mode.ADDING;
        home.showGrid(available, Math.min(available.size(), home.getResources().getInteger(R.integer.grid_columns)));
        listen(available);
        home.viewOf(available.get(0)).requestFocus();
        instruct(R.string.edit_adding);
    }

    // --- showing it

    /** Lays the dock out, with the + after it while there is room, and selects a disc. */
    private void showDock(String focusKey, boolean entering) {
        List<Tile> tiles = new ArrayList<>(dock);
        if (plusShown()) {
            tiles.add(plus);
        }
        home.showGrid(tiles, tiles.size());
        listen(tiles);
        for (Tile tile : dock) {
            home.viewOf(tile).jiggle(true);
        }
        for (Tile tile : tiles) {
            if (tile.key.equals(focusKey)) {
                Bubble view = home.viewOf(tile);
                view.requestFocus();
                if (entering) {
                    view.setScaleX(ENTER_SCALE);
                    view.setScaleY(ENTER_SCALE);
                    view.setAlpha(0f);
                    view.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(SLIDE_MS).setInterpolator(Bubble.OUT);
                }
            }
        }
        instruct(R.string.edit_idle);
    }

    private boolean plusShown() {
        return dock.size() < DockStore.MAX;
    }

    private void listen(List<Tile> tiles) {
        for (final Tile tile : tiles) {
            home.viewOf(tile).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    press(tile);
                }
            });
        }
    }

    private Bubble held() {
        return home.viewOf(held);
    }

    private void instruct(int line) {
        instruction = line;
        handler.removeCallbacks(restore);
        home.say(home.getString(line));
    }

    /** The dock can't do what was asked: it shakes and says so for a moment. 0 is a shake alone. */
    private void refuse(int reason) {
        View view = home.getCurrentFocus();
        if (view != null) {
            float shake = home.getResources().getDimension(R.dimen.shake);
            ObjectAnimator.ofFloat(view, View.TRANSLATION_X, 0f, -shake, shake, -shake, shake, 0f)
                    .setDuration(SHAKE_MS).start();
        }
        if (reason != 0) {
            home.say(home.getString(reason));
            handler.removeCallbacks(restore);
            handler.postDelayed(restore, MESSAGE_MS);
        }
    }

    private List<String> packages() {
        List<String> packages = new ArrayList<>();
        for (Tile tile : dock) {
            packages.add(tile.packageName());
        }
        return packages;
    }

    private void save() {
        store.save(packages());
    }
}
