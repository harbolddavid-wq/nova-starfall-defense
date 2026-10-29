import com.novasky.starlightthemes.WorkspaceModel;
import com.novasky.starlightthemes.WorkspaceModel.*;
import java.util.*;

public class WorkspaceModelTest {
    static void check(boolean b, String s) { if (!b) throw new AssertionError(s); }
    static List<String> all(WorkspaceModel m) {
        List<String> v = new ArrayList<>();
        for (int p = -1; p < m.pages.size(); p++) for (int i = 0; i < (p == -1 ? m.DOCK : m.CELLS); i++) {
            Item x = m.get(new Pos(p, i));
            if (x != null) { if (x.isWidget()) v.add("widget/"+x.widgetId); else if (x.isFolder()) v.addAll(x.children); else v.add(x.component); }
        }
        Collections.sort(v); return v;
    }
    public static void main(String[] args) {
        WorkspaceModel m = new WorkspaceModel();
        check(!m.removePage(0), "keep one page");
        check(m.addToPage("a/A", 0) && m.addToPage("b/B", 0), "seed");
        check(m.addPage() && m.move(new Pos(0, 1), new Pos(1, 7)), "cross page");
        check(!m.removePage(1), "nonempty page cannot be deleted");
        check(m.move(new Pos(1, 7), new Pos(0, 0)), "make folder");
        check(m.get(new Pos(0, 0)).children.equals(Arrays.asList("a/A", "b/B")), "folder contents");
        check(m.removeFolderChild(new Pos(0, 0), 0), "remove child");
        check("b/B".equals(m.get(new Pos(0, 0)).component), "collapse folder");
        m.put(new Pos(-1, 0), Item.app("c/C"));
        check(m.move(new Pos(0, 0), new Pos(-1, 0)), "dock folder");
        check(all(m).equals(Arrays.asList("b/B", "c/C")), "no item lost");
        Pos widget=m.firstRegion(1,2,2);check(widget!=null,"find widget space");
        m.put(widget,Item.widget(123,2,2));
        check(m.occupant(new Pos(1,widget.slot+5)).equals(widget),"covered cell identifies widget");
        check(!m.move(new Pos(-1,0),new Pos(1,widget.slot+5)),"cannot drop icons into widget");
        check(!m.regionFree(new Pos(1,widget.slot+1),2,2,null),"no overlapping widgets");
        check(!m.move(widget,new Pos(-1,1)),"widgets cannot enter dock");
        check(m.move(widget,new Pos(1,2)),"move widget within page");
        Pos movedWidget=new Pos(1,2);
        check(m.containsWidget(123) && !m.containsWidget(124),"find only the placed widget ID");
        check(!m.resizeWidget(movedWidget,3,2),"resize cannot extend beyond a page edge");
        check(m.resizeWidget(movedWidget,1,2),"shrink a widget in place");
        check(m.occupant(new Pos(1,3))==null,"shrinking frees previously occupied cells");
        m.put(new Pos(1,3),Item.app("neighbor/App"));
        check(!m.resizeWidget(movedWidget,2,2),"resize cannot cover a shortcut");
        check(m.get(movedWidget).spanX==1 && m.get(movedWidget).spanY==2,"rejected resize leaves widget unchanged");
        check(!m.resizeWidget(new Pos(1,3),2,2),"a shortcut cannot be resized as a widget");
        check(m.move(movedWidget,new Pos(0,12)),"move widget between pages");
        check(m.occupant(new Pos(0,12)).equals(new Pos(0,12)) && m.get(new Pos(1,3))!=null,"move preserves the neighbor");
        check(m.validLayout(),"valid widget layout");
        Random random = new Random(82);
        for (int i = 0; i < 15; i++) m.addToPage("x/" + i, 0);
        List<String> before = all(m);
        for (int i = 0; i < 3000; i++) {
            int ap = random.nextInt(3) - 1, bp = random.nextInt(3) - 1;
            Pos a = new Pos(ap, random.nextInt(ap == -1 ? m.DOCK : m.CELLS));
            Pos b = new Pos(bp, random.nextInt(bp == -1 ? m.DOCK : m.CELLS));
            m.move(a, b); check(before.equals(all(m)) && m.validLayout(), "drag preserved all shortcuts and occupancy at " + i);
        }
        System.out.println("PASS: pages, dock, folders, widget occupancy and 3000 lossless moves");
    }
}
