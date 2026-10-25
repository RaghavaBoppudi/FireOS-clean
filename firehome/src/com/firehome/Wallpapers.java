package com.firehome;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The background wallpapers: which one is showing now, and the decoded pictures. The bundled ones are listed in
 * assets/backgrounds/index.json. Which photo shows is a pure function of the clock and the chosen
 * interval, so every screen agrees and nothing has to be remembered between runs.
 */
final class Wallpapers {
    private static final String DIRECTORY = "backgrounds";
    private static final String PREFERENCES = "wallpapers";
    private static final String KEY_INTERVAL = "interval_seconds";
    private static final int BLUR_WIDTH = 160;
    private static final int BLUR_RADIUS = 3;
    private static final int BLUR_PASSES = 3;
    private static final int CACHED = 3;

    /** A wallpaper. Bundled ones are assets; downloaded ones are files in the cache. */
    static final class Photo {
        final String file;       // asset name, or the cache file's name: also the key of its decoded frame
        final File local;        // null for a bundled asset
        final String credit;     // the photographer, or null for none

        Photo(String file, File local, String credit) {
            this.file = file;
            this.local = local;
            this.credit = credit;
        }

        /** The same wallpaper is the same file: a sync builds new objects for files that did not change. */
        @Override
        public boolean equals(Object other) {
            return other instanceof Photo && ((Photo) other).file.equals(file);
        }

        @Override
        public int hashCode() {
            return file.hashCode();
        }
    }

    /** A photo ready to draw: the picture, and a blurred small copy of it for frosted glass to show. */
    static final class Frame {
        final Photo photo;
        final Bitmap image;
        final Bitmap blur;
        private BitmapShader blurShader;

        Frame(Photo photo, Bitmap image, Bitmap blur) {
            this.photo = photo;
            this.image = image;
            this.blur = blur;
        }

        BitmapShader blurShader() {
            if (blurShader == null) {
                blurShader = new BitmapShader(blur, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            }
            return blurShader;
        }
    }

    interface Callback {
        void onFrame(Frame frame);
    }

    private static Wallpapers instance;

    private final Context context;
    private final SharedPreferences preferences;
    private final long[] intervalsMs;
    private final List<Photo> bundled = new ArrayList<>();
    private volatile List<Photo> photos = bundled;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();   // one per screen showing the wallpaper
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService decoder = Executors.newSingleThreadExecutor();
    private final Map<String, Frame> cache = new LinkedHashMap<String, Frame>(CACHED + 1, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Frame> eldest) {
            return size() > CACHED;
        }
    };

    static synchronized Wallpapers get(Context context) {
        if (instance == null) {
            instance = new Wallpapers(context.getApplicationContext());
        }
        return instance;
    }

    private Wallpapers(Context context) {
        this.context = context;
        preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        int[] seconds = context.getResources().getIntArray(R.array.interval_seconds);
        intervalsMs = new long[seconds.length];
        for (int i = 0; i < seconds.length; i++) {
            intervalsMs[i] = seconds[i] * 1000L;
        }
        try {
            JSONArray list = new JSONArray(read(DIRECTORY + "/index.json"));
            for (int i = 0; i < list.length(); i++) {
                bundled.add(new Photo(list.getString(i), null, null));   // the bundled gradients need no credit
            }
        } catch (IOException | JSONException e) {
            bundled.clear();   // no wallpapers: screens stay on the plain background
        }
        reload();
    }

    // --- where the wallpapers come from ---

    /**
     * Picks the list in use: the downloaded wallpapers (see RemoteWallpapers) if there is at least one, otherwise the
     * ones bundled in the app. Tells the screen if the choice changed.
     */
    void reload() {
        List<Photo> next = RemoteWallpapers.cached(context);
        if (next.isEmpty()) {
            next = bundled;
        }
        boolean changed = next != photos && !sameFiles(next, photos);
        photos = next;
        if (changed) {
            for (final Runnable listener : listeners) {
                main.post(listener);
            }
        }
    }

    /** Runs the listener on the main thread whenever the list in use changes. A screen adds one while it shows. */
    void addOnChanged(Runnable listener) {
        listeners.add(listener);
    }

    void removeOnChanged(Runnable listener) {
        listeners.remove(listener);
    }

