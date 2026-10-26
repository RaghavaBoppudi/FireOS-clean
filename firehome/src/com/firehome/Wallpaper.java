package com.firehome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TimeZone;

/**
 * The picture behind the home, when the setting is on. It is one of the wallpapers downloaded from the GitHub
 * repository (see RemoteWallpapers), or, with none, one of the dunes in assets/backgrounds. They are shown in turn, in the
 * order of their names, each for as long as the shuffle setting says (from 30 seconds to a day, the day's turn starting
 * at local midnight); which one is on is worked out from the clock, so every screen shows the same. Two copies are kept: the picture itself,
 * cut to the screen's shape and dimmed a little, and one blurred until it is only soft fields of colour, which the discs show as glass.
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
    private static final String PREFERENCES = "wallpaper";
    private static final String KEY_SHUFFLE = "shuffle";
    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;
    private static final int SOFT_WIDTH = 240;    // halved down to this, then blurred
    private static final int BLUR_RADIUS = 5;     // pixels of that small picture: about 40 on the screen
    private static final int BLUR_PASSES = 3;     // three passes of a box blur make a smooth, Gaussian blur
    private static final int DIM = Color.argb(31, 0, 0, 0);    // a little black over every picture, so white text reads

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static List<Photo> photos;     // null until listed, and again after a sync
    private static Photo current;
    private static Bitmap sharp;
    private static Bitmap soft;
    // the picture before the one on show, kept while the screens fade from one to the other; mix is how far they are
    private static Bitmap beforeSharp;
    private static Bitmap beforeSoft;
    private static volatile float mix = 1f;

    private Wallpaper() {
    }

    static synchronized Bitmap sharp() {
        return sharp;
    }

    static synchronized Bitmap soft() {
        return soft;
    }

    static synchronized Bitmap beforeSharp() {
        return beforeSharp;
    }

    static synchronized Bitmap beforeSoft() {
        return beforeSoft;
    }

    /** How far the fade from the picture before to the one on show has come: 0 is all before, 1 all now. */
    static float mix() {
        return mix;
    }

    static void setMix(float value) {
        mix = value;
    }

    /** The fade is over: the picture before is let go. */
    static synchronized void settle() {
        beforeSharp = null;
        beforeSoft = null;
        mix = 1f;
    }

    /** The photographer of the picture on show, or null. */
    static synchronized String credit() {
        return current != null ? current.credit : null;
    }

    /** A sync has changed what is downloaded: list the wallpapers again at the next load. */
    static synchronized void stale() {
        photos = null;
    }

    /**
     * How light the picture is in a part of the screen, 0 to 1, as the blurred copy has it; the part is given as shares of
     * the screen's width and height. While the picture changes it is the two pictures' lightness, as far as the fade has
     * come. -1 if there is no picture.
     */
    static synchronized float lightness(float left, float top, float right, float bottom) {
        if (soft == null) {
            return -1f;
        }
        float now = lightness(soft, left, top, right, bottom);
        if (beforeSoft == null || mix >= 1f) {
            return now;
        }
        return lightness(beforeSoft, left, top, right, bottom) * (1f - mix) + now * mix;
    }

    private static float lightness(Bitmap picture, float left, float top, float right, float bottom) {
        int w = picture.getWidth();
        int h = picture.getHeight();
        int x0 = Math.min(w - 1, Math.max(0, (int) (left * w)));
        int x1 = Math.min(w - 1, Math.max(x0, (int) (right * w)));
        int y0 = Math.min(h - 1, Math.max(0, (int) (top * h)));
        int y1 = Math.min(h - 1, Math.max(y0, (int) (bottom * h)));
        float sum = 0;
        int count = 0;
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                int pixel = picture.getPixel(x, y);
                sum += 0.2126f * ((pixel >> 16) & 0xFF) + 0.7152f * ((pixel >> 8) & 0xFF) + 0.0722f * (pixel & 0xFF);
                count++;
            }
        }
        return sum / (255f * count);
    }

    // --- how often it changes

    /** The wallpapers in turn, in the order of their names; this is how long each is shown. */
    static long intervalMs(Context context) {
        return context.getResources().getIntArray(R.array.shuffle_seconds)[shuffleIndex(context)] * 1000L;
    }

    /** The choice, in words, for the caption under its setting. */
    static String shuffleCaption(Context context) {
        return context.getResources().getStringArray(R.array.shuffle_captions)[shuffleIndex(context)];
    }

    /** Moves to the next choice; after the last it wraps round to the first. */
    static void nextShuffle(Context context) {
        int count = context.getResources().getIntArray(R.array.shuffle_seconds).length;
        prefs(context).edit().putInt(KEY_SHUFFLE, (shuffleIndex(context) + 1) % count).apply();
    }

    private static int shuffleIndex(Context context) {
        int count = context.getResources().getIntArray(R.array.shuffle_seconds).length;
        int saved = prefs(context).getInt(KEY_SHUFFLE, context.getResources().getInteger(R.integer.shuffle_default_index));
        return saved >= 0 && saved < count ? saved : 0;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    /** The turn of the wallpapers now: every interval it goes up by one, and a day's turns start at local midnight. */
    private static long turn(Context context, long now) {
        return (now + TimeZone.getDefault().getOffset(now)) / intervalMs(context);
    }

    /** How long until the wallpaper changes. */
    static long untilNextMs(Context context) {
        long now = System.currentTimeMillis();
        long interval = intervalMs(context);
        return interval - (now + TimeZone.getDefault().getOffset(now)) % interval;
    }

    // --- loading

    /** The wallpaper for now, or null if there is none or the setting is off. */
    private static synchronized Photo pick(Context context) {
        if (!Look.wallpaper(context)) {
            return null;
        }
        if (photos == null) {
            photos = list(context);
        }
        if (photos.isEmpty()) {
            return null;
        }
        return photos.get((int) (turn(context, System.currentTimeMillis()) % photos.size()));
    }

    /** Makes the pictures for now, or lets them go if the setting is off. Call on the main thread. */
    static synchronized void load(Context context) {
        Photo next = pick(context);
        if (next == null) {
            if (!Look.wallpaper(context)) {
                current = null;
                sharp = null;
                soft = null;
            }
            return;
        }
        if (next == current && sharp != null) {
            return;
        }
        try {
            install(next, make(context, next));
        } catch (IOException | RuntimeException e) {
            fail();
        }
    }

    /**
     * Makes the pictures for now on another thread, which a decoding of this size needs to be kept from the screen, and
     * then, on the main thread, puts them in the place of the ones on show (which become the picture before) and calls
     * {@code done}. {@code done} is called whether or not anything changed.
     */
    static void loadLater(Context context, final Runnable done) {
        final Context app = context.getApplicationContext();
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
                final Photo next = pick(app);
                Bitmap[] made = null;
                boolean needed;
                synchronized (Wallpaper.class) {
                    needed = next != null && (next != current || sharp == null);
                }
                if (needed) {
                    try {
                        made = make(app, next);
                    } catch (IOException | RuntimeException | OutOfMemoryError e) {
                        made = null;
                    }
                }
                final Bitmap[] pictures = made;
                MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        synchronized (Wallpaper.class) {
                            if (pictures != null && Look.wallpaper(app)) {
                                install(next, pictures);
                            }
                        }
                        done.run();
                    }
                });
            }
        }, "wallpaper-load");
        thread.setDaemon(true);
        thread.start();
    }

    /** Puts the new pictures on show; the ones that were become the picture before. */
    private static void install(Photo next, Bitmap[] pictures) {
        beforeSharp = sharp;
        beforeSoft = soft;
        mix = beforeSharp != null ? 0f : 1f;
        sharp = pictures[0];
        soft = pictures[1];
        current = next;
    }

    private static void fail() {
        photos = null;    // a file that will not decode: list again, and go without a picture meanwhile
        current = null;
        sharp = null;
        soft = null;
    }

    /** The picture and, blurred until it is only soft fields of colour, its copy for the discs. */
    private static Bitmap[] make(Context context, Photo photo) throws IOException {
        Bitmap picture = decode(context, photo);
        Bitmap blur = picture;
        for (int w = WIDTH / 2, h = HEIGHT / 2; w >= SOFT_WIDTH; w /= 2, h /= 2) {
            blur = Bitmap.createScaledBitmap(blur, w, h, true);    // each halving averages 2 x 2 pixels
        }
        return new Bitmap[]{picture, blurred(blur)};
    }

    /**
     * An intense, smooth blur of a small picture, which the screen then stretches up: without it the discs show the
     * stretched pixels as blotches. Box blurs along the rows and the columns, three times over.
     */
    private static Bitmap blurred(Bitmap small) {
        int w = small.getWidth();
        int h = small.getHeight();
        int[] pixels = new int[w * h];
        small.getPixels(pixels, 0, w, 0, 0, w, h);
        int[] scratch = new int[w * h];
        for (int pass = 0; pass < BLUR_PASSES; pass++) {
            boxBlur(pixels, scratch, h, w, w, 1, BLUR_RADIUS);       // along each row: h of them, w long
            boxBlur(scratch, pixels, w, 1, h, w, BLUR_RADIUS);       // along each column: w of them, h long
        }
        Bitmap result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        result.setPixels(pixels, 0, w, 0, 0, w, h);
        return result;
    }

    /**
     * Averages each pixel with the {@code radius} either side of it along a line (the edge pixel standing in beyond the
     * ends). There are {@code lines} lines, {@code lineStep} apart, each {@code length} pixels long with its pixels
     * {@code step} apart.
     */
    private static void boxBlur(int[] from, int[] to, int lines, int lineStep, int length, int step, int radius) {
        int window = 2 * radius + 1;
        for (int line = 0; line < lines; line++) {
            int start = line * lineStep;
            int a = 0;
            int r = 0;
            int g = 0;
            int b = 0;
            for (int i = -radius; i <= radius; i++) {
                int pixel = from[start + Math.min(Math.max(i, 0), length - 1) * step];
                a += pixel >>> 24;
                r += (pixel >> 16) & 0xFF;
                g += (pixel >> 8) & 0xFF;
                b += pixel & 0xFF;
            }
            for (int i = 0; i < length; i++) {
                to[start + i * step] = ((a / window) << 24) | ((r / window) << 16) | ((g / window) << 8) | (b / window);
                int out = from[start + Math.max(i - radius, 0) * step];
                int in = from[start + Math.min(i + radius + 1, length - 1) * step];
                a += (in >>> 24) - (out >>> 24);
                r += ((in >> 16) & 0xFF) - ((out >> 16) & 0xFF);
                g += ((in >> 8) & 0xFF) - ((out >> 8) & 0xFF);
                b += (in & 0xFF) - (out & 0xFF);
            }
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

    /** The picture cut to the screen's 16:9, scaled to it, and dimmed. Both copies are made from it, so they match. */
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
        Bitmap dimmed = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(dimmed);
        canvas.drawBitmap(cut, null, new Rect(0, 0, WIDTH, HEIGHT), new Paint(Paint.FILTER_BITMAP_FLAG));
        canvas.drawColor(DIM);
        return dimmed;
    }
}
