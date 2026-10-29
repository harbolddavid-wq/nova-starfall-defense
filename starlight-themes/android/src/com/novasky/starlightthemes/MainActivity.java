package com.novasky.starlightthemes;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.role.RoleManager;
import android.animation.ValueAnimator;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.LruCache;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** A separate, preview-first Home. Widget hosting and advanced studio are future work. */
public final class MainActivity extends Activity {
    private static final String PREFS = "starlight_home_v1";
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ArrayList<App> apps = new ArrayList<>();
    private final HashMap<String, App> byKey = new HashMap<>();
    private final HashMap<String, Bitmap> walls = new HashMap<>();
    private final LruCache<String,Bitmap> iconArt = new LruCache<>(24);
    private SharedPreferences prefs;
    private WorkspaceModel model;
    private boolean loading, destroyed, dragging, resumed;
    private int currentPage;
    private String screen = "home", themeId;
    private FrameLayout root;
    private LinearLayout content;
    private PageScroll workspace;
    private TextView dropRemove, pageDots;
    private GridView drawerGrid;
    private TextView drawerEmpty;
    private EditText search;
    private float downX, downY;
    private Runnable edgeTask;
    private ImageView ambient;
    private ValueAnimator ambientAnimator;
    private static final String[] ICON_KEYS={"original","notes","dream","atlas","video","reader","camera","music","files","settings","messages","calendar","browser","phone","gallery","weather","clock","search","apps"};
    private static final String[] ICON_LABELS={"Original app icon","Notes","Art / Dream","Atlas","Video","Reader","Camera","Music","Files","Settings","Messages","Calendar","Browser","Phone","Gallery","Weather","Clock","Search","App drawer"};
    private static final class App {
        final ResolveInfo info; final ComponentName component; final String key, label;
        App(ResolveInfo info, String label) {
            this.info = info; this.label = label;
            this.component = new ComponentName(info.activityInfo.packageName, info.activityInfo.name);
            this.key = component.flattenToString();
        }
    }
    private static final class ThemeSpec {
        final String id, name, subtitle; final int accent, tint;
        ThemeSpec(String id, String name, String subtitle, int accent, int tint) {
            this.id=id; this.name=name; this.subtitle=subtitle; this.accent=accent; this.tint=tint;
        }
    }
    private final ThemeSpec[] themes = {
        new ThemeSpec("moonflower", "Moonflower", "Moths, moonlight and a little magic", 0xffe8c8ed, 0xff29213f),
        new ThemeSpec("conservatory", "Starlit Conservatory", "A quiet garden under the stars", 0xffc5dded, 0xff1e3446)
    };
    private ThemeSpec theme(String id) { for (ThemeSpec t : themes) if (t.id.equals(id)) return t; return themes[0]; }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(0xff171527);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        themeId = theme(prefs.getString("theme", "moonflower")).id;
        model = readLayout(); currentPage = Math.max(0, Math.min(prefs.getInt("page", 0), model.pages.size()-1));
        showHome(); loadApps();
    }
    @Override protected void onResume() { super.onResume(); resumed=true; startAmbient(); if (prefs != null) loadApps(); }
    @Override protected void onPause() { resumed=false; stopAmbient(); super.onPause(); }
    @Override protected void onDestroy() { destroyed=true; stopAmbient(); ui.removeCallbacksAndMessages(null); worker.shutdownNow(); super.onDestroy(); }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); hideKeyboard(); currentPage=0; showHome(); }
    @Override public void onBackPressed() {
        if (!screen.equals("home")) { hideKeyboard(); showHome(); }
        else if (currentPage != 0) { currentPage=0; showHome(); }
        else if (!isHome()) super.onBackPressed();
    }

    private WorkspaceModel readLayout() {
        WorkspaceModel m = new WorkspaceModel(); String raw = prefs.getString("layout", null);
        if (raw == null) return m;
        try {
            JSONObject root = new JSONObject(raw); JSONArray pages = root.getJSONArray("pages");
            if (pages.length() < 1 || pages.length() > WorkspaceModel.MAX_PAGES) throw new IllegalArgumentException("pages");
            m.pages.clear();
            for (int p=0; p<pages.length(); p++) {
                JSONArray slots = pages.getJSONArray(p); if (slots.length()!=WorkspaceModel.CELLS) throw new IllegalArgumentException("slots");
                WorkspaceModel.Item[] arr = new WorkspaceModel.Item[WorkspaceModel.CELLS];
                for (int i=0; i<arr.length; i++) arr[i]=readItem(slots.optJSONObject(i));
                m.pages.add(arr);
            }
            JSONArray dock = root.getJSONArray("dock");
            if (dock.length()!=WorkspaceModel.DOCK) throw new IllegalArgumentException("dock");
            for (int i=0; i<m.dock.length; i++) m.dock[i]=readItem(dock.optJSONObject(i));
            return m;
        } catch (Exception e) {
            prefs.edit().putString("layout_recovery", raw).remove("layout").apply();
            return new WorkspaceModel();
        }
    }
    private WorkspaceModel.Item readItem(JSONObject o) throws Exception {
        if (o == null) return null;
        String type=o.getString("type");
        if (type.equals("app")) return WorkspaceModel.Item.app(o.getString("component"));
        if (!type.equals("folder")) throw new IllegalArgumentException("type");
        JSONArray members=o.getJSONArray("children"); ArrayList<String> children=new ArrayList<>();
        for (int i=0; i<members.length(); i++) children.add(members.getString(i));
        return WorkspaceModel.Item.folder(o.optString("title", "Folder"), children);
    }
    private JSONObject writeItem(WorkspaceModel.Item item) throws Exception {
        if (item == null) return null;
        JSONObject o=new JSONObject();
        if (!item.isFolder()) { o.put("type", "app"); o.put("component", item.component); }
        else { o.put("type", "folder"); o.put("title", item.title); o.put("children", new JSONArray(item.children)); }
        return o;
    }
    private void saveLayout() {
        try {
            JSONObject o=new JSONObject(); JSONArray pages=new JSONArray(), dock=new JSONArray();
            for (WorkspaceModel.Item[] page : model.pages) { JSONArray a=new JSONArray(); for (WorkspaceModel.Item i:page) a.put(writeItem(i)); pages.put(a); }
            for (WorkspaceModel.Item i:model.dock) dock.put(writeItem(i));
            o.put("pages", pages); o.put("dock", dock);
            prefs.edit().putString("layout", o.toString()).putInt("page", currentPage).apply();
        } catch (Exception e) { toast("Could not save the layout"); }
    }
    private void loadApps() {
        if (loading || destroyed) return; loading=true;
        worker.execute(() -> {
            ArrayList<App> found=new ArrayList<>(); HashSet<String> seen=new HashSet<>();
            try {
                Intent q=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
                for (ResolveInfo r:getPackageManager().queryIntentActivities(q, 0)) {
                    if (r.activityInfo==null || !r.activityInfo.exported || getPackageName().equals(r.activityInfo.packageName)) continue;
                    App a=new App(r, r.loadLabel(getPackageManager()).toString());
                    if (seen.add(a.key)) found.add(a);
                }
                Collections.sort(found, (a,b)->a.label.compareToIgnoreCase(b.label));
            } catch (Exception ignored) { }
            runOnUiThread(() -> {
                loading=false; if (destroyed) return;
                apps.clear(); apps.addAll(found); byKey.clear(); for (App a:apps) byKey.put(a.key,a);
                if (!prefs.contains("layout") && !apps.isEmpty()) seedLayout();
                if (screen.equals("home") && !dragging) showHome();
                else if (screen.equals("drawer")) refreshDrawer(search == null ? "" : search.getText().toString());
            });
        });
    }
    private void seedLayout() {
        HashSet<String> used=new HashSet<>(); String[] terms={"phone","messages","chrome","camera"}; int dock=0;
        for (String term:terms) for (App a:apps) if (!used.contains(a.key) && (a.label+" "+a.component.getPackageName()).toLowerCase(Locale.ROOT).contains(term)) {
            model.dock[dock++]=WorkspaceModel.Item.app(a.key); used.add(a.key); break;
        }
        for (App a:apps) if (used.size()<12 && !used.contains(a.key)) { model.addToPage(a.key,0); used.add(a.key); }
        saveLayout();
    }
    private void launch(String key) {
        App a=byKey.get(key);
        if (a==null) { toast("This app is unavailable. Your shortcut is still saved."); loadApps(); return; }
        try { startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(a.component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)); }
        catch (Exception e) { toast("Couldn't open this app"); }
    }
    private void hideKeyboard() {
        if (root!=null) ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(root.getWindowToken(),0);
    }

    private LinearLayout col() { LinearLayout v=new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private LinearLayout row() { LinearLayout v=new LinearLayout(this); v.setGravity(Gravity.CENTER_VERTICAL); return v; }
    private TextView text(String s,int size) { TextView v=new TextView(this); v.setText(s); v.setTextSize(size); v.setTextColor(Color.WHITE); return v; }
    private GradientDrawable rounded(int color,int stroke,int radius) {
        GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); if(stroke!=0)d.setStroke(dp(1),stroke); return d;
    }
    private TextView button(String label,Runnable action) {
        TextView v=text(label,14); v.setGravity(Gravity.CENTER); v.setMinHeight(dp(48)); v.setPadding(dp(14),0,dp(14),0);
        v.setBackground(rounded(0xbb201d30,0x668c7ca4,18)); v.setClickable(true); v.setFocusable(true); v.setOnClickListener(w->action.run()); return v;
    }
    private void add(LinearLayout parent,View child,int top) { LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.topMargin=dp(top); parent.addView(child,lp); }
    private Bitmap wall(String id) {
        if (walls.containsKey(id)) return walls.get(id);
        try (InputStream s=getAssets().open("wallpapers/"+id+".png")) {
            BitmapFactory.Options opts=new BitmapFactory.Options(); opts.inSampleSize=2; Bitmap b=BitmapFactory.decodeStream(s,null,opts); walls.put(id,b); return b;
        } catch (Exception e) { return null; }
    }
    private Bitmap chosenIcon(String key) {
        String choice=prefs.getString("icon:"+key,"original"); boolean valid=false;
        for(String candidate:ICON_KEYS)if(candidate.equals(choice))valid=true;
        if(!valid || choice.equals("original"))return null;
        String palette=themeId.equals("moonflower")?"petal":"midnight";
        String cacheKey=palette+"_"+choice; Bitmap cached=iconArt.get(cacheKey);if(cached!=null)return cached;
        try(InputStream in=getAssets().open("icons/"+palette+"_"+choice+".png")){
            BitmapFactory.Options opts=new BitmapFactory.Options();opts.inSampleSize=2;Bitmap image=BitmapFactory.decodeStream(in,null,opts);if(image!=null)iconArt.put(cacheKey,image);return image;
        }catch(Exception ignored){return null;}
    }
    private void base(String which,ThemeSpec t,boolean dim) {
        stopAmbient(); ambient=null;
        screen=which; workspace=null; dropRemove=null; pageDots=null; drawerGrid=null; drawerEmpty=null; search=null;
        root=new FrameLayout(this); root.setBackgroundColor(t.tint);
        Bitmap w=wall(t.id);
        if(w!=null) { ImageView iv=new ImageView(this); iv.setScaleType(ImageView.ScaleType.CENTER_CROP); iv.setImageBitmap(w); root.addView(iv,new FrameLayout.LayoutParams(-1,-1)); }
        if (which.equals("home") && prefs.getBoolean("ambient",true)) {
            try (InputStream sky=getAssets().open("ambient/night-sky.png")) {
                ambient=new ImageView(this); ambient.setScaleType(ImageView.ScaleType.CENTER_CROP);
                ambient.setImageBitmap(BitmapFactory.decodeStream(sky)); ambient.setAlpha(.035f);
                root.addView(ambient,new FrameLayout.LayoutParams(-1,-1));
            } catch (Exception ignored) { }
        }
        View shade=new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{dim?0xcc151425:0x44131120,dim?0xd9191729:0x22131120,dim?0xee171526:0x99100e1b}));
        root.addView(shade,new FrameLayout.LayoutParams(-1,-1));
        content=col(); root.addView(content,new FrameLayout.LayoutParams(-1,-1));
        root.setOnApplyWindowInsetsListener((v,insets)->{
            content.setPadding(dp(14)+insets.getSystemWindowInsetLeft(),dp(8)+insets.getSystemWindowInsetTop(),dp(14)+insets.getSystemWindowInsetRight(),dp(8)+insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(root); root.requestApplyInsets();
        if(resumed)startAmbient();
    }
    private void startAmbient() {
        if(ambient==null || !resumed || ambientAnimator!=null || !ValueAnimator.areAnimatorsEnabled())return;
        ambientAnimator=ValueAnimator.ofFloat(.025f,.07f); ambientAnimator.setDuration(6500);
        ambientAnimator.setRepeatMode(ValueAnimator.REVERSE); ambientAnimator.setRepeatCount(ValueAnimator.INFINITE);
        ambientAnimator.addUpdateListener(a->{if(ambient!=null)ambient.setAlpha((float)a.getAnimatedValue());}); ambientAnimator.start();
    }
    private void stopAmbient() { if(ambientAnimator!=null){ambientAnimator.cancel();ambientAnimator=null;} }
    private void header(String title) {
        LinearLayout r=row(); TextView back=button("‹",this::showHome); back.setContentDescription("Back to home"); r.addView(back,new LinearLayout.LayoutParams(dp(52),dp(48)));
        TextView titleView=text(title,22); titleView.setTypeface(Typeface.DEFAULT,Typeface.BOLD); titleView.setPadding(dp(14),0,0,0); r.addView(titleView,new LinearLayout.LayoutParams(0,-2,1)); add(content,r,0);
    }
    private void showHome() {
        if (dragging) return;
        ThemeSpec t=theme(themeId); base("home",t,false);
        LinearLayout top=row();
        TextView date=text(android.text.format.DateFormat.getDateFormat(this).format(new java.util.Date()),14); date.setShadowLayer(dp(4),0,dp(1),Color.BLACK); top.addView(date,new LinearLayout.LayoutParams(0,-2,1));
        top.addView(button("Themes",this::showThemes));
        TextView menu=button("⋯",this::homeMenu); menu.setContentDescription("Home options"); LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(dp(52),dp(48)); mp.leftMargin=dp(6); top.addView(menu,mp); add(content,top,0);
        TextView status=text(isHome()?"":"Preview · Choose Home when you're ready",12); status.setTextColor(0xfff1dff6); status.setShadowLayer(dp(3),0,dp(1),Color.BLACK); status.setMinHeight(dp(30)); status.setGravity(Gravity.CENTER_VERTICAL); status.setOnClickListener(v->homeChoice()); add(content,status,0);
        dropRemove=button("Remove shortcut",()->{}); dropRemove.setVisibility(View.GONE); dropRemove.setBackground(rounded(0xee492e43,0xffedbad5,18));
        dropRemove.setOnDragListener((view,event)->{
            if(event.getAction()==DragEvent.ACTION_DRAG_STARTED) return event.getLocalState() instanceof WorkspaceModel.Pos;
            if(event.getAction()==DragEvent.ACTION_DROP) { WorkspaceModel.Pos p=(WorkspaceModel.Pos)event.getLocalState(); if(model.valid(p)){model.put(p,null); saveLayout();} return true; }
            return true;
        });
        FrameLayout.LayoutParams removeParams=new FrameLayout.LayoutParams(-1,dp(52),Gravity.TOP);
        removeParams.setMargins(dp(14),dp(58),dp(14),0); root.addView(dropRemove,removeParams);
        workspace=new PageScroll(this); workspace.setFillViewport(false); workspace.setHorizontalScrollBarEnabled(false);
        LinearLayout strip=row(); strip.setGravity(Gravity.FILL); int width=getResources().getDisplayMetrics().widthPixels-dp(28);
        for(int p=0;p<model.pages.size();p++) strip.addView(new PageView(this,p),new LinearLayout.LayoutParams(width,-1));
        workspace.addView(strip,new HorizontalScrollView.LayoutParams(-2,-1));
        content.addView(workspace,new LinearLayout.LayoutParams(-1,0,1));
        workspace.post(()->{ if(workspace==null)return; int actual=workspace.getWidth(); if(actual>0){for(int i=0;i<strip.getChildCount();i++){View v=strip.getChildAt(i); ViewGroup.LayoutParams lp=v.getLayoutParams();lp.width=actual;v.setLayoutParams(lp);}workspace.post(()->{if(workspace!=null)workspace.scrollTo(currentPage*workspace.getWidth(),0);});} });
        pageDots=text("",14); pageDots.setGravity(Gravity.CENTER); pageDots.setShadowLayer(dp(4),0,0,Color.BLACK); updateDots(); add(content,pageDots,2);
        TextView drawer=button("⌃   All apps",this::showDrawer); drawer.setBackground(rounded(0x99221d32,0x669e8cae,18)); add(content,drawer,4);
        LinearLayout dock=row(); dock.setGravity(Gravity.CENTER); dock.setBackground(rounded(0x99302a40,0x77d8c4dc,24));
        for(int i=0;i<WorkspaceModel.DOCK;i++){ WorkspaceModel.Pos pos=new WorkspaceModel.Pos(-1,i); View v=itemView(model.dock[i],pos,true); dock.addView(v,new LinearLayout.LayoutParams(0,dp(78),1)); }
        dock.setOnDragListener((v,e)->{
            if(e.getAction()==DragEvent.ACTION_DRAG_STARTED) return e.getLocalState() instanceof WorkspaceModel.Pos;
            if(e.getAction()==DragEvent.ACTION_DROP) { int slot=Math.min(3,Math.max(0,(int)(e.getX()*4/Math.max(1,v.getWidth())))); if(model.move((WorkspaceModel.Pos)e.getLocalState(),new WorkspaceModel.Pos(-1,slot)))saveLayout(); return true; }
            return true;
        }); add(content,dock,8);
    }
    private void updateDots() {
        if(pageDots==null)return; StringBuilder b=new StringBuilder(); for(int i=0;i<model.pages.size();i++)b.append(i==currentPage?"● ":"○ "); pageDots.setText(b.toString().trim());
        pageDots.setContentDescription("Home page "+(currentPage+1)+" of "+model.pages.size());
    }
    private final class PageScroll extends HorizontalScrollView {
        float swipeStart; int startPage;
        PageScroll(Context c) { super(c); setOverScrollMode(View.OVER_SCROLL_NEVER); }
        @Override public boolean onInterceptTouchEvent(MotionEvent e) {
            if(e.getAction()==MotionEvent.ACTION_DOWN){swipeStart=e.getX();startPage=currentPage;}
            return super.onInterceptTouchEvent(e);
        }
        @Override public boolean onTouchEvent(MotionEvent e) {
            boolean result=super.onTouchEvent(e);
            if(e.getAction()==MotionEvent.ACTION_UP || e.getAction()==MotionEvent.ACTION_CANCEL) {
                int w=Math.max(1,getWidth()); float dx=e.getX()-swipeStart;
                int p=Math.abs(dx)>dp(48)?startPage+(dx<0?1:-1):Math.round((float)getScrollX()/w);
                p=Math.min(model.pages.size()-1,Math.max(0,p));
                currentPage=p; smoothScrollTo(p*w,0); prefs.edit().putInt("page",p).apply(); updateDots();
            }
            return result;
        }
    }
    private final class PageView extends ViewGroup {
        final int page;
        PageView(Context c,int page) {
            super(c); this.page=page; setClipChildren(false); setLongClickable(true); setOnLongClickListener(v->{homeMenu();return true;});
            for(int i=0;i<WorkspaceModel.CELLS;i++){WorkspaceModel.Pos p=new WorkspaceModel.Pos(page,i); addView(itemView(model.get(p),p,false));}
            setOnDragListener((v,e)->{
                if(e.getAction()==DragEvent.ACTION_DRAG_STARTED)return e.getLocalState() instanceof WorkspaceModel.Pos;
                if(e.getAction()==DragEvent.ACTION_DRAG_LOCATION){ maybeEdge(page,e.getX(),v.getWidth()); return true; }
                if(e.getAction()==DragEvent.ACTION_DRAG_EXITED){cancelEdge();return true;}
                if(e.getAction()==DragEvent.ACTION_DROP){cancelEdge(); int col=Math.max(0,Math.min(3,(int)(e.getX()*4/Math.max(1,v.getWidth())))); int row=Math.max(0,Math.min(4,(int)(e.getY()*5/Math.max(1,v.getHeight())))); if(model.move((WorkspaceModel.Pos)e.getLocalState(),new WorkspaceModel.Pos(page,row*4+col)))saveLayout(); return true;}
                if(e.getAction()==DragEvent.ACTION_DRAG_ENDED){cancelEdge();finishDrag();} return true;
            });
        }
        @Override protected void onMeasure(int w,int h) {
            int width=MeasureSpec.getSize(w),height=MeasureSpec.getSize(h); setMeasuredDimension(width,height);
            for(int i=0;i<getChildCount();i++)getChildAt(i).measure(MeasureSpec.makeMeasureSpec(width/4,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(height/5,MeasureSpec.EXACTLY));
        }
        @Override protected void onLayout(boolean c,int l,int t,int r,int b) {
            int cw=(r-l)/4,ch=(b-t)/5; for(int i=0;i<getChildCount();i++){int x=(i%4)*cw,y=(i/4)*ch;getChildAt(i).layout(x,y,x+cw,y+ch);}
        }
    }
    private void maybeEdge(int page,float x,int width) {
        int dest=x<dp(28)?page-1:x>width-dp(28)?page+1:-1;
        if(dest<0 || dest>=model.pages.size()){cancelEdge();return;}
        if(edgeTask!=null)return;
        edgeTask=()->{edgeTask=null;if(dragging && workspace!=null){currentPage=dest;workspace.smoothScrollTo(dest*workspace.getWidth(),0);updateDots();}};
        ui.postDelayed(edgeTask,650);
    }
    private void cancelEdge(){if(edgeTask!=null){ui.removeCallbacks(edgeTask);edgeTask=null;}}
    private void finishDrag(){if(!dragging)return;dragging=false;cancelEdge();if(dropRemove!=null)dropRemove.setVisibility(View.GONE);ui.post(this::showHome);}
    private View itemView(WorkspaceModel.Item item, WorkspaceModel.Pos pos, boolean dock) {
        LinearLayout tile=col(); tile.setGravity(Gravity.CENTER); tile.setPadding(dp(2),dp(2),dp(2),dp(2));
        if(item==null){tile.setContentDescription("Empty position");return tile;}
        FrameLayout iconFrame=new FrameLayout(this); iconFrame.setBackground(rounded(0xcc302b48,0xbbddc9e6,20));
        int iconSize=dp(dock?51:56);
        if(item.isFolder()) {
            LinearLayout preview=col(); preview.setPadding(dp(8),dp(8),dp(8),dp(8));
            for(int r=0;r<2;r++){LinearLayout line=row();for(int c=0;c<2;c++){int n=r*2+c;ImageView icon=new ImageView(this);if(n<item.children.size()){App a=byKey.get(item.children.get(n));if(a!=null)try{icon.setImageDrawable(a.info.loadIcon(getPackageManager()));}catch(Exception ignored){}}line.addView(icon,new LinearLayout.LayoutParams(0,dp(19),1));}preview.addView(line);} iconFrame.addView(preview,new FrameLayout.LayoutParams(-1,-1));
        } else {
            ImageView icon=new ImageView(this); icon.setScaleType(ImageView.ScaleType.FIT_CENTER); App a=byKey.get(item.component);
            Bitmap custom=chosenIcon(item.component);
            if(custom!=null){iconFrame.setBackgroundColor(Color.TRANSPARENT);icon.setImageBitmap(custom);}
            else if(a!=null)try{icon.setImageDrawable(a.info.loadIcon(getPackageManager()));}catch(Exception ignored){}
            int inner=custom!=null?iconSize:dp(dock?38:42);
            FrameLayout.LayoutParams ip=new FrameLayout.LayoutParams(inner,inner,Gravity.CENTER); iconFrame.addView(icon,ip);
        }
        tile.addView(iconFrame,new LinearLayout.LayoutParams(iconSize,iconSize));
        String label=item.isFolder()?item.title:(byKey.containsKey(item.component)?byKey.get(item.component).label:"Unavailable");
        TextView name=text(label,11); name.setGravity(Gravity.CENTER); name.setMaxLines(1); name.setEllipsize(TextUtils.TruncateAt.END); name.setShadowLayer(dp(4),0,dp(1),Color.BLACK); tile.addView(name,new LinearLayout.LayoutParams(-1,-2));
        tile.setContentDescription(label+(item.isFolder()?", folder":"")); tile.setFocusable(true); tile.setClickable(true);
        tile.setOnClickListener(v->{if(item.isFolder())openFolder(pos);else launch(item.component);});
        tile.setOnLongClickListener(v->{
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); dragging=true; if(dropRemove!=null)dropRemove.setVisibility(View.VISIBLE);
            boolean started=v.startDragAndDrop(ClipData.newPlainText("shortcut",""),new View.DragShadowBuilder(v),pos,0);
            if(!started)finishDrag(); return started;
        });
        return tile;
    }
    private void openFolder(WorkspaceModel.Pos pos) {
        WorkspaceModel.Item folder=model.get(pos); if(folder==null || !folder.isFolder())return;
        LinearLayout list=col(); list.setPadding(dp(10),0,dp(10),0);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(folder.title).setView(list).setNegativeButton("Close",null).setNeutralButton("Rename",(d,w)->renameFolder(pos)).create();
        for(int i=0;i<folder.children.size();i++) {
            final int index=i; String key=folder.children.get(i); App a=byKey.get(key); TextView b=button(a==null?"Unavailable app":a.label,()->{dialog.dismiss();launch(key);});
            b.setTextColor(0xff282137); b.setBackgroundColor(Color.TRANSPARENT); b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
            b.setOnLongClickListener(v->{dialog.dismiss();folderMemberOptions(pos,index);return true;}); list.addView(b);
        }
        dialog.show();
    }
    private void renameFolder(WorkspaceModel.Pos pos) {
        WorkspaceModel.Item f=model.get(pos);if(f==null || !f.isFolder())return;
        EditText input=new EditText(this); input.setSingleLine(true);input.setText(f.title);
        new AlertDialog.Builder(this).setTitle("Folder name").setView(input).setPositiveButton("Save",(d,w)->{String title=input.getText().toString().trim();f.title=title.isEmpty()?"Folder":title;saveLayout();showHome();}).setNegativeButton("Cancel",null).show();
    }
    private void folderMemberOptions(WorkspaceModel.Pos pos,int index) {
        WorkspaceModel.Item f=model.get(pos);if(f==null || !f.isFolder() || index>=f.children.size())return;String key=f.children.get(index);
        new AlertDialog.Builder(this).setItems(new String[]{"Move to Home","Remove shortcut"},(d,which)->{
            if(which==0){WorkspaceModel.Pos free=model.firstFree(currentPage);if(free==null){toast("There is no free space on this page");return;}model.put(free,WorkspaceModel.Item.app(key));}
            model.removeFolderChild(pos,index);saveLayout();showHome();
        }).show();
    }
    private void homeMenu() {
        new AlertDialog.Builder(this).setTitle("Home options").setItems(new String[]{"Themes","Add a home page","Remove this empty page","Choose default Home","Home settings"},(d,n)->{
            switch(n){case 0:showThemes();break;case 1:if(model.addPage()){currentPage=model.pages.size()-1;saveLayout();showHome();}else toast("Maximum of eight pages");break;
            case 2:if(model.removePage(currentPage)){currentPage=Math.min(currentPage,model.pages.size()-1);saveLayout();showHome();}else toast("Keep one page and remove its shortcuts first");break;
            case 3:homeChoice();break;case 4:showSettings();break;}
        }).show();
    }
    private void showDrawer() {
        base("drawer",theme(themeId),true);header("All apps");
        search=new EditText(this); search.setSingleLine(true); search.setTextColor(Color.WHITE); search.setHintTextColor(0xffc5bdcf); search.setHint("Search apps"); search.setPadding(dp(16),0,dp(16),0); search.setBackground(rounded(0xdd282439,0x778e809d,18)); add(content,search,12);
        TextView tip=text("Touch and hold an app to add it to Home",12);tip.setTextColor(0xffe2d8e8);add(content,tip,10);
        FrameLayout drawerBody=new FrameLayout(this);drawerGrid=new GridView(this);drawerGrid.setNumColumns(4);drawerGrid.setVerticalSpacing(dp(3));drawerGrid.setClipToPadding(false);
        drawerBody.addView(drawerGrid,new FrameLayout.LayoutParams(-1,-1));drawerEmpty=text("No matching apps",16);drawerEmpty.setGravity(Gravity.CENTER);
        drawerBody.addView(drawerEmpty,new FrameLayout.LayoutParams(-1,-1));drawerGrid.setEmptyView(drawerEmpty);
        content.addView(drawerBody,new LinearLayout.LayoutParams(-1,0,1));
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int before,int count){refreshDrawer(s.toString());}public void afterTextChanged(Editable e){}});
        content.setFocusableInTouchMode(true);content.requestFocus();refreshDrawer("");
    }
    private void refreshDrawer(String filter) {
        if(drawerGrid==null)return;ArrayList<App> matching=new ArrayList<>();String f=filter.toLowerCase(Locale.ROOT);
        for(App a:apps)if(a.label.toLowerCase(Locale.ROOT).contains(f))matching.add(a);
        if(drawerEmpty!=null)drawerEmpty.setText(loading?"Finding apps…":"No matching apps");
        drawerGrid.setAdapter(new BaseAdapter(){
            public int getCount(){return matching.size();}
            public Object getItem(int pos){return matching.get(pos);}
            public long getItemId(int pos){return pos;}
            public View getView(int pos,View recycled,ViewGroup parent){App a=matching.get(pos);return drawerTile(WorkspaceModel.Item.app(a.key),a,recycled);}
        });
    }
    private View drawerTile(WorkspaceModel.Item item,App app,View recycled) {
        LinearLayout tile; ImageView icon; TextView label;
        if(recycled instanceof LinearLayout && ((LinearLayout)recycled).getChildCount()==2){tile=(LinearLayout)recycled;icon=(ImageView)tile.getChildAt(0);label=(TextView)tile.getChildAt(1);}
        else {tile=col();tile.setGravity(Gravity.CENTER);tile.setLayoutParams(new AbsListView.LayoutParams(-1,dp(96)));icon=new ImageView(this);tile.addView(icon,new LinearLayout.LayoutParams(dp(48),dp(48)));
            label=text("",11);label.setGravity(Gravity.CENTER);label.setMaxLines(1);label.setEllipsize(TextUtils.TruncateAt.END);tile.addView(label,new LinearLayout.LayoutParams(-1,-2));tile.setFocusable(true);}
        Bitmap custom=chosenIcon(app.key);if(custom!=null)icon.setImageBitmap(custom);else icon.setImageDrawable(app.info.loadIcon(getPackageManager()));label.setText(app.label);tile.setContentDescription(app.label);tile.setOnClickListener(v->launch(app.key));
        tile.setOnLongClickListener(v->{v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);new AlertDialog.Builder(this).setTitle(app.label).setItems(new String[]{"Add to Home","Add to dock","Customize icon","App info"},(d,n)->{
            if(n==0){if(model.addToPage(app.key,currentPage)){saveLayout();hideKeyboard();showHome();}else toast("No free space on this page");}
            else if(n==1){int i=0;while(i<model.dock.length && model.dock[i]!=null)i++;if(i==model.dock.length){toast("The dock is full");return;}model.dock[i]=item;saveLayout();hideKeyboard();showHome();}
            else if(n==2)customizeIcon(app);
            else try{startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+app.component.getPackageName())));}catch(Exception e){toast("App info unavailable");}
        }).show();return true;});return tile;
    }
    private void customizeIcon(App app) {
        String old=prefs.getString("icon:"+app.key,"original");int selected=0;
        for(int i=0;i<ICON_KEYS.length;i++)if(ICON_KEYS[i].equals(old))selected=i;
        new AlertDialog.Builder(this).setTitle("Icon for "+app.label).setSingleChoiceItems(ICON_LABELS,selected,(d,which)->{
            prefs.edit().putString("icon:"+app.key,ICON_KEYS[which]).apply();d.dismiss();if(screen.equals("drawer") && search!=null)refreshDrawer(search.getText().toString());toast("Icon saved for "+app.label);
        }).setNegativeButton("Cancel",null).show();
    }
    private void themeFlourish() {
        if(root==null || !prefs.getBoolean("ambient",true) || !ValueAnimator.areAnimatorsEnabled())return;
        AnimationDrawable frames=new AnimationDrawable();frames.setOneShot(true);
        try {
            for(int i=0;i<16;i++)try(InputStream in=getAssets().open(String.format(Locale.ROOT,"moth/moth_%02d.png",i))){
                Bitmap b=BitmapFactory.decodeStream(in);if(b==null)return;frames.addFrame(new BitmapDrawable(getResources(),b),80);
            }
        }catch(Exception e){return;}
        FrameLayout host=root;ImageView moth=new ImageView(this);moth.setImageDrawable(frames);
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(dp(84),dp(84),Gravity.BOTTOM|Gravity.END);lp.setMargins(0,0,dp(18),dp(145));host.addView(moth,lp);
        moth.post(frames::start);moth.animate().translationYBy(-dp(72)).alpha(0).setDuration(1400).withEndAction(()->host.removeView(moth)).start();
    }
    private void showThemes() {
        base("themes",theme(themeId),true);header("Themes"); TextView intro=text("Choose a look, then preview it on your Home.",15);add(content,intro,12);
        ScrollView scroll=new ScrollView(this);LinearLayout list=col();
        for(ThemeSpec t:themes){FrameLayout card=new FrameLayout(this);card.setBackground(rounded(t.tint,0xff8e82a6,22));Bitmap b=wall(t.id);
            if(b!=null){ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setImageBitmap(b);card.addView(image,new FrameLayout.LayoutParams(-1,-1));}
            View shade=new View(this);shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x00000000,0xdd141323}));card.addView(shade,new FrameLayout.LayoutParams(-1,-1));
            TextView name=text(t.name+(themeId.equals(t.id)?"  ·  Current":""),22);name.setTypeface(Typeface.DEFAULT,Typeface.BOLD);name.setPadding(dp(18),0,dp(18),dp(17));name.setShadowLayer(dp(5),0,dp(1),Color.BLACK);card.addView(name,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
            card.setContentDescription("Preview "+t.name);card.setOnClickListener(v->preview(t));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(195));lp.topMargin=dp(14);list.addView(card,lp);
        }
        scroll.addView(list);content.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
    }
    private void preview(ThemeSpec t) {
        base("preview",t,false);header("Preview");content.addView(new View(this),new LinearLayout.LayoutParams(1,0,1));
        LinearLayout panel=col();panel.setPadding(dp(18),dp(18),dp(18),dp(18));panel.setBackground(rounded(0xe8232033,t.accent,24));
        TextView title=text(t.name,26);title.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));panel.addView(title);add(panel,text(t.subtitle,14),7);
        LinearLayout sample=row();for(int i=0;i<Math.min(4,apps.size());i++){App a=apps.get(i);ImageView icon=new ImageView(this);icon.setImageDrawable(a.info.loadIcon(getPackageManager()));icon.setPadding(dp(6),dp(6),dp(6),dp(6));icon.setBackground(rounded(0xcc302b48,t.accent,16));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(50),dp(50));lp.rightMargin=dp(9);sample.addView(icon,lp);}add(panel,sample,16);
        add(panel,text("Your shortcuts stay exactly where you placed them.",13),12);
        TextView apply=button("Apply theme",()->{prefs.edit().putString("previous_theme",themeId).putString("theme",t.id).apply();themeId=t.id;showHome();themeFlourish();toast("Theme applied to Starlight Home");});apply.setTextColor(0xff282136);apply.setBackground(rounded(t.accent,0,18));add(panel,apply,16);add(content,panel,0);
    }
    private boolean isHome() {
        if(Build.VERSION.SDK_INT>=29){RoleManager role=getSystemService(RoleManager.class);if(role!=null&&role.isRoleAvailable(RoleManager.ROLE_HOME))return role.isRoleHeld(RoleManager.ROLE_HOME);}
        ResolveInfo r=getPackageManager().resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),PackageManager.MATCH_DEFAULT_ONLY);
        return r!=null && r.activityInfo!=null && getPackageName().equals(r.activityInfo.packageName);
    }
    private void homeChoice() {
        new AlertDialog.Builder(this).setTitle("Choose your Home app").setMessage(isHome()?"Starlight is your current Home. You can switch back to Samsung Home in Android settings.":"You're previewing Starlight. Android still uses your current Home. Choosing Starlight won't erase your Samsung Home layout; you can switch back in Android settings.")
        .setPositiveButton(isHome()?"Home settings":"Choose Starlight",(d,w)->{if(isHome())homeSettings();else requestHome();}).setNegativeButton("Not now",null).show();
    }
    private void requestHome() {
        try {if(Build.VERSION.SDK_INT>=29){RoleManager role=getSystemService(RoleManager.class);if(role!=null && role.isRoleAvailable(RoleManager.ROLE_HOME)){startActivityForResult(role.createRequestRoleIntent(RoleManager.ROLE_HOME),42);return;}}homeSettings();}catch(Exception e){homeSettings();}
    }
    private void homeSettings() {try{startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));}catch(Exception e){try{startActivity(new Intent(Settings.ACTION_SETTINGS));}catch(Exception ignored){toast("Open Android settings to choose Home");}}}
    private void showSettings() {
        base("settings",theme(themeId),true);header("Home settings");
        add(content,text(isHome()?"Starlight is your Home":"You are previewing Starlight",18),18);
        add(content,button("Choose default Home",this::homeChoice),14);add(content,button("Switch to Samsung Home",this::homeSettings),8);
        add(content,button("Undo last theme",()->{String prev=prefs.getString("previous_theme",themeId);prefs.edit().putString("previous_theme",themeId).putString("theme",prev).apply();themeId=theme(prev).id;showHome();}),8);
        add(content,button(prefs.getBoolean("ambient",true)?"Ambient effect: on":"Ambient effect: off",()->{prefs.edit().putBoolean("ambient",!prefs.getBoolean("ambient",true)).apply();showSettings();}),8);
        add(content,text("Touch and hold an icon to move it. Drop it onto another icon to make a folder. Swipe up for all apps. Long-press an app in the drawer to add it to Home. Your Samsung Home and its layout stay separate.",14),18);
        add(content,text("Widgets, detailed appearance controls, layout backup and more themes are still being developed.",13),18);
    }
    @Override public boolean dispatchTouchEvent(MotionEvent e) {
        if(e.getAction()==MotionEvent.ACTION_DOWN){downX=e.getX();downY=e.getY();}
        else if(e.getAction()==MotionEvent.ACTION_UP && screen.equals("home") && !dragging){float dx=e.getX()-downX,dy=e.getY()-downY;
            if(dy < -dp(110) && Math.abs(dx)<dp(65)){MotionEvent cancel=MotionEvent.obtain(e);cancel.setAction(MotionEvent.ACTION_CANCEL);super.dispatchTouchEvent(cancel);cancel.recycle();showDrawer();return true;}}
        return super.dispatchTouchEvent(e);
    }
}