    private static boolean sameFiles(List<Photo> a, List<Photo> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!a.get(i).file.equals(b.get(i).file)) {
                return false;
            }
        }
        return true;
    }

    // --- the interval ---

    /** Index of the interval in use. Saved as seconds, so a saved choice survives the list being reordered. */
    int interval() {
        int seconds = preferences.getInt(KEY_INTERVAL,
                context.getResources().getInteger(R.integer.interval_default_seconds));
        for (int i = 0; i < intervalsMs.length; i++) {
            if (intervalsMs[i] == seconds * 1000L) {
                return i;
            }
        }
        return 0;
    }

    /** The interval in a few characters, for a tile. */
    String intervalBadge() {
        return context.getResources().getStringArray(R.array.interval_badges)[interval()];
    }

    /** Moves to the next interval in the list; after the longest it wraps round to the shortest. */
    void nextInterval() {
        preferences.edit().putInt(KEY_INTERVAL, (int) (intervalsMs[(interval() + 1) % intervalsMs.length] / 1000)).apply();
    }

    // --- which photo ---

    /** The photo for this moment: the list in order, one step per interval. Null if there are no photos. */
    Photo photoAt(long now) {
        List<Photo> list = photos;
        if (list.isEmpty()) {
            return null;
        }
        return list.get((int) (slot(now) % list.size()));
    }

    /** Milliseconds until the photo changes. */
    long untilNext(long now) {
        long interval = intervalsMs[interval()];
        return interval - localTime(now) % interval;
    }

    private long slot(long now) {
        return localTime(now) / intervalsMs[interval()];
    }

    /** Local time, so that "daily" changes at midnight where the TV is rather than at midnight UTC. */
    private static long localTime(long now) {
        return now + TimeZone.getDefault().getOffset(now);
    }

    // --- the pictures ---

    /** Delivers the photo's frame on the main thread, from the cache if it is there. */
    void load(final Photo photo, final Callback callback) {
        Frame cached;
        synchronized (cache) {
            cached = cache.get(photo.file);
        }
        if (cached != null) {
            callback.onFrame(cached);
            return;
        }
        decoder.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final Frame frame = decode(photo);
                    synchronized (cache) {
                        cache.put(photo.file, frame);
                    }
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onFrame(frame);
                        }
                    });
                } catch (IOException e) {
                    // an unreadable photo is skipped; the next interval tries the next one
                }
            }
        });
    }

    private Frame decode(Photo photo) throws IOException {
        InputStream in = photo.local != null ? new FileInputStream(photo.local)
                : context.getAssets().open(DIRECTORY + "/" + photo.file, AssetManager.ACCESS_STREAMING);
        try {
            Bitmap image = BitmapFactory.decodeStream(in);
            if (image == null) {
                throw new IOException("not an image: " + photo.file);
            }
            return new Frame(photo, image, blurred(image));
        } finally {
            in.close();
        }
    }

    private String read(String path) throws IOException {
        InputStream in = context.getAssets().open(path);
        try {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            for (int n; (n = in.read(buffer)) > 0; ) {
                out.write(buffer, 0, n);
            }
            return out.toString("UTF-8");
        } finally {
            in.close();
        }
    }

    // --- blur ---

    /** A small, heavily blurred copy of the picture: what frosted glass over it shows. */
    private static Bitmap blurred(Bitmap image) {
        int width = BLUR_WIDTH;
        int height = Math.max(1, Math.round(BLUR_WIDTH * image.getHeight() / (float) image.getWidth()));
        // shrunk in two steps: one big step skips pixels and leaves noise that blurring cannot remove
        Bitmap half = Bitmap.createScaledBitmap(image, width * 3, height * 3, true);
        Bitmap small = Bitmap.createScaledBitmap(half, width, height, true);
        if (half != image) {
            half.recycle();
        }
        int[] pixels = new int[width * height];
        small.getPixels(pixels, 0, width, 0, 0, width, height);
        for (int pass = 0; pass < BLUR_PASSES; pass++) {
            boxBlur(pixels, width, height, BLUR_RADIUS, true);
            boxBlur(pixels, width, height, BLUR_RADIUS, false);
        }
        small.setPixels(pixels, 0, width, 0, 0, width, height);
        return small;
    }

    /** One pass of a box blur along rows or columns, with the edge pixels repeated. */
    private static void boxBlur(int[] pixels, int width, int height, int radius, boolean horizontal) {
        int length = horizontal ? width : height;
        int lines = horizontal ? height : width;
        int[] line = new int[length];
        int window = 2 * radius + 1;
        for (int l = 0; l < lines; l++) {
            for (int i = 0; i < length; i++) {
                line[i] = pixels[horizontal ? l * width + i : i * width + l];
            }
            for (int shift = 0; shift < 24; shift += 8) {   // blue, green, red
                int sum = 0;
                for (int k = -radius; k <= radius; k++) {
                    sum += (line[Math.min(Math.max(k, 0), length - 1)] >> shift) & 0xFF;
                }
                for (int i = 0; i < length; i++) {
                    int index = horizontal ? l * width + i : i * width + l;
                    int value = sum / window;
                    pixels[index] = (pixels[index] & ~(0xFF << shift)) | (value << shift);
                    sum += ((line[Math.min(i + radius + 1, length - 1)] >> shift) & 0xFF)
                            - ((line[Math.max(i - radius, 0)] >> shift) & 0xFF);
                }
            }
        }
    }
}
