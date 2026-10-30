package com.novasky.starlightthemes;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.role.RoleManager;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.animation.ValueAnimator;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
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
import android.util.SizeF;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewConfiguration;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RemoteViews;
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

/** A separate, preview-first Home with an early native widget host. */
public final class MainActivity extends Activity {
    private static final String PREFS = "starlight_home_v1";
    private static final int WIDGET_HOST_ID=18331, REQUEST_WIDGET_BIND=501, REQUEST_WIDGET_CONFIG=502;
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
    private TextView dropRemove, pageDots, dragHelp;
    private GridView drawerGrid;
    private TextView drawerEmpty;
    private EditText search;
    private float downX, downY;
    private Runnable edgeTask;
    private Runnable folderHoverTask;
    private WorkspaceModel.Pos dragTarget;
    private View dragTargetView;
    private boolean folderDropReady;
    private int dragOffsetCol,dragOffsetRow;
    private AppWidgetHost widgetHost;
    private AppWidgetManager widgetManager;
    private int pendingWidgetId=-1, pendingPage, pendingSlot, pendingWidth, pendingHeight;
    private String pendingProvider;
    private ImageView ambient;
    private ValueAnimator ambientAnimator;
    private static final String[] ICON_KEYS={"auto","original","notes","dream","atlas","video","reader","camera","music","files","settings","messages","calendar","browser","phone","gallery","weather","clock","search","apps"};
    private static final String[] ICON_LABELS={"Themed automatically","Original app icon","Notes","Art / Dream","Atlas","Video","Reader","Camera","Music","Files","Settings","Messages","Calendar","Browser","Phone","Gallery","Weather","Clock","Search","App drawer"};
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
        widgetHost=new AppWidgetHost(this,WIDGET_HOST_ID);widgetManager=AppWidgetManager.getInstance(this);
        pendingWidgetId=prefs.getInt("pending_widget_id",-1);pendingPage=prefs.getInt("pending_widget_page",0);
        pendingSlot=prefs.getInt("pending_widget_slot",0);pendingWidth=prefs.getInt("pending_widget_width",1);pendingHeight=prefs.getInt("pending_widget_height",1);
        pendingProvider=prefs.getString("pending_widget_provider",null);
        themeId = theme(prefs.getString("theme", "moonflower")).id;
        model = readLayout(); currentPage = Math.max(0, Math.min(prefs.getInt("page", 0), model.pages.size()-1));
        // A previous version could save placement and pending state separately.
        // Never delete an ID that already belongs to the saved workspace.
        if(pendingWidgetId>0 && model.containsWidget(pendingWidgetId))clearPendingWidget();
        showHome(); loadApps();
    }
    @Override protected void onStart() {super.onStart();try{widgetHost.startListening();}catch(Exception ignored){}}
    @Override protected void onStop() {try{widgetHost.stopListening();}catch(Exception ignored){}super.onStop();}
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
            if(!m.validLayout())throw new IllegalArgumentException("overlapping items");
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
        if (type.equals("widget")) return WorkspaceModel.Item.widget(o.getInt("id"),o.getInt("width"),o.getInt("height"));
        if (!type.equals("folder")) throw new IllegalArgumentException("type");
        JSONArray members=o.getJSONArray("children"); ArrayList<String> children=new ArrayList<>();
        for (int i=0; i<members.length(); i++) children.add(members.getString(i));
        return WorkspaceModel.Item.folder(o.optString("title", "Folder"), children);
    }
    private JSONObject writeItem(WorkspaceModel.Item item) throws Exception {
        if (item == null) return null;
        JSONObject o=new JSONObject();
        if (item.isWidget()) { o.put("type","widget");o.put("id",item.widgetId);o.put("width",item.spanX);o.put("height",item.spanY); }
        else if (!item.isFolder()) { o.put("type", "app"); o.put("component", item.component); }
        else { o.put("type", "folder"); o.put("title", item.title); o.put("children", new JSONArray(item.children)); }
        return o;
    }
    private String layoutSnapshot() throws Exception {
        JSONObject o=new JSONObject(); JSONArray pages=new JSONArray(), dock=new JSONArray();
        for (WorkspaceModel.Item[] page : model.pages) { JSONArray a=new JSONArray(); for (WorkspaceModel.Item i:page) a.put(writeItem(i)); pages.put(a); }
        for (WorkspaceModel.Item i:model.dock) dock.put(writeItem(i));
        o.put("pages", pages); o.put("dock", dock);return o.toString();
    }
    private boolean persistLayout(boolean sync, boolean finishPending) {
        try {
            SharedPreferences.Editor edit=prefs.edit().putString("layout",layoutSnapshot()).putInt("page",currentPage);
            if(finishPending)removePendingFields(edit);
            if(sync)return edit.commit();
            edit.apply(); return true;
        } catch (Exception e) { return false; }
    }
    private void saveLayout() { if(!persistLayout(false,false))toast("Could not save the layout"); }
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
        // Let the person choose the home-page shortcuts instead of filling it
        // with the first alphabetic apps found on the phone.
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
    private String iconChoice(App app) {
        if(app==null)return "original";
        if(!prefs.getBoolean("themed_icons",true))return "original";
        String choice=prefs.getString("icon:"+app.key,"auto");
        boolean valid=false;for(String candidate:ICON_KEYS)if(candidate.equals(choice))valid=true;
        if(!valid)choice="auto";
        if(choice.equals("auto"))return IconClassifier.classify(app.component.getPackageName(),app.label);
        return choice;
    }
    private Bitmap illustratedIcon(String choice,String selectedTheme) {
        if(choice.equals("original") || choice.equals("frame"))return null;
        String palette=selectedTheme.equals("moonflower")?"petal":"midnight";
        String cacheKey=palette+"_"+choice; Bitmap cached=iconArt.get(cacheKey);if(cached!=null)return cached;
        try(InputStream in=getAssets().open("icons/"+palette+"_"+choice+".png")){
            BitmapFactory.Options opts=new BitmapFactory.Options();opts.inSampleSize=2;Bitmap image=BitmapFactory.decodeStream(in,null,opts);if(image!=null)iconArt.put(cacheKey,image);return image;
        }catch(Exception ignored){return null;}
    }
    /** A complete themed tile, retaining a small app mark so similar apps stay recognizable. */
    private View appIcon(App app,String selectedTheme,int size,boolean badge) {
        FrameLayout frame=new FrameLayout(this);String choice=iconChoice(app);Bitmap art=illustratedIcon(choice,selectedTheme);
        if(art!=null){
            ImageView image=new ImageView(this);image.setImageBitmap(art);image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            frame.addView(image,new FrameLayout.LayoutParams(-1,-1));
        } else if(!choice.equals("original")) {
            int[] colors=selectedTheme.equals("moonflower")?new int[]{0xfff2deed,0xffd5c0df}:new int[]{0xff382453,0xff241a40};
            GradientDrawable background=new GradientDrawable(GradientDrawable.Orientation.TL_BR,colors);
            background.setCornerRadius(size*.24f);background.setStroke(dp(1),selectedTheme.equals("moonflower")?0xffaa8eba:0xff8b78a5);
            frame.setBackground(background);
            TextView star=text("✦",8);star.setTextColor(selectedTheme.equals("moonflower")?0xffa96287:0xffe8c8ed);
            FrameLayout.LayoutParams sp=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.END);sp.setMargins(0,dp(1),dp(3),0);frame.addView(star,sp);
        }
        if(app!=null)try{
            ImageView mark=new ImageView(this);mark.setImageDrawable(app.info.loadIcon(getPackageManager()));mark.setScaleType(ImageView.ScaleType.FIT_CENTER);
            boolean small=art!=null && badge;
            int markSize=small?Math.max(dp(15),Math.round(size*.31f)):art!=null?0:Math.round(size*(choice.equals("original")?.84f:.65f));
            if(markSize>0){
                if(small){mark.setPadding(dp(1),dp(1),dp(1),dp(1));mark.setBackground(rounded(0xfffbf7ff,0xffb5a3bf,7));}
                FrameLayout.LayoutParams mp=new FrameLayout.LayoutParams(markSize,markSize,small?Gravity.BOTTOM|Gravity.END:Gravity.CENTER);
                if(small)mp.setMargins(0,0,dp(1),dp(1));frame.addView(mark,mp);
            }
        }catch(Exception ignored){}
        if(app==null){TextView missing=text("?",18);missing.setGravity(Gravity.CENTER);frame.addView(missing,new FrameLayout.LayoutParams(-1,-1));}
        return frame;
    }
    private void base(String which,ThemeSpec t,boolean dim) {
        stopAmbient(); ambient=null;
        screen=which; workspace=null; dropRemove=null; pageDots=null; dragHelp=null; drawerGrid=null; drawerEmpty=null; search=null;
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
        boolean emptyPage=true;for(WorkspaceModel.Item entry:model.pages.get(currentPage))if(entry!=null){emptyPage=false;break;}
        if(emptyPage)add(content,button("Choose your favorite apps",this::chooseFavorites),4);
        dropRemove=button("Remove shortcut",()->{}); dropRemove.setVisibility(View.GONE); dropRemove.setBackground(rounded(0xee492e43,0xffedbad5,18));
        dropRemove.setOnDragListener((view,event)->{
            if(event.getAction()==DragEvent.ACTION_DRAG_STARTED) return event.getLocalState() instanceof WorkspaceModel.Pos;
            if(event.getAction()==DragEvent.ACTION_DROP) {
                WorkspaceModel.Pos p=(WorkspaceModel.Pos)event.getLocalState();WorkspaceModel.Item item=model.get(p);
                if(item!=null){
                    model.put(p,null);
                    if(item.isWidget()){
                        if(!persistLayout(true,false)){model.put(p,item);persistLayout(true,false);toast("Couldn't save the removal");}
                        else try{widgetHost.deleteAppWidgetId(item.widgetId);}catch(Exception ignored){}
                    }else saveLayout();
                }
                return true;
            }
            return true;
        });
        FrameLayout.LayoutParams removeParams=new FrameLayout.LayoutParams(-1,dp(52),Gravity.TOP);
        removeParams.setMargins(dp(14),dp(58),dp(14),0); root.addView(dropRemove,removeParams);
        dragHelp=text("Drag to move · pause over an icon to group",12);dragHelp.setGravity(Gravity.CENTER);
        dragHelp.setPadding(dp(12),dp(6),dp(12),dp(6));dragHelp.setBackground(rounded(0xee242037,0x99ddc6e8,16));dragHelp.setVisibility(View.GONE);
        FrameLayout.LayoutParams helpParams=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
        helpParams.bottomMargin=dp(148);root.addView(dragHelp,helpParams);
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
            int slot=Math.min(3,Math.max(0,(int)(e.getX()*4/Math.max(1,v.getWidth()))));
            WorkspaceModel.Pos dest=new WorkspaceModel.Pos(-1,slot);
            if(e.getAction()==DragEvent.ACTION_DRAG_LOCATION){updateDragTarget((WorkspaceModel.Pos)e.getLocalState(),dest,dock.getChildAt(slot));return true;}
            if(e.getAction()==DragEvent.ACTION_DRAG_EXITED){clearDragTarget();return true;}
            if(e.getAction()==DragEvent.ACTION_DROP) {boolean combine=folderDropReady && dest.equals(dragTarget);clearDragTarget();if(model.move((WorkspaceModel.Pos)e.getLocalState(),dest,combine))saveLayout();return true;}
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
            for(int i=0;i<WorkspaceModel.CELLS;i++){WorkspaceModel.Pos p=new WorkspaceModel.Pos(page,i); WorkspaceModel.Item item=model.get(p);addView(itemView(item!=null && item.isWidget()?null:item,p,false));}
            for(int i=0;i<WorkspaceModel.CELLS;i++){
                WorkspaceModel.Pos pos=new WorkspaceModel.Pos(page,i);WorkspaceModel.Item item=model.get(pos);
                if(item!=null && item.isWidget()){View widget=widgetView(item,pos);widget.setTag(pos);addView(widget);}
            }
            setOnDragListener((v,e)->{
                if(e.getAction()==DragEvent.ACTION_DRAG_STARTED)return e.getLocalState() instanceof WorkspaceModel.Pos;
                if(e.getAction()==DragEvent.ACTION_DRAG_LOCATION){
                    maybeEdge(page,e.getX(),v.getWidth());
                    WorkspaceModel.Pos dest=dragDestination(page,e.getX(),e.getY(),v.getWidth(),v.getHeight());
                    if(dest!=null)updateDragTarget((WorkspaceModel.Pos)e.getLocalState(),dest,getChildAt(dest.slot));
                    else {clearDragTarget();if(dragHelp!=null)dragHelp.setText("The widget needs more free space");}
                    return true;
                }
                if(e.getAction()==DragEvent.ACTION_DRAG_EXITED){cancelEdge();clearDragTarget();return true;}
                if(e.getAction()==DragEvent.ACTION_DROP){
                    cancelEdge();WorkspaceModel.Pos dest=dragDestination(page,e.getX(),e.getY(),v.getWidth(),v.getHeight());
                    boolean combine=folderDropReady && dest!=null && dest.equals(dragTarget);clearDragTarget();
                    WorkspaceModel.Pos origin=(WorkspaceModel.Pos)e.getLocalState();WorkspaceModel.Item moving=model.get(origin);
                    if(dest!=null && model.move(origin,dest,combine))saveLayout();else if(dest==null || !dest.equals(origin))toast(moving!=null && moving.isWidget()?"The widget needs more free space there":"That spot is occupied by a widget");return true;
                }
                if(e.getAction()==DragEvent.ACTION_DRAG_ENDED){cancelEdge();finishDrag();} return true;
            });
        }
        @Override protected void onMeasure(int w,int h) {
            int width=MeasureSpec.getSize(w),height=MeasureSpec.getSize(h); setMeasuredDimension(width,height);
            for(int i=0;i<getChildCount();i++){
                View child=getChildAt(i);int sx=1,sy=1;
                if(i>=WorkspaceModel.CELLS){WorkspaceModel.Item item=model.get((WorkspaceModel.Pos)child.getTag());if(item!=null){sx=item.spanX;sy=item.spanY;}}
                child.measure(MeasureSpec.makeMeasureSpec((width/4)*sx,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec((height/5)*sy,MeasureSpec.EXACTLY));
            }
        }
        @Override protected void onLayout(boolean c,int l,int t,int r,int b) {
            int cw=(r-l)/4,ch=(b-t)/5;
            for(int i=0;i<getChildCount();i++){
                View child=getChildAt(i);int slot=i,sx=1,sy=1;
                if(i>=WorkspaceModel.CELLS){WorkspaceModel.Pos p=(WorkspaceModel.Pos)child.getTag();slot=p.slot;WorkspaceModel.Item item=model.get(p);if(item!=null){sx=item.spanX;sy=item.spanY;}}
                int x=(slot%4)*cw,y=(slot/4)*ch;child.layout(x,y,x+cw*sx,y+ch*sy);
            }
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
    private WorkspaceModel.Pos dragDestination(int page,float x,float y,int width,int height) {
        int col=Math.max(0,Math.min(3,(int)(x*4/Math.max(1,width))))-dragOffsetCol;
        int row=Math.max(0,Math.min(4,(int)(y*5/Math.max(1,height))))-dragOffsetRow;
        return col<0 || row<0?null:new WorkspaceModel.Pos(page,row*4+col);
    }
    private void updateDragTarget(WorkspaceModel.Pos from,WorkspaceModel.Pos to,View cell) {
        if(to.equals(dragTarget) && cell==dragTargetView)return;
        clearDragTarget();dragTarget=to;dragTargetView=cell;
        WorkspaceModel.Item source=model.get(from),target=model.get(to);
        WorkspaceModel.Pos occupant=model.occupant(to);
        boolean movingWidget=source!=null && source.isWidget();
        boolean blocked=movingWidget?!model.regionFree(to,source.spanX,source.spanY,from):occupant!=null && model.get(occupant).isWidget();
        if(cell!=null)cell.setBackground(rounded(blocked?0x447a394c:0x553a3454,blocked?0xaaff9a9a:0xaaddc5ed,16));
        if(dragHelp!=null)dragHelp.setText(blocked?(movingWidget?"The widget needs more free space":"That space belongs to a widget"):movingWidget?"Drag widget to a free space":"Drag to move · pause over an icon to group");
        boolean canGroup=source!=null && !source.isWidget() && !source.isFolder() && target!=null && !target.isWidget() && !from.equals(to);
        if(canGroup){
            folderHoverTask=()->{
                folderHoverTask=null;
                if(!dragging || !to.equals(dragTarget))return;
                folderDropReady=true;if(dragTargetView!=null){dragTargetView.setBackground(rounded(0x995f437b,0xfff5d8f4,16));dragTargetView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);}
                if(dragHelp!=null)dragHelp.setText(target.isFolder()?"Release to add to folder":"Release to create a folder");
            };
            ui.postDelayed(folderHoverTask,600);
        }
    }
    private void clearDragTarget(){
        if(folderHoverTask!=null){ui.removeCallbacks(folderHoverTask);folderHoverTask=null;}
        if(dragTargetView!=null)dragTargetView.setBackground(null);
        dragTarget=null;dragTargetView=null;folderDropReady=false;
    }
    private void finishDrag(){if(!dragging)return;dragging=false;cancelEdge();clearDragTarget();if(dropRemove!=null)dropRemove.setVisibility(View.GONE);if(dragHelp!=null)dragHelp.setVisibility(View.GONE);ui.post(this::showHome);}
    private boolean startMoving(View view,WorkspaceModel.Pos pos) {return startMoving(view,pos,view.getWidth()/2,view.getHeight()/2);}
    private boolean startMoving(View view,WorkspaceModel.Pos pos,int touchX,int touchY) {
        WorkspaceModel.Item item=model.get(pos);if(item==null || dragging)return false;
        dragOffsetCol=item.isWidget()?Math.min(item.spanX-1,Math.max(0,touchX/Math.max(1,view.getWidth()/item.spanX))):0;
        dragOffsetRow=item.isWidget()?Math.min(item.spanY-1,Math.max(0,touchY/Math.max(1,view.getHeight()/item.spanY))):0;
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);dragging=true;
        if(dropRemove!=null){dropRemove.setText(item.isWidget()?"Remove widget":"Remove shortcut");dropRemove.setVisibility(View.VISIBLE);}
        if(dragHelp!=null){dragHelp.setText(item.isWidget()?"Drag widget to a free space":"Drag to move · pause over an icon to group");dragHelp.setVisibility(View.VISIBLE);}
        View.DragShadowBuilder shadow=new View.DragShadowBuilder(view){
            @Override public void onProvideShadowMetrics(Point size,Point touch){size.set(view.getWidth(),view.getHeight());touch.set(touchX,touchY);}
        };
        boolean started=view.startDragAndDrop(ClipData.newPlainText(item.isWidget()?"widget":"shortcut",""),shadow,pos,0);
        if(started)view.setAlpha(.35f);else finishDrag();return started;
    }
    private View itemView(WorkspaceModel.Item item, WorkspaceModel.Pos pos, boolean dock) {
        LinearLayout tile=col(); tile.setGravity(Gravity.CENTER); tile.setPadding(dp(2),dp(2),dp(2),dp(2));
        if(item==null){tile.setContentDescription("Empty position");return tile;}
        FrameLayout iconFrame=new FrameLayout(this); iconFrame.setBackground(rounded(0xcc302b48,0xbbddc9e6,20));
        int iconSize=dp(dock?51:56);
        if(item.isFolder()) {
            iconFrame.setBackground(libraryBackground());
            LinearLayout preview=col(); preview.setPadding(dp(6),dp(5),dp(6),dp(3));
            for(int r=0;r<2;r++){
                LinearLayout line=row();for(int c=0;c<2;c++){int n=r*2+c;App a=n<item.children.size()?byKey.get(item.children.get(n)):null;View icon=n<item.children.size()?appIcon(a,themeId,dp(17),false):new View(this);line.addView(icon,new LinearLayout.LayoutParams(0,dp(17),1));}
                preview.addView(line);View shelf=new View(this);shelf.setBackgroundColor(themeId.equals("moonflower")?0xffcb9e9d:0xffbba0aa);preview.addView(shelf,new LinearLayout.LayoutParams(-1,dp(2)));
            }
            iconFrame.addView(preview,new FrameLayout.LayoutParams(-1,-1));
        } else {
            iconFrame.setBackgroundColor(Color.TRANSPARENT);
            iconFrame.addView(appIcon(byKey.get(item.component),themeId,iconSize,true),new FrameLayout.LayoutParams(-1,-1));
        }
        tile.addView(iconFrame,new LinearLayout.LayoutParams(iconSize,iconSize));
        String label=item.isFolder()?item.title:(byKey.containsKey(item.component)?byKey.get(item.component).label:"Unavailable");
        TextView name=text(label,11); name.setGravity(Gravity.CENTER); name.setMaxLines(1); name.setEllipsize(TextUtils.TruncateAt.END); name.setShadowLayer(dp(4),0,dp(1),Color.BLACK); tile.addView(name,new LinearLayout.LayoutParams(-1,-2));
        tile.setContentDescription(label+(item.isFolder()?", folder":"")); tile.setFocusable(true); tile.setClickable(true);
        tile.setOnClickListener(v->{if(item.isFolder())openFolder(pos);else launch(item.component);});
        tile.setOnLongClickListener(v->startMoving(v,pos));
        return tile;
    }
    private void openFolder(WorkspaceModel.Pos pos) {
        WorkspaceModel.Item folder=model.get(pos); if(folder==null || !folder.isFolder())return;
        LinearLayout shelves=col();shelves.setPadding(dp(10),dp(12),dp(10),dp(12));shelves.setBackground(libraryBackground());
        TextView hint=text("Hold a book for more options",12);hint.setTextColor(0xffead8e6);add(shelves,hint,0);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);scroll.addView(shelves);
        LinearLayout titleRow=row();titleRow.setPadding(dp(22),dp(18),dp(22),dp(12));
        TextView heading=text(folder.title,21);heading.setTypeface(Typeface.DEFAULT,Typeface.BOLD);titleRow.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        TextView sparkle=text("✦",18);sparkle.setTextColor(0xffeac5d8);titleRow.addView(sparkle);
        AlertDialog dialog=new AlertDialog.Builder(this).setCustomTitle(titleRow).setView(scroll).setNegativeButton("Close",null).setNeutralButton("Rename",(d,w)->renameFolder(pos)).create();
        int[] bookColors=themeId.equals("moonflower")?new int[]{0xff79526a,0xff745773,0xff705666,0xff805768}:new int[]{0xff49415f,0xff514362,0xff3d4b62,0xff58455b};
        for(int start=0;start<folder.children.size();start+=4){
            LinearLayout row=row();row.setGravity(Gravity.BOTTOM);
            for(int offset=0;offset<4;offset++){
                int index=start+offset;LinearLayout shelfSlot=col();shelfSlot.setGravity(Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
                if(index<folder.children.size()){
                    String key=folder.children.get(index);App app=byKey.get(key);
                    FrameLayout book=new FrameLayout(this);book.setBackground(rounded(bookColors[index%bookColors.length],0xffd7b8c4,6));
                    View spine=new View(this);spine.setBackgroundColor(0x66f0cdd2);FrameLayout.LayoutParams stripe=new FrameLayout.LayoutParams(dp(3),-1,Gravity.START);stripe.leftMargin=dp(4);book.addView(spine,stripe);
                    FrameLayout.LayoutParams image=new FrameLayout.LayoutParams(dp(43),dp(43),Gravity.CENTER);image.leftMargin=dp(3);book.addView(appIcon(app,themeId,dp(43),true),image);
                    shelfSlot.addView(book,new LinearLayout.LayoutParams(dp(58),dp(63)));
                    String label=app==null?"Unavailable":app.label;TextView name=text(label,10);name.setGravity(Gravity.CENTER);name.setMaxLines(2);name.setEllipsize(TextUtils.TruncateAt.END);shelfSlot.addView(name,new LinearLayout.LayoutParams(-1,dp(30)));
                    shelfSlot.setContentDescription(label);shelfSlot.setClickable(true);shelfSlot.setFocusable(true);
                    shelfSlot.setOnClickListener(v->{dialog.dismiss();launch(key);});
                    shelfSlot.setOnLongClickListener(v->{v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);dialog.dismiss();folderMemberOptions(pos,index);return true;});
                }
                row.addView(shelfSlot,new LinearLayout.LayoutParams(0,dp(97),1));
            }
            add(shelves,row,8);
            View shelf=new View(this);shelf.setBackground(rounded(themeId.equals("moonflower")?0xffc59591:0xffa78e9e,0xffdfc1be,3));shelves.addView(shelf,new LinearLayout.LayoutParams(-1,dp(5)));
        }
        dialog.show();
        if(dialog.getWindow()!=null)dialog.getWindow().setBackgroundDrawable(rounded(0xff292337,0xffb798ac,20));
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(0xffe8cce5);
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(0xffe8cce5);
    }
    private GradientDrawable libraryBackground() {
        int[] colors=themeId.equals("moonflower")?new int[]{0xff5d354c,0xff322339}:new int[]{0xff393047,0xff211c32};
        GradientDrawable wood=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,colors);
        wood.setCornerRadius(dp(15));wood.setStroke(dp(2),themeId.equals("moonflower")?0xffc397a1:0xffad97a8);return wood;
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
        new AlertDialog.Builder(this).setTitle("Home options").setItems(new String[]{"Themes","Add a home page","Remove this empty page","Widgets","Choose favorite apps","Undo favorite changes","Choose default Home","Home settings"},(d,n)->{
            switch(n){case 0:showThemes();break;case 1:if(model.addPage()){currentPage=model.pages.size()-1;saveLayout();showHome();}else toast("Maximum of eight pages");break;
            case 2:if(model.removePage(currentPage)){currentPage=Math.min(currentPage,model.pages.size()-1);saveLayout();showHome();}else toast("Keep one page and remove its shortcuts first");break;
            case 3:widgetMenu();break;case 4:chooseFavorites();break;case 5:undoFavorites();break;case 6:homeChoice();break;case 7:showSettings();break;}
        }).show();
    }
    private void chooseFavorites() {
        if(apps.isEmpty()){toast(loading?"Finding apps…":"No apps are available yet");return;}
        final ArrayList<App> choices=new ArrayList<>(apps);
        final int pageNumber=currentPage;String[] labels=new String[choices.size()];boolean[] selected=new boolean[choices.size()];
        HashSet<String> current=new HashSet<>();
        for(WorkspaceModel.Item item:model.pages.get(pageNumber))
            if(item!=null && !item.isWidget() && !item.isFolder())current.add(item.component);
        for(int i=0;i<choices.size();i++){labels[i]=choices.get(i).label;selected[i]=current.contains(choices.get(i).key);}
        LinearLayout dialogTitle=col();dialogTitle.setPadding(dp(24),dp(18),dp(24),dp(8));
        TextView title=new TextView(this);title.setText("Choose favorite apps");title.setTextSize(20);title.setTextColor(0xff302638);dialogTitle.addView(title);
        TextView detail=new TextView(this);detail.setText("Replaces this page's app shortcuts in alphabetical order. Folders and widgets stay; you can undo in Home options.");detail.setTextSize(13);detail.setTextColor(0xff554c5a);add(dialogTitle,detail,6);
        AlertDialog dialog=new AlertDialog.Builder(this).setCustomTitle(dialogTitle)
                .setMultiChoiceItems(labels,selected,(d,n,on)->selected[n]=on)
                .setPositiveButton("Set favorites",null).setNeutralButton("Clear selection",null).setNegativeButton("Cancel",null).create();
        dialog.setOnShowListener(ignored->{
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{
                for(int i=0;i<selected.length;i++){selected[i]=false;dialog.getListView().setItemChecked(i,false);}
            });
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                if(pageNumber>=model.pages.size()){dialog.dismiss();return;}
                WorkspaceModel.Item[] page=model.pages.get(pageNumber),original=page.clone();
                String previous;
                try{previous=layoutSnapshot();}catch(Exception e){toast("Couldn't save the current layout");return;}
                // Preserve folders, widgets, and unavailable shortcuts; only replace ordinary available app icons.
                for(int i=0;i<page.length;i++){
                    WorkspaceModel.Item item=page[i];
                    if(item!=null && !item.isWidget() && !item.isFolder() && byKey.containsKey(item.component))page[i]=null;
                }
                for(int i=0;i<selected.length;i++)if(selected[i] && !model.addToPage(choices.get(i).key,pageNumber)){
                    System.arraycopy(original,0,page,0,page.length);toast("Too many favorites for this page");return;
                }
                try {
                    String updated=layoutSnapshot();
                    SharedPreferences.Editor edit=prefs.edit().putString("layout",updated).putInt("page",currentPage);
                    if(!previous.equals(updated))edit.putString("favorites_before",previous).putString("favorites_after",updated);
                    if(!edit.commit())throw new IllegalStateException("save failed");
                }catch(Exception e){
                    System.arraycopy(original,0,page,0,page.length);persistLayout(true,false);toast("Couldn't save your favorites");return;
                }
                dialog.dismiss();showHome();toast("Favorites set. Undo is in Home options.");
            });
        });dialog.show();
    }
    private void undoFavorites() {
        String previous=prefs.getString("favorites_before",null),after=prefs.getString("favorites_after",null);
        if(previous==null || after==null){toast("There is no favorites change to undo");return;}
        if(!after.equals(prefs.getString("layout",null))){toast("The layout changed since then; favorites can't be undone safely");return;}
        if(!prefs.edit().putString("layout",previous).remove("favorites_before").remove("favorites_after").commit()){
            toast("Couldn't restore the previous layout");return;
        }
        model=readLayout();currentPage=Math.min(currentPage,model.pages.size()-1);showHome();toast("Previous layout restored");
    }
    private final class WidgetFrame extends FrameLayout {
        final WorkspaceModel.Pos position;
        final int touchSlop;
        float pressX,pressY;
        Runnable longPress;
        WidgetFrame(Context context,WorkspaceModel.Pos pos){super(context);position=pos;touchSlop=ViewConfiguration.get(context).getScaledTouchSlop();setClickable(true);}
        private void clearWidgetPress(){if(longPress!=null){ui.removeCallbacks(longPress);longPress=null;}}
        @Override public boolean dispatchTouchEvent(MotionEvent event){
            switch(event.getActionMasked()){
                case MotionEvent.ACTION_DOWN:
                    clearWidgetPress();pressX=event.getX();pressY=event.getY();
                    longPress=()->{longPress=null;startMoving(this,position,Math.round(pressX),Math.round(pressY));};
                    ui.postDelayed(longPress,ViewConfiguration.getLongPressTimeout());break;
                case MotionEvent.ACTION_MOVE:
                    if(Math.abs(event.getX()-pressX)>touchSlop || Math.abs(event.getY()-pressY)>touchSlop)clearWidgetPress();break;
                case MotionEvent.ACTION_UP:case MotionEvent.ACTION_CANCEL:clearWidgetPress();break;
            }
            return super.dispatchTouchEvent(event);
        }
        @Override protected void onDetachedFromWindow(){clearWidgetPress();super.onDetachedFromWindow();}
    }
    private View widgetView(WorkspaceModel.Item item,WorkspaceModel.Pos pos) {
        FrameLayout frame=new WidgetFrame(this,pos);
        try {
            AppWidgetProviderInfo info=widgetManager.getAppWidgetInfo(item.widgetId);
            if(info==null)throw new IllegalStateException("Provider missing");
            AppWidgetHostView view=widgetHost.createView(this,item.widgetId,info);
            frame.addView(view,new FrameLayout.LayoutParams(-1,-1));
            view.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,oright,ob)->{
                if((r-l)!=(oright-ol) || (b-t)!=(ob-ot)){
                    int w=Math.round((r-l)/getResources().getDisplayMetrics().density),h=Math.round((b-t)/getResources().getDisplayMetrics().density);
                    if(w>0 && h>0)try{
                        if(Build.VERSION.SDK_INT>=31)view.updateAppWidgetSize(new Bundle(),Collections.singletonList(new SizeF(w,h)));
                        else view.updateAppWidgetSize(null,w,h,w,h);
                    }catch(Exception ignored){}
                }
            });
        }catch(Exception e){TextView missing=text("Widget unavailable\nRemove in Home options",13);missing.setGravity(Gravity.CENTER);missing.setBackground(rounded(0xbb242034,0x88dfc6e7,18));frame.addView(missing,new FrameLayout.LayoutParams(-1,-1));}
        return frame;
    }
    private void widgetMenu() {
        String[] choices=pendingWidgetId>0
                ? new String[]{"Add a widget","Move or resize a widget","Remove a widget","Continue unfinished setup","Cancel unfinished setup"}
                : new String[]{"Add a widget","Move or resize a widget","Remove a widget"};
        new AlertDialog.Builder(this).setTitle("Widgets").setItems(choices,(d,n)->{
            switch(n){case 0:chooseWidget();break;case 1:editWidget();break;case 2:removeWidget();break;
                case 3:continuePendingWidget();break;case 4:cancelPendingWidget();toast("Unfinished widget setup cancelled");break;}
        }).show();
    }
    private void chooseWidget() {
        if(pendingWidgetId>0){toast("Finish the current widget setup first");return;}
        ArrayList<WidgetGroup> groups=new ArrayList<>();HashMap<String,WidgetGroup> packages=new HashMap<>();
        try {
            for(AppWidgetProviderInfo info:widgetManager.getInstalledProviders()) {
                if((info.widgetCategory & AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN)==0 || info.provider==null)continue;
                String pkg=info.provider.getPackageName();WidgetGroup group=packages.get(pkg);
                if(group==null){group=new WidgetGroup(pkg);packages.put(pkg,group);groups.add(group);}
                group.options.add(new WidgetOption(info,group));
            }
        }catch(Exception e){toast("Android couldn't list widgets");return;}
        if(groups.isEmpty()){toast("No available Home widgets found");return;}
        Collections.sort(groups,(a,b)->{int n=a.name.compareToIgnoreCase(b.name);return n!=0?n:a.packageName.compareToIgnoreCase(b.packageName);});
        for(WidgetGroup group:groups){
            Collections.sort(group.options,(a,b)->{int n=a.label.compareToIgnoreCase(b.label);if(n!=0)return n;
                int[] as=widgetSize(a.info),bs=widgetSize(b.info);n=Integer.compare(as[0],bs[0]);if(n==0)n=Integer.compare(as[1],bs[1]);
                return n!=0?n:a.info.provider.flattenToString().compareTo(b.info.provider.flattenToString());});
            for(WidgetOption option:group.options)for(WidgetOption other:group.options)if(option.label.equalsIgnoreCase(other.label)){
                option.sameLabelCount++;if(other==option)option.ordinal=option.sameLabelCount;
            }
        }

        LinearLayout panel=col();panel.setPadding(dp(16),dp(8),dp(16),0);
        TextView hint=text("Choose an app to compare its widgets.",13);hint.setTextColor(0xffdfd4e5);add(panel,hint,0);
        EditText query=new EditText(this);query.setSingleLine(true);query.setTextSize(15);query.setTextColor(Color.WHITE);
        query.setHintTextColor(0xffc5b8cd);query.setHint("Search apps or widgets");query.setPadding(dp(12),0,dp(12),0);
        query.setBackground(rounded(0xff40364a,0xff9b88a4,12));add(panel,query,10);
        query.getLayoutParams().height=dp(48);
        LinearLayout tabs=row();final boolean[] audioOnly={false};
        TextView all=button("All apps",()->{}),audio=button("Music & audio",()->{});
        tabs.addView(all,new LinearLayout.LayoutParams(0,dp(48),1));LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(0,dp(48),1);ap.leftMargin=dp(8);tabs.addView(audio,ap);add(panel,tabs,10);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);LinearLayout results=col();scroll.addView(results);
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,Math.min(dp(410),getResources().getDisplayMetrics().heightPixels*44/100));sp.topMargin=dp(10);panel.addView(scroll,sp);
        LinearLayout title=col();title.setPadding(dp(22),dp(18),dp(22),dp(4));
        TextView heading=text("Add widget",22);heading.setTypeface(Typeface.DEFAULT,Typeface.BOLD);title.addView(heading);
        final AlertDialog dialog=new AlertDialog.Builder(this).setCustomTitle(title).setView(panel).setNegativeButton("Close",null).create();
        final Runnable render=()->{
            all.setBackground(rounded(audioOnly[0]?0xff362e42:0xff765a7c,0xffac92ae,14));
            audio.setBackground(rounded(audioOnly[0]?0xff765a7c:0xff362e42,0xffac92ae,14));
            all.setContentDescription("All apps"+(audioOnly[0]?"":" selected"));audio.setContentDescription("Music & audio"+(audioOnly[0]?" selected":""));
            results.removeAllViews();String term=query.getText().toString();int shown=0;
            for(WidgetGroup group:groups){
                ArrayList<WidgetOption> found=new ArrayList<>();
                for(WidgetOption option:group.options)if((!audioOnly[0] || option.audio) && WidgetFinder.matches(term,group.packageName,group.name,option.label,option.description,option.audio))found.add(option);
                if(found.isEmpty())continue;shown++;
                LinearLayout section=col();section.setPadding(dp(10),dp(8),dp(10),dp(8));section.setBackground(rounded(0xff362e42,0xff79677e,14));
                LinearLayout groupHeading=row();groupHeading.setMinimumHeight(dp(48));groupHeading.setClickable(true);groupHeading.setFocusable(true);
                ImageView icon=new ImageView(this);try{icon.setImageDrawable(found.get(0).info.loadIcon(this,0));}catch(Exception ignored){}
                icon.setScaleType(ImageView.ScaleType.FIT_CENTER);groupHeading.addView(icon,new LinearLayout.LayoutParams(dp(38),dp(38)));
                LinearLayout names=col();names.setPadding(dp(10),0,0,0);TextView name=text(group.name,16);name.setTypeface(Typeface.DEFAULT,Typeface.BOLD);names.addView(name);
                boolean containsAudio=false;for(WidgetOption option:found)if(option.audio){containsAudio=true;break;}
                TextView count=text((found.size()==1?"1 widget":found.size()+" widgets")+(containsAudio?" · Music & audio":""),12);count.setTextColor(0xffd5c5d7);names.addView(count);
                groupHeading.addView(names,new LinearLayout.LayoutParams(0,-2,1));TextView arrow=text("⌄",22);groupHeading.addView(arrow);section.addView(groupHeading);
                LinearLayout choices=col();choices.setVisibility(View.GONE);section.addView(choices);
                groupHeading.setContentDescription(group.name+", "+found.size()+" widgets, show choices");
                groupHeading.setOnClickListener(v->{
                    if(choices.getVisibility()==View.VISIBLE){choices.setVisibility(View.GONE);arrow.setText("⌄");groupHeading.setContentDescription(group.name+", "+found.size()+" widgets, show choices");}
                    else {if(choices.getChildCount()==0)for(WidgetOption option:found)add(choices,widgetChoice(option,dialog),10);
                        choices.setVisibility(View.VISIBLE);arrow.setText("⌃");groupHeading.setContentDescription(group.name+", "+found.size()+" widgets, hide choices");}
                });
                add(results,section,shown==1?0:8);
            }
            if(shown==0){TextView empty=text(audioOnly[0]?"No recognized music or audio widgets here. Check All apps too.":"No matching widgets. Try another search.",14);empty.setTextColor(0xffdfd4e5);empty.setPadding(dp(8),dp(20),dp(8),dp(16));add(results,empty,0);}
            else if(audioOnly[0]){TextView note=text("Some apps don't identify their widgets as music or audio. Check All apps too.",12);note.setTextColor(0xffd5c5d7);note.setPadding(dp(4),dp(12),dp(4),dp(8));add(results,note,0);}
        };
        all.setOnClickListener(v->{audioOnly[0]=false;render.run();scroll.scrollTo(0,0);});
        audio.setOnClickListener(v->{audioOnly[0]=true;render.run();scroll.scrollTo(0,0);});
        query.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){render.run();scroll.scrollTo(0,0);}public void afterTextChanged(Editable e){}});
        render.run();dialog.show();
        if(dialog.getWindow()!=null){dialog.getWindow().setBackgroundDrawable(rounded(0xff292337,0xffb798ac,20));dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE|WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);}
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(0xffe8cce5);
    }
    private final class WidgetGroup {
        final String packageName,name;final boolean audio;final ArrayList<WidgetOption> options=new ArrayList<>();
        WidgetGroup(String pkg){packageName=pkg;String appName=pkg;boolean isAudio=false;
            try{ApplicationInfo app=getPackageManager().getApplicationInfo(pkg,0);CharSequence label=getPackageManager().getApplicationLabel(app);
                if(label!=null && !label.toString().trim().isEmpty())appName=label.toString();isAudio=app.category==ApplicationInfo.CATEGORY_AUDIO;
            }catch(Exception ignored){}name=appName;audio=isAudio;}
    }
    private final class WidgetOption {
        final AppWidgetProviderInfo info;final WidgetGroup group;final String label,description;final boolean audio;int ordinal,sameLabelCount;
        WidgetOption(AppWidgetProviderInfo i,WidgetGroup g){info=i;group=g;label=widgetLabel(i);String detail="";
            if(Build.VERSION.SDK_INT>=31)try{CharSequence d=i.loadDescription(MainActivity.this);if(d!=null)detail=d.toString().trim();}catch(Exception ignored){}
            description=detail;audio=WidgetFinder.isAudio(g.audio,g.packageName,g.name,label,description);}
    }
    private View widgetChoice(WidgetOption option,AlertDialog dialog){
        LinearLayout card=col();card.setPadding(dp(12),dp(12),dp(12),dp(12));card.setBackground(rounded(0xff493a50,0xff9d879e,12));
        TextView label=text(option.label,16);label.setTypeface(Typeface.DEFAULT,Typeface.BOLD);add(card,label,0);
        int[] size=widgetSize(option.info);String number=option.sameLabelCount>1?"Choice "+option.ordinal+" of "+option.sameLabelCount+" · ":"";
        TextView sizeText=text(number+(size[0]>0?size[0]+" × "+size[1]+" cells on Home":"Size depends on Home grid")+(option.audio?" · Music & audio":""),13);sizeText.setTextColor(0xffead9e8);add(card,sizeText,3);
        String description=option.description.isEmpty() || option.description.equalsIgnoreCase(option.label)?"No description available from this app.":option.description;
        TextView detail=text(description,13);detail.setTextColor(0xffe4d8e5);add(card,detail,6);
        add(card,widgetPreview(option),8);
        String addLabel=option.label+(option.sameLabelCount>1?" choice "+option.ordinal+" of "+option.sameLabelCount:"");
        TextView addButton=button("Add widget",()->{dialog.dismiss();beginWidget(option.info);});addButton.setContentDescription("Add "+addLabel);add(card,addButton,8);
        return card;
    }
    private View widgetPreview(WidgetOption option){
        FrameLayout preview=new FrameLayout(this);preview.setBackground(rounded(0xff221d2e,0xff75647a,10));
        preview.setMinimumHeight(dp(112));boolean loaded=false;
        AppWidgetProviderInfo info=option.info;
        try{
            RemoteViews remote=null;
            if(Build.VERSION.SDK_INT>=35)remote=widgetManager.getWidgetPreview(info.provider,info.getProfile(),AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN);
            if(remote!=null){View rendered=remote.apply(this,preview);rendered.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);preview.addView(rendered,new FrameLayout.LayoutParams(-1,-1));loaded=true;}
        }catch(Exception ignored){}
        if(!loaded && Build.VERSION.SDK_INT>=31 && info.previewLayout!=0)try{
            View rendered=new RemoteViews(info.provider.getPackageName(),info.previewLayout).apply(this,preview);
            rendered.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);preview.addView(rendered,new FrameLayout.LayoutParams(-1,-1));loaded=true;
        }catch(Exception ignored){}
        if(!loaded && info.previewImage!=0)try{
            Drawable drawable=info.loadPreviewImage(this,0);if(drawable!=null){ImageView image=new ImageView(this);image.setImageDrawable(drawable);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setContentDescription("App-supplied preview image");preview.addView(image,new FrameLayout.LayoutParams(-1,-1));loaded=true;}
        }catch(Exception ignored){}
        if(loaded){preview.setContentDescription("Preview provided by "+option.group.name);preview.setFocusable(true);View shield=new View(this);shield.setOnTouchListener((v,event)->true);shield.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);preview.addView(shield,new FrameLayout.LayoutParams(-1,-1));}
        else{TextView empty=text("Preview unavailable",13);empty.setTextColor(0xffd8cadc);empty.setGravity(Gravity.CENTER);preview.addView(empty,new FrameLayout.LayoutParams(-1,-1));}
        return preview;
    }
    private String widgetLabel(AppWidgetProviderInfo info) {
        try{CharSequence s=info.loadLabel(getPackageManager());return s==null?"Widget":s.toString();}catch(Exception e){return "Widget";}
    }
    private int[] widgetSize(AppWidgetProviderInfo info) {
        if(workspace==null || workspace.getChildCount()==0)return new int[]{0,0};
        ViewGroup strip=(ViewGroup)workspace.getChildAt(0);View page=strip.getChildAt(currentPage);
        int cw=page.getWidth()/WorkspaceModel.COLS,ch=page.getHeight()/WorkspaceModel.ROWS;
        if(cw<1 || ch<1)return new int[]{0,0};
        int x=Math.max(1,(info.minWidth+cw-1)/cw),y=Math.max(1,(info.minHeight+ch-1)/ch);
        if(Build.VERSION.SDK_INT>=31){x=Math.max(x,info.targetCellWidth);y=Math.max(y,info.targetCellHeight);}
        return new int[]{x,y};
    }
    private void beginWidget(AppWidgetProviderInfo info) {
        int[] size=widgetSize(info);int x=size[0],y=size[1];
        if(x<1 || y<1){toast("Home is not ready yet");return;}
        WorkspaceModel.Pos free=model.firstRegion(currentPage,x,y);
        if(free==null){toast("This widget needs more free space on this page");return;}
        try {
            pendingWidgetId=widgetHost.allocateAppWidgetId();pendingPage=currentPage;pendingSlot=free.slot;pendingWidth=x;pendingHeight=y;
            pendingProvider=info.provider.flattenToString();
            if(!savePendingWidget()){cancelPendingWidget();toast("Couldn't save widget setup");return;}
            if(widgetManager.bindAppWidgetIdIfAllowed(pendingWidgetId,info.provider))configureWidget();
            else {Intent bind=new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND);bind.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,pendingWidgetId);bind.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER,info.provider);startActivityForResult(bind,REQUEST_WIDGET_BIND);}
        }catch(Exception e){cancelPendingWidget();toast("Couldn't start widget setup");}
    }
    private boolean savePendingWidget() {
        return prefs.edit().putInt("pending_widget_id",pendingWidgetId).putInt("pending_widget_page",pendingPage).putInt("pending_widget_slot",pendingSlot).putInt("pending_widget_width",pendingWidth).putInt("pending_widget_height",pendingHeight).putString("pending_widget_provider",pendingProvider).commit();
    }
    private void removePendingFields(SharedPreferences.Editor edit) {
        edit.remove("pending_widget_id").remove("pending_widget_page").remove("pending_widget_slot").remove("pending_widget_width").remove("pending_widget_height").remove("pending_widget_provider");
    }
    private void clearPendingWidget() {
        pendingWidgetId=-1;pendingProvider=null;SharedPreferences.Editor edit=prefs.edit();removePendingFields(edit);edit.apply();
    }
    private void cancelPendingWidget() {
        if(pendingWidgetId>0 && !model.containsWidget(pendingWidgetId))try{widgetHost.deleteAppWidgetId(pendingWidgetId);}catch(Exception ignored){}clearPendingWidget();
    }
    private void continuePendingWidget() {
        if(pendingWidgetId<1)return;
        if(model.containsWidget(pendingWidgetId)){clearPendingWidget();toast("That widget is already on Home");return;}
        if(widgetManager.getAppWidgetInfo(pendingWidgetId)!=null){configureWidget();return;}
        ComponentName provider=pendingProvider==null?null:ComponentName.unflattenFromString(pendingProvider);
        if(provider==null){cancelPendingWidget();toast("Please choose that widget again");return;}
        try {
            if(widgetManager.bindAppWidgetIdIfAllowed(pendingWidgetId,provider))configureWidget();
            else {Intent bind=new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND);bind.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,pendingWidgetId);bind.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER,provider);startActivityForResult(bind,REQUEST_WIDGET_BIND);}
        }catch(Exception e){toast("Couldn't resume widget setup; cancel it and try again");}
    }
    private void configureWidget() {
        AppWidgetProviderInfo info=widgetManager.getAppWidgetInfo(pendingWidgetId);
        if(info==null){cancelPendingWidget();toast("Widget setup was cancelled");return;}
        if(info.configure!=null){try{widgetHost.startAppWidgetConfigureActivityForResult(this,pendingWidgetId,0,REQUEST_WIDGET_CONFIG,null);}catch(Exception e){cancelPendingWidget();toast("Couldn't open widget setup");}}
        else finishWidget();
    }
    private void finishWidget() {
        if(pendingWidgetId<1)return;
        WorkspaceModel.Pos p=new WorkspaceModel.Pos(pendingPage,pendingSlot);
        if(!model.regionFree(p,pendingWidth,pendingHeight,null))p=model.firstRegion(pendingPage,pendingWidth,pendingHeight);
        if(p==null){cancelPendingWidget();toast("There is no longer room for that widget");return;}
        int previousPage=currentPage;
        model.put(p,WorkspaceModel.Item.widget(pendingWidgetId,pendingWidth,pendingHeight));currentPage=p.page;
        // Placement and clearing pending state must reach disk together.
        if(!persistLayout(true,true)){
            model.put(p,null);currentPage=previousPage;persistLayout(true,false);savePendingWidget();
            toast("Couldn't save that widget; try continuing setup again");showHome();return;
        }
        pendingWidgetId=-1;pendingProvider=null;showHome();
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==REQUEST_WIDGET_BIND || requestCode==REQUEST_WIDGET_CONFIG){
            if(pendingWidgetId<1)return;
            if(resultCode!=RESULT_OK){cancelPendingWidget();toast("Widget not added");}
            else if(requestCode==REQUEST_WIDGET_BIND)configureWidget();else finishWidget();
        }
    }
    private void editWidget() {
        ArrayList<WorkspaceModel.Pos> positions=new ArrayList<>();ArrayList<String> labels=new ArrayList<>();
        for(int page=0;page<model.pages.size();page++)for(int slot=0;slot<WorkspaceModel.CELLS;slot++){
            WorkspaceModel.Pos pos=new WorkspaceModel.Pos(page,slot);WorkspaceModel.Item item=model.get(pos);
            if(item!=null && item.isWidget()){
                AppWidgetProviderInfo info=widgetManager.getAppWidgetInfo(item.widgetId);
                positions.add(pos);labels.add((info==null?"Unavailable widget":widgetLabel(info))+" · page "+(page+1)+" · "+item.spanX+" × "+item.spanY);
            }
        }
        if(positions.isEmpty()){toast("There are no widgets to edit");return;}
        new AlertDialog.Builder(this).setTitle("Choose a widget").setItems(labels.toArray(new String[0]),(d,n)->widgetEditOptions(positions.get(n))).setNegativeButton("Cancel",null).show();
    }
    private void widgetEditOptions(WorkspaceModel.Pos pos) {
        WorkspaceModel.Item item=model.get(pos);if(item==null || !item.isWidget())return;
        new AlertDialog.Builder(this).setTitle("Widget options").setItems(new String[]{"Move to a position","Resize in place"},(d,n)->{
            if(n==0)chooseWidgetPage(pos);else resizeWidget(pos);
        }).setNegativeButton("Cancel",null).show();
    }
    private void chooseWidgetPage(WorkspaceModel.Pos origin) {
        String[] pages=new String[model.pages.size()];for(int i=0;i<pages.length;i++)pages[i]="Page "+(i+1)+(i==origin.page?" · current":"");
        new AlertDialog.Builder(this).setTitle("Move widget to page").setItems(pages,(d,n)->chooseWidgetSpot(origin,n)).setNegativeButton("Cancel",null).show();
    }
    private void chooseWidgetSpot(WorkspaceModel.Pos origin,int page) {
        WorkspaceModel.Item widget=model.get(origin);if(widget==null || !widget.isWidget())return;
        LinearLayout grid=col();grid.setPadding(dp(12),dp(4),dp(12),dp(4));
        TextView instructions=new TextView(this);instructions.setText("Choose the widget's top-left position.\n◆ current  ·  ○ available  ·  × doesn't fit");instructions.setTextColor(0xff30283c);instructions.setTextSize(14);add(grid,instructions,0);
        final AlertDialog[] dialog=new AlertDialog[1];
        for(int y=0;y<WorkspaceModel.ROWS;y++){
            LinearLayout line=row();
            for(int x=0;x<WorkspaceModel.COLS;x++){
                WorkspaceModel.Pos dest=new WorkspaceModel.Pos(page,y*WorkspaceModel.COLS+x);
                boolean here=dest.equals(origin),free=model.regionFree(dest,widget.spanX,widget.spanY,origin);
                TextView cell=button(here?"◆":free?"○":"×",()->{
                    dialog[0].dismiss();int previousPage=currentPage;
                    if(!model.move(origin,dest)){toast("That position is no longer available");return;}
                    currentPage=dest.page;
                    if(!persistLayout(true,false)){
                        model.move(dest,origin);currentPage=previousPage;persistLayout(true,false);
                        toast("Couldn't save the move");
                    }
                    showHome();
                });
                cell.setContentDescription("Row "+(y+1)+", column "+(x+1)+(here?", current position":free?", available":", unavailable"));
                cell.setEnabled(free && !here);cell.setAlpha(free?1f:.4f);
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(48),1);lp.setMargins(dp(2),dp(2),dp(2),dp(2));line.addView(cell,lp);
            }
            grid.addView(line);
        }
        dialog[0]=new AlertDialog.Builder(this).setTitle("Page "+(page+1)+" · "+widget.spanX+" × "+widget.spanY+" cells").setView(grid).setNegativeButton("Cancel",null).create();dialog[0].show();
    }
    private void resizeWidget(WorkspaceModel.Pos pos) {
        WorkspaceModel.Item item=model.get(pos);if(item==null || !item.isWidget())return;
        AppWidgetProviderInfo info=widgetManager.getAppWidgetInfo(item.widgetId);
        if(info==null){toast("This widget's app is unavailable");return;}
        boolean horizontal=(info.resizeMode & AppWidgetProviderInfo.RESIZE_HORIZONTAL)!=0;
        boolean vertical=(info.resizeMode & AppWidgetProviderInfo.RESIZE_VERTICAL)!=0;
        if(!horizontal && !vertical){toast("This widget does not support resizing");return;}
        if(workspace==null || workspace.getChildCount()==0){toast("Home is not ready yet");return;}
        View page=((ViewGroup)workspace.getChildAt(0)).getChildAt(pos.page);
        int cw=page.getWidth()/WorkspaceModel.COLS,ch=page.getHeight()/WorkspaceModel.ROWS;
        if(cw<1 || ch<1){toast("Home is not ready yet");return;}
        int minW=info.minResizeWidth>0 && info.minResizeWidth<=info.minWidth?info.minResizeWidth:info.minWidth;
        int minH=info.minResizeHeight>0 && info.minResizeHeight<=info.minHeight?info.minResizeHeight:info.minHeight;
        int maxW=0,maxH=0;
        if(Build.VERSION.SDK_INT>=31){
            if(info.maxResizeWidth>=info.minWidth && info.maxResizeWidth>0)maxW=(info.maxResizeWidth+cw-1)/cw;
            if(info.maxResizeHeight>=info.minHeight && info.maxResizeHeight>0)maxH=(info.maxResizeHeight+ch-1)/ch;
        }
        ArrayList<int[]> sizes=new ArrayList<>();ArrayList<String> labels=new ArrayList<>();
        for(int h=1;h<=WorkspaceModel.ROWS;h++)for(int w=1;w<=WorkspaceModel.COLS;w++){
            if(w==item.spanX && h==item.spanY)continue;
            if((!horizontal && w!=item.spanX) || (!vertical && h!=item.spanY))continue;
            if(w!=item.spanX && (w*cw<minW || (maxW>0 && w>maxW)))continue;
            if(h!=item.spanY && (h*ch<minH || (maxH>0 && h>maxH)))continue;
            if(!model.regionFree(pos,w,h,pos))continue;
            sizes.add(new int[]{w,h});labels.add(w+" × "+h+" cells");
        }
        if(sizes.isEmpty()){toast("No other size fits here; try moving the widget first");return;}
        new AlertDialog.Builder(this).setTitle("Resize widget · now "+item.spanX+" × "+item.spanY).setItems(labels.toArray(new String[0]),(d,n)->{
            int oldW=item.spanX,oldH=item.spanY;int[] size=sizes.get(n);
            if(!model.resizeWidget(pos,size[0],size[1])){toast("That size no longer fits");return;}
            if(!persistLayout(true,false)){
                model.resizeWidget(pos,oldW,oldH);persistLayout(true,false);toast("Couldn't save the new size");
            }
            showHome();
        }).setNegativeButton("Cancel",null).show();
    }
    private void removeWidget() {
        ArrayList<WorkspaceModel.Pos> positions=new ArrayList<>();ArrayList<String> labels=new ArrayList<>();
        for(int p=0;p<model.pages.size();p++)for(int i=0;i<WorkspaceModel.CELLS;i++){
            WorkspaceModel.Pos pos=new WorkspaceModel.Pos(p,i);WorkspaceModel.Item item=model.get(pos);
            if(item!=null && item.isWidget()){AppWidgetProviderInfo info=widgetManager.getAppWidgetInfo(item.widgetId);positions.add(pos);labels.add((info==null?"Unavailable widget":widgetLabel(info))+" · page "+(p+1));}
        }
        if(positions.isEmpty()){toast("There are no widgets to remove");return;}
        new AlertDialog.Builder(this).setTitle("Remove widget").setItems(labels.toArray(new String[0]),(d,n)->{
            WorkspaceModel.Pos p=positions.get(n);WorkspaceModel.Item item=model.get(p);if(item==null || !item.isWidget())return;
            new AlertDialog.Builder(this).setMessage("Remove this widget from Starlight Home?").setPositiveButton("Remove",(dialog,which)->{
                model.put(p,null);
                if(!persistLayout(true,false)){model.put(p,item);persistLayout(true,false);toast("Couldn't save the removal");showHome();return;}
                try{widgetHost.deleteAppWidgetId(item.widgetId);}catch(Exception ignored){}showHome();
            }).setNegativeButton("Cancel",null).show();
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
        LinearLayout tile; FrameLayout icon; TextView label;
        if(recycled instanceof LinearLayout && ((LinearLayout)recycled).getChildCount()==2 && ((LinearLayout)recycled).getChildAt(0) instanceof FrameLayout){tile=(LinearLayout)recycled;icon=(FrameLayout)tile.getChildAt(0);label=(TextView)tile.getChildAt(1);}
        else {tile=col();tile.setGravity(Gravity.CENTER);tile.setLayoutParams(new AbsListView.LayoutParams(-1,dp(105)));icon=new FrameLayout(this);tile.addView(icon,new LinearLayout.LayoutParams(dp(54),dp(54)));
            label=text("",11);label.setGravity(Gravity.CENTER);label.setMaxLines(2);label.setEllipsize(TextUtils.TruncateAt.END);tile.addView(label,new LinearLayout.LayoutParams(-1,-2));tile.setFocusable(true);}
        icon.removeAllViews();icon.addView(appIcon(app,themeId,dp(54),true),new FrameLayout.LayoutParams(-1,-1));
        label.setText(app.label);tile.setContentDescription(app.label);tile.setOnClickListener(v->launch(app.key));
        tile.setOnLongClickListener(v->{v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);new AlertDialog.Builder(this).setTitle(app.label).setItems(new String[]{"Add to Home","Add to dock","Customize icon","App info"},(d,n)->{
            if(n==0){if(model.addToPage(app.key,currentPage)){saveLayout();hideKeyboard();showHome();}else toast("No free space on this page");}
            else if(n==1){int i=0;while(i<model.dock.length && model.dock[i]!=null)i++;if(i==model.dock.length){toast("The dock is full");return;}model.dock[i]=item;saveLayout();hideKeyboard();showHome();}
            else if(n==2)customizeIcon(app);
            else try{startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+app.component.getPackageName())));}catch(Exception e){toast("App info unavailable");}
        }).show();return true;});return tile;
    }
    private void customizeIcon(App app) {
        String old=prefs.getString("icon:"+app.key,"auto");int selected=0;
        for(int i=0;i<ICON_KEYS.length;i++)if(ICON_KEYS[i].equals(old))selected=i;
        new AlertDialog.Builder(this).setTitle("Icon for "+app.label).setSingleChoiceItems(ICON_LABELS,selected,(d,which)->{
            SharedPreferences.Editor edit=prefs.edit().putString("icon:"+app.key,ICON_KEYS[which]);
            if(!ICON_KEYS[which].equals("original"))edit.putBoolean("themed_icons",true);
            edit.apply();d.dismiss();if(screen.equals("drawer") && search!=null)refreshDrawer(search.getText().toString());toast("Icon saved for "+app.label);
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
        LinearLayout sample=row();for(int i=0;i<Math.min(4,apps.size());i++){App a=apps.get(i);View icon=appIcon(a,t.id,dp(50),true);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(50),dp(50));lp.rightMargin=dp(9);sample.addView(icon,lp);}add(panel,sample,16);
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
        add(content,button(prefs.getBoolean("themed_icons",true)?"Themed icons: on":"Themed icons: off",()->{prefs.edit().putBoolean("themed_icons",!prefs.getBoolean("themed_icons",true)).apply();showSettings();}),8);
        add(content,button(prefs.getBoolean("ambient",true)?"Ambient effect: on":"Ambient effect: off",()->{prefs.edit().putBoolean("ambient",!prefs.getBoolean("ambient",true)).apply();showSettings();}),8);
        add(content,text("Touch and hold an icon to move it. Pause over another icon to make a folder. Swipe up for all apps. Long-press an app in the drawer to add it to Home. Your Samsung Home and its layout stay separate.",14),18);
        add(content,text("Touch and hold a widget to move it, or use Home options → Widgets to move or resize it. More themes, layout backup and detailed appearance controls are still being developed.",13),18);
    }
    @Override public boolean dispatchTouchEvent(MotionEvent e) {
        if(e.getAction()==MotionEvent.ACTION_DOWN){downX=e.getX();downY=e.getY();}
        else if(e.getAction()==MotionEvent.ACTION_UP && screen.equals("home") && !dragging){float dx=e.getX()-downX,dy=e.getY()-downY;
            if(dy < -dp(110) && Math.abs(dx)<dp(65)){MotionEvent cancel=MotionEvent.obtain(e);cancel.setAction(MotionEvent.ACTION_CANCEL);super.dispatchTouchEvent(cancel);cancel.recycle();showDrawer();return true;}}
        return super.dispatchTouchEvent(e);
    }
}
