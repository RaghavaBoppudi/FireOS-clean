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
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * The background wallpapers: which one is showing now, and the decoded pictures. The bundled ones are listed in
 * assets/backgrounds/index.json. Which photo shows is a pure function of the clock and the chosen
 * interval, so every screen agrees and nothing has to be remembered between runs.
 */
final class Wallpapers {
    private static final String DIRECTORY = "backgrounds";
    private static final String PREFERENCES = "wallpapers";
    private static final String KEY_INTERVAL = "interval_seconds";
    private static final String KEY_FILE = "playing_file";     // the wallpaper showing now
    private static final String KEY_SINCE = "playing_since";   // and when it came up, ms since the epoch
    private static final int BLUR_WIDTH = 160;
    private static final int BLUR_RADIUS = 3;
    private static final int BLUR_PASSES = 3;
    private static final int CACHED = 2;   // the one showing and the one fading in
    // the backdrop of the launch cover: a few dozen pixels across, blurred until only colour is left, then stretched
    private static final int BACKDROP_WIDTH = 48;
    private static final int BACKDROP_RADIUS = 2;
    private static final int BACKDROP_PASSES = 4;
    private static final int BACKDROP_DECODE_WIDTH = 480;

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

    interface BackdropCallback {
        void onBackdrop(Bitmap backdrop);
    }

    private static Wallpapers instance;

    private final Context context;
    private final SharedPreferences preferences;
    private final long[] intervalsMs;
    private final List<Photo> bundled = new ArrayList<>();
    private volatile List<Photo> photos = bundled;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();   // one per screen showing the wallpaper
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService decoder = idleExecutor();
    // the launch cover must not wait behind the home screen's own picture, so it has its own thread and a small cache
    private final ExecutorService backdropDecoder = idleExecutor();
    private final Map<String, Bitmap> backdrops = new LinkedHashMap<>();
    private final java.util.Random random = new java.util.Random();
    private final Map<String, Frame> cache = new LinkedHashMap<String, Frame>(CACHED + 1, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Frame> eldest) {
            return size() > CACHED;
        }
    };

    /** One worker thread that exits after a few idle seconds, so a TV showing the same picture all day holds none. */
    private static ExecutorService idleExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 10, TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>());
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    /** Lets go of every decoded picture; they are made again when next needed. For when the system is short of memory. */
    void trim() {
        synchronized (cache) {
            cache.clear();
        }
        synchronized (backdrops) {
            backdrops.clear();
        }
    }

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
        preferences.edit().putInt(KEY_INTERVAL, (int) (intervalsMs[(interval() + 1) % intervalsMs.length] / 1000))
                .putLong(KEY_SINCE, System.currentTimeMillis()).apply();   // the new interval counts from now
    }

    // --- which photo ---

    /**
     * The wallpaper for this moment. They play in list order, one step each interval, and the place in the list is
     * kept (see Rotation), so every screen shows the same one and nothing jumps. Null if there are none.
     */
    synchronized Photo photoAt(long now) {
        List<Photo> list = photos;
        if (list.isEmpty()) {
            return null;
        }
        List<String> files = new ArrayList<>();
        for (Photo photo : list) {
            files.add(photo.file);
        }
        String file = preferences.getString(KEY_FILE, null);
        Rotation.Step saved = file != null ? new Rotation.Step(file, preferences.getLong(KEY_SINCE, 0)) : null;
        Rotation.Step step = Rotation.at(files, saved, now, intervalsMs[interval()], TimeZone.getDefault());
        if (saved == null || !step.file.equals(saved.file) || step.since != saved.since) {
            preferences.edit().putString(KEY_FILE, step.file).putLong(KEY_SINCE, step.since).apply();
        }
        return list.get(files.indexOf(step.file));
    }

    /** Milliseconds until the wallpaper changes. */
    synchronized long untilNext(long now) {
        long since = preferences.getLong(KEY_SINCE, now);
        return Math.max(1000, Rotation.untilNext(since, now, intervalsMs[interval()], TimeZone.getDefault()));
    }

    // --- the launch cover's backdrop ---

    /** A wallpaper chosen at random, or null if there are none. */
    synchronized Photo randomPhoto() {
        List<Photo> list = photos;
        return list.isEmpty() ? null : list.get(random.nextInt(list.size()));
    }

    /**
     * Delivers, on the main thread, a very soft picture of the wallpaper: only its colours, in blurred washes. It is
     * decoded at a fraction of the size, so it comes up quickly even for a huge photograph, and kept once made.
     */
    void loadBackdrop(final Photo photo, final BackdropCallback callback) {
        Bitmap cached;
        synchronized (backdrops) {
            cached = backdrops.get(photo.file);
        }
        if (cached != null) {
            callback.onBackdrop(cached);
            return;
        }
        backdropDecoder.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final Bitmap soft = softPicture(photo);
                    synchronized (backdrops) {
                        backdrops.put(photo.file, soft);
                    }
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onBackdrop(soft);
                        }
                    });
                } catch (IOException | RuntimeException e) {
                    // no backdrop: the cover stays a plain dark screen
                }
            }
        });
    }

    private Bitmap softPicture(Photo photo) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        InputStream in = open(photo);
        try {
            BitmapFactory.decodeStream(in, null, bounds);
        } finally {
            in.close();
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = Integer.highestOneBit(Math.max(1, bounds.outWidth / BACKDROP_DECODE_WIDTH));
        in = open(photo);
        try {
            Bitmap small = BitmapFactory.decodeStream(in, null, options);
            if (small == null) {
                throw new IOException("not an image: " + photo.file);
            }
            return blurred(small, BACKDROP_WIDTH, BACKDROP_RADIUS, BACKDROP_PASSES);
        } finally {
            in.close();
        }
    }

    private InputStream open(Photo photo) throws IOException {
        return photo.local != null ? new FileInputStream(photo.local)
                : context.getAssets().open(DIRECTORY + "/" + photo.file, AssetManager.ACCESS_STREAMING);
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
        InputStream in = open(photo);
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
        return blurred(image, BLUR_WIDTH, BLUR_RADIUS, BLUR_PASSES);
    }

    private static Bitmap blurred(Bitmap image, int width, int radius, int passes) {
        int height = Math.max(1, Math.round(width * image.getHeight() / (float) image.getWidth()));
        // shrunk in two steps: one big step skips pixels and leaves noise that blurring cannot remove
        Bitmap half = Bitmap.createScaledBitmap(image, width * 3, height * 3, true);
        Bitmap small = Bitmap.createScaledBitmap(half, width, height, true);
        if (half != image) {
            half.recycle();
        }
        int[] pixels = new int[width * height];
        small.getPixels(pixels, 0, width, 0, 0, width, height);
        for (int pass = 0; pass < passes; pass++) {
            boxBlur(pixels, width, height, radius, true);
            boxBlur(pixels, width, height, radius, false);
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
