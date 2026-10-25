package com.firehome;

import java.util.List;
import java.util.TimeZone;

/**
 * Which wallpaper is showing, as a playlist: after an interval the next one in the list comes up, then the next, and
 * after the last the first. Where it is up to is remembered by file name, not by position, so adding or removing
 * wallpapers, restarting the TV or changing the interval never makes it jump. A TV that was off for a day moves on by
 * one wallpaper, not by a day's worth, so the order is never skipped. An interval of a day or more changes at local
 * midnight, wherever the TV is; shorter ones count from the last change.
 */
final class Rotation {
    /** The wallpaper to show, and when it started showing (ms since the epoch). */
    static final class Step {
        final String file;
        final long since;

        Step(String file, long since) {
            this.file = file;
            this.since = since;
        }
    }

    static final long DAY_MS = 24L * 60 * 60 * 1000;

    private Rotation() {
    }

    private static long localDay(long time, TimeZone zone) {
        return Math.floorDiv(time + zone.getOffset(time), DAY_MS);
    }

    /** The first moment of the local day {@code time} falls in. */
    private static long midnightBefore(long time, TimeZone zone) {
        long start = localDay(time, zone) * DAY_MS;
        return start - zone.getOffset(time);
    }

    /** Milliseconds from {@code now} until the wallpaper that started at {@code since} should change. */
    static long untilNext(long since, long now, long intervalMs, TimeZone zone) {
        if (intervalMs >= DAY_MS) {
            long next = midnightBefore(now, zone) + DAY_MS;
            return next - now;
        }
        return intervalMs - (now - since);
    }

    /**
     * The step for {@code now}, given the saved one (null at the very first run). {@code files} is the playlist in
     * order and must not be empty.
     */
    static Step at(List<String> files, Step saved, long now, long intervalMs, TimeZone zone) {
        int at = saved == null ? -1 : files.indexOf(saved.file);
        if (at < 0 || saved.since > now) {
            // first run, the saved wallpaper is gone, or the clock went backwards: begin again, at the start
            return new Step(files.get(0), now);
        }
        if (intervalMs >= DAY_MS) {
            if (localDay(now, zone) == localDay(saved.since, zone)) {
                return saved;
            }
            return new Step(files.get((at + 1) % files.size()), midnightBefore(now, zone));
        }
        long elapsed = now - saved.since;
        if (elapsed < intervalMs) {
            return saved;
        }
        // one step on, however long it has been, and the next change a whole interval after the one that was due
        long due = saved.since + intervalMs * (elapsed / intervalMs);
        return new Step(files.get((at + 1) % files.size()), elapsed < 2 * intervalMs ? due : now);
    }
}
