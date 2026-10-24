package com.nova.lockscapes;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaPlayer;
import android.view.Surface;
import java.io.File;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;

/** Battery-aware animated wallpaper: only schedules frames while visible. */
public class LockscapeWallpaperService extends WallpaperService {
  @Override public Engine onCreateEngine(){return new SceneEngine();}
  final class SceneEngine extends Engine implements SensorEventListener, SharedPreferences.OnSharedPreferenceChangeListener {
    final SharedPreferences prefs=getSharedPreferences("lockscapes",MODE_PRIVATE);
    final Handler handler=new Handler(Looper.getMainLooper());
    final SensorManager sensorManager=(SensorManager)getSystemService(Context.SENSOR_SERVICE);
    final Sensor accelerometer=sensorManager==null?null:sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    Worlds.SceneArt art=new Worlds.SceneArt();
    String key="built:0";
    boolean visible=false,closed=false; MediaPlayer videoPlayer=null; boolean videoPrepared=false; boolean playbackFailed=false; int playerGeneration=0;
    float tiltX=0,tiltY=0;
    long started=SystemClock.uptimeMillis();
    final Runnable render=new Runnable(){@Override public void run(){paintFrame();}};
    SceneEngine(){super();prefs.registerOnSharedPreferenceChangeListener(this);loadScene();}
    void releaseVideo(){
      if(videoPlayer!=null){try{videoPlayer.stop();}catch(Exception ignored){}try{videoPlayer.reset();}catch(Exception ignored){}try{videoPlayer.release();}catch(Exception ignored){}videoPlayer=null;}
      videoPrepared=false;playerGeneration++;
    }
    void ensureVideo(){
      if(!Worlds.isVideo(key)||closed||playbackFailed)return;
      if(videoPlayer!=null){if(visible&&videoPrepared){try{videoPlayer.start();}catch(Exception ignored){}}return;}
      SurfaceHolder holder=getSurfaceHolder();if(holder==null||holder.getSurface()==null||!holder.getSurface().isValid())return;
      File file=Worlds.videoFile(LockscapeWallpaperService.this,key);
      if(file==null||!file.isFile())return;
      try{
        MediaPlayer player=new MediaPlayer();videoPlayer=player;videoPrepared=false; final int generation=++playerGeneration;
        player.setDataSource(file.getAbsolutePath());player.setDisplay(holder);player.setVolume(0f,0f);player.setLooping(true);
        player.setOnPreparedListener(mp->{if(videoPlayer!=mp||generation!=playerGeneration)return;videoPrepared=true;if(visible&&!closed){try{mp.start();}catch(Exception error){failVideo();}}});
        player.setOnErrorListener((mp,what,extra)->{if(videoPlayer==mp)failVideo();return true;});
        player.prepareAsync();
      }catch(Exception ex){failVideo();}
    }
    void failVideo(){releaseVideo();playbackFailed=true;schedule();}
    void drawVideoPoster(){
      SurfaceHolder holder=getSurfaceHolder();if(holder==null||holder.getSurface()==null||!holder.getSurface().isValid())return;
      Canvas c=null;Bitmap still=null;
      try{c=holder.lockCanvas();if(c==null)return;
        c.drawColor(Color.BLACK);
        still=Worlds.videoStill(LockscapeWallpaperService.this,key,c.getWidth(),c.getHeight());
        if(still==null){Worlds.SceneArt fallback=Worlds.SceneArt.load(LockscapeWallpaperService.this,"built:0");
          try{Worlds.draw(c,c.getWidth(),c.getHeight(),"built:0",fallback,0,0,0,0,false);}finally{fallback.clear();}}
        else Worlds.drawFramed(c,still,c.getWidth(),c.getHeight(),0,0,100,100,0,0);
      }catch(Exception ignored){}finally{if(still!=null&&!still.isRecycled())still.recycle();
        if(c!=null)try{holder.unlockCanvasAndPost(c);}catch(Exception ignored){}}
    }
    void loadScene(){String next=prefs.getString("scene","built:0");
      if(next.equals(key) && (Worlds.isVideo(next)?videoPlayer!=null:((next.startsWith("built:")&&!next.equals("built:0"))||(art.background!=null))))return;
      releaseVideo();playbackFailed=false;art.clear();key=next;art=Worlds.SceneArt.load(LockscapeWallpaperService.this,key);
      if(Worlds.isVideo(key))ensureVideo();
      started=SystemClock.uptimeMillis();schedule();
    }
    @Override public void onSharedPreferenceChanged(SharedPreferences preferences,String k){
      if("scene".equals(k))loadScene();else schedule();
      if("tilt".equals(k))registerSensor();
    }
    void schedule(){handler.removeCallbacks(render);if(visible&&!closed){
      if(Worlds.isVideo(key)){if(!playbackFailed)ensureVideo();if(playbackFailed||videoPlayer==null)handler.post(render);}
      else handler.post(render);
    }}
    void registerSensor(){if(sensorManager==null||accelerometer==null)return;
      sensorManager.unregisterListener(this);
      if(visible&&prefs.getBoolean("tilt",true))sensorManager.registerListener(this,accelerometer,SensorManager.SENSOR_DELAY_UI);
    }
    @Override public void onSensorChanged(SensorEvent event){if(event.sensor.getType()!=Sensor.TYPE_ACCELEROMETER)return;
      tiltX=tiltX*.87f+(event.values[0]/9.8f)*.13f;
      tiltY=tiltY*.87f+(event.values[1]/9.8f)*.13f;
    }
    @Override public void onAccuracyChanged(Sensor sensor,int accuracy){}
    @Override public void onVisibilityChanged(boolean showing){super.onVisibilityChanged(showing);visible=showing;
      registerSensor();
      if(Worlds.isVideo(key)){
        if(!showing&&videoPlayer!=null&&videoPrepared){try{videoPlayer.pause();}catch(Exception ignored){}}
        else if(showing){if(playbackFailed){playbackFailed=false;releaseVideo();}ensureVideo();}
      }
      schedule();}
    @Override public void onSurfaceCreated(SurfaceHolder holder){super.onSurfaceCreated(holder);playbackFailed=false;schedule();}
    @Override public void onSurfaceChanged(SurfaceHolder holder,int format,int w,int h){super.onSurfaceChanged(holder,format,w,h);if(Worlds.isVideo(key)){releaseVideo();playbackFailed=false;}schedule();}
    @Override public void onSurfaceDestroyed(SurfaceHolder holder){handler.removeCallbacks(render);releaseVideo();super.onSurfaceDestroyed(holder);}
    void paintFrame(){handler.removeCallbacks(render);if(closed||!visible)return;
      if(Worlds.isVideo(key)&&videoPlayer!=null)return;
      if(Worlds.isVideo(key)){drawVideoPoster();return;}
      SurfaceHolder holder=getSurfaceHolder();Canvas c=null;
      try{
        c=holder.lockCanvas();
        if(c!=null){
          float seconds=(SystemClock.uptimeMillis()-started)/1000f;
          float strength=prefs.getInt("motion",55)/100f;
          Worlds.draw(c,c.getWidth(),c.getHeight(),key,art,seconds,strength,tiltX,tiltY,prefs.getBoolean("particles",true),prefs.getInt("fitMode",0),prefs.getInt("zoom",0),prefs.getInt("panX",100),prefs.getInt("panY",100));
        }
      }catch(Exception ignored){}finally{
        if(c!=null){try{holder.unlockCanvasAndPost(c);}catch(Exception ignored){}}
      }
      // 24 frames per second, no rendering when hidden or destroyed.
      if(visible&&!closed)handler.postDelayed(render,Worlds.isVideo(key)?500:42);
    }
    @Override public void onDestroy(){closed=true;visible=false;handler.removeCallbacks(render);
      if(sensorManager!=null)sensorManager.unregisterListener(this);
      prefs.unregisterOnSharedPreferenceChangeListener(this);releaseVideo();art.clear();super.onDestroy();}
  }
}