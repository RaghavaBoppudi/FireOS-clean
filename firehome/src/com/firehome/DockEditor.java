package com.firehome;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.res.Resources;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Edit mode for the home dock. The dock opens out to six slots and its marks rock. OK picks a mark up and the arrows
 * move it; OK sets it down. Up lifts it off the dock, and OK then removes it. The + at the end opens the apps that are
 * not on the dock under it, and OK on one adds it. Back steps out one level at a time. The dock holds one app to six;
 * asking for more or less shakes it and says why. Every change is saved as it is made.
 *
 * It takes over the dock's row while it runs, and gives it back to HomeActivity, rebuilt, when it ends.
 */
final class DockEditor {
    private enum Mode {OFF, IDLE, HOLDING, LIFTED, ADDING}

    private static final long SLIDE_MS = 180;
    private static final long RESIZE_MS = 420;
    private static final float STAGGER = 0.1f;
    private static final long REMOVE_MS = 200;
    private static final long SHAKE_MS = 320;
    private static final long MESSAGE_MS = 2500;
    // a mark picked up is much larger than one only selected, and larger again once it is off the dock
    private static final float PICKED_SCALE = 1.3f;
    private static final float LIFT_SCALE = 1.45f;

    private final HomeActivity home;
    private final DockStore store;
    private final Resources res;
    private final Handler handler = new Handler();
    private final View panel;
    private final LinearLayout row;
    private final View bannerPanel;
    private final TextView banner;
    private final View dash;
    private final ScrollView addable;
    private final LinearLayout addableGrid;
    private final View[] outside;       // the buttons at the top, which cannot be reached while editing

    private final List<Tile> dock = new ArrayList<>();
    private final Map<String, TileView> views = new HashMap<>();
    private final List<Tile> available = new ArrayList<>();
    private final Tile plusTile;
    private TileView plus;
    private Mode mode = Mode.OFF;
    private boolean hinting;            // the first-run line is up
    private final Runnable endHint = new Runnable() {
        @Override
        public void run() {
            closeHint();
        }
    };
    private ValueAnimator resizing;     // the dock opening or closing, if it is
    private boolean removing;          // a mark is shrinking away; keys wait for it
    private boolean plusShown;          // the + comes in once the dock has opened out
    private Tile held;
    private int heldFrom;
    private int hint;                   // the instructions for the mode, which a message replaces for a moment
    private final Runnable restoreHint = new Runnable() {
        @Override
        public void run() {
            banner.setText(hint);
        }
    };

    DockEditor(HomeActivity home, DockStore store, View[] outside) {
        this.home = home;
        this.store = store;
        this.outside = outside;
        res = home.getResources();
        panel = home.findViewById(R.id.panel);
        row = (LinearLayout) home.findViewById(R.id.row);
        bannerPanel = home.findViewById(R.id.banner_panel);
        banner = (TextView) home.findViewById(R.id.banner);
        dash = home.findViewById(R.id.dash);
        addable = (ScrollView) home.findViewById(R.id.addable);
        addableGrid = (LinearLayout) home.findViewById(R.id.addable_grid);
        plusTile = Tile.adding(home.getString(R.string.add_app), res.getDrawable(R.drawable.ic_add));
        ((FrostedPanel) bannerPanel).setWallpaper(home.wallpaper());
        banner.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    }

    boolean active() {
        return mode != Mode.OFF;
    }

    /** Tells the user, once, how to edit the dock: a line above it that goes at the first button or after a while. */
    void hintOnce() {
        if (store.hintSeen() || mode != Mode.OFF || hinting) {
            return;
        }
        store.setHintSeen();
        hinting = true;
        banner.setText(R.string.first_run_hint);
        bannerPanel.setAlpha(0f);
        bannerPanel.setVisibility(View.VISIBLE);
        bannerPanel.animate().alpha(1f).setInterpolator(ActiveView.OUT).setDuration(SLIDE_MS);
        handler.postDelayed(endHint, res.getInteger(R.integer.hint_ms));
    }

