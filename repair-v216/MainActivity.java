package com.nova.lockscapes;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.WallpaperManager;
import android.app.WallpaperInfo;
import android.os.Build;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.media.MediaMetadataRetriever;
import android.widget.VideoView;
import android.view.TextureView;
import android.graphics.SurfaceTexture;
import android.view.Surface;
import android.media.MediaPlayer;
import android.widget.FrameLayout;
import android.widget.EditText;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.database.Cursor;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class MainActivity extends Activity {
  static final int BG=0xff100c1b, PANEL=0xff2b203d, LILAC=0xffd4b5ff, WHITE=0xfffff1fc, ORANGE=0xffffad68;
  static final int OPEN_IMAGE=20, OPEN_PACK=21, OPEN_VIDEO=22;
  SharedPreferences prefs; LinearLayout root, body; Preview preview; TextureView videoTexture; MediaPlayer previewPlayer; TextView videoStatus; boolean resumed=true; int previewGeneration=0; final Handler videoHandler=new Handler(Looper.getMainLooper());
  String chosen="built:0";
  int dp(float px){return (int)(getResources().getDisplayMetrics().density*px+.5f);}
  void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
  TextView label(String text,int size){TextView v=new TextView(this);v.setText(text);v.setTextSize(size);v.setTextColor(WHITE);v.setPadding(dp(5),dp(6),dp(5),dp(6));return v;}
  LinearLayout vert(){LinearLayout c=new LinearLayout(this);c.setOrientation(1);return c;}
  void add(LinearLayout p,View v){p.addView(v,new LinearLayout.LayoutParams(-1,-2));}
  Button button(String text,Runnable tap){Button b=new Button(this);b.setAllCaps(false);b.setText(text);b.setTextColor(WHITE);b.setTextSize(14);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(PANEL));b.setOnClickListener(v->tap.run());return b;}
  @Override public void onCreate(Bundle b){super.onCreate(b);prefs=getSharedPreferences("lockscapes",MODE_PRIVATE);chosen=prefs.getString("scene","built:0");home();}
  void screen(String title,String subtitle){
    if(preview!=null){preview.stop();preview=null;}
    releasePreview();
    root=vert();root.setBackgroundColor(BG);setContentView(root);
    TextView head=label("✦ "+title,23);head.setTextColor(ORANGE);head.setTypeface(null,1);head.setPadding(dp(15),dp(18),dp(15),dp(4));add(root,head);
    TextView sub=label(subtitle,13);sub.setPadding(dp(15),0,dp(15),dp(9));add(root,sub);
    ScrollView scroll=new ScrollView(this);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
    body=vert();body.setPadding(dp(11),dp(4),dp(11),dp(30));scroll.addView(body);
  }
  void home(){screen("NOVA LOCKSCAPES","Your own wallpaper vault · Images + motion · No ads or accounts.");
    TextView info=label("Artwork worlds + classic scenes · Save PNG/JPG/WebP/MP4/WebM · Looping live wallpaper",15);info.setTextColor(LILAC);add(body,info);
    add(body,button("✦ Preview my selected world",()->openScene(chosen)));
    add(body,button("＋ ADD ARTWORK (PNG, JPG, WEBP)",()->pick(OPEN_IMAGE,"image/*")));
    add(body,button("▶ ADD ANIMATED VIDEO (MP4, WEBM)",()->pick(OPEN_VIDEO,"video/*")));
    add(body,label("Files are copied into your own private Lockscapes library. Reopen the app and they are still here. Updating the app keeps them; uninstalling or clearing data removes them.",12));
    add(body,button("＋ Import Nova Scene Pack (ZIP)",()->pick(OPEN_PACK,"application/zip")));
    add(body,label("Featured detailed worlds",19));
    add(body,label("Animated video preset",17));
    libraryTile(body,"motion:0","✦ Infernal Emberfall · Moving embers",true);
    add(body,label("Built-in artwork & original classics",17));
    GridLayout grid=new GridLayout(this);grid.setColumnCount(3);add(body,grid);
    int cell=(getResources().getDisplayMetrics().widthPixels-dp(43))/3;
    for(int i=0;i<Worlds.NAMES.length;i++){
      int index=i;String key="built:"+i;
      LinearLayout tile=vert();tile.setBackgroundColor(PANEL);tile.setPadding(dp(3),dp(4),dp(3),dp(4));
      ImageView thumb=new ImageView(this);thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
      Bitmap bitmap=Worlds.sample(this,key,130,195);thumb.setImageBitmap(bitmap);
      tile.addView(thumb,new LinearLayout.LayoutParams(-1,dp(135)));
      TextView title=label(Worlds.NAMES[i],11);title.setGravity(Gravity.CENTER);title.setMaxLines(2);tile.addView(title,new LinearLayout.LayoutParams(-1,dp(44)));
      GridLayout.LayoutParams gp=new GridLayout.LayoutParams();gp.width=cell;gp.height=dp(187);gp.setMargins(dp(2),dp(2),dp(2),dp(2));grid.addView(tile,gp);
      tile.setOnClickListener(v->openScene(key));
    }
    add(body,label("My saved wallpaper collection",20));
    File imports=new File(getFilesDir(),"imports");File[] images=imports.listFiles();
    int found=0;
    if(images!=null){Arrays.sort(images,Collections.reverseOrder());for(File f:images){if(!f.isFile()||!f.getName().matches("(?i).+\\.(jpg|jpeg|png|webp)"))continue;
      String key="image:"+f.getName();libraryTile(body,key,displayFor(key),false);found++;}}
    File videos=new File(getFilesDir(),"videos");File[] clips=videos.listFiles();
    if(clips!=null){Arrays.sort(clips,Collections.reverseOrder());for(File f:clips){if(!f.isFile()||!f.getName().matches("(?i).+\\.(mp4|webm)"))continue;
      String key="video:"+f.getName();libraryTile(body,key,"▶ "+displayFor(key),true);found++;}}
    File packs=new File(getFilesDir(),"packs");File[] folders=packs.listFiles();
    if(folders!=null){Arrays.sort(folders,Collections.reverseOrder());for(File f:folders){if(!f.isDirectory())continue;
      String key="pack:"+f.getName();libraryTile(body,key,"✧ "+displayFor(key)+" · layered",false);found++;}}
    if(found==0)add(body,label("Your library is empty. Import a picture, an MP4 loop, or a layered Nova Scene Pack. Every import stays until you remove it.",13));
    add(body,label("Lock screen safety: this changes wallpaper artwork, never your PIN, fingerprint, clock or notifications. Live wallpaper placement depends on your phone's Android wallpaper picker.",12));
  }
  String displayName(String filename){return filename.replaceFirst("(?i)\\.(jpg|jpeg|png|webp|mp4|webm)$","").replace('_',' ').replace('-',' ');}
  String displayFor(String key){
    int pos=key.indexOf(':');String plain=pos>=0?key.substring(pos+1):key;
    return prefs.getString("title:"+key,displayName(plain));
  }
  void libraryTile(LinearLayout parent,String key,String title,boolean moving){
    LinearLayout cell=new LinearLayout(this);cell.setOrientation(LinearLayout.HORIZONTAL);cell.setGravity(Gravity.CENTER_VERTICAL);
    cell.setPadding(dp(6),dp(6),dp(6),dp(6));cell.setBackgroundColor(PANEL);
    ImageView thumb=new ImageView(this);thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
    try{Bitmap shot=Worlds.sample(this,key,180,230);thumb.setImageBitmap(shot);}catch(Exception ignored){}
    cell.addView(thumb,new LinearLayout.LayoutParams(dp(75),dp(90)));
    LinearLayout detail=vert();detail.setPadding(dp(9),0,0,0);
    TextView text=label(title,14);text.setMaxLines(2);detail.addView(text);
    TextView meta=label(moving?"ANIMATED · Plays on lock screen":"STILL ART · Add ambient motion",11);meta.setTextColor(LILAC);detail.addView(meta);
    cell.addView(detail,new LinearLayout.LayoutParams(0,-2,1));
    cell.setOnClickListener(v->openScene(key));
    parent.addView(cell,new LinearLayout.LayoutParams(-1,-2));
    View gap=new View(this);parent.addView(gap,new LinearLayout.LayoutParams(-1,dp(5)));
  }
  void rename(String key){
    EditText edit=new EditText(this);edit.setSingleLine(true);edit.setText(displayFor(key));
    new AlertDialog.Builder(this).setTitle("Rename library item").setView(edit)
      .setPositiveButton("Save",(d,w)->{String name=edit.getText().toString().trim();if(!name.isEmpty())prefs.edit().putString("title:"+key,name).apply();openScene(key);})
      .setNegativeButton("Cancel",null).show();
  }
  void pick(int request,String type){try{Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType(type);if(request==OPEN_PACK)intent.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/zip","application/x-zip-compressed","application/octet-stream"});startActivityForResult(intent,request);}catch(Exception ex){toast("Could not open file picker: "+ex.getMessage());}}
  void openScene(String key){chosen=key;prefs.edit().putString("scene",key).apply();
    String name="Your world";
    if(key.startsWith("built:")){try{name=Worlds.NAMES[Integer.parseInt(key.substring(6))];}catch(Exception ignored){}}
    else if(key.startsWith("image:"))name=displayFor(key);
    else if(key.startsWith("pack:"))name=displayFor(key);
    else if(key.startsWith("video:"))name=displayFor(key);
    else if(key.equals("motion:0"))name="Infernal Emberfall · Motion";
    screen(name,"Original-quality artwork, adjustable fit and gentle optional movement.");
    add(body,button("← Back to all worlds",this::home));
    if(Worlds.isVideo(key)){
      FrameLayout frame=new FrameLayout(this);
      ImageView poster=new ImageView(this);
      poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
      try{poster.setImageBitmap(Worlds.sample(this,key,480,850));}catch(Exception ignored){}
      frame.addView(poster,new FrameLayout.LayoutParams(-1,-1));
      videoTexture=new TextureView(this);
      videoTexture.setOpaque(false);
      videoTexture.setAlpha(0f); // Poster remains visible until a real video frame arrives.
      frame.addView(videoTexture,new FrameLayout.LayoutParams(-1,-1));
      // Preview is portrait like the actual phone, not the old landscape-shaped 410dp box.
      android.util.DisplayMetrics metrics=getResources().getDisplayMetrics();
      int previewW=Math.max(200,metrics.widthPixels-dp(32));
      int previewH=Math.min(dp(750),Math.max(dp(380),Math.round(previewW*metrics.heightPixels/(float)Math.max(1,metrics.widthPixels))));
      body.addView(frame,new LinearLayout.LayoutParams(-1,previewH));
      videoStatus=label("Loading animation…",12);videoStatus.setTextColor(LILAC);add(body,videoStatus);
      videoTexture.setSurfaceTextureListener(new TextureView.SurfaceTextureListener(){
        @Override public void onSurfaceTextureAvailable(SurfaceTexture st,int w,int h){startVideoPreview();}
        @Override public void onSurfaceTextureSizeChanged(SurfaceTexture st,int w,int h){}
        @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture st){releasePreviewPlayer();return true;}
        @Override public void onSurfaceTextureUpdated(SurfaceTexture st){}
      });
      add(body,button("▶ Retry video preview",()->{releasePreviewPlayer();startVideoPreview();}));
      add(body,button("ⓘ Live wallpaper diagnostics",()->{
        new AlertDialog.Builder(this).setTitle("Live wallpaper status")
          .setMessage(prefs.getString("livePlaybackStatus","No live wallpaper playback attempt recorded yet."))
          .setPositiveButton("OK",null).show();
      }));
    } else {preview=new Preview(key);add(body,preview);preview.getLayoutParams().height=dp(410);}
    if(!key.startsWith("built:")&&!key.startsWith("motion:"))add(body,button("✎ Rename in my library",()->rename(key)));
    if(key.startsWith("image:")||key.startsWith("pack:")||key.startsWith("built:")){
      final String[] modes={"Fill screen (crop edges)","Fit entire image (no crop)","Custom crop and position"};
      int current=prefs.getInt("fitMode",0);
      if(current<0||current>2)current=0;
      android.widget.Spinner sizing=new android.widget.Spinner(this);
      android.widget.ArrayAdapter<String> options=new android.widget.ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,modes);
      sizing.setAdapter(options);sizing.setSelection(current);
      add(body,label("Image framing",17));add(body,sizing);
      sizing.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
        public void onNothingSelected(android.widget.AdapterView<?> p){}
        public void onItemSelected(android.widget.AdapterView<?> p,View v,int i,long id){prefs.edit().putInt("fitMode",i).apply();preview.invalidate();}
      });
      add(body,label("Zoom",14));SeekBar zoom=new SeekBar(this);zoom.setMax(150);zoom.setProgress(prefs.getInt("zoom",0));add(body,zoom);
      zoom.setOnSeekBarChangeListener(seek("zoom"));
      add(body,label("Horizontal position",14));SeekBar panX=new SeekBar(this);panX.setMax(200);panX.setProgress(prefs.getInt("panX",100));add(body,panX);
      panX.setOnSeekBarChangeListener(seek("panX"));
      add(body,label("Vertical position",14));SeekBar panY=new SeekBar(this);panY.setMax(200);panY.setProgress(prefs.getInt("panY",100));add(body,panY);
      panY.setOnSeekBarChangeListener(seek("panY"));
      TextView pixels=label("Imported images are kept in their original format. Portrait fill can crop wide images; Fit keeps every part visible.",12);
      pixels.setTextColor(LILAC);add(body,pixels);
      add(body,button("Reset crop and position",()->{prefs.edit().putInt("zoom",0).putInt("panX",100).putInt("panY",100).putInt("fitMode",0).apply();openScene(key);}));
    }
    if(Worlds.isVideo(key)){
      add(body,label("Video loops with its original pixels. Audio is muted. Playback pauses whenever Android hides the wallpaper.",12));
    }else add(body,label("Ambient motion / parallax strength",15));
    SeekBar strength=new SeekBar(this);strength.setMax(100);strength.setProgress(prefs.getInt("motion",55));add(body,strength);
    strength.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}
      public void onProgressChanged(SeekBar s,int progress,boolean from){if(from)prefs.edit().putInt("motion",progress).apply();}});
    CheckBox particles=new CheckBox(this);particles.setText("Glowing dust, stars and particles");particles.setTextColor(WHITE);particles.setChecked(prefs.getBoolean("particles",true));add(body,particles);
    particles.setOnCheckedChangeListener((v,checked)->prefs.edit().putBoolean("particles",checked).apply());
    CheckBox tilt=new CheckBox(this);tilt.setText("Tilt-motion effect (uses accelerometer)");tilt.setTextColor(WHITE);tilt.setChecked(prefs.getBoolean("tilt",true));add(body,tilt);
    tilt.setOnCheckedChangeListener((v,checked)->prefs.edit().putBoolean("tilt",checked).apply());
    add(body,button("✦ USE ANIMATED LIVE WALLPAPER",this::applyLive));
    add(body,button("ⓘ CHECK ACTUAL HOME / LOCK WALLPAPER",this::showWallpaperPlacement));
    add(body,button("✦ SHOW MY LIVE WALLPAPER ON LOCK SCREEN",this::confirmLockMirror));
    add(body,label("If Android set the animation as Home only, the old static Lock wallpaper can hide it. The lock-screen option checks your current wallpaper, then asks before removing only that separate lock image.",12));
    add(body,label("Android will show its own preview and installation choices. Some phones do not offer 'Lock only' for animated wallpapers.",12));
    add(body,button("▧ SET STATIC LOCK SCREEN ONLY",()->applyStatic(WallpaperManager.FLAG_LOCK)));
    add(body,button("▧ Set static Home only",()->applyStatic(WallpaperManager.FLAG_SYSTEM)));
    add(body,button("▧ Set static Home + Lock",()->applyStatic(WallpaperManager.FLAG_LOCK|WallpaperManager.FLAG_SYSTEM)));
    if(!key.startsWith("built:")&&!key.startsWith("motion:"))add(body,button("× Remove from my saved collection",()->confirmDelete(key)));
  }
  SeekBar.OnSeekBarChangeListener seek(String name){return new SeekBar.OnSeekBarChangeListener(){
    public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}
    public void onProgressChanged(SeekBar s,int progress,boolean from){if(from){prefs.edit().putInt(name,progress).apply();if(preview!=null)preview.invalidate();}}
  };}
  void previewMessage(String msg){if(videoStatus!=null)videoStatus.setText(msg);}
  void releasePreviewPlayer(){
    ++previewGeneration;
    if(previewPlayer!=null){try{previewPlayer.release();}catch(Exception ignored){}previewPlayer=null;}
  }
  void releasePreview(){releasePreviewPlayer();videoTexture=null;videoStatus=null;}
  void startVideoPreview(){
    if(videoTexture==null||!videoTexture.isAvailable()||!resumed||!Worlds.isVideo(chosen))return;
    File source=Worlds.videoFile(this,chosen);
    if(source==null||!source.isFile()||source.length()==0){previewMessage("Video source missing; poster shown.");return;}
    releasePreviewPlayer();final int mine=previewGeneration;
    final TextureView view=videoTexture;
    view.setAlpha(0f);previewMessage("Opening video…");
    try{
      final MediaPlayer mp=new MediaPlayer();previewPlayer=mp;
      try(java.io.FileInputStream input=new java.io.FileInputStream(source)){
        mp.setDataSource(input.getFD(),0,source.length());
      }
      Surface surface=new Surface(view.getSurfaceTexture());
      try{mp.setSurface(surface);}finally{surface.release();}
      mp.setLooping(true);mp.setVolume(0f,0f);
      mp.setOnPreparedListener(player->{
        if(previewPlayer!=player||previewGeneration!=mine)return;
        previewMessage("Video prepared; waiting for first frame…");
        try{player.start();}catch(Exception e){previewMessage("Video start failed: "+e.getClass().getSimpleName());releasePreviewPlayer();}
      });
      mp.setOnInfoListener((player,what,extra)->{
        if(player==previewPlayer&&mine==previewGeneration&&what==MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START){
          if(videoTexture==view)view.setAlpha(1f);
          previewMessage("Animation playing");
        }
        return false;
      });
      mp.setOnErrorListener((player,what,extra)->{
        if(player==previewPlayer){previewMessage("Decoder error "+what+"/"+extra+"; showing still image");releasePreviewPlayer();}
        return true;
      });
      mp.prepareAsync();
      videoHandler.postDelayed(()->{
        if(previewPlayer==mp&&previewGeneration==mine&&view.getAlpha()<0.5f){
          previewMessage("No frames received; poster shown. Check live-wallpaper diagnostics.");
          releasePreviewPlayer();
        }
      },7000);
    }catch(Exception e){previewMessage("Video unavailable: "+e.getClass().getSimpleName()+"; showing poster");releasePreviewPlayer();}
  }
  @Override protected void onPause(){resumed=false;releasePreviewPlayer();super.onPause();}
  @Override protected void onResume(){super.onResume();resumed=true;
    if(videoTexture!=null&&videoTexture.isAvailable())startVideoPreview();
  }
  @Override protected void onDestroy(){releasePreview();super.onDestroy();}
  private WallpaperInfo wallpaperInfo(WallpaperManager manager,int which){
    try { return Build.VERSION.SDK_INT>=34 ? manager.getWallpaperInfo(which) :
        (which==WallpaperManager.FLAG_SYSTEM ? manager.getWallpaperInfo() : null);
    }catch(Exception e){return null;}
  }
  private boolean isOurLiveHome(WallpaperManager manager){
    WallpaperInfo info=wallpaperInfo(manager,WallpaperManager.FLAG_SYSTEM);
    return info!=null && new ComponentName(this,LockscapeWallpaperService.class).equals(info.getComponent());
  }
  void showWallpaperPlacement(){
    WallpaperManager manager=WallpaperManager.getInstance(this);
    WallpaperInfo home=wallpaperInfo(manager,WallpaperManager.FLAG_SYSTEM);
    WallpaperInfo lock=wallpaperInfo(manager,WallpaperManager.FLAG_LOCK);
    String homeText=home==null?"Static, unavailable, or not readable":"Live: "+home.getPackageName();
    String lockText=lock==null?"No live wallpaper reported (static / separate / unknown)":"Live: "+lock.getPackageName();
    String recent=prefs.getString("lastEngineEvent","No wallpaper-engine callback recorded");
    long lastLock=prefs.getLong("lastLockEngineSeen",0L);
    String seen=lastLock==0L?"No lock-screen engine callback recorded":"Last lock-screen engine visible: "+new java.text.SimpleDateFormat("MMM d, h:mm a",java.util.Locale.getDefault()).format(new java.util.Date(lastLock));
    new AlertDialog.Builder(this).setTitle("Android wallpaper placement")
      .setMessage("Home: "+homeText+"\nLock: "+lockText+"\n\n"+seen+"\nEngine: "+recent+"\n\nA live preview is not proof the lock screen is using that wallpaper.")
      .setPositiveButton("OK",null).show();
  }
  void confirmLockMirror(){
    WallpaperManager manager=WallpaperManager.getInstance(this);
    WallpaperInfo currentLock=wallpaperInfo(manager,WallpaperManager.FLAG_LOCK);
    boolean liveLock=currentLock!=null && new ComponentName(this,LockscapeWallpaperService.class).equals(currentLock.getComponent());
    if(liveLock){
      new AlertDialog.Builder(this).setTitle("Lockscapes is already your lock wallpaper")
        .setMessage("Android confirms Nova Lockscapes is assigned to the LOCK screen. There is nothing to reapply or remove. If it stays static, open Live wallpaper diagnostics: the problem is playback inside the lock-screen engine, not wallpaper placement.")
        .setPositiveButton("OK",null).show();
      return;
    }
    if(!isOurLiveHome(manager)){
      new AlertDialog.Builder(this).setTitle("Apply the live wallpaper first")
      .setMessage("Android is not reporting Nova Lockscapes as the active HOME live wallpaper. First choose 'Use Animated Live Wallpaper' and confirm Home and lock or Home in Android's wallpaper picker. Then return here.")
      .setPositiveButton("Open wallpaper picker",(d,w)->applyLive())
      .setNegativeButton("Cancel",null).show();
      return;
    }
    new AlertDialog.Builder(this).setTitle("Use the animation on your lock screen?")
      .setMessage("This removes your separate static LOCK wallpaper so Android can show your current animated HOME wallpaper there instead.\n\nYour imported artwork is NOT deleted. The change may not animate on phones whose lock screens don't support shared live wallpaper. You can restore your old static lock image by applying it again.")
      .setPositiveButton("Replace separate lock wallpaper",(d,w)->{
        try{
          WallpaperManager.getInstance(this).clear(WallpaperManager.FLAG_LOCK);
          new AlertDialog.Builder(this).setTitle("Lock wallpaper setting changed")
            .setMessage("Android has removed the separate lock image. Lock and wake the phone to check whether the home animation now appears. If it remains static, open the wallpaper placement report—your phone may not allow this type of live lock screen.")
            .setPositiveButton("Check status",(dd,ww)->showWallpaperPlacement())
            .setNegativeButton("OK",null).show();
        }catch(Exception ex){new AlertDialog.Builder(this).setTitle("Android refused the change").setMessage(ex.toString()).setPositiveButton("OK",null).show();}
      }).setNegativeButton("Cancel",null).show();
  }
  void applyLive(){prefs.edit().putString("scene",chosen).apply();try{
      Intent intent=new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
      intent.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,new ComponentName(this,LockscapeWallpaperService.class));
      startActivity(intent);
    }catch(Exception e){toast("This device could not open the live wallpaper picker: "+e.getMessage());}}
  void applyStatic(int flags){
    try{
      int width=Math.max(720,getResources().getDisplayMetrics().widthPixels);
      int height=Math.max(1280,getResources().getDisplayMetrics().heightPixels);
      // Render at the actual device resolution; never impose the old 1600-pixel cap.
      // A 4096px safeguard protects low-RAM devices with exotic external displays.
      float scale=Math.min(1f,4096f/Math.max(width,height));width=(int)(width*scale);height=(int)(height*scale);
      Bitmap wallpaper=Worlds.sample(this,chosen,width,height,prefs.getInt("fitMode",0),prefs.getInt("zoom",0),prefs.getInt("panX",100),prefs.getInt("panY",100));
      try{WallpaperManager manager=WallpaperManager.getInstance(this);manager.setBitmap(wallpaper,null,false,flags);}finally{wallpaper.recycle();}
      toast(flags==WallpaperManager.FLAG_LOCK?"Static LOCK wallpaper applied!":"Static wallpaper applied!");
    }catch(Exception ex){new AlertDialog.Builder(this).setTitle("Could not set wallpaper").setMessage(ex.getClass().getSimpleName()+": "+ex.getMessage()).setPositiveButton("OK",null).show();}
  }
  void confirmDelete(String key){new AlertDialog.Builder(this).setTitle("Delete imported world?").setMessage("Deletes the saved copy inside Nova Lockscapes, not the original photo on your phone.")
      .setPositiveButton("Delete",(d,w)->{try{
         File f;if(key.startsWith("image:"))f=new File(new File(getFilesDir(),"imports"),key.substring(6));
         else if(key.startsWith("video:"))f=new File(new File(getFilesDir(),"videos"),key.substring(6));
         else f=new File(new File(getFilesDir(),"packs"),key.substring(5));remove(f);
         prefs.edit().putString("scene","built:0").apply();chosen="built:0";home();
      }catch(Exception ex){toast("Could not delete: "+ex.getMessage());}}).setNegativeButton("Cancel",null).show();}
  static void remove(File f){if(f.isDirectory()){File[] children=f.listFiles();if(children!=null)for(File c:children)remove(c);}f.delete();}

  @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);
    if(result!=RESULT_OK||data==null||data.getData()==null)return;
    try{String newScene=request==OPEN_IMAGE?importImage(data.getData()):request==OPEN_VIDEO?importVideo(data.getData()):importPack(data.getData());openScene(newScene);
    }catch(Exception e){new AlertDialog.Builder(this).setTitle("Could not import").setMessage(e.getMessage()==null?e.toString():e.getMessage()).setPositiveButton("OK",null).show();}
  }
  static String newId(){return "nova_"+Long.toString(System.currentTimeMillis(),36)+"_"+Integer.toString((int)(Math.random()*100000),36);}
  // Import is BYTE FOR BYTE. Never convert PNG/WEBP to JPEG or downscale the saved master.
  String importImage(Uri uri) throws Exception {
    File folder=new File(getFilesDir(),"imports");
    if(!folder.exists()&&!folder.mkdirs())throw new Exception("Cannot create imports folder");
    byte[] buf=new byte[32768];byte[] header=new byte[12];
    try(InputStream stream=getContentResolver().openInputStream(uri)){
      if(stream==null)throw new Exception("Cannot open source image");
      int received=0;while(received<header.length){int n=stream.read(header,received,header.length-received);if(n<0)break;received+=n;}
      String ext;
      if(received>=8 && (header[0]&255)==137 && header[1]==80 && header[2]==78 && header[3]==71)ext="png";
      else if(received>=3 && (header[0]&255)==255 && (header[1]&255)==216 && (header[2]&255)==255)ext="jpg";
      else if(received>=12 && header[0]=='R'&&header[1]=='I'&&header[2]=='F'&&header[3]=='F'&&header[8]=='W'&&header[9]=='E'&&header[10]=='B'&&header[11]=='P')ext="webp";
      else throw new Exception("Choose a PNG, JPEG or WebP image.");
      File dest=new File(folder,newId()+"."+ext);
      try{
        long size=received;
        try(OutputStream out=new FileOutputStream(dest)){
          out.write(header,0,received);int count;
          while((count=stream.read(buf))>=0){size+=count;
            if(size>60L*1024*1024)throw new Exception("Image is over the 60 MB limit.");
            out.write(buf,0,count);
          }
        }
        BitmapFactory.Options dimensions=new BitmapFactory.Options();dimensions.inJustDecodeBounds=true;
        BitmapFactory.decodeFile(dest.getAbsolutePath(),dimensions);
        if(dimensions.outWidth<1||dimensions.outHeight<1||dimensions.outWidth>30000||dimensions.outHeight>30000||
          ((long)dimensions.outWidth)*dimensions.outHeight>150000000L)throw new Exception("Image has invalid or unsupported dimensions.");
        String key="image:"+dest.getName();prefs.edit().putString("title:"+key,sourceName(uri)).apply();return key;
      }catch(Exception ex){dest.delete();throw ex;}
    }
  }
  String sourceName(Uri uri){
    String label="Artwork";
    try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){
      if(c!=null&&c.moveToFirst())label=c.getString(0);
    }catch(Exception ignored){}
    label=displayName(label==null?"Artwork":label).replaceAll("[^A-Za-z0-9 _\\-]", " ").trim();
    return label.isEmpty()?"Artwork":label.substring(0,Math.min(48,label.length()));
  }
  String importVideo(Uri uri) throws Exception {
    File folder=new File(getFilesDir(),"videos");if(!folder.exists()&&!folder.mkdirs())throw new Exception("Cannot create videos library");
    byte[] buf=new byte[32768];byte[] header=new byte[16];
    try(InputStream in=getContentResolver().openInputStream(uri)){
      if(in==null)throw new Exception("Cannot open the video");
      int got=0;while(got<header.length){int n=in.read(header,got,header.length-got);if(n<0)break;got+=n;}
      boolean mp4=got>=12&&header[4]=='f'&&header[5]=='t'&&header[6]=='y'&&header[7]=='p';
      boolean webm=got>=4&&(header[0]&255)==0x1a&&(header[1]&255)==0x45&&(header[2]&255)==0xdf&&(header[3]&255)==0xa3;
      if(!mp4&&!webm)throw new Exception("Choose an MP4 or WebM video file.");
      File dest=new File(folder,newId()+(mp4?".mp4":".webm"));
      try{
        long size=got;
        try(OutputStream out=new FileOutputStream(dest)){
          out.write(header,0,got);int n;
          while((n=in.read(buf))!=-1){size+=n;if(size>250L*1024*1024)throw new Exception("Video exceeds the 250 MB limit.");out.write(buf,0,n);}
        }
        if(size<1000)throw new Exception("Video is empty or truncated.");
        MediaMetadataRetriever meta=new MediaMetadataRetriever();
        try{meta.setDataSource(dest.getAbsolutePath());
          String width=meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
          String height=meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
          String duration=meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
          if(width==null||height==null||duration==null)throw new Exception("Unsupported video metadata.");
          int w=Integer.parseInt(width),h=Integer.parseInt(height);long ms=Long.parseLong(duration);
          if(w<50||h<50||w>8192||h>8192||ms<250||ms>180000)throw new Exception("Video must be 0.25–180 seconds, at most 8K resolution.");
        }finally{meta.release();}
        String key="video:"+dest.getName();prefs.edit().putString("title:"+key,sourceName(uri)).apply();return key;
      }catch(Exception error){dest.delete();throw error;}
    }
  }
  String importPack(Uri uri) throws Exception {
    File parent=new File(getFilesDir(),"packs");if(!parent.exists()&&!parent.mkdirs())throw new Exception("Cannot create packs directory");
    String id=newId();File folder=new File(parent,id);if(!folder.mkdir())throw new Exception("Cannot create this scene folder");
    int total=0,entries=0;boolean found=false;
    try(InputStream source=getContentResolver().openInputStream(uri)){
      if(source==null)throw new Exception("Cannot open scene ZIP");
      try(ZipInputStream zip=new ZipInputStream(source)){
        ZipEntry entry;byte[] buffer=new byte[8192];
        while((entry=zip.getNextEntry())!=null){
          entries++;if(entries>30)throw new Exception("Scene pack has too many files");
          String name=entry.getName();if(entry.isDirectory()){zip.closeEntry();continue;}
          boolean accepted=name.equals("background.png")||name.equals("background.jpg")||name.equals("background.webp")||name.equals("foreground.png")||name.equals("foreground.webp")||name.equals("name.txt");
          if(!accepted){zip.closeEntry();continue;}
          if(name.startsWith("background."))found=true;
          try(OutputStream output=new FileOutputStream(new File(folder,name))){int len,fileBytes=0;
            while((len=zip.read(buffer))!=-1){fileBytes+=len;total+=len;
              if(fileBytes>14*1024*1024||total>28*1024*1024)throw new Exception("Scene pack is too large (limit 28 MB)");
              output.write(buffer,0,len);
            }}zip.closeEntry();
        }
      }
      if(!found)throw new Exception("ZIP needs background.png (or background.jpg/background.webp). Optional foreground.png adds a second depth layer.");
      Worlds.SceneArt check=Worlds.SceneArt.load(this,"pack:"+id,1080,2400);
      boolean valid=check.background!=null;check.clear();if(!valid)throw new Exception("Scene background was not a valid image");
      String key="pack:"+id;prefs.edit().putString("title:"+key,sourceName(uri)).apply();return key;
    }catch(Exception ex){remove(folder);throw ex;}
  }

  class Preview extends View {
    final String key;final Worlds.SceneArt art;final Handler handler=new Handler(Looper.getMainLooper());long first=android.os.SystemClock.uptimeMillis();boolean running=true;
    Runnable tick=new Runnable(){@Override public void run(){if(!running)return;invalidate();handler.postDelayed(this,44);}};
    Preview(String key){super(MainActivity.this);this.key=key;this.art=Worlds.SceneArt.load(MainActivity.this,key,1080,2400);
      setBackgroundColor(0xff1b122a);handler.postDelayed(tick,50);}
    @Override public void onDraw(Canvas c){super.onDraw(c);float seconds=(android.os.SystemClock.uptimeMillis()-first)/1000f;
      Worlds.draw(c,getWidth(),getHeight(),key,art,seconds,prefs.getInt("motion",55)/100f,0,0,prefs.getBoolean("particles",true),prefs.getInt("fitMode",0),prefs.getInt("zoom",0),prefs.getInt("panX",100),prefs.getInt("panY",100));}
    void stop(){running=false;handler.removeCallbacks(tick);art.clear();}
    @Override protected void onDetachedFromWindow(){stop();super.onDetachedFromWindow();}
  }
}