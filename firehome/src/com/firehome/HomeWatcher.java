package com.firehome;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Fire OS sends Home to com.amazon.tv.launcher whatever else is installed, and silently drops any accessibility
 * service ADB tries to switch on. It does let ADB grant READ_LOGS, and ActivityManager logs every Home press as
 * "START u0 {... cat=[android.intent.category.HOME] ... cmp=com.amazon.tv.launcher/...}". This service follows
 * the log and puts HomeActivity on top each time.
 */
public class HomeWatcher extends Service {
    private static long pausedUntil;
    private static long lastLaunch;
    private static HomeWatcher running;
    private final Handler main = new Handler(Looper.getMainLooper());
    private LaunchOverlay overlay;
    private Thread reader;

    /** Leave Amazon's home alone for a while, for the "Fire TV home" button. */
    static void pause(long millis) {
        pausedUntil = SystemClock.elapsedRealtime() + millis;
    }

    /** HomeActivity is on screen: the "Launching Home" cover, if one is up, can come down. */
    static void homeShown() {
        final HomeWatcher watcher = running;
        if (watcher != null) {
            watcher.main.post(new Runnable() {
                @Override
                public void run() {
                    watcher.overlay.dismiss();
                }
            });
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        running = this;
        overlay = new LaunchOverlay(this, main);
    }

    @Override
    public void onDestroy() {
        running = null;
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (reader == null) {
            // foreground, so the low-memory killer on a 1 GB stick leaves the watcher running
            startForeground(1, new Notification.Builder(this)
                    .setSmallIcon(R.drawable.icon)
                    .setContentTitle(getString(R.string.app_name))
                    .setPriority(Notification.PRIORITY_MIN)
                    .build());
            reader = new Thread(this::follow, "home-watcher");
            reader.setDaemon(true);
            reader.start();
        }
        return START_STICKY;
    }

    private void follow() {
        catchUp();
        while (true) {
            Process logcat = null;
            try {
                // -T 1 starts at the newest line, so Home presses already in the buffer are not replayed
                logcat = new ProcessBuilder("logcat", "-v", "brief", "-T", "1", "ActivityManager:I", "*:S")
                        .redirectErrorStream(true).start();
                BufferedReader lines = new BufferedReader(new InputStreamReader(logcat.getInputStream()));
                for (String line; (line = lines.readLine()) != null; ) {
                    if (isAmazonHome(line)) {
                        amazonHomeStarted();
                    }
                }
            } catch (Exception e) {
                // logcat ended or could not start; try again below
            } finally {
                if (logcat != null) {
                    logcat.destroy();
                }
            }
            SystemClock.sleep(2000);
        }
    }

    /**
     * At boot, or when the system restarts this service, Amazon's home may already be on screen. Only take over
     * if it was the last thing started, so a video someone opened in the meantime is left alone.
     */
    private void catchUp() {
        String last = null;
        try {
            Process dump = new ProcessBuilder("logcat", "-d", "-v", "brief", "ActivityManager:I", "*:S")
                    .redirectErrorStream(true).start();
            BufferedReader lines = new BufferedReader(new InputStreamReader(dump.getInputStream()));
            for (String line; (line = lines.readLine()) != null; ) {
                if (line.contains("START u0")) {
                    last = line;
                }
            }
            dump.waitFor();
        } catch (Exception e) {
            return;
        }
        if (last != null && isAmazonHome(last)) {
            amazonHomeStarted();
        }
    }

    private static boolean isAmazonHome(String line) {
        return line.contains("android.intent.category.HOME")
                && line.contains("cmp=" + HomeActivity.AMAZON_HOME + "/");
    }

    private void amazonHomeStarted() {
        long now = SystemClock.elapsedRealtime();
        if (now < pausedUntil || now - lastLaunch < 1500) {
            return;
        }
        lastLaunch = now;
        // cover Amazon's home first: its window is not drawn yet when its start shows up in the log
        main.post(new Runnable() {
            @Override
            public void run() {
                overlay.show();
            }
        });
        startHome();
    }

    /**
     * For five seconds after the Home button is pressed Android refuses to start a screen on behalf of an ordinary app
     * ("Activity start request ... stopped"), so that nothing takes the screen from the home it is about to show. Only
     * system callers are exempt. A pending intent fired by an alarm is started by the system on the app's behalf, which
     * is allowed at once, so ask for one that is due now. An alarm-clock alarm is used because the system fires those
     * immediately; an ordinary exact alarm was sometimes held back for the same five seconds.
     */
    private void startHome() {
        Intent home = new Intent(this, HomeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        PendingIntent pending = PendingIntent.getActivity(this, 0, home, PendingIntent.FLAG_UPDATE_CURRENT);
        AlarmManager alarms = (AlarmManager) getSystemService(ALARM_SERVICE);
        alarms.setAlarmClock(new AlarmManager.AlarmClockInfo(System.currentTimeMillis(), pending), pending);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
