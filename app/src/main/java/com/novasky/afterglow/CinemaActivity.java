package com.novasky.afterglow;

import android.app.Activity;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.videolan.libvlc.util.VLCVideoLayout;

/** A landscape screen for video; playback stays owned by the service. */
public final class CinemaActivity extends Activity implements PlaybackService.Listener {
    private PlaybackService service;private boolean bound=false,binding=false,active=false,dragging=false,shown=true;
    private VLCVideoLayout video;private LinearLayout bars;private TextView title;private Button play;private SeekBar seek;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Runnable tick=new Runnable(){@Override public void run(){if(active){update();main.postDelayed(this,600);}}};
    private final ServiceConnection connection=new ServiceConnection(){
        @Override public void onServiceConnected(ComponentName n,IBinder b){service=((PlaybackService.LocalBinder)b).service();bound=true;service.setListener(CinemaActivity.this);service.attachVideo(video);update();}
        @Override public void onServiceDisconnected(ComponentName n){bound=false;service=null;}
    };
    @Override public void onCreate(Bundle state){super.onCreate(state);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(0xff000000);setContentView(root);video=new VLCVideoLayout(this);root.addView(video,new FrameLayout.LayoutParams(-1,-1));
        bars=new LinearLayout(this);bars.setOrientation(LinearLayout.VERTICAL);bars.setPadding(Ui.dp(this,20),Ui.dp(this,14),Ui.dp(this,20),Ui.dp(this,18));bars.setBackgroundColor(0x66000000);root.addView(bars,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout top=Ui.row(this);Button close=Ui.button(this,"‹  Back",false);top.addView(close,Ui.lp(this,90,48));close.setOnClickListener(v->finish());title=Ui.text(this,"Video",17,Ui.CREAM,true);title.setSingleLine(true);title.setEllipsize(android.text.TextUtils.TruncateAt.END);LinearLayout.LayoutParams name=new LinearLayout.LayoutParams(0,-2,1);name.setMargins(Ui.dp(this,16),0,0,0);top.addView(title,name);bars.addView(top);
        View spacer=new View(this);bars.addView(spacer,new LinearLayout.LayoutParams(1,0,1));
        seek=new SeekBar(this);seek.setMax(10000);seek.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));seek.setThumbTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));bars.addView(seek,Ui.lp(this,-1,40));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar s){dragging=true;}public void onStopTrackingTouch(SeekBar s){if(service!=null&&service.length()>0)service.seek(service.length()*s.getProgress()/10000);dragging=false;}public void onProgressChanged(SeekBar s,int p,boolean fromUser){}});
        LinearLayout controls=Ui.row(this);controls.setGravity(Gravity.CENTER);Button back=Ui.button(this,"−10 sec",false);play=Ui.button(this,"▶",true);Button next=Ui.button(this,"Next  ▶▶",false);controls.addView(back,Ui.lp(this,110,48));LinearLayout.LayoutParams m=Ui.lp(this,86,52);m.setMargins(Ui.dp(this,12),0,Ui.dp(this,12),0);controls.addView(play,m);controls.addView(next,Ui.lp(this,110,48));bars.addView(controls);
        back.setOnClickListener(v->{if(service!=null)service.seek(Math.max(0,service.time()-10000));});play.setOnClickListener(v->{if(service!=null)service.toggle();});next.setOnClickListener(v->{if(service!=null)service.next();});
        root.setOnClickListener(v->{shown=!shown;bars.setVisibility(shown?View.VISIBLE:View.INVISIBLE);});video.setOnClickListener(v->{shown=!shown;bars.setVisibility(shown?View.VISIBLE:View.INVISIBLE);});
    }
    @Override protected void onStart(){super.onStart();active=true;binding=bindService(new Intent(this,PlaybackService.class),connection,Context.BIND_AUTO_CREATE);main.post(tick);}
    @Override protected void onStop(){active=false;main.removeCallbacks(tick);if(binding){if(service!=null){service.removeListener(this);service.detachVideo(video);}unbindService(connection);binding=false;bound=false;service=null;}super.onStop();}
    @Override public void onStateChanged(){update();}
    @Override public void onProblem(String message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
    private void update(){if(service==null)return;PlaybackService.Item i=service.current();if(i==null||!i.video){finish();return;}title.setText(i.title);play.setText(service.isPlaying()?"Ⅱ":"▶");play.setContentDescription(service.isPlaying()?"Pause":"Play");long d=service.length();seek.setEnabled(d>0);if(!dragging)seek.setProgress(d>0?(int)Math.min(10000,service.time()*10000/d):0);}
}
