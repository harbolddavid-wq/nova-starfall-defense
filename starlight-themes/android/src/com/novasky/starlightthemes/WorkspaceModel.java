package com.novasky.starlightthemes;

import java.util.ArrayList;
import java.util.List;

/** Home layout independent of the presentation and the current theme. */
public final class WorkspaceModel {
    public static final int COLS = 4, ROWS = 5, CELLS = COLS * ROWS, DOCK = 4, MAX_PAGES = 8;
    public static final class Item {
        public String component;
        public String title;
        public int widgetId = -1, spanX = 1, spanY = 1;
        public final ArrayList<String> children = new ArrayList<>();
        public static Item app(String component) {
            if (component == null || component.isEmpty()) throw new IllegalArgumentException("component");
            Item i = new Item(); i.component = component; return i;
        }
        public static Item folder(String title, List<String> apps) {
            if (apps == null || apps.size() < 2) throw new IllegalArgumentException("folder needs two apps");
            Item i = new Item(); i.title = title == null ? "Folder" : title;
            i.children.addAll(apps); return i;
        }
        public static Item widget(int id, int width, int height) {
            if (id < 1 || width < 1 || width > COLS || height < 1 || height > ROWS) throw new IllegalArgumentException("widget");
            Item i = new Item(); i.widgetId=id; i.spanX=width; i.spanY=height; return i;
        }
        public boolean isWidget() { return widgetId > 0; }
        public boolean isFolder() { return component == null && !isWidget(); }
    }
    public static final class Pos {
        public final int page, slot; // page -1 = dock
        public Pos(int page, int slot) { this.page = page; this.slot = slot; }
        @Override public boolean equals(Object o) { return o instanceof Pos && ((Pos)o).page == page && ((Pos)o).slot == slot; }
        @Override public int hashCode() { return page * 31 + slot; }
    }
    public final ArrayList<Item[]> pages = new ArrayList<>();
    public final Item[] dock = new Item[DOCK];
    public WorkspaceModel() { pages.add(new Item[CELLS]); }
    public boolean valid(Pos p) {
        return p != null && (p.page == -1 ? p.slot >= 0 && p.slot < DOCK :
                p.page >= 0 && p.page < pages.size() && p.slot >= 0 && p.slot < CELLS);
    }
    public Item get(Pos p) { if (!valid(p)) return null; return p.page == -1 ? dock[p.slot] : pages.get(p.page)[p.slot]; }
    public void put(Pos p, Item item) {
        if (!valid(p)) throw new IllegalArgumentException("position");
        if (p.page == -1) dock[p.slot] = item; else pages.get(p.page)[p.slot] = item;
    }
    public boolean addPage() { if (pages.size() >= MAX_PAGES) return false; pages.add(new Item[CELLS]); return true; }
    public boolean removePage(int page) {
        if (page < 0 || page >= pages.size() || pages.size() <= 1) return false;
        for (Item item : pages.get(page)) if (item != null) return false;
        pages.remove(page); return true;
    }
    public Pos firstFree(int page) {
        if (page < 0 || page >= pages.size()) return null;
        for (int i = 0; i < CELLS; i++) if (occupant(new Pos(page, i)) == null) return new Pos(page, i);
        return null;
    }
    /** Returns the origin occupying this cell, including cells covered by a widget. */
    public Pos occupant(Pos p) {
        if (!valid(p)) return null;
        if (p.page == -1) return get(p) == null ? null : p;
        int x=p.slot%COLS, y=p.slot/COLS;
        for(int i=0;i<CELLS;i++) {
            Item item=pages.get(p.page)[i]; if(item==null)continue;
            int ox=i%COLS, oy=i/COLS;
            if(item.isWidget() && x>=ox && x<ox+item.spanX && y>=oy && y<oy+item.spanY)return new Pos(p.page,i);
            if(!item.isWidget() && i==p.slot)return new Pos(p.page,i);
        }
        return null;
    }
    public boolean regionFree(Pos origin,int width,int height,Pos ignore) {
        if(!valid(origin) || origin.page<0 || width<1 || height<1) return false;
        int x=origin.slot%COLS,y=origin.slot/COLS;
        if(x+width>COLS || y+height>ROWS)return false;
        for(int dy=0;dy<height;dy++)for(int dx=0;dx<width;dx++) {
            Pos occupied=occupant(new Pos(origin.page,(y+dy)*COLS+x+dx));
            if(occupied!=null && !occupied.equals(ignore))return false;
        }
        return true;
    }
    public Pos firstRegion(int page,int width,int height) {
        for(int i=0;i<CELLS;i++){Pos p=new Pos(page,i);if(regionFree(p,width,height,null))return p;}return null;
    }
    public boolean validLayout() {
        java.util.HashSet<Integer> ids=new java.util.HashSet<>();
        for(Item item:dock)if(item!=null && item.isWidget())return false;
        for(Item[] page:pages){
            boolean[] occupied=new boolean[CELLS];
            for(int i=0;i<CELLS;i++){
                Item item=page[i];if(item==null)continue;
                int x=i%COLS,y=i/COLS,w=item.isWidget()?item.spanX:1,h=item.isWidget()?item.spanY:1;
                if(w<1||h<1||x+w>COLS||y+h>ROWS)return false;
                if(item.isWidget() && !ids.add(item.widgetId))return false;
                for(int dy=0;dy<h;dy++)for(int dx=0;dx<w;dx++){
                    int cell=(y+dy)*COLS+x+dx;if(occupied[cell])return false;occupied[cell]=true;
                }
            }
        }
        return true;
    }
    public boolean addToPage(String key, int page) {
        Pos p = firstFree(page); if (p == null) return false; put(p, Item.app(key)); return true;
    }
    /** Drop onto an app to make a folder; drop an app onto a folder to add it.
     * Other occupied targets swap, so no shortcut is lost. */
    public boolean move(Pos from, Pos to) {
        if (!valid(from) || !valid(to) || from.equals(to)) return false;
        Item source = get(from), target = get(to); if (source == null) return false;
        if(source.isWidget()) {
            if(!regionFree(to,source.spanX,source.spanY,from))return false;
            put(from,null);put(to,source);return true;
        }
        Pos occupied=occupant(to);
        if(occupied!=null && get(occupied).isWidget())return false;
        if (target != null && !source.isFolder() && !target.isFolder()) {
            ArrayList<String> apps = new ArrayList<>(); apps.add(target.component); apps.add(source.component);
            put(to, Item.folder("Folder", apps)); put(from, null);
        } else if (target != null && target.isFolder() && !source.isFolder()) {
            target.children.add(source.component); put(from, null);
        } else { put(from, target); put(to, source); }
        return true;
    }
    public boolean removeFolderChild(Pos p, int index) {
        Item folder = get(p);
        if (folder == null || !folder.isFolder() || index < 0 || index >= folder.children.size()) return false;
        folder.children.remove(index);
        if (folder.children.size() == 1) put(p, Item.app(folder.children.get(0)));
        else if (folder.children.isEmpty()) put(p, null);
        return true;
    }
}
