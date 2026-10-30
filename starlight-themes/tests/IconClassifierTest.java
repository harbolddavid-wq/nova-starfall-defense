import com.novasky.starlightthemes.IconClassifier;

public final class IconClassifierTest {
    static void eq(String expected,String actual) {
        if(!expected.equals(actual))throw new AssertionError("expected "+expected+" got "+actual);
    }
    public static void main(String[] args) {
        eq("camera",IconClassifier.classify("com.android.camera2","Camera"));
        eq("music",IconClassifier.classify("com.spotify.music","Spotify"));
        eq("browser",IconClassifier.classify("com.sec.android.app.sbrowser","Internet"));
        eq("video",IconClassifier.classify("com.google.android.youtube","YouTube"));
        eq("dream",IconClassifier.classify("com.nova.starlightsdream","Starlight's Dream"));
        eq("frame",IconClassifier.classify("com.example.unknown","My app"));
        eq("frame",IconClassifier.classify(null,null));
        System.out.println("PASS: automatic illustrated icon selection and recognizable fallback");
    }
}
