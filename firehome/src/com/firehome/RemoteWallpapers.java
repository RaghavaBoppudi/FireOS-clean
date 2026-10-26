package com.firehome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * Wallpapers from a public GitHub repository (res/values/config.xml). The folder is listed with the GitHub contents
 * API, each new image is downloaded, cut to 16:9 at 1920 x 1080 and kept in the app's files, and an index of them
 * is written for Wallpaper to read. Nothing here runs on the main thread, and nothing is shown until it is whole:
 * files are written under a temporary name and renamed, so a dropped connection leaves the last good set in place.
 *
 * With no repository named in config.xml nothing here runs, and the bundled wallpapers are used.
 *
 * A private repository needs an access token (see TokenReceiver). It is read from the app's private storage, sent only
 * to GitHub's own two hosts, and never written anywhere else.
 *
 * What happens when:
 *  - the repository has at least one usable image: only those are used;
 *  - the repository is reachable but has none (or is empty): the cache is cleared, so the built-in wallpapers are used;
 *  - GitHub cannot be reached, or answers with an error: the cache is left as it is.
 *
 * A file named profile.jpg is not a wallpaper: it is the small picture beside the greeting (see Profile).
 * It is cut to a square and shrunk to Profile.SIZE, so it can be any size; remove it from the repository and the
 * picture goes from the TV at the next sync.
 *
 * The file name carries the order and the credit: "3-Raghava-Boppudi.jpg" comes third and is credited to Raghava
 * Boppudi. A file with no number in front is shown after the numbered ones, and with no credit.
 */
final class RemoteWallpapers {
    private static final String LIST_URL = "https://api.github.com/repos/%s/contents/%s";
    private static final String RAW_HOST = "raw.githubusercontent.com";
    private static final String INDEX = "index.json";
    private static final long REFRESH_MS = 30L * 60 * 1000;   // after a success: a check that finds nothing new costs next to nothing
    private static final long RETRY_MS = 15L * 60 * 1000;         // after a failure
    private static final int MAX_BYTES = 8 * 1024 * 1024;               // listings
    private static final long MAX_IMAGE_BYTES = 100L * 1024 * 1024;     // GitHub's own limit for a pushed file
    private static final long MAX_PIXELS = 300L * 1000 * 1000;          // beyond this a "picture" is more likely a bomb
    private static final long FALLBACK_PIXELS = 12L * 1000 * 1000;      // for formats that cannot be decoded in part
    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;
    private static final String PROFILE_SHA = "profile.sha";   // which version of the profile picture is on disk
    private static final int TIMEOUT_MS = 20 * 1000;
    private static final String ETAG = "etag-v3";   // the sync learned something new (the profile picture): a saved tag from before must not hide it
    private static final Pattern SHA = Pattern.compile("[0-9a-f]{40}");
    private static final Pattern IMAGE = Pattern.compile(".+\\.(jpe?g|png|webp)", Pattern.CASE_INSENSITIVE);
    private static final String PREFERENCES = "remote_wallpapers";

    private static final String TOKEN = "token";
    private static final String API_HOST = "api.github.com";
    private static final AtomicBoolean running = new AtomicBoolean();
    // who is waiting to hear how the sync now running went; with running, only touched holding this lock
    private static final Object lock = new Object();
    private static final List<Callback> waiting = new ArrayList<>();
    private static volatile String token = "";   // set at the start of each sync, which only one thread runs at a time
    private static final Handler main = new Handler(Looper.getMainLooper());

    /** How a sync ended. */
    enum Outcome {
        SYNCED,    // there are images in the repository, now downloaded
        EMPTY,     // the repository has none: the bundled wallpapers are in use
        CURRENT,   // nothing changed since the last sync
        OFFLINE,   // GitHub could not be reached; what was cached is kept
        DENIED,    // GitHub does not let this TV see the repository: a wrong name, or a missing or refused token
        NOT_SET    // no repository is named in config.xml: only the bundled wallpapers are used
    }

    interface Callback {
        void onDone(Outcome outcome, int count);
    }

    private RemoteWallpapers() {
    }

    /** The access token for a private repository, kept in the app's private storage. Empty removes it. */
    static void setToken(Context context, String value) {
        SharedPreferences state = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        SharedPreferences.Editor edit = state.edit().remove(ETAG).putLong("attempt", 0);   // look again at once
        if (value.isEmpty()) {
            edit.remove(TOKEN);
        } else {
            edit.putString(TOKEN, value);
        }
        edit.apply();
    }

    private static final class DeniedException extends IOException {
        DeniedException(String message) {
            super(message);
        }
    }

    // --- what is cached ---