    private void closeHint() {
        if (hinting) {
            hinting = false;
            handler.removeCallbacks(endHint);
            bannerPanel.animate().alpha(0f).setInterpolator(ActiveView.OUT).setDuration(SLIDE_MS).withEndAction(new Runnable() {
                @Override
                public void run() {
                    if (!hinting && mode == Mode.OFF) {
                        bannerPanel.setVisibility(View.GONE);
                    }
                }
            });
        }
    }

    // --- starting and stopping

    void enter() {
        if (mode != Mode.OFF) {
            return;
        }
        dock.clear();
        dock.addAll(home.dockTiles());
        if (store.packages() == null) {
            save();   // the first edit turns the apps the dock shows into a list of its own
        }
        mode = Mode.IDLE;
        closeHint();
        for (View button : outside) {
            button.setFocusable(false);
        }
        plusShown = false;
        build();
        widen(fullWidth());
        jiggleAll(true);
        bannerPanel.setAlpha(0f);
        bannerPanel.setVisibility(View.VISIBLE);
        bannerPanel.animate().alpha(1f).setInterpolator(ActiveView.OUT).setDuration(SLIDE_MS);
        say(R.string.edit_idle);
        focus(0);
    }

    /** Ends edit mode at once, wherever it stands (the screen is leaving); what was done is already saved. */
    void leave() {
        if (mode == Mode.OFF) {
            return;
        }
        handler.removeCallbacks(restoreHint);
        close(null, false);
    }

    private void exit() {
        close(held != null ? held.key : focusedKey(), true);
    }

    private void close(String focusKey, boolean animate) {
        mode = Mode.OFF;
        plusShown = false;
        held = null;
        jiggleAll(false);
        for (View button : outside) {
            button.setFocusable(true);
        }
        panel.setTranslationX(0f);
        addable.setVisibility(View.GONE);
        dash.setVisibility(View.GONE);
        bannerPanel.setVisibility(View.GONE);
        List<String> order = packages(dock);
        row.removeAllViews();
        if (animate) {
            narrow(naturalWidth(), order, focusKey);
        } else {
            row.setMinimumWidth(0);
            home.finishEditing(order, focusKey);
        }
    }

    // --- keys

    /** True if the editor used the key. Arrows are left to Android in the modes where they just move the focus. */
    boolean consume(KeyEvent event) {
        if (mode == Mode.OFF) {
            if (hinting && event.getAction() == KeyEvent.ACTION_DOWN) {
                closeHint();
            }
            return false;
        }
        if (removing) {
            return true;
        }
        int code = event.getKeyCode();
        boolean arrow = code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT
                || code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN;
        boolean select = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
        boolean back = code == KeyEvent.KEYCODE_BACK;
        boolean ours = select || back || (arrow && (mode == Mode.HOLDING || mode == Mode.LIFTED));
        if (arrow && !ours) {
            // moving between marks is Android's; keep it from reaching past the dock or the apps to add
            return mode == Mode.IDLE && (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN);
        }
        if (!ours) {
            return false;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN && (event.getRepeatCount() == 0 || arrow)) {
            if (select) {
                select();
            } else if (back) {
                back();
            } else {
                arrow(code);
            }
        }
        return true;
    }

    private void select() {
        switch (mode) {
            case IDLE:
                pickUpOrAdd();
                break;
            case HOLDING:
                putDown();
                break;
            case LIFTED:
                remove();
                break;
            case ADDING:
                add();
                break;
            default:
                break;
        }
    }

    private void back() {
        switch (mode) {
            case IDLE:
                exit();
                break;
            case HOLDING:
                cancelMove();
                break;
            case LIFTED:
                lower();
                break;
            case ADDING:
                closeAdding();
                break;
            default:
                break;
        }
    }

