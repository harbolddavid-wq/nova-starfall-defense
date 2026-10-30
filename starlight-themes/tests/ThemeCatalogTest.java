import com.novasky.starlightthemes.ThemeCatalog;
import java.util.HashSet;
public final class ThemeCatalogTest {
    private static void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}
    public static void main(String[] args){
        check(ThemeCatalog.ALL.length==5,"Two originals plus three pilot themes");
        check(ThemeCatalog.find("moonflower")==ThemeCatalog.ALL[0],"Old theme ID");
        check(ThemeCatalog.find("conservatory")==ThemeCatalog.ALL[1],"Second old theme ID");
        check(ThemeCatalog.find("missing")==ThemeCatalog.ALL[0],"Safe fallback");
        HashSet<String> ids=new HashSet<>(),palettes=new HashSet<>();
        for(ThemeCatalog.ThemeSpec t:ThemeCatalog.ALL){
            check(ids.add(t.id),"Unique ID: "+t.id);
            check(t.bookColors.length>=4,"Bookshelf palette: "+t.id);
            check(t.name.length()>3 && t.subtitle.length()>8,"Metadata: "+t.id);
            palettes.add(t.iconPalette);
        }
        check(palettes.contains("petal") && palettes.contains("midnight"),"Original icon palettes retained");
        for(String id:new String[]{"lanternwood","astralclock","velvetmasque"}){
            ThemeCatalog.ThemeSpec t=ThemeCatalog.find(id);
            check(t.id.equals(id) && t.iconPalette.equals(id),"New art palette: "+id);
            check(t.accent!=t.tint,"Legible contrast: "+id);
        }
        System.out.println("ThemeCatalogTest PASS: 5 unique stable themes and 3 new art palettes");
    }
}