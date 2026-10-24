package com.firehome;

import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What a wallpaper's file name says: "3-Raghava-Boppudi.jpg" is the third wallpaper, by Raghava Boppudi. A file
 * with no number in front comes after the numbered ones and has no credit.
 */
final class WallpaperName {
    private static final Pattern NUMBERED = Pattern.compile("^(\\d{1,9})[-_ .]*(.*)$");

    /** Numbered files in numeric order (2 before 10), then the rest; ties by name. */
    static final Comparator<String> ORDER = new Comparator<String>() {
        @Override
        public int compare(String a, String b) {
            int byNumber = Long.compare(numberOf(a), numberOf(b));
            return byNumber != 0 ? byNumber : a.compareToIgnoreCase(b);
        }
    };

    private WallpaperName() {
    }

    private static long numberOf(String fileName) {
        Matcher match = NUMBERED.matcher(fileName);
        return match.find() ? Long.parseLong(match.group(1)) : Long.MAX_VALUE;
    }

    /** The photographer: the words after the number, without the extension, hyphens and underscores as spaces. */
    static String creditOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        Matcher match = NUMBERED.matcher(dot > 0 ? fileName.substring(0, dot) : fileName);
        if (!match.find()) {
            return null;
        }
        String credit = match.group(2).replaceAll("[-_\\s]+", " ").trim();
        return credit.isEmpty() ? null : credit;
    }
}