    private void arrow(int code) {
        if (mode == Mode.HOLDING) {
            if (code == KeyEvent.KEYCODE_DPAD_LEFT) {
                move(-1);
            } else if (code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                move(1);
            } else if (code == KeyEvent.KEYCODE_DPAD_UP) {
                raise();
            }
        } else if (mode == Mode.LIFTED && code == KeyEvent.KEYCODE_DPAD_DOWN) {
            lower();
        }
    }

    // --- picking up, moving, putting down

    private void pickUpOrAdd() {
        View focused = row.getFocusedChild();
        int index = row.indexOfChild(focused);
        if (index < 0) {
            return;
        }
        if (index >= dock.size()) {
            startAdding();
            return;
        }
        held = dock.get(index);
        heldFrom = index;
        mode = Mode.HOLDING;
        TileView view = views.get(held.key);
        view.setTranslationZ(1f);      // above its neighbours as it grows
        view.animate().scaleX(PICKED_SCALE).scaleY(PICKED_SCALE).setInterpolator(ActiveView.OUT).setDuration(SLIDE_MS);
        say(R.string.edit_holding);
    }

    private void move(final int step) {
        final int from = dock.indexOf(held);
        final int to = from + step;
        if (to < 0 || to >= dock.size()) {
            return;
        }
        slide(new Runnable() {
            @Override
            public void run() {
                Collections.swap(dock, from, to);
                arrange();
            }
        });
        views.get(held.key).requestFocus();
    }

    private void putDown() {
        TileView view = views.get(held.key);
        setDown(view);
        if (store.alphabetical()) {
            // the dock follows its setting: the mark goes to where its name puts it
            slide(new Runnable() {
                @Override
                public void run() {
                    Collections.sort(dock, BY_NAME);
                    arrange();
                }
            });
            view.requestFocus();
        }
        held = null;
        mode = Mode.IDLE;
        say(R.string.edit_idle);
    }

    private void cancelMove() {
        final Tile tile = held;
        final int to = heldFrom;
        setDown(views.get(tile.key));
        slide(new Runnable() {
            @Override
            public void run() {
                dock.remove(tile);
                dock.add(to, tile);
                arrange();
            }
        });
        views.get(tile.key).requestFocus();
        held = null;
        mode = Mode.IDLE;
        say(R.string.edit_idle);
    }

    /** The mark goes back to the size of the others. */
    private void setDown(TileView view) {
        view.animate().scaleX(1f).scaleY(1f).translationY(0f).setInterpolator(ActiveView.OUT).setDuration(SLIDE_MS)
                .withEndAction(new ZReset(view));
    }

    private static final class ZReset implements Runnable {
        private final View view;

        ZReset(View view) {
            this.view = view;
        }

        @Override
        public void run() {
            view.setTranslationZ(0f);
        }
    }

    // --- taking a mark off the dock

    private void raise() {
        if (dock.size() <= DockStore.MIN) {
            shake();
            flash(R.string.edit_last);
            return;
        }
        mode = Mode.LIFTED;
        views.get(held.key).animate().translationY(-res.getDimension(R.dimen.lift))
                .scaleX(LIFT_SCALE).scaleY(LIFT_SCALE).setInterpolator(ActiveView.OUT).setDuration(SLIDE_MS);
        say(R.string.edit_lifted);
    }

    private void lower() {
        mode = Mode.HOLDING;
        views.get(held.key).animate().translationY(0f).scaleX(PICKED_SCALE).scaleY(PICKED_SCALE).setInterpolator(ActiveView.OUT)
                .setDuration(SLIDE_MS);
        say(R.string.edit_holding);
    }

