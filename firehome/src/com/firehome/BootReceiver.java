package com.firehome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Amazon's home comes up at boot before the watcher is running, so open this one straight away. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        context.startService(new Intent(context, HomeWatcher.class));
        context.startActivity(new Intent(context, HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
}
