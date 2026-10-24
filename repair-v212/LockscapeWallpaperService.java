package com.nova.lockscapes;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaPlayer;
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
    String key="built:0";
    boolean visible=false,closed=false,surfaceReady=false,playbackFailed=false,videoRendered=false;
    boolean prepared=false;
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
    void report(String message){prefs.edit().putString("livePlaybackStatus",message).apply();}
    void stopPlayer(){
      main.removeCallbacks(timeout);
      ++token;
      if(player!=null){try{player.setSurface(null);}catch(Exception ignored){}try{player.release();}catch(Exception ignored){}player=null;}
      prepared=false;videoRendered=false;
    }
    void failVideo(){
      stopPlayer();playbackFailed=true;
      // Draw only after the video output has released its surface.
      main.removeCallbacks(frame);
      if(visible&&surfaceReady&&!closed)main.post(frame);
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
      if(player!=null){if(prepared){try{player.start();}catch(Exception e){report("Resume failed: "+e.getClass().getSimpleName());failVideo();}}return;}
      File source=Worlds.videoFile(LockscapeWallpaperService.this,key);
      if(source==null||!source.isFile()||source.length()==0){report("Video file missing or empty");failVideo();return;}
      SurfaceHolder holder=getSurfaceHolder();if(holder==null||holder.getSurface()==null||!holder.getSurface().isValid())return;
      try{
        MediaPlayer p=new MediaPlayer();player=p;prepared=false;videoRendered=false;final int mine=++token;
        // Private app files must be provided through a file descriptor, not a file:// URI.
        try(FileInputStream in=new FileInputStream(source)){
          p.setDataSource(in.getFD(),0,source.length());
        }
        p.setDisplay(holder);p.setLooping(true);p.setVolume(0f,0f);
        p.setOnPreparedListener(mp->{
          if(player!=mp||mine!=token)return;
          prepared=true;
          report("Video decoded and prepared; waiting for first frame");
          if(visible&&!closed)try{mp.start();}catch(Exception e){report("Video start error: "+e.getClass().getSimpleName());failVideo();}
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
        main.removeCallbacks(timeout);main.postDelayed(timeout,7000);
        p.prepareAsync();
      }catch(Exception e){report("Video setup: "+e.getClass().getSimpleName()+" "+e.getMessage());failVideo();}
    }
    void reload(boolean force){
      String next=prefs.getString("scene","built:0");
      if(!force&&next.equals(key))return;
      main.removeCallbacks(frame);stopPlayer();art.clear();key=next;playbackFailed=false;
      art=Worlds.SceneArt.load(LockscapeWallpaperService.this,key);started=SystemClock.uptimeMillis();
      if(surfaceReady&&visible){if(Worlds.isVideo(key)){poster();beginVideo();}else main.post(frame);}
    }
    @Override public void onSharedPreferenceChanged(SharedPreferences sp,String changed){
      if("livePlaybackStatus".equals(changed))return;
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
      super.onVisibilityChanged(showing);visible=showing;listen();main.removeCallbacks(frame);
      if(!showing){main.removeCallbacks(timeout);if(player!=null&&prepared)try{player.pause();}catch(Exception ignored){}}
      else if(surfaceReady){
        if(Worlds.isVideo(key)){
          if(playbackFailed){playbackFailed=false;poster();}
          if(player==null)beginVideo();else if(prepared)try{player.start();}catch(Exception e){report("Resume error: "+e.getClass().getSimpleName());failVideo();}
          if(playbackFailed)main.post(frame);
        }else main.post(frame);
      }
    }
    @Override public void onSurfaceCreated(SurfaceHolder h){super.onSurfaceCreated(h);surfaceReady=true;
      if(Worlds.isVideo(key)){poster();if(visible)beginVideo();}else if(visible)main.post(frame);
    }
    @Override public void onSurfaceChanged(SurfaceHolder h,int fmt,int w,int hgt){
      super.onSurfaceChanged(h,fmt,w,hgt);surfaceReady=w>0&&hgt>0;
      if(Worlds.isVideo(key)){
        // Surface replacement invalidates codec output; resize alone need not restart playback.
        if(player==null&&visible){poster();beginVideo();}
      }else if(visible){main.removeCallbacks(frame);main.post(frame);}
    }
    @Override public void onSurfaceDestroyed(SurfaceHolder h){surfaceReady=false;main.removeCallbacks(frame);stopPlayer();super.onSurfaceDestroyed(h);}
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
    @Override public void onDestroy(){closed=true;visible=false;main.removeCallbacks(frame);main.removeCallbacks(timeout);
      if(sensors!=null)sensors.unregisterListener(this);prefs.unregisterOnSharedPreferenceChangeListener(this);
      stopPlayer();art.clear();super.onDestroy();
    }
  }
}