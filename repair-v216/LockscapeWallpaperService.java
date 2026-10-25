package com.nova.lockscapes;

import android.content.Context;
import android.app.WallpaperManager;
import android.os.Build;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaPlayer;
import android.media.MediaMetadataRetriever;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.util.Log;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;
import java.io.File;
import java.io.FileInputStream;

/** Live wallpaper. MediaPlayer output and Canvas poster are never drawn simultaneously. */
public final class LockscapeWallpaperService extends WallpaperService {
  @Override public Engine onCreateEngine(){ return new SceneEngine(); }

  final class SceneEngine extends Engine implements SensorEventListener, SharedPreferences.OnSharedPreferenceChangeListener {
    final SharedPreferences prefs=getSharedPreferences("lockscapes",MODE_PRIVATE);
    final Handler main=new Handler(Looper.getMainLooper());
    final SensorManager sensors=(SensorManager)getSystemService(Context.SENSOR_SERVICE);
    final Sensor accel=sensors==null?null:sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    Worlds.SceneArt art=new Worlds.SceneArt();
    volatile String key="built:0";
    volatile boolean visible=false,closed=false,surfaceReady=false,playbackFailed=false,videoRendered=false;
    boolean prepared=false;
    volatile boolean fallbackFramesStarted=false;
    volatile int liveToken=0;
    String setupStage="not started";
    MediaPlayer player=null;
    int token=0;
    float tiltX=0,tiltY=0;
    long started=SystemClock.uptimeMillis();
    final Runnable frame=this::drawFrame;
    final Runnable timeout=new Runnable(){@Override public void run(){
      if(!closed&&visible&&Worlds.isVideo(key)&&player!=null&&!videoRendered){
        report("No video frames after 7 seconds: showing poster instead");
        failVideo();
      }
    }};
    SceneEngine(){prefs.registerOnSharedPreferenceChangeListener(this);reload(true);}
    void report(String message){
      Log.i("NovaLockscapesVideo",message);
      prefs.edit().putString("livePlaybackStatus",message).apply();
    }
    void armFrameTimeout(){main.removeCallbacks(timeout);if(!videoRendered&&visible)main.postDelayed(timeout,9000);}
    void recordEngine(String event){
      int flags=Build.VERSION.SDK_INT>=34?getWallpaperFlags():0;
      boolean preview=isPreview();
      String target=((flags & WallpaperManager.FLAG_LOCK)!=0 ? "Lock " : "")+
        ((flags & WallpaperManager.FLAG_SYSTEM)!=0 ? "Home " : "");
      if(target.isEmpty())target="unknown";
      SharedPreferences.Editor edit=prefs.edit().putString("lastEngineEvent",event+" ["+target+", preview="+preview+"]");
      if(!preview && visible && (flags & WallpaperManager.FLAG_LOCK)!=0)edit.putLong("lastLockEngineSeen",System.currentTimeMillis());
      edit.apply();
    }
    @Override public void onWallpaperFlagsChanged(int flags){super.onWallpaperFlagsChanged(flags);recordEngine("Wallpaper flags changed");}
    void stopPlayer(){
      main.removeCallbacks(timeout);
      ++token;
      if(player!=null){try{player.setSurface(null);}catch(Exception ignored){}try{player.release();}catch(Exception ignored){}player=null;}
      prepared=false;videoRendered=false;
    }
    void failVideo(){
      stopPlayer();playbackFailed=true;
      // An OEM video player can enter Error state even with a valid local MP4.
      // Prefer an actual moving lock wallpaper to a permanently frozen poster.
      main.removeCallbacks(frame);
      if(visible&&surfaceReady&&!closed){main.post(frame);startFrameFallback();}
    }
    void startFrameFallback(){
      if(fallbackFramesStarted||closed||!visible||!surfaceReady||!Worlds.isVideo(key))return;
      final String target=key;
      final File file=Worlds.videoFile(LockscapeWallpaperService.this,target);
      if(file==null||!file.isFile())return;
      final int generation=++liveToken;
      fallbackFramesStarted=true;
      Thread t=new Thread(()->{
        MediaMetadataRetriever retriever=new MediaMetadataRetriever();
        int delivered=0;
        try{
          retriever.setDataSource(file.getAbsolutePath());
          String raw=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
          long lengthMs=8000L;
          try{if(raw!=null)lengthMs=Math.max(1000,Long.parseLong(raw));}catch(Exception ignored){}
          final long duration=lengthMs;
          long start=SystemClock.uptimeMillis();
          while(!closed && visible && surfaceReady && playbackFailed && generation==liveToken && key.equals(target)){
            long atMs=(SystemClock.uptimeMillis()-start)%duration;
            Bitmap b=retriever.getScaledFrameAtTime(atMs*1000L,
              MediaMetadataRetriever.OPTION_CLOSEST,432,768);
            if(b!=null){
              final Bitmap image=b;
              main.post(()->{
                try{if(!closed&&visible&&surfaceReady&&generation==liveToken&&playbackFailed){
                  drawFallbackFrame(image);
                }}finally{image.recycle();}
              });
              if(++delivered==1)report("Fallback animation: decoded frames are moving on the wallpaper surface (reduced frame rate)");
            }
            Thread.sleep(180); // capped at roughly 5-6 fps to limit battery use
          }
        }catch(Exception e){report("Fallback frame decoder: "+e.getClass().getSimpleName()+" "+e.getMessage());}
        finally{try{retriever.release();}catch(Exception ignored){}main.post(()->{if(generation==liveToken)fallbackFramesStarted=false;});}
      },"Lockscapes-frame-fallback");
      t.setDaemon(true);t.start();
    }
    void drawFallbackFrame(Bitmap frameBitmap){
      SurfaceHolder holder=getSurfaceHolder();
      if(holder==null||holder.getSurface()==null||!holder.getSurface().isValid())return;
      Canvas canvas=null;
      try{
        canvas=holder.lockCanvas();if(canvas==null)return;
        canvas.drawColor(Color.BLACK);
        float scale=Math.min(canvas.getWidth()/(float)frameBitmap.getWidth(),canvas.getHeight()/(float)frameBitmap.getHeight());
        float x=(canvas.getWidth()-frameBitmap.getWidth()*scale)*.5f;
        float y=(canvas.getHeight()-frameBitmap.getHeight()*scale)*.5f;
        Matrix matrix=new Matrix();matrix.setScale(scale,scale);matrix.postTranslate(x,y);
        Paint paint=new Paint(Paint.FILTER_BITMAP_FLAG|Paint.ANTI_ALIAS_FLAG);
        canvas.drawBitmap(frameBitmap,matrix,paint);
      }catch(Exception e){report("Fallback canvas: "+e.getClass().getSimpleName());}
      finally{if(canvas!=null)try{holder.unlockCanvasAndPost(canvas);}catch(Exception ignored){}}
    }
    void poster(){
      if(!surfaceReady||closed)return;
      SurfaceHolder holder=getSurfaceHolder();if(holder==null||holder.getSurface()==null||!holder.getSurface().isValid())return;
      Canvas c=null;
      try{
        c=holder.lockCanvas();if(c==null)return;
        c.drawColor(Color.BLACK);
        if(art!=null&&art.background!=null){
          Worlds.draw(c,c.getWidth(),c.getHeight(),key,art,0,0,0,0,false,
            prefs.getInt("fitMode",0),prefs.getInt("zoom",0),prefs.getInt("panX",100),prefs.getInt("panY",100));
        }else{
          Worlds.SceneArt fallback=Worlds.SceneArt.load(LockscapeWallpaperService.this,"built:0",c.getWidth(),c.getHeight());
          try{Worlds.draw(c,c.getWidth(),c.getHeight(),"built:0",fallback,0,0,0,0,false);}finally{fallback.clear();}
        }
      }catch(Exception e){report("Poster draw: "+e.getClass().getSimpleName());}
      finally{if(c!=null)try{holder.unlockCanvasAndPost(c);}catch(Exception ignored){}}
    }
    void beginVideo(){
      if(!Worlds.isVideo(key)||!visible||!surfaceReady||closed||playbackFailed)return;
      if(player!=null){if(prepared){try{player.start();armFrameTimeout();}catch(Exception e){report("Resume failed: "+e.getClass().getSimpleName());failVideo();}}return;}
      File source=Worlds.videoFile(LockscapeWallpaperService.this,key);
      if(source==null||!source.isFile()||source.length()==0){report("Video file missing or empty");failVideo();return;}
      SurfaceHolder holder=getSurfaceHolder();if(holder==null||holder.getSurface()==null||!holder.getSurface().isValid())return;
      try{
        setupStage="create player";
        MediaPlayer p=new MediaPlayer();player=p;prepared=false;videoRendered=false;final int mine=++token;
        report("Lockscape video: opening file ("+(isPreview()?"preview":"installed")+")");
        // Keep setup minimal until MediaPlayer is PREPARED. Several decoders reject
        // playback options while their native engine is still initializing.
        setupStage="setDataSource";
        try(FileInputStream in=new FileInputStream(source)){
          p.setDataSource(in.getFD(),0,source.length());
        }
        // Deliberately do not attach the WallpaperService Surface before preparation.
        // The source is a valid MP4, but on this OEM assigning the engine surface
        // during initialization was followed by IllegalStateException in prepareAsync.
        // Attach the surface in the Prepared callback, before start().
        setupStage="register prepared listener";
        p.setOnPreparedListener(mp->{
          if(player!=mp||mine!=token)return;
          prepared=true;
          report("Live video prepared ("+(isPreview()?"preview":"installed")+"); waiting for frame");
          try{
            // These settings are legal and reliable in the Prepared state.
            if(!surfaceReady || getSurfaceHolder().getSurface()==null || !getSurfaceHolder().getSurface().isValid()){
              report("Prepared before valid output surface; falling back to moving frames");failVideo();return;
            }
            setupStage="attach prepared surface";
            mp.setSurface(getSurfaceHolder().getSurface());
            mp.setLooping(true);
            mp.setVolume(0f,0f);
            if(visible&&!closed){mp.start();armFrameTimeout();}
          }catch(Exception e){report("Prepared/start error: "+e.getClass().getSimpleName());Log.e("NovaLockscapesVideo","Prepared player failed",e);failVideo();}
        });
        p.setOnInfoListener((mp,what,extra)->{
          if(player==mp&&mine==token&&what==MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START){
            videoRendered=true;main.removeCallbacks(timeout);report("Animated video is rendering");
          }
          return false;
        });
        p.setOnErrorListener((mp,what,extra)->{
          if(player==mp&&mine==token){report("Video decoder error "+what+" / "+extra);failVideo();}
          return true;
        });
        setupStage="prepareAsync";
        p.prepareAsync();
        report("Live video preparing ("+(isPreview()?"preview":"installed")+")");
      }catch(Exception e){
        report("Video setup at "+setupStage+": "+e.getClass().getSimpleName()+" "+e.getMessage());
        Log.e("NovaLockscapesVideo","Video setup failed at "+setupStage,e);
        failVideo();
      }
    }
    void reload(boolean force){
      String next=prefs.getString("scene","built:0");
      if(!force&&next.equals(key))return;
      ++liveToken;fallbackFramesStarted=false;
      main.removeCallbacks(frame);stopPlayer();art.clear();key=next;playbackFailed=false;
      art=Worlds.SceneArt.load(LockscapeWallpaperService.this,key);started=SystemClock.uptimeMillis();
      if(surfaceReady&&visible){if(Worlds.isVideo(key)){poster();beginVideo();}else main.post(frame);}
    }
    @Override public void onSharedPreferenceChanged(SharedPreferences sp,String changed){
      if("livePlaybackStatus".equals(changed)||"lastEngineEvent".equals(changed)||"lastLockEngineSeen".equals(changed))return;
      if("scene".equals(changed))reload(false);
      else if(visible){if(!Worlds.isVideo(key)){main.removeCallbacks(frame);main.post(frame);}if("tilt".equals(changed))listen();}
    }
    void listen(){if(sensors==null||accel==null)return;sensors.unregisterListener(this);
      if(visible&&prefs.getBoolean("tilt",false))sensors.registerListener(this,accel,SensorManager.SENSOR_DELAY_UI);
    }
    @Override public void onSensorChanged(SensorEvent e){if(e.sensor.getType()!=Sensor.TYPE_ACCELEROMETER)return;
      tiltX=.87f*tiltX+.13f*(e.values[0]/9.8f);tiltY=.87f*tiltY+.13f*(e.values[1]/9.8f);
    }
    @Override public void onAccuracyChanged(Sensor sensor,int accuracy){}
    @Override public void onVisibilityChanged(boolean showing){
      super.onVisibilityChanged(showing);visible=showing;recordEngine(showing?"Visible":"Hidden");listen();main.removeCallbacks(frame);
      if(!showing){++liveToken;fallbackFramesStarted=false;main.removeCallbacks(timeout);if(player!=null&&prepared)try{player.pause();}catch(Exception ignored){}}
      else if(surfaceReady){
        if(Worlds.isVideo(key)){
          if(playbackFailed){playbackFailed=false;poster();}
          if(player==null)beginVideo();else if(prepared)try{player.start();armFrameTimeout();}catch(Exception e){report("Resume error: "+e.getClass().getSimpleName());failVideo();}
          if(playbackFailed)main.post(frame);
        }else main.post(frame);
      }
    }
    @Override public void onSurfaceCreated(SurfaceHolder h){super.onSurfaceCreated(h);surfaceReady=true;recordEngine("Surface created");
      if(Worlds.isVideo(key)){playbackFailed=false;poster();if(visible)beginVideo();}else if(visible)main.post(frame);
    }
    @Override public void onSurfaceChanged(SurfaceHolder h,int fmt,int w,int hgt){
      super.onSurfaceChanged(h,fmt,w,hgt);surfaceReady=w>0&&hgt>0;
      if(Worlds.isVideo(key)){
        // Surface replacement invalidates codec output; resize alone need not restart playback.
        if(player==null&&visible){playbackFailed=false;poster();beginVideo();}
      }else if(visible){main.removeCallbacks(frame);main.post(frame);}
    }
    @Override public void onSurfaceDestroyed(SurfaceHolder h){surfaceReady=false;++liveToken;fallbackFramesStarted=false;main.removeCallbacks(frame);stopPlayer();super.onSurfaceDestroyed(h);}
    void drawFrame(){
      main.removeCallbacks(frame);if(closed||!visible||!surfaceReady)return;
      if(Worlds.isVideo(key)){if(playbackFailed||player==null)poster();return;}
      SurfaceHolder holder=getSurfaceHolder();Canvas canvas=null;
      try{
        canvas=holder.lockCanvas();if(canvas!=null)Worlds.draw(canvas,canvas.getWidth(),canvas.getHeight(),key,art,
          (SystemClock.uptimeMillis()-started)/1000f,prefs.getInt("motion",55)/100f,
          tiltX,tiltY,prefs.getBoolean("particles",true),prefs.getInt("fitMode",0),
          prefs.getInt("zoom",0),prefs.getInt("panX",100),prefs.getInt("panY",100));
      }catch(Exception ignored){}finally{if(canvas!=null)try{holder.unlockCanvasAndPost(canvas);}catch(Exception ignored){}}
      if(visible&&!closed)main.postDelayed(frame,42);
    }
    @Override public void onDestroy(){closed=true;visible=false;++liveToken;fallbackFramesStarted=false;main.removeCallbacks(frame);main.removeCallbacks(timeout);
      if(sensors!=null)sensors.unregisterListener(this);prefs.unregisterOnSharedPreferenceChangeListener(this);
      stopPlayer();art.clear();super.onDestroy();
    }
  }
}