    private void remove() {
        final Tile tile = held;
        final int index = dock.indexOf(tile);
        held = null;
        mode = Mode.IDLE;
        removing = true;
        say(R.string.edit_idle);
        views.get(tile.key).animate().scaleX(0.4f).scaleY(0.4f).alpha(0f).setInterpolator(ActiveView.OUT).setDuration(REMOVE_MS)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        removing = false;
                        slide(new Runnable() {
                            @Override
                            public void run() {
                                dock.remove(tile);
                                views.remove(tile.key);
                                arrange();
                            }
                        });
                        save();
                        focus(Math.min(index, dock.size() - 1));
                    }
                });
    }

    // --- adding apps

    private void startAdding() {
        available.clear();
        List<String> onDock = packages(dock);
        for (Tile tile : home.catalog().alphabetical()) {
            if (!onDock.contains(tile.packageName())) {
                available.add(tile);
            }
        }
        if (available.isEmpty()) {
            flash(R.string.edit_adding_none);
            return;
        }
        mode = Mode.ADDING;
        for (int i = 0; i < row.getChildCount(); i++) {
            row.getChildAt(i).setFocusable(false);
        }
        say(R.string.edit_adding);
        showAddable(0);
        dash.setVisibility(View.VISIBLE);
        reveal(true);
    }

    /** Fills the apps to add and focuses the one at this place in them. */
    private void showAddable(int focusAt) {
        addableGrid.removeAllViews();
        home.showGrid(addableGrid, available, res.getDimensionPixelSize(R.dimen.tile_height));
        for (Tile tile : available) {
            home.viewOf(tile).jiggle(true);
        }
        if (!available.isEmpty()) {
            home.viewOf(available.get(Math.min(focusAt, available.size() - 1))).requestFocus();
        }
    }

    private void add() {
        View focused = addableGrid.findFocus();
        int at = -1;
        for (int i = 0; i < available.size(); i++) {
            if (home.viewOf(available.get(i)) == focused) {
                at = i;
            }
        }
        if (at < 0) {
            return;
        }
        if (dock.size() >= DockStore.MAX) {
            shake();
            flash(R.string.edit_full);
            return;
        }
        Tile tile = available.remove(at);
        int[] from = new int[2];
        focused.getLocationOnScreen(from);       // where the app is now, in the list
        dock.add(tile);
        TileView view = (TileView) home.tileView(tile);
        view.setFocusable(false);
        view.jiggle(true);
        views.put(tile.key, view);
        arrange();
        flyIn(view, from);
        save();
        showAddable(at);
        if (available.isEmpty()) {
            flash(R.string.edit_adding_none);
            closeAdding();           // nothing left to choose from
        }
    }

    /** The new mark starts where its app was in the list and settles into its slot, as if it had been lifted there. */
    private void flyIn(final TileView view, final int[] from) {
        view.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                view.getViewTreeObserver().removeOnPreDrawListener(this);
                int[] to = new int[2];
                view.getLocationOnScreen(to);
                view.setTranslationX(from[0] - to[0]);
                view.setTranslationY(from[1] - to[1]);
                view.setTranslationZ(1f);
                view.animate().translationX(0f).translationY(0f).setInterpolator(ActiveView.OUT)
                        .setDuration(RESIZE_MS).withEndAction(new ZReset(view));
                return true;
            }
        });
    }

    private void closeAdding() {
        reveal(false);
    }

    /** Opens or closes the apps to add under the dock; the dock stays put and the panel grows upward. */
    private void reveal(final boolean open) {
        final int limit = addableLimit();
        addable.setVisibility(View.VISIBLE);
        addable.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        final int target = Math.min(addable.getMeasuredHeight(), limit);
        final ViewGroup.LayoutParams params = addable.getLayoutParams();
        ValueAnimator grow = ValueAnimator.ofInt(open ? 0 : params.height > 0 ? params.height : target,
                open ? target : 0);
        grow.setDuration(SLIDE_MS);
        grow.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                params.height = (Integer) animation.getAnimatedValue();
                addable.setLayoutParams(params);
            }
        });
        grow.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (!open) {
                    addable.setVisibility(View.GONE);
                    dash.setVisibility(View.GONE);
                    params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                    finishAdding();
                } else {
                    params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                    if (addable.getMeasuredHeight() > limit) {
                        params.height = limit;
                    }
                    addable.setLayoutParams(params);
                }
            }
        });
        grow.start();
    }

    private void finishAdding() {
        mode = Mode.IDLE;
        for (int i = 0; i < row.getChildCount(); i++) {
            row.getChildAt(i).setFocusable(true);
        }
        say(R.string.edit_idle);
        focus(Math.max(0, dock.size() - 1));
    }

    /** How tall the apps to add may be: what is left of the screen under the top bar, less the dock and the line above. */
    private int addableLimit() {
        DisplayMetrics screen = res.getDisplayMetrics();
        int used = res.getDimensionPixelSize(R.dimen.grid_top) + res.getDimensionPixelSize(R.dimen.dock_bottom)
                + res.getDimensionPixelSize(R.dimen.tile_height) + 2 * res.getDimensionPixelSize(R.dimen.dock_padding_v)
                + res.getDimensionPixelSize(R.dimen.banner_height);
        return Math.max(res.getDimensionPixelSize(R.dimen.tile_height), screen.heightPixels - used);
    }

    // --- the row

    private void build() {
        row.removeAllViews();
        views.clear();
        for (Tile tile : dock) {
            TileView view = (TileView) home.tileView(tile);
            views.put(tile.key, view);
        }
        plus = (TileView) home.tileView(plusTile);
        arrange();
    }

    /** Puts the marks in the row in the dock's order, with the + after them while there is room. */
    private void arrange() {
        row.removeAllViews();
        for (int i = 0; i < dock.size(); i++) {
            row.addView(views.get(dock.get(i).key), params(i));
        }
        if (plusShown && dock.size() < DockStore.MAX) {
            row.addView(plus, params(dock.size()));
        }
    }

    private LinearLayout.LayoutParams params(int index) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                res.getDimensionPixelSize(R.dimen.tile_width), res.getDimensionPixelSize(R.dimen.tile_height));
        params.setMarginStart(index == 0 ? 0 : res.getDimensionPixelSize(R.dimen.tile_gap));
        return params;
    }

    /** Runs a change to the row, and slides each mark from where it was to where it now is. */
    private void slide(Runnable change) {
        final Map<View, Integer> before = new HashMap<>();
        for (int i = 0; i < row.getChildCount(); i++) {
            View child = row.getChildAt(i);
            before.put(child, child.getLeft() + Math.round(child.getTranslationX()));
        }
        change.run();
        final ViewTreeObserver observer = row.getViewTreeObserver();
        observer.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                row.getViewTreeObserver().removeOnPreDrawListener(this);
                for (int i = 0; i < row.getChildCount(); i++) {
                    View child = row.getChildAt(i);
                    Integer was = before.get(child);
                    if (was != null && was != child.getLeft()) {
                        child.setTranslationX(was - child.getLeft());
                        child.animate().translationX(0f).setInterpolator(ActiveView.OUT).setDuration(SLIDE_MS);
                    }
                }
                return true;
            }
        });
    }

    private void focus(int index) {
        if (index >= 0 && index < row.getChildCount()) {
            row.getChildAt(index).requestFocus();
        }
    }

    private String focusedKey() {
        int index = row.indexOfChild(row.getFocusedChild());
        return index >= 0 && index < dock.size() ? dock.get(index).key : null;
    }

    private void jiggleAll(boolean on) {
        for (TileView view : views.values()) {
            view.jiggle(on);
        }
    }

    // --- the dock's width

    private int slots(int count) {
        int gap = res.getDimensionPixelSize(R.dimen.tile_gap);
        int width = res.getDimensionPixelSize(R.dimen.tile_width);
        return count * width + (count - 1) * gap + 2 * res.getDimensionPixelSize(R.dimen.dock_padding_h);
    }

    private int fullWidth() {
        return slots(DockStore.MAX);
    }

    private int naturalWidth() {
        return slots(dock.size());
    }

    /** The dock grows from where it is to this width, about its middle, so the marks slide toward the first slots. */
    private void widen(int to) {
        resize(slots(Math.max(1, dock.size())), to, false, new Runnable() {
            @Override
            public void run() {
                if (mode == Mode.OFF) {
                    return;
                }
                plusShown = true;
                arrange();
                plus.setAlpha(0f);
                plus.animate().alpha(1f).setInterpolator(ActiveView.OUT).setDuration(SLIDE_MS);
                if (row.getFocusedChild() == null) {
                    focus(0);
                }
            }
        });
    }

    private void narrow(int to, final List<String> order, final String focusKey) {
        for (int i = 0; i < dock.size(); i++) {
            row.addView(views.get(dock.get(i).key), params(i));
        }
        resize(Math.max(row.getWidth(), to), to, true, new Runnable() {
            @Override
            public void run() {
                row.setMinimumWidth(0);
                home.finishEditing(order, focusKey);
            }
        });
    }

    /**
     * Changes the width of the dock's row, which is centred, so its edges move half the change each. A mark is left of
     * where the layout alone would put it by as much as it lags: each follows the one before it, a tenth of the way
     * behind, as if the marks queued. When the dock is closing, the last mark goes first.
     */
    private void resize(final int from, final int to, final boolean lastFirst, final Runnable done) {
        final int count = dock.size();
        final float half = (to - from) / 2f;
        final float span = Math.max(0.1f, 1f - STAGGER * (count - 1));
        if (resizing != null) {
            resizing.removeAllListeners();   // the new one carries on from where this one got to
            resizing.cancel();
        }
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        resizing = animator;
        animator.setDuration(RESIZE_MS);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float fraction = animation.getAnimatedFraction();
                float eased = ActiveView.OUT.getInterpolation(fraction);
                row.setMinimumWidth(from + Math.round((to - from) * eased));
                for (int i = 0; i < count; i++) {
                    int rank = lastFirst ? count - 1 - i : i;
                    float own = ActiveView.OUT.getInterpolation(Math.max(0f, Math.min(1f, (fraction - rank * STAGGER) / span)));
                    View mark = views.get(dock.get(i).key);
                    if (mark != null) {
                        mark.setTranslationX(half * (eased - own));
                    }
                }
            }
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                for (TileView mark : views.values()) {
                    mark.setTranslationX(0f);
                }
                if (done != null) {
                    done.run();
                }
            }
        });
        animator.start();
    }

    // --- the line above the dock, and the shake

    private void say(int instructions) {
        hint = instructions;
        handler.removeCallbacks(restoreHint);
        banner.setText(instructions);
    }

    /** A message for a moment, in place of the instructions. */
    private void flash(int message) {
        handler.removeCallbacks(restoreHint);
        banner.setText(message);
        handler.postDelayed(restoreHint, MESSAGE_MS);
    }

    private void shake() {
        float reach = res.getDimension(R.dimen.shake);
        ObjectAnimator.ofFloat(panel, View.TRANSLATION_X, 0f, -reach, reach, -reach * 0.8f, reach * 0.8f,
                -reach * 0.4f, reach * 0.4f, 0f).setDuration(SHAKE_MS).start();
    }

    // --- saving

    private void save() {
        store.save(packages(dock));
    }

    private static List<String> packages(List<Tile> tiles) {
        List<String> packages = new ArrayList<>();
        for (Tile tile : tiles) {
            packages.add(tile.packageName());
        }
        return packages;
    }

    private static final Comparator<Tile> BY_NAME = new Comparator<Tile>() {
        @Override
        public int compare(Tile a, Tile b) {
            return a.label.compareToIgnoreCase(b.label);
        }
    };
}
