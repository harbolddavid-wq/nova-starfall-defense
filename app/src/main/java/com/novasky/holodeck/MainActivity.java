package com.novasky.holodeck;

import android.app.*;
import android.os.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import org.json.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
 public static final int DISCOVER=109, REFRESH=110, INSTALL_WORLD=111, OPEN_WORLD=112, TEST_SOUND=108, LAUNCH=101, LIBRARY=102, SETTINGS=103, HOME=104, EXIT=105, SOUND=106, MOTION=107;
 static final int BG=0xff080d1b, PANEL=0xff111c30, TEXT=0xffedf4ff, MUTED=0xffa7b7cf, MINT=0xff7df7e5;
 ExperienceStore store;volatile ExperienceStore.World playing;final ExecutorService downloads=Executors.newSingleThreadExecutor();boolean busy=false;String deliveryStatus="";TextView deliveryText;ProgressBar deliveryProgress;
 FrameLayout root; LinearLayout shell; android.content.SharedPreferences prefs;
 volatile WebView player; HolodeckAudio audioEngine; String audioSession="";long audioLastSeq=0; String tab="home", returnTab="home"; boolean active=true;
 @Override public void onCreate(Bundle b){
  super.onCreate(b);setVolumeControlStream(android.media.AudioManager.STREAM_MUSIC);audioEngine=new HolodeckAudio(this); prefs=getSharedPreferences("holodeck",MODE_PRIVATE);store=new ExperienceStore(this);
  root=new FrameLayout(this);root.setBackgroundColor(BG);setContentView(root);
  root.setOnApplyWindowInsetsListener((v,i)->{if(Build.VERSION.SDK_INT>=30){android.graphics.Insets s=i.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());v.setPadding(s.left,s.top,s.right,s.bottom);}else{v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());}return i;});
  getWindow().getDecorView().setSystemUiVisibility(0);
  if(Build.VERSION.SDK_INT>=33)getOnBackInvokedDispatcher().registerOnBackInvokedCallback(0,()->goBack());
  show("home");
 }
 int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
 boolean sound(){return prefs.getBoolean("sound",true);} boolean motion(){return prefs.getBoolean("motion",true);}
 TextView text(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setFontFeatureSettings("kern");t.setPadding(0,dp(5),0,dp(5));return t;}
 GradientDrawable shape(int color,int stroke){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(24));if(stroke!=0)g.setStroke(dp(1),stroke);return g;}
 void space(LinearLayout l,int h){View v=new View(this);l.addView(v,new LinearLayout.LayoutParams(1,dp(h)));}
 LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
 Button button(String label,int id,boolean bright,Runnable action){
  Button b=new Button(this);b.setId(id);b.setText(label);b.setTextSize(15);b.setAllCaps(false);b.setTextColor(bright?BG:TEXT);b.setMinHeight(dp(52));b.setPadding(dp(16),dp(4),dp(16),dp(4));
  b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x667df7e5),shape(bright?MINT:PANEL,bright?0:0xff30415b),null));
  b.setOnClickListener(v->{v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);action.run();});return b;
 }
 void show(String target){
  if(player!=null)disposePlayer();tab=target;player=null;root.removeAllViews();shell=column();root.addView(shell,new FrameLayout.LayoutParams(-1,-1));
  LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(dp(24),dp(12),dp(24),dp(8));
  TextView mark=text("✧  NOVA",15,MINT);mark.setLetterSpacing(.18f);top.addView(mark,new LinearLayout.LayoutParams(0,-2,1));top.addView(text("POCKET HOLODECK",10,MUTED));shell.addView(top);
  ScrollView scroll=new ScrollView(this);scroll.setDefaultFocusHighlightEnabled(false);scroll.setFillViewport(true);scroll.setClipToPadding(false);shell.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  LinearLayout body=column();body.setPadding(dp(24),dp(8),dp(24),dp(20));scroll.addView(body);
  if(target.equals("settings"))settings(body);else if(target.equals("library"))library(body);else if(target.equals("discover"))discover(body);else home(body);
  LinearLayout nav=new LinearLayout(this);nav.setPadding(dp(16),dp(8),dp(16),dp(12));
  String[] tabs={"home","library","discover","settings"},labels={"Home","Library","Discover","Settings"};int[] ids={HOME,LIBRARY,DISCOVER,SETTINGS};
  for(int n=0;n<4;n++){final String dest=tabs[n];Button b=button(labels[n],ids[n],target.equals(dest),()->show(dest));b.setTextSize(12);b.setPadding(dp(3),0,dp(3),0);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(50),1);p.setMargins(dp(3),0,dp(3),0);nav.addView(b,p);}shell.addView(nav);
  if(target.equals("discover")&&store.catalog==null&&!busy&&deliveryStatus.isEmpty())root.post(()->{if(tab.equals("discover")&&!busy&&!isDestroyed())refreshCatalog();});
  if(motion()){body.setAlpha(0);body.setTranslationY(dp(10));body.animate().alpha(1).translationY(0).setDuration(240).start();}
 }
 void home(LinearLayout b){
  b.addView(text("A universe.\nWithin reach.",34,TEXT));b.addView(text("Small worlds. Real wonder.",15,MUTED));space(b,12);
  FrameLayout portal=new FrameLayout(this);portal.setBackground(shape(PANEL,0xff243956));portal.setClipToOutline(true);portal.addView(new PortalView(this,false),new FrameLayout.LayoutParams(-1,-1));
  TextView cap=text("THE DOOR IS OPEN",11,MINT);cap.setLetterSpacing(.2f);cap.setGravity(Gravity.CENTER);FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(-1,dp(40),Gravity.BOTTOM);portal.addView(cap,cp);b.addView(portal,new LinearLayout.LayoutParams(-1,dp(230)));portal.setContentDescription("Animated portal. Touch to bend the starlight.");
  space(b,14);b.addView(text("TONIGHT’S DISCOVERY",11,MINT));b.addView(text("Starwell",27,TEXT));b.addView(text("The Listening Sky",16,0xffb7a4ff));
  b.addView(text("Carry a little light into the dark. Wake three constellations and discover what the sky remembers.",15,MUTED));space(b,12);
  b.addView(button(prefs.getBoolean("visited",false)?"Enter Starwell  ↗":"Open the portal  ↗",LAUNCH,true,()->launch()));space(b,8);b.addView(text("✧  Installed · Works offline · Saves as you play",11,MUTED));space(b,10);b.addView(button("Find your next world  →",0,false,()->show("discover")));
 }
 void library(LinearLayout b){
  b.addView(text("Your worlds",32,TEXT));int count=store.installed().size();b.addView(text(count+(count==1?" world. Always within reach.":" worlds. Always within reach."),15,MUTED));space(b,18);
  for(ExperienceStore.World w:store.installed())worldCard(b,w.metadata,w,false);
  space(b,8);b.addView(button("Discover more worlds  →",0,false,()->show("discover")));b.addView(text("Installed worlds open without a connection. Your progress stays on this phone.",13,MUTED));
 }
 void worldCard(LinearLayout b,JSONObject m,ExperienceStore.World installed,boolean online){
  String id=m.optString("id"),title=m.optString("title"),subtitle=m.optString("subtitle"),description=m.optString("description");int version=m.optInt("versionCode",1);boolean newer=installed!=null&&version>installed.versionCode;
  LinearLayout card=column();card.setPadding(dp(16),dp(12),dp(16),dp(18));card.setBackground(shape(PANEL,0xff30415b));
  View art=id.equals("lanternwake")?new SeaView(this):new PortalView(this,true);card.addView(art,new LinearLayout.LayoutParams(-1,dp(160)));card.addView(text(title.toUpperCase(Locale.ROOT),11,MINT));card.addView(text(subtitle.isEmpty()?title:subtitle,24,TEXT));card.addView(text(description,14,MUTED));
  String state=online?(installed==null?"READY TO DISCOVER":newer?"NEW VERSION AVAILABLE":"INSTALLED · OFFLINE READY"):"INSTALLED · "+installed.version;
  card.addView(text(state,10,MINT));space(card,8);
  if(online&&(installed==null||newer)){Button download=button(newer?"Update world  ↓":"Download world  ↓",id.equals("lanternwake")?INSTALL_WORLD:0,true,()->install(m));download.setEnabled(!busy);card.addView(download);}
  else if(installed!=null){card.addView(button("Enter "+installed.title+"  ↗",id.equals("starwell")?LAUNCH:OPEN_WORLD,true,()->launch(installed)));if(installed.directory!=null){space(card,5);card.addView(button("World options",0,false,()->worldOptions(installed)));}}
  b.addView(card);space(b,16);
 }
 void discover(LinearLayout b){
  b.addView(text("Beyond this door",31,TEXT));b.addView(text("New worlds arrive here. Download once, wander offline.",15,MUTED));space(b,10);
  Button refresh=button(busy?"Working…":"Refresh worlds  ↻",REFRESH,false,()->refreshCatalog());refresh.setEnabled(!busy);b.addView(refresh);space(b,8);
  deliveryText=text(deliveryStatus.isEmpty()?(store.catalog==null?"Tap Refresh worlds to see what’s waiting.":"Your latest saved catalog. Refresh to check for more."):deliveryStatus,13,MUTED);b.addView(deliveryText);
  deliveryProgress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);deliveryProgress.setIndeterminate(busy);deliveryProgress.setVisibility(busy?View.VISIBLE:View.GONE);b.addView(deliveryProgress,new LinearLayout.LayoutParams(-1,dp(5)));space(b,16);
  List<JSONObject> worlds=store.available();for(JSONObject m:worlds)worldCard(b,m,store.installed(m.optString("id")),true);
  if(worlds.isEmpty()){SeaView sea=new SeaView(this);b.addView(sea,new LinearLayout.LayoutParams(-1,dp(190)));b.addView(text("A lantern is waiting beyond the horizon.",15,MUTED));}
 }
 void refreshCatalog(){if(busy)return;busy=true;deliveryStatus="Looking beyond the horizon…";show("discover");downloads.execute(()->{try{store.refresh();runOnUiThread(()->{if(isDestroyed())return;busy=false;deliveryStatus="Catalog refreshed. Choose your next adventure.";if(tab.equals("discover")&&player==null)show("discover");});}catch(Exception e){android.util.Log.w("HolodeckDelivery","Catalog refresh failed: "+e.getClass().getSimpleName());runOnUiThread(()->{if(isDestroyed())return;busy=false;deliveryStatus=store.catalog==null?"Couldn’t reach the catalog. Your installed worlds still open from Library.":"Couldn’t refresh right now. Showing your saved catalog.";if(tab.equals("discover")&&player==null)show("discover");});}});}
 void install(JSONObject m){if(busy)return;busy=true;deliveryStatus="Bringing "+m.optString("title")+" home…";show("discover");downloads.execute(()->{try{ExperienceStore.World w=store.install(m,p->runOnUiThread(()->{if(isDestroyed()||player!=null||!tab.equals("discover"))return;if(deliveryText!=null)deliveryText.setText("Downloading "+m.optString("title")+" · "+p+"%");if(deliveryProgress!=null){deliveryProgress.setIndeterminate(false);deliveryProgress.setProgress(p);}}));runOnUiThread(()->{if(isDestroyed())return;busy=false;deliveryStatus=w.title+" is ready. It now opens offline from Library.";if(player==null){show("library");Toast.makeText(this,w.title+" is ready",Toast.LENGTH_LONG).show();if(sound()){audioEngine.enabled(true);audioEngine.cue("success");new Handler().postDelayed(()->{if(player==null)audioEngine.stopScene();},1500);}}});}catch(Exception e){android.util.Log.w("HolodeckDelivery","Install failed: "+e.getClass().getSimpleName());runOnUiThread(()->{if(isDestroyed())return;busy=false;deliveryStatus="Couldn’t install this world. Your installed worlds and progress are safe. Try refreshing the catalog.";if(tab.equals("discover")&&player==null)show("discover");});}});}
 void worldOptions(ExperienceStore.World w){
  ArrayList<String> options=new ArrayList<>();if(store.previous(w.id))options.add("Restore previous version");options.add("Remove download");
  new AlertDialog.Builder(this).setTitle(w.title+" · "+w.version).setItems(options.toArray(new String[0]),(dialog,n)->{
   if(options.get(n).startsWith("Restore")){
    downloads.execute(()->{try{store.rollback(w.id);runOnUiThread(()->{if(!isDestroyed()&&player==null){show("library");Toast.makeText(this,"Previous version restored. Your saved progress stays.",Toast.LENGTH_LONG).show();}});}catch(Exception e){runOnUiThread(()->Toast.makeText(this,"Couldn’t restore that version",Toast.LENGTH_SHORT).show());}});
   }else{
    new AlertDialog.Builder(this).setTitle("Remove "+w.title+"?").setMessage("Its download will be removed. Saved progress stays on this phone for when you bring it back.").setNegativeButton("Keep",null).setPositiveButton("Remove",(x,y)->downloads.execute(()->{store.remove(w.id);runOnUiThread(()->{if(!isDestroyed()&&player==null)show("library");});})).show();
   }
  }).show();
 }
 void settings(LinearLayout b){
  b.addView(text("Make it yours",32,TEXT));b.addView(text("A little control over your universe.",15,MUTED));space(b,20);
  setting(b,"Soundscape","Entrance chimes and musical touch feedback",SOUND,sound(),v->{prefs.edit().putBoolean("sound",v).apply();audioEngine.enabled(v);});
  setting(b,"Living motion","Portal orbits, drifting stars and touch trails",MOTION,motion(),v->{prefs.edit().putBoolean("motion",v).apply();show("settings");});
  b.addView(button("Hear a test chime",TEST_SOUND,false,()->{prefs.edit().putBoolean("sound",true).apply();audioEngine.enabled(true);audioEngine.cue("start");Switch toggle=findViewById(SOUND);if(toggle!=null)toggle.setChecked(true);new Handler().postDelayed(()->{if(player==null)audioEngine.stopScene();},1600);Toast.makeText(this,"Use the phone’s volume buttons for media sound",Toast.LENGTH_SHORT).show();}));
  space(b,22);b.addView(text("PRIVATE BY DESIGN",11,MINT));b.addView(text("Connect to discover and download worlds. Installed worlds stay offline. No accounts, ads, tracking, or access to your photos, microphone or contacts.",15,MUTED));space(b,20);b.addView(text("Nova Pocket Holodeck 0.2.0\nBuilt for small adventures and big imaginations.",13,MUTED));
 }
 interface Changed{void value(boolean v);}
 void setting(LinearLayout b,String label,String subtitle,int id,boolean checked,Changed changed){
  LinearLayout card=column();card.setPadding(dp(16),dp(12),dp(16),dp(12));card.setBackground(shape(PANEL,0xff243956));Switch s=new Switch(this);s.setId(id);s.setText(label);s.setTextColor(TEXT);s.setTextSize(18);s.setMinHeight(dp(52));s.setChecked(checked);s.setOnCheckedChangeListener((x,v)->changed.value(v));card.addView(s);card.addView(text(subtitle,13,MUTED));b.addView(card);space(b,14);
 }
 public WebView getPlayer(){return player;}
 void launch(){launch(store.installed("starwell"));}
 void launch(ExperienceStore.World world){
  if(world==null)return;disposePlayer();playing=world;audioSession=UUID.randomUUID().toString();audioLastSeq=0;audioEngine.enabled(sound());returnTab=tab;prefs.edit().putBoolean("visited",true).putString("last_world",world.id).apply();root.removeAllViews();LinearLayout layout=column();root.addView(layout,new FrameLayout.LayoutParams(-1,-1));
  LinearLayout toolbar=new LinearLayout(this);toolbar.setPadding(dp(12),dp(6),dp(12),dp(6));toolbar.setGravity(Gravity.CENTER_VERTICAL);
  Button back=button("‹  Library",EXIT,false,()->leave());toolbar.addView(back,new LinearLayout.LayoutParams(dp(110),dp(48)));TextView title=text(world.title.toUpperCase(Locale.ROOT),13,MINT);title.setLetterSpacing(.16f);title.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);toolbar.addView(title,new LinearLayout.LayoutParams(0,-1,1));layout.addView(toolbar);
  player=new WebView(this);player.setBackgroundColor(BG);layout.addView(player,new LinearLayout.LayoutParams(-1,0,1));
  WebSettings s=player.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setAllowFileAccessFromFileURLs(false);s.setAllowUniversalAccessFromFileURLs(false);s.setBlockNetworkLoads(true);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);s.setSupportMultipleWindows(false);s.setJavaScriptCanOpenWindowsAutomatically(false);s.setMediaPlaybackRequiresUserGesture(true);s.setSafeBrowsingEnabled(true);s.setTextZoom(100);
  CookieManager.getInstance().setAcceptThirdPartyCookies(player,false);player.setWebChromeClient(new WebChromeClient(){@Override public void onPermissionRequest(PermissionRequest r){r.deny();}@Override public void onGeolocationPermissionsShowPrompt(String o,GeolocationPermissions.Callback c){c.invoke(o,false,false);}});
  player.setWebViewClient(new WebViewClient(){
   @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return true;}
   @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){if(v!=player||!"GET".equals(r.getMethod()))return blocked(403,"Blocked");WebResourceResponse cue=audioRequest(v,r);return cue!=null?cue:asset(r.getUrl());}
   @Override public void onReceivedSslError(WebView v,android.webkit.SslErrorHandler h,android.net.http.SslError e){h.cancel();}
   @Override public boolean onRenderProcessGone(WebView v,RenderProcessGoneDetail d){if(player!=v||isFinishing()||isDestroyed())return true;runOnUiThread(()->{leave();new AlertDialog.Builder(MainActivity.this).setTitle("The portal rested").setMessage("Your saved starlight is safe. Open Starwell to continue.").setPositiveButton("Okay",null).show();});return true;}
  });
  player.loadUrl(world.origin()+"/"+world.entry+"?sound="+(sound()?1:0)+"&motion="+(motion()?1:0)+"&audio=native&session="+audioSession);
 }
 WebResourceResponse audioRequest(WebView view,WebResourceRequest request){
  Uri u=request.getUrl();if(!"/_audio".equals(u.getPath()))return null;String name=u.getQueryParameter("cue"),sequence=u.getQueryParameter("seq"),requestedSound=u.getQueryParameter("enabled");
  boolean valid="GET".equals(request.getMethod())&&"https".equals(u.getScheme())&&(playing!=null?playing.id:"starwell").concat(".holodeck.invalid").equals(u.getHost())&&u.getPort()==-1&&audioSession.equals(u.getQueryParameter("session"))&&("0".equals(requestedSound)||"1".equals(requestedSound))&&sequence!=null&&sequence.matches("[1-9][0-9]{0,8}")&&name!=null&&(name.matches("note[0-6]")||Arrays.asList("start","success","on","off").contains(name));
  if(!valid)return new WebResourceResponse("text/plain","UTF-8",403,"Blocked",Collections.emptyMap(),new ByteArrayInputStream(new byte[0]));
  runOnUiThread(()->{
   if(view!=player||!active||Long.parseLong(sequence)<=audioLastSeq)return;
   audioLastSeq=Long.parseLong(sequence);boolean value="1".equals(requestedSound);
   if(sound()!=value){prefs.edit().putBoolean("sound",value).apply();audioEngine.enabled(value);}
   if(value&&!name.equals("off")){audioEngine.cue(name.equals("on")?"start":name);
    if((name.equals("start")||name.equals("on"))&&((android.media.AudioManager)getSystemService(AUDIO_SERVICE)).getStreamVolume(android.media.AudioManager.STREAM_MUSIC)==0)Toast.makeText(this,"Media sound is muted. Press Volume up.",Toast.LENGTH_LONG).show();}
  });
  byte[] pixel=android.util.Base64.decode("R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7",0);
  return new WebResourceResponse("image/gif","UTF-8",200,"OK",Collections.singletonMap("Cache-Control","no-store"),new ByteArrayInputStream(pixel));
 }
 WebResourceResponse asset(Uri u){
  ExperienceStore.World w=playing!=null?playing:store.builtin();String path=u.getPath();boolean allowed="https".equals(u.getScheme())&&(w.id+".holodeck.invalid").equals(u.getHost())&&u.getPort()==-1&&path!=null&&path.startsWith("/")&&ExperienceStore.path(path.substring(1));
  if(!allowed)return blocked(403,"Blocked");
  try{String name=path.substring(1),type=name.endsWith(".js")?"application/javascript":name.endsWith(".css")?"text/css":name.endsWith(".html")?"text/html":name.endsWith(".json")?"application/json":name.endsWith(".png")?"image/png":name.endsWith(".jpg")||name.endsWith(".jpeg")?"image/jpeg":name.endsWith(".svg")?"image/svg+xml":name.endsWith(".webp")?"image/webp":name.endsWith(".gif")?"image/gif":name.endsWith(".wav")?"audio/wav":name.endsWith(".mp3")?"audio/mpeg":name.endsWith(".ogg")?"audio/ogg":name.endsWith(".woff2")?"font/woff2":"application/octet-stream";
   Map<String,String> headers=new HashMap<>();headers.put("Cache-Control","no-store");headers.put("X-Content-Type-Options","nosniff");headers.put("Content-Security-Policy","default-src 'none'; script-src 'self'; style-src 'self'; img-src 'self' data:; media-src 'self' data:; font-src 'self'; connect-src 'self'; frame-src 'none'; worker-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none'");return new WebResourceResponse(type,"UTF-8",200,"OK",headers,store.open(w,name));
  }catch(Exception e){return blocked(404,"Missing");}
 }
 WebResourceResponse blocked(int code,String reason){return new WebResourceResponse("text/plain","UTF-8",code,reason,Collections.emptyMap(),new ByteArrayInputStream(new byte[0]));}
 void disposePlayer(){if(audioEngine!=null)audioEngine.stopScene();WebView old=player;player=null;playing=null;if(old!=null){old.stopLoading();if(old.getParent() instanceof ViewGroup)((ViewGroup)old.getParent()).removeView(old);old.destroy();}}
 void leave(){disposePlayer();show("library");}
 void goBack(){if(player!=null)leave();else if(!tab.equals("home"))show("home");else new AlertDialog.Builder(this).setTitle("Close Holodeck?").setMessage("Your worlds will be here when you return.").setNegativeButton("Stay",null).setPositiveButton("Close",(d,w)->finish()).show();}
 @Override public void onBackPressed(){goBack();}
 @Override protected void onPause(){active=false;if(audioEngine!=null)audioEngine.foreground(false);if(player!=null){player.evaluateJavascript("window.holodeckPause && window.holodeckPause()",null);player.onPause();}super.onPause();}
 @Override protected void onResume(){super.onResume();active=true;if(audioEngine!=null)audioEngine.foreground(true);if(player!=null){player.onResume();player.evaluateJavascript("window.holodeckResume && window.holodeckResume()",null);}}
 @Override protected void onDestroy(){downloads.shutdownNow();disposePlayer();if(audioEngine!=null)audioEngine.close();super.onDestroy();}
 class SeaView extends View {
  final Paint paint=new Paint(3);final long born=System.nanoTime();
  SeaView(Context c){super(c);setContentDescription("Lanternwake: a golden boat on a moving night sea");}
  @Override protected void onDraw(Canvas c){float w=getWidth(),h=getHeight(),t=motion()?(System.nanoTime()-born)/1e9f:0;paint.setShader(new LinearGradient(0,0,0,h,new int[]{0xff101833,0xff163c53,0xff092333},null,Shader.TileMode.CLAMP));paint.setStyle(Paint.Style.FILL);c.drawRect(0,0,w,h,paint);paint.setShader(null);
   paint.setColor(0xffd9e6ee);c.drawCircle(w*.77f,h*.2f,h*.08f,paint);paint.setColor(0xffa1beca);c.drawRect(w*.12f,h*.21f,w*.16f,h*.52f,paint);paint.setColor(0xffffd995);c.drawRect(w*.1f,h*.2f,w*.18f,h*.25f,paint);
   paint.setStyle(Paint.Style.STROKE);for(int i=0;i<8;i++){paint.setColor(0x4454bad1);paint.setStrokeWidth(dp(1));Path p=new Path();float y=h*(.46f+i*.065f);p.moveTo(0,y);for(int x=0;x<=w;x+=8)p.lineTo(x,y+(float)Math.sin(x*.023+t+i)*dp(3));c.drawPath(p,paint);}
   paint.setStyle(Paint.Style.FILL);float bx=w*.53f,by=h*.72f+(float)Math.sin(t*1.8)*dp(2);paint.setColor(0xffaa795a);Path boat=new Path();boat.moveTo(bx-dp(23),by);boat.lineTo(bx-dp(14),by+dp(9));boat.lineTo(bx+dp(15),by+dp(9));boat.lineTo(bx+dp(23),by);boat.close();c.drawPath(boat,paint);paint.setColor(0xffffce78);c.drawCircle(bx,by-dp(9),dp(6),paint);
   for(int i=0;i<3;i++){float x=w*(.3f+i*.25f),y=h*(.45f+(i%2)*.1f)+(float)Math.sin(t+i)*dp(3);paint.setColor(0xfff6c981);c.drawCircle(x,y,dp(3),paint);}if(active&&isShown()&&motion())postInvalidateDelayed(40);
  }
  @Override public void onWindowFocusChanged(boolean f){super.onWindowFocusChanged(f);if(f)invalidate();}
 }
 class PortalView extends View {
  Paint p=new Paint(3);long begin=System.nanoTime();boolean galaxy;float bend=0;
  PortalView(Context c,boolean g){super(c);galaxy=g;setLayerType(View.LAYER_TYPE_HARDWARE,null);setOnTouchListener((v,e)->{if(e.getAction()==MotionEvent.ACTION_DOWN){bend=1;performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);invalidate();}return true;});}
  void line(Canvas c,int color,float width){p.setShader(null);p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(width));}
  @Override protected void onDraw(Canvas c){
   super.onDraw(c);float w=getWidth(),h=getHeight(),cx=w*.5f,cy=h*.46f,r=Math.min(w*.34f,h*.34f);float t=motion()?(System.nanoTime()-begin)/1e9f:0;
   p.setStyle(Paint.Style.FILL);p.setShader(new RadialGradient(cx,cy,r*2.3f,new int[]{0xff25365a,0xff101d34,BG},null,Shader.TileMode.CLAMP));c.drawRect(0,0,w,h,p);p.setShader(null);
   for(int i=0;i<74;i++){float x=(i*137.51f)%w,y=(i*71.31f)%h;p.setColor(Color.argb((int)(100+100*Math.sin(i+t)),190,218,255));c.drawCircle(x,y,dp(i%9==0?1.2f:.6f),p);}
   if(galaxy){for(int arm=0;arm<3;arm++)for(int i=1;i<115;i++){float a=i*.06f+arm*2.094f+t*.07f,rr=i*r/110;float x=cx+(float)Math.cos(a)*rr,y=cy+(float)Math.sin(a)*rr*.56f;p.setColor(arm%2==0?0xff8d9fe6:MINT);c.drawCircle(x,y,dp(i%8==0?1.5f:.6f),p);}}
   else{
    for(int i=8;i>=0;i--){line(c,Color.argb(18+i*7,80+i*12,115+i*14,190+i*7),i==0?2:1);c.save();c.rotate((i%2==0?1:-1)*(t*8+i*19),cx,cy);c.drawOval(cx-r*(1+i*.045f)-bend*5,cy-r*.65f,cx+r*(1+i*.045f)+bend*5,cy+r*.65f,p);c.restore();}
    line(c,0xff7df7e5,2);c.drawArc(cx-r,cy-r,cx+r,cy+r,t*24,238,false,p);line(c,0xffaa90ff,2);c.drawArc(cx-r*.84f,cy-r*.84f,cx+r*.84f,cy+r*.84f,-t*32,190,false,p);
   }
   p.setStyle(Paint.Style.FILL);p.setShader(new RadialGradient(cx,cy,r*.55f,new int[]{0xcc7df7e5,0x007df7e5},null,Shader.TileMode.CLAMP));c.drawCircle(cx,cy,r*.55f,p);p.setShader(null);p.setColor(TEXT);c.drawCircle(cx,cy,dp(3),p);
   bend*=.92f;if(active&&isShown()&&(motion()||bend>.01f))postInvalidateDelayed(32);
  }
  @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(focus)invalidate();}
  @Override protected void onWindowVisibilityChanged(int v){super.onWindowVisibilityChanged(v);if(v==VISIBLE)invalidate();}
 }
}
