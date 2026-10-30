import com.novasky.starlightthemes.WidgetFinder;

public final class WidgetFinderTest {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        check(WidgetFinder.isAudio(true,"test.app","Unusual name","Small",""),"Android audio category");
        check(WidgetFinder.isAudio(false,"com.spotify.music","Spotify","Circle",""),"music app with shape label");
        check(WidgetFinder.isAudio(false,"test.app","Odd app","Now playing","Listen to music"),"description hint");
        check(!WidgetFinder.isAudio(false,"test.weather","Forecast","Circle","Current weather"),"unrelated app");
        check(WidgetFinder.matches("music","x","Odd app","Circle","",true),"music search recognizes audio category");
        check(WidgetFinder.matches("weather","x","Forecast","Circle","Current weather",false),"search description");
        check(!WidgetFinder.matches("music","x","Forecast","Circle","",false),"music excludes unknown widget");
        check(WidgetFinder.matches("",null,null,null,null,false),"empty search");
        System.out.println("PASS: widget audio classification and search");
    }
}
