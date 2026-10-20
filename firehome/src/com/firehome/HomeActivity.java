package com.firehome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

/** A home screen with one thing on it, YouTube. HomeWatcher brings it up whenever Amazon's home is started. */
public class HomeActivity extends Activity {
    static final String YOUTUBE = "com.amazon.firetv.youtube";
    static final String AMAZON_HOME = "com.amazon.tv.launcher";
    // "Fire TV home" starts Amazon's home the way a Home press does, so the watcher stands down this long
    private static final long AMAZON_PAUSE_MS = 10 * 60 * 1000L;

    private float dp;
    private View youtube;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        dp = getResources().getDisplayMetrics().density;
        startService(new Intent(this, HomeWatcher.class));

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);

        LinearLayout corner = new LinearLayout(this);
        corner.setOrientation(LinearLayout.VERTICAL);
        TextClock time = new TextClock(this);
        time.setFormat12Hour("h:mm");
        time.setFormat24Hour("HH:mm");
        style(time, 72, 0xFFFFFFFF, "sans-serif-thin");
        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEEE, d MMMM");
        date.setFormat24Hour("EEEE, d MMMM");
        style(date, 20, 0x99FFFFFF, "sans-serif-light");
        corner.addView(time);
        corner.addView(date);
        root.addView(corner, at(Gravity.TOP | Gravity.START, 56, 40));

        youtube = tile();
        root.addView(youtube, new FrameLayout.LayoutParams(px(400), px(225), Gravity.CENTER));

        LinearLayout bottom = new LinearLayout(this);
        bottom.addView(pill("Settings", v -> openSettings()));
        bottom.addView(pill("Fire TV home", v -> openAmazonHome()));
        root.addView(bottom, at(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 40));

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        HomeWatcher.pause(0);   // back from Settings or Amazon's home: Home brings this screen up again
        youtube.requestFocus();
    }

    @Override
    public void onBackPressed() {
        // this is Home: Back has nowhere to go
    }

    private View tile() {
        ImageView art = new ImageView(this);
        GradientDrawable card = new GradientDrawable();
        card.setColor(0xFF161616);
        card.setCornerRadius(px(14));
        art.setBackground(card);
        art.setClipToOutline(true);
        PackageManager pm = getPackageManager();
        try {
            Drawable banner = pm.getApplicationBanner(YOUTUBE);
            if (banner != null) {
                art.setScaleType(ImageView.ScaleType.CENTER_CROP);
                art.setImageDrawable(banner);
            } else {
                art.setScaleType(ImageView.ScaleType.FIT_CENTER);
                art.setPadding(px(60), px(40), px(60), px(40));
                art.setImageDrawable(pm.getApplicationIcon(YOUTUBE));
            }
        } catch (PackageManager.NameNotFoundException e) {
            // YouTube is not installed: an empty card, and the click says so
        }

        // YouTube's banner is white, so a white focus ring would vanish into it: focus lifts the tile, and the
        // tile dims while a button below has focus
        art.setFocusable(true);
        art.setOnClickListener(v -> openYouTube());
        art.setOnFocusChangeListener((v, focused) -> {
            float scale = focused ? 1.06f : 1f;
            v.animate().scaleX(scale).scaleY(scale).alpha(focused ? 1f : 0.45f).setDuration(150).start();
        });
        return art;
    }

    private TextView pill(String label, View.OnClickListener click) {
        TextView pill = new TextView(this);
        style(pill, 18, 0xCCFFFFFF, "sans-serif");
        pill.setText(label);
        pill.setPadding(px(26), px(10), px(26), px(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(px(24));
        bg.setColor(0x22FFFFFF);
        pill.setBackground(bg);
        pill.setFocusable(true);
        pill.setOnClickListener(click);
        pill.setOnFocusChangeListener((v, focused) -> {
            bg.setColor(focused ? 0xFFFFFFFF : 0x22FFFFFF);
            pill.setTextColor(focused ? 0xFF000000 : 0xCCFFFFFF);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(px(10), 0, px(10), 0);
        pill.setLayoutParams(lp);
        return pill;
    }

    private void openYouTube() {
        PackageManager pm = getPackageManager();
        Intent intent = pm.getLeanbackLaunchIntentForPackage(YOUTUBE);
        if (intent == null) {
            intent = pm.getLaunchIntentForPackage(YOUTUBE);
        }
        launch(intent, "YouTube isn't installed. Get it from the Appstore under Fire TV home.");
    }

    private void openSettings() {
        // Fire OS answers ACTION_SETTINGS with the launcher's Settings grid; not a Home start, so no pause needed
        launch(new Intent(Settings.ACTION_SETTINGS), "Couldn't open Settings.");
    }

    private void openAmazonHome() {
        HomeWatcher.pause(AMAZON_PAUSE_MS);
        Toast.makeText(this, "Amazon's home for the next 10 minutes", Toast.LENGTH_LONG).show();
        launch(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(AMAZON_HOME),
                "Couldn't open Amazon's home.");
    }

    private void launch(Intent intent, String failure) {
        try {
            if (intent == null) {
                throw new ActivityNotFoundException();
            }
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException e) {
            HomeWatcher.pause(0);
            Toast.makeText(this, failure, Toast.LENGTH_LONG).show();
        }
    }

    private void style(TextView view, float sp, int color, String family) {
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setTypeface(Typeface.create(family, Typeface.NORMAL));
    }

    private FrameLayout.LayoutParams at(int gravity, int horizontalDp, int verticalDp) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, gravity);
        lp.setMargins(px(horizontalDp), px(verticalDp), px(horizontalDp), px(verticalDp));
        return lp;
    }

    private int px(int dpValue) {
        return Math.round(dpValue * dp);
    }
}