    /** The downloaded wallpapers in order, or none. */
    static List<Wallpaper.Photo> cached(Context context) {
        List<Wallpaper.Photo> photos = new ArrayList<>();
        File dir = directory(context);
        try {
            JSONArray index = new JSONArray(new String(readAll(new FileInputStream(new File(dir, INDEX)), MAX_BYTES), "UTF-8"));
            for (int i = 0; i < index.length(); i++) {
                JSONObject item = index.getJSONObject(i);
                File file = new File(dir, item.getString("file"));
                if (file.isFile()) {
                    photos.add(new Wallpaper.Photo(file.getName(), file, item.optString("credit", null)));
                }
            }
        } catch (IOException | JSONException e) {
            photos.clear();   // no index, or a damaged one: nothing is cached
        }
        return photos;
    }

    private static File directory(Context context) {
        File dir = new File(context.getFilesDir(), "remote");
        dir.mkdirs();
        return dir;
    }

    // --- syncing ---

    /** Starts a sync in the background if one is due and none is running. */
    static void syncIfDue(Context context) {
        start(context, false, null);
    }

    /**
     * Starts a sync now, due or not, and reports how it went on the main thread. If one is already running, this joins
     * it and is told when it ends. False only if there was nothing to do.
     */
    static boolean syncNow(Context context, Callback callback) {
        return start(context, true, callback);
    }

