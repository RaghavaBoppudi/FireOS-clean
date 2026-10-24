package com.firehome;

import android.content.Intent;
import android.service.notification.NotificationListenerService;

/**
 * The system binds a notification listener itself as soon as the notification service is up, well before
 * BOOT_COMPLETED reaches this app's receiver (Fire OS works through its boot receivers one by one, and this
 * app's turn came about 25 seconds after Amazon's home was on screen). install.sh allows it with
 * `cmd notification allow_listener`; it never reads a notification and only starts the watcher.
 */
public class BootListener extends NotificationListenerService {
    @Override
    public void onListenerConnected() {
        startService(new Intent(this, HomeWatcher.class));
    }
}
