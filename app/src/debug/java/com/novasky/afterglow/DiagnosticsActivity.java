package com.novasky.afterglow;
import android.app.Activity;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.util.Log;
import org.videolan.libvlc.util.VLCVideoLayout;
import java.io.File;
import java.util.ArrayList;

/** Debug-only device check; it never appears in the family release APK. */
public final class DiagnosticsActivity extends Activity implements PlaybackService.Listener {
    private static final String TAG="AFTERGLOW_TEST";
    private static final String[] FILES={"sample.mp3","sample.flac","sample.opus","sample.wav","sample.mp4","sample.mkv","sample.webm","sample.avi"};
    private final Handler main=new Handler(Looper.getMainLooper());
    private PlaybackService service;private VLCVideoLayout video;private int index=0,attempts=0;private boolean failed=false,binding=false,controlsChecked=false;
    private final ServiceConnection conn=new ServiceConnection(){
        @Override public void onServiceConnected(ComponentName n,IBinder b){service=((PlaybackService.LocalBinder)b).service();service.setListener(DiagnosticsActivity.this);service.attachVideo(video);
            ArrayList<PlaybackService.Item> list=new ArrayList<>();for(String f:FILES){File file=new File(getFilesDir(),"fixtures/"+f);if(!file.isFile()){fail("missing "+f);return;}list.add(new PlaybackService.Item(Uri.fromFile(file),f,""));}
            service.clearQueue();service.addItems(list,false);start();}
        @Override public void onServiceDisconnected(ComponentName n){fail("service disconnected");}
    };
    @Override public void onCreate(Bundle b){super.onCreate(b);video=new VLCVideoLayout(this);setContentView(video);binding=bindService(new Intent(this,PlaybackService.class),conn,Context.BIND_AUTO_CREATE);}
    private void start(){if(index>=FILES.length){Log.i(TAG,"ALL_PASS "+FILES.length);return;}attempts=0;service.playIndex(index,false);main.postDelayed(this::poll,500);}
    private void poll(){if(failed||service==null)return;long t=service.time();if(service.isPlaying()&&t>=200){Log.i(TAG,"PASS "+FILES[index]+" time="+t);
        if(index==0&&!controlsChecked){checkControls();return;}index++;main.postDelayed(this::start,300);}
        else if(++attempts>=18)fail("timeout "+FILES[index]+" time="+t);else main.postDelayed(this::poll,500);}
    private void checkControls(){service.pauseByUser();main.postDelayed(()->{if(service.isPlaying()){fail("pause did not take effect");return;}service.resume();main.postDelayed(()->{if(!service.isPlaying()){fail("resume did not take effect");return;}service.seek(1800);main.postDelayed(()->{if(service.time()<1100){fail("seek did not advance");return;}controlsChecked=true;Log.i(TAG,"PASS controls pause/resume/seek");index++;start();},600);},700);},700);}
    private void fail(String s){if(failed)return;failed=true;Log.e(TAG,"FAIL "+s);}
    @Override public void onStateChanged(){}
    @Override public void onProblem(String s){fail(s);}
    @Override protected void onDestroy(){main.removeCallbacksAndMessages(null);if(binding){if(service!=null){service.removeListener(this);service.detachVideo(video);service.stopPlayback();}unbindService(conn);binding=false;}super.onDestroy();}
}