    private static boolean start(Context context, boolean force, final Callback callback) {
        final Context app = context.getApplicationContext();
        if (app.getString(R.string.wallpaper_repo).trim().isEmpty()) {
            if (force) {
                report(callback, Outcome.NOT_SET, 0);
            }
            return force;
        }
        final SharedPreferences state = app.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        long since = System.currentTimeMillis() - state.getLong("attempt", 0);
        if (!force && since >= 0 && since < (state.getBoolean("ok", false) ? REFRESH_MS : RETRY_MS)) {
            return false;
        }
        synchronized (lock) {
            if (callback != null) {
                waiting.add(callback);
            }
            if (!running.compareAndSet(false, true)) {
                return force;   // one is running: a press that wanted an answer will have it when that one ends
            }
        }
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                // this is housekeeping: it must never take the processor from what is on screen
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
                Outcome outcome = Outcome.OFFLINE;
                int count = 0;
                try {
                    state.edit().putLong("attempt", System.currentTimeMillis()).putBoolean("ok", false).apply();
                    count = sync(app, state);
                    state.edit().putBoolean("ok", true).apply();
                    Wallpaper.stale();
                    Profile.stale();
                    outcome = count < 0 ? Outcome.CURRENT : count == 0 ? Outcome.EMPTY : Outcome.SYNCED;
                    count = Math.max(count, 0);
                } catch (DeniedException e) {
                    outcome = Outcome.DENIED;
                } catch (IOException | JSONException e) {
                    // offline, rate limited or unexpected: keep what is cached and try again later
                    outcome = Outcome.OFFLINE;
                } finally {
                    List<Callback> told;
                    synchronized (lock) {
                        told = new ArrayList<>(waiting);
                        waiting.clear();
                        running.set(false);
                    }
                    for (Callback each : told) {
                        report(each, outcome, count);
                    }
                }
            }
        }, "wallpaper-sync");
        thread.setDaemon(true);
        thread.start();
        return true;
    }

    private static void report(final Callback callback, final Outcome outcome, final int count) {
        if (callback != null) {
            main.post(new Runnable() {
                @Override
                public void run() {
                    callback.onDone(outcome, count);
                }
            });
        }
    }

    /** Syncs the cache with the repository. Returns the number of images now cached, or -1 if nothing had changed. */
    private static int sync(Context context, SharedPreferences state) throws IOException, JSONException {
        File dir = directory(context);
        token = state.getString(TOKEN, "");
        String url = String.format(LIST_URL, context.getString(R.string.wallpaper_repo).trim(),
                context.getString(R.string.wallpaper_path).trim());
        HttpURLConnection connection = open(url);
        String etag = state.getString(ETAG, null);
        if (etag != null) {
            connection.setRequestProperty("If-None-Match", etag);
        }
        int code = connection.getResponseCode();
        if (code == HttpURLConnection.HTTP_NOT_MODIFIED) {
            return -1;
        }
        if (code == HttpURLConnection.HTTP_NOT_FOUND) {
            // an empty repository has no folder to list, and says so; any other 404 is a mistake in the settings
            String body = new String(readAll(connection.getErrorStream(), MAX_BYTES), "UTF-8");
            if (!body.contains("empty")) {
                // a private repository answers "not found" to anyone it will not show itself to
                throw new DeniedException("not found: " + url);
            }
            keepOnly(dir, new JSONArray());
            syncProfile(dir, null);
            state.edit().remove(ETAG).apply();
            return 0;
        }
        if (code == HttpURLConnection.HTTP_UNAUTHORIZED
                || (code == HttpURLConnection.HTTP_FORBIDDEN && !"0".equals(connection.getHeaderField("X-RateLimit-Remaining")))) {
            throw new DeniedException("HTTP " + code + " for " + url);   // a refused token; a spent rate limit is "0" left
        }
        if (code != HttpURLConnection.HTTP_OK) {
            throw new IOException("HTTP " + code + " for " + url);
        }
        JSONArray listing = new JSONArray(new String(readAll(connection.getInputStream(), MAX_BYTES), "UTF-8"));
        String newEtag = connection.getHeaderField("ETag");

        Map<String, JSONObject> images = new TreeMap<>(WallpaperName.ORDER);
        JSONObject profile = null;
        for (int i = 0; i < listing.length(); i++) {
            JSONObject entry = listing.getJSONObject(i);
            if (!"file".equals(entry.optString("type"))) {
                continue;
            }
            String name = entry.getString("name");
            if (IMAGE.matcher(name).matches() && entry.optLong("size") <= MAX_IMAGE_BYTES
                    && SHA.matcher(entry.optString("sha")).matches()) {
                if (name.equalsIgnoreCase(Profile.FILE)) {
                    profile = entry;       // the picture beside the greeting, not a wallpaper
                } else {
                    images.put(name, entry);
                }
            }
        }

        JSONArray index = new JSONArray();
        for (Map.Entry<String, JSONObject> image : images.entrySet()) {
            String name = image.getKey();
            String sha = image.getValue().getString("sha");
            File file = new File(dir, sha + ".jpg");
            if (!file.isFile() && !fetch(image.getValue().getString("download_url"), file, WIDTH, HEIGHT)) {
                continue;   // not an image after all: skipped
            }
            JSONObject item = new JSONObject().put("file", file.getName());
            String credit = WallpaperName.creditOf(name);
            if (credit != null) {
                item.put("credit", credit);
            }
            index.put(item);
        }
        keepOnly(dir, index);
        syncProfile(dir, profile);
        if (newEtag != null) {
            state.edit().putString(ETAG, newEtag).apply();
        }
        return index.length();
    }

    /** Writes the index, then deletes every cached file it does not name. */
    private static void keepOnly(File dir, JSONArray index) throws IOException {
        File temporary = new File(dir, INDEX + ".tmp");
        write(temporary, index.toString().getBytes("UTF-8"));
        if (!temporary.renameTo(new File(dir, INDEX))) {
            throw new IOException("could not write the index");
        }
        List<String> keep = new ArrayList<>();
        keep.add(INDEX);
        keep.add(Profile.FILE);
        keep.add(PROFILE_SHA);
        for (int i = 0; i < index.length(); i++) {
            keep.add(index.optJSONObject(i).optString("file"));
        }
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (!keep.contains(file.getName())) {
                    file.delete();
                }
            }
        }
    }

    // --- downloading ---

    /**
     * Makes the profile picture on disk the one in the repository, or removes it if there is none: the repository says
     * what it is. It is cut to a square, and shrunk to Profile.SIZE, however large the original is. A picture that
     * cannot be used is as good as none.
     */
    private static void syncProfile(File dir, JSONObject entry) throws IOException, JSONException {
        File picture = new File(dir, Profile.FILE);
        File version = new File(dir, PROFILE_SHA);
        if (entry == null) {
            picture.delete();
            version.delete();
            return;
        }
        String sha = entry.getString("sha");
        String have = version.isFile() ? new String(readAll(new FileInputStream(version), 64), "UTF-8") : "";
        if (picture.isFile() && sha.equals(have)) {
            return;
        }
        if (fetch(entry.getString("download_url"), picture, Profile.SIZE, Profile.SIZE)) {
            write(version, sha.getBytes("UTF-8"));
        } else {
            picture.delete();
            version.delete();
        }
    }

    /**
     * Downloads an image to disk, then cuts the middle of it, at the shape of {@code width} x {@code height}, to that
     * size. Whatever the size of the original, memory use stays flat: the download never sits in memory, and only the
     * part of the picture that is kept is decoded. False if it is not a usable image.
     */
    private static boolean fetch(String url, File target, int width, int height) throws IOException {
        File source = new File(target.getParentFile(), target.getName() + ".src");
        try {
            downloadTo(url, source);
            return cut(source, target, width, height);
        } finally {
            source.delete();
        }
    }

    /**
     * Writes the middle of the picture in {@code source}, at the shape of {@code outWidth} x {@code outHeight}, to
     * {@code target} as a jpeg of that size. The window is decoded alone, and at the largest reduction that still leaves
     * it at least that wide (which the decoder does cheaply), so a 100 megapixel photograph costs no more memory than
     * an ordinary one.
     */
    static boolean cut(File source, File target, int outWidth, int outHeight) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(source.getPath(), bounds);
        int width = bounds.outWidth;
        int height = bounds.outHeight;
        if (width <= 0 || height <= 0 || (long) width * height > MAX_PIXELS) {
            return false;
        }
        int windowWidth = Math.min(width, Math.round(height * (float) outWidth / outHeight));
        int windowHeight = Math.min(height, Math.round(width * (float) outHeight / outWidth));
        Rect window = new Rect((width - windowWidth) / 2, (height - windowHeight) / 2,
                (width - windowWidth) / 2 + windowWidth, (height - windowHeight) / 2 + windowHeight);

        Bitmap cut = null;
        try {
            Bitmap piece = decodeWindow(source, window, width, height, outWidth);
            if (piece == null) {
                return false;
            }
            cut = Bitmap.createScaledBitmap(piece, outWidth, outHeight, true);
            if (cut != piece) {
                piece.recycle();
            }
            File temporary = new File(target.getParentFile(), target.getName() + ".tmp");
            FileOutputStream out = new FileOutputStream(temporary);
            try {
                cut.compress(Bitmap.CompressFormat.JPEG, 88, out);
            } finally {
                out.close();
            }
            if (!temporary.renameTo(target)) {
                throw new IOException("could not save " + target);
            }
            return true;
        } catch (OutOfMemoryError e) {
            return false;   // too much for this device: skipped like any other unusable image
        } finally {
            if (cut != null) {
                cut.recycle();
            }
        }
    }

    private static Bitmap decodeWindow(File source, Rect window, int width, int height, int outWidth) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = Integer.highestOneBit(Math.max(1, window.width() / outWidth));
        try {
            BitmapRegionDecoder decoder = BitmapRegionDecoder.newInstance(source.getPath(), false);
            try {
                return decoder.decodeRegion(window, options);
            } finally {
                decoder.recycle();
            }
        } catch (IOException | IllegalArgumentException e) {
            // a format that cannot be read in part: decode all of it, reduced to a fixed number of pixels, then crop
            int sample = 1;
            while ((long) (width / sample) * (height / sample) > FALLBACK_PIXELS) {
                sample *= 2;
            }
            options.inSampleSize = sample;
            Bitmap whole = BitmapFactory.decodeFile(source.getPath(), options);
            if (whole == null) {
                return null;
            }
            Bitmap cropped = Bitmap.createBitmap(whole, window.left / sample, window.top / sample,
                    Math.min(window.width() / sample, whole.getWidth() - window.left / sample),
                    Math.min(window.height() / sample, whole.getHeight() - window.top / sample));
            if (cropped != whole) {
                whole.recycle();
            }
            return cropped;
        }
    }

    /** Streams a file to disk, giving up if it grows past the limit. */
    private static void downloadTo(String url, File target) throws IOException {
        if (!new URL(url).getHost().equals(RAW_HOST)) {
            throw new IOException("unexpected host: " + url);
        }
        HttpURLConnection connection = open(url);
        if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
            throw new IOException("HTTP " + connection.getResponseCode() + " for " + url);
        }
        InputStream in = connection.getInputStream();
        FileOutputStream out = new FileOutputStream(target);
        try {
            byte[] buffer = new byte[64 * 1024];
            long total = 0;
            for (int n; (n = in.read(buffer)) > 0; ) {
                total += n;
                if (total > MAX_IMAGE_BYTES) {
                    throw new IOException("too large: " + url);
                }
                out.write(buffer, 0, n);
            }
        } finally {
            in.close();
            out.close();
        }
    }

    private static HttpURLConnection open(String url) throws IOException {
        if (!url.startsWith("https://")) {
            throw new IOException("not https: " + url);
        }
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        connection.setInstanceFollowRedirects(false);   // GitHub's own hosts answer directly; follow nothing elsewhere
        connection.setRequestProperty("User-Agent", "firehome");
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        String host = connection.getURL().getHost();
        if (!token.isEmpty() && (host.equals(API_HOST) || host.equals(RAW_HOST))) {
            connection.setRequestProperty("Authorization", "Bearer " + token);   // to GitHub, and nowhere else
        }
        return connection;
    }

    private static byte[] readAll(InputStream in, int limit) throws IOException {
        if (in == null) {
            return new byte[0];
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[16 * 1024];
            for (int n; (n = in.read(buffer)) > 0; ) {
                out.write(buffer, 0, n);
                if (out.size() > limit) {
                    throw new IOException("too large");
                }
            }
            return out.toByteArray();
        } finally {
            in.close();
        }
    }

    private static void write(File file, byte[] data) throws IOException {
        FileOutputStream out = new FileOutputStream(file);
        try {
            out.write(data);
        } finally {
            out.close();
        }
    }
}
