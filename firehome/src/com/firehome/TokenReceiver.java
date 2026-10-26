package com.firehome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Receives the GitHub access token for a private wallpaper repository from install.sh, which sends it over ADB:
 * {@code am broadcast -n com.firehome/.TokenReceiver --es token ...}. The manifest requires the DUMP permission to send
 * to this receiver, which the ADB shell holds and other apps cannot, so nothing else on the TV can set or clear it.
 * The token is stored in the app's private storage, not in the APK (which every app can read) and not in git.
 */
public class TokenReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String token = intent.getStringExtra("token");
        if (token != null) {
            RemoteWallpapers.setToken(context, token.trim());
        }
    }
}
