import com.novasky.afterglow.MediaKind;
public class MediaKindTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    public static void main(String[] args) {
        check(MediaKind.video("movie.MKV", "application/octet-stream"), "video extension");
        check(MediaKind.video("film", "video/x-matroska"), "video MIME fallback");
        check(!MediaKind.video("song.MKA", "video/x-matroska"), "audio-only MKA");
        check(MediaKind.candidate("song.OpUs", null), "opus is discoverable");
        check(MediaKind.candidate("concert.m2ts", "application/octet-stream"), "transport stream");
        check(MediaKind.candidate("file", "application/ogg"), "ogg MIME");
        check(!MediaKind.candidate("photo.jpg", "image/jpeg"), "no unrelated folder file");
        check(!MediaKind.candidate(null, null), "missing metadata safe");
        System.out.println("PASS media classification and uncommon folder formats");
    }
}
