package com.novasky.afterglow;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** File selection is advisory; the player, not a suffix, decides if it can decode. */
public final class MediaKind {
    private static final Set<String> AUDIO = new HashSet<>(Arrays.asList(
        "mp3","m4a","aac","ogg","oga","opus","flac","wav","wave","aiff","aif",
        "wma","alac","ape","amr","ac3","dts","mka","mp2","mpa","tta","wv",
        "mid","midi","mod","xm","it","s3m","m4b","3ga","caf"));
    private static final Set<String> VIDEO = new HashSet<>(Arrays.asList(
        "mp4","m4v","mkv","webm","avi","mov","wmv","flv","f4v","mpeg","mpg",
        "mpe","m2v","m2ts","mts","ts","3gp","3g2","ogv","vob","divx","asf",
        "mxf","m2p","dv","rm","rmvb","mjpg","mjpeg"));
    public static String extension(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).trim().toLowerCase(Locale.ROOT);
    }
    public static boolean video(String name, String mime) {
        String ext = extension(name);
        if (VIDEO.contains(ext)) return true;
        if (AUDIO.contains(ext)) return false;
        return mime != null && mime.toLowerCase(Locale.ROOT).startsWith("video/");
    }
    public static boolean candidate(String name, String mime) {
        String ext = extension(name);
        if (AUDIO.contains(ext) || VIDEO.contains(ext)) return true;
        if (mime == null) return false;
        String m = mime.toLowerCase(Locale.ROOT);
        return m.startsWith("audio/") || m.startsWith("video/") || m.equals("application/ogg");
    }
    private MediaKind() {}
}
