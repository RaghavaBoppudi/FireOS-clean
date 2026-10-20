package com.firehome;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
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
    private Thread reader;

    /** Leave Amazon's home alone for a while, for the "Fire TV home" button. */
    static void pause(long millis) {
        pausedUntil = SystemClock.elapsedRealtime() + millis;
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
        while (true) {
            Process logcat = null;
            try {
                // -T 1 starts at the newest line, so Home presses already in the buffer are not replayed
                logcat = new ProcessBuilder("logcat", "-v", "brief", "-T", "1", "ActivityManager:I", "*:S")
                        .redirectErrorStream(true).start();
                BufferedReader lines = new BufferedReader(new InputStreamReader(logcat.getInputStream()));
                for (String line; (line = lines.readLine()) != null; ) {
                    if (line.contains("android.intent.category.HOME")
                            && line.contains("cmp=" + HomeActivity.AMAZON_HOME + "/")) {
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

    private void amazonHomeStarted() {
        long now = SystemClock.elapsedRealtime();
        if (now < pausedUntil || now - lastLaunch < 1500) {
            return;
        }
        lastLaunch = now;
        startActivity(new Intent(this, HomeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
