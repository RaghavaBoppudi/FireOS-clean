package com.firehome;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

/**
 * The Wi-Fi glyph as Android 5 drew it: a solid wedge, dimmed, with the same wedge filled from its point out to the
 * signal.
 */
final class WifiSignal {
    static final int LEVELS = 5;   // 0 (none) to 4 (full): the wedge fills in four steps

    private static final int[] FILLED = {0, R.drawable.ic_network_1, R.drawable.ic_network_2, R.drawable.ic_network_3,
            R.drawable.ic_network_4};
    private static final int DIMMED_ALPHA = 80;   // out of 255

    private WifiSignal() {
    }

    /** The current signal, 0 to 4; 0 when Wi-Fi is off or not connected (an Ethernet stick shows an empty fan). */
    static int level(Context context) {
        WifiManager wifi = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        WifiInfo info = wifi != null && wifi.isWifiEnabled() ? wifi.getConnectionInfo() : null;
        if (info == null || info.getNetworkId() < 0) {
            return 0;
        }
        return WifiManager.calculateSignalLevel(info.getRssi(), LEVELS);
    }

    static Drawable glyph(Context context, int level) {
        Drawable base = context.getResources().getDrawable(R.drawable.ic_network_4).mutate();
        base.setAlpha(DIMMED_ALPHA);
        if (level <= 0) {
            return base;
        }
        Drawable filled = context.getResources().getDrawable(FILLED[Math.min(level, LEVELS - 1)]).mutate();
        return new LayerDrawable(new Drawable[]{base, filled});
    }
}
