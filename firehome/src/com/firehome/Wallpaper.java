package com.firehome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The picture behind the home, when the setting is on. It is one of the wallpapers downloaded from the GitHub
 * repository (see RemoteWallpapers), or, with none, one of the dunes in assets/backgrounds; which one is chosen by the
 * time of day, so it changes every half hour and every screen shows the same. Two copies are kept: the picture itself,
 * cut to the screen's shape, and one blurred until it is only soft fields of colour, which the discs show as glass.
 * Both cover the whole screen, so a disc can show the part of the soft one that lies behind it.
 */
final class Wallpaper {
    /** A wallpaper: a downloaded file, or (with no file) a bundled one named in assets/backgrounds. */
    static final class Photo {
        final String name;
        final File file;
        final String credit;

        Photo(String name, File file, String credit) {
            this.name = name;
            this.file = file;
            this.credit = credit;
        }
    }

    private static final String FOLDER = "backgrounds";
    private static final long CHANGE_MS = 30 * 60 * 1000L;
    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;
    private static final int SOFT_WIDTH = 120;    // halved down to this: the blur

    private static List<Photo> photos;     // null until listed, and again after a sync
    private static Photo current;
    private static Bitmap sharp;
    private static Bitmap soft;

    private Wallpaper() {
    }

    static synchronized Bitmap sharp() {
        return sharp;
    }

    static synchronized Bitmap soft() {
        return soft;
    }

    /** The photographer of the picture on show, or null. */
    static synchronized String credit() {
        return current != null ? current.credit : null;
    }

    /** A sync has changed what is downloaded: list the wallpapers again at the next load. */
    static synchronized void stale() {
        photos = null;
    }

    /** Makes the pictures for now, or lets them go if the setting is off. Call on the main thread. */
    static synchronized void load(Context context) {
        if (!Look.wallpaper(context)) {
            current = null;
            sharp = null;
            soft = null;
            return;
        }
        if (photos == null) {
            photos = list(context);
        }
        if (photos.isEmpty()) {
            return;
        }
        Photo next = photos.get((int) (System.currentTimeMillis() / CHANGE_MS % photos.size()));
        if (next == current && sharp != null) {
            return;
        }
        try {
            sharp = decode(context, next);
            Bitmap blur = sharp;
            for (int w = WIDTH / 2, h = HEIGHT / 2; w >= SOFT_WIDTH; w /= 2, h /= 2) {
                blur = Bitmap.createScaledBitmap(blur, w, h, true);    // each halving averages 2 x 2 pixels
            }
            soft = blur;
            current = next;
        } catch (IOException | RuntimeException e) {
            photos = null;    // a file that will not decode: list again, and go without a picture meanwhile
            current = null;
            sharp = null;
            soft = null;
        }
    }

    private static List<Photo> list(Context context) {
        List<Photo> list = RemoteWallpapers.cached(context);
        if (list.isEmpty()) {
            try {
                String[] names = context.getAssets().list(FOLDER);
                Arrays.sort(names);
                for (String name : names) {
                    list.add(new Photo(name, null, null));
                }
            } catch (IOException e) {
                list = new ArrayList<>();
            }
        }
        return list;
    }

    /** The picture cut to the screen's 16:9 and scaled to it. */
    private static Bitmap decode(Context context, Photo photo) throws IOException {
        Bitmap full;
        if (photo.file != null) {
            full = BitmapFactory.decodeFile(photo.file.getPath());
        } else {
            try (InputStream stream = context.getAssets().open(FOLDER + "/" + photo.name)) {
                full = BitmapFactory.decodeStream(stream);
            }
        }
        if (full == null) {
            throw new IOException("not a picture");
        }
        int w = Math.min(full.getWidth(), full.getHeight() * WIDTH / HEIGHT);
        int h = w * HEIGHT / WIDTH;
        Bitmap cut = Bitmap.createBitmap(full, (full.getWidth() - w) / 2, (full.getHeight() - h) / 2, w, h);
        return Bitmap.createScaledBitmap(cut, WIDTH, HEIGHT, true);
    }
}
