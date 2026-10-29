package com.novasky.afterglow;

import android.app.*;
import android.content.*;
import android.media.*;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import android.database.Cursor;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.util.VLCVideoLayout;
import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** One player and queue survive activity rotation and background playback. */
public final class PlaybackService extends Service {
    public interface Listener { void onStateChanged(); void onProblem(String message); }
    public static final class Item {
        public final Uri uri;
        public final String title, mime;
        public final boolean video;
        public Item(Uri uri, String title, String mime) {
            this.uri=uri; this.title=(title==null || title.trim().isEmpty())?"Untitled":title;
            this.mime=mime==null?"":mime; this.video=MediaKind.video(this.title,this.mime);
        }
    }
    public final class LocalBinder extends Binder { public PlaybackService service() { return PlaybackService.this; } }
    private static final String CHANNEL="afterglow_playback", ACTION_TOGGLE="afterglow.toggle", ACTION_PREV="afterglow.prev", ACTION_NEXT="afterglow.next", ACTION_WAKE="afterglow.wake";
    private static final int NOTIFICATION_ID=17;
    private final LocalBinder binder=new LocalBinder();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final ArrayList<Item> queue=new ArrayList<>();
    private final Random random=new Random();
    private LibVLC vlc;
    private MediaPlayer player;
    private MediaSession session;
    private AudioManager audio;
    private AudioFocusRequest focusRequest;
    private ParcelFileDescriptor currentFd;
    private VLCVideoLayout attachedVideo;
    private Listener listener;
    private int index=-1, lastIndex=-1, repeat=0, generation=0;
    private boolean shuffle=false, foreground=false, resumeOnFocus=false, seekingResume=false;
    private long pendingResume=0;
    private final Runnable checkpoint=new Runnable(){@Override public void run(){if(isPlaying())savePosition();main.postDelayed(this,5000);}};

    private final BroadcastReceiver noisy = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(i.getAction())) pauseByUser(); }
    };
    private final AudioManager.OnAudioFocusChangeListener focusChange = change -> {
        if(player==null)return;
        if(change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK){ player.setVolume(20); }
        else if(change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT){ resumeOnFocus=player.isPlaying(); if(resumeOnFocus)player.pause(); update(); }
        else if(change==AudioManager.AUDIOFOCUS_LOSS){ resumeOnFocus=false; if(player.isPlaying())player.pause(); savePosition(); update(); }
        else if(change==AudioManager.AUDIOFOCUS_GAIN){ player.setVolume(100); if(resumeOnFocus){resumeOnFocus=false;ensureForeground();player.play();} update(); }
    };

    @Override public void onCreate(){
        super.onCreate();
        audio=(AudioManager)getSystemService(AUDIO_SERVICE);
        AudioAttributes attrs=new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();
        focusRequest=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attrs).setOnAudioFocusChangeListener(focusChange,main).build();
        ArrayList<String> options=new ArrayList<>(); options.add("--no-video-title-show");
        vlc=new LibVLC(getApplicationContext(),options); player=new MediaPlayer(vlc);
        session=new MediaSession(this,"NovaAfterglow");
        session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS|MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        session.setCallback(new MediaSession.Callback(){
            @Override public void onPlay(){resume();}
            @Override public void onPause(){pauseByUser();}
            @Override public void onSkipToNext(){next();}
            @Override public void onSkipToPrevious(){previous();}
            @Override public void onSeekTo(long pos){seek(pos);}
            @Override public void onStop(){stopPlayback();}
        });
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL,"Music and video playback",NotificationManager.IMPORTANCE_LOW));
        IntentFilter filter=new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        if(Build.VERSION.SDK_INT>=33)registerReceiver(noisy,filter,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(noisy,filter);
        restoreQueue(); updateSession();main.postDelayed(checkpoint,5000);
    }
    @Override public IBinder onBind(Intent i){return binder;}
    @Override public int onStartCommand(Intent i,int flags,int startId){
        String a=i==null?null:i.getAction();
        if(a!=null&&!ACTION_WAKE.equals(a)){
            promote();
            if(ACTION_TOGGLE.equals(a)){if(isPlaying())pauseByUser();else resume();}
            else if(ACTION_PREV.equals(a))previous();
            else if(ACTION_NEXT.equals(a))next();
        }
        return START_NOT_STICKY;
    }
    @Override public void onDestroy(){
        main.removeCallbacks(checkpoint);savePosition();persist();unregisterReceiver(noisy);worker.shutdownNow();
        if(attachedVideo!=null&&player!=null){player.detachViews();attachedVideo=null;}
        if(player!=null){player.stop();player.release();player=null;} closeFd();
        if(vlc!=null){vlc.release();vlc=null;} if(session!=null){session.setActive(false);session.release();}
        audio.abandonAudioFocusRequest(focusRequest);super.onDestroy();
    }
    public void setListener(Listener l){listener=l;}
    public void removeListener(Listener l){if(listener==l)listener=null;}
    public List<Item> items(){return Collections.unmodifiableList(queue);}
    public Item current(){return index>=0&&index<queue.size()?queue.get(index):null;}
    public int index(){return index;}
    public boolean isPlaying(){return player!=null&&player.isPlaying();}
    public boolean isVideo(){return current()!=null&&current().video;}
    public long time(){return player==null?0:Math.max(0,player.getTime());}
    public long length(){return player==null?0:Math.max(0,player.getLength());}
    public int repeat(){return repeat;}
    public boolean shuffle(){return shuffle;}
    public void cycleRepeat(){repeat=(repeat+1)%3;persist();update();}
    public void toggleShuffle(){shuffle=!shuffle;persist();update();}
    public void addItems(List<Item> incoming,boolean playFirst){
        if(incoming==null||incoming.isEmpty())return;
        int first=-1;HashMap<Uri,Integer> positions=new HashMap<>();
        for(int j=0;j<queue.size();j++)positions.put(queue.get(j).uri,j);
        for(Item item:incoming){if(item==null||item.uri==null)continue;Integer existing=positions.get(item.uri);
            if(existing==null){queue.add(item);existing=queue.size()-1;positions.put(item.uri,existing);}if(first<0)first=existing;}
        persist(); if(playFirst&&first>=0)playIndex(first,true);else update();
    }
    public void removeAt(int n){
        if(n<0||n>=queue.size())return;
        if(n==index){stopPlayback();queue.remove(n);index=-1;}else{queue.remove(n);if(n<index)index--;}
        if(lastIndex>=queue.size())lastIndex=-1;persist();update();
    }
    public void clearQueue(){stopPlayback();queue.clear();index=-1;lastIndex=-1;persist();update();}
    public void playIndex(int n,boolean resumePosition){
        if(n<0||n>=queue.size())return;
        savePosition();generation++;final int token=generation;lastIndex=index;index=n;pendingResume=resumePosition?loadPosition(queue.get(n)):0;seekingResume=false;
        player.stop();closeFd();persist();ensureForeground();
        if(audio.requestAudioFocus(focusRequest)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED){problem("Another app is using audio. Please try again.");leaveForeground();return;}
        Item item=queue.get(n);update();
        worker.execute(()->{
            ParcelFileDescriptor fd=null;String error=null;
            try{fd=getContentResolver().openFileDescriptor(item.uri,"r");if(fd==null)throw new FileNotFoundException();}
            catch(Exception ex){error="Can't open this file. Add it again or check where it is stored.";}
            final ParcelFileDescriptor open=fd;final String issue=error;
            main.post(()->{
                if(token!=generation){if(open!=null)try{open.close();}catch(Exception ignored){}return;}
                if(issue!=null){problem(issue);leaveForeground();update();return;}
                currentFd=open;
                try{
                    player.setEventListener(event->{final int type=event.type;main.post(()->onPlayerEvent(token,type));});
                    Media media=new Media(vlc,currentFd.getFileDescriptor());media.setHWDecoderEnabled(true,false);
                    player.setMedia(media);media.release();player.play();session.setActive(true);update();
                }catch(Exception ex){closeFd();problem("This file could not be opened by the player.");leaveForeground();update();}
            });
        });
    }
    private void onPlayerEvent(int token,int type){
        if(token!=generation||player==null)return;
        if(type==MediaPlayer.Event.TimeChanged||type==MediaPlayer.Event.PositionChanged)return;
        if(type==MediaPlayer.Event.Playing && pendingResume>1000&&!seekingResume){
            seekingResume=true;main.postDelayed(()->{if(token==generation&&player!=null){if(player.isSeekable())player.setTime(pendingResume);pendingResume=0;seekingResume=false;}},500);
        }else if(type==MediaPlayer.Event.EndReached){savePositionAt(0);if(repeat==2)playIndex(index,false);else nextAfterEnd();return;}
        else if(type==MediaPlayer.Event.EncounteredError){problem("This file couldn't be played. Its codec, access or file may be unsupported.");leaveForeground();}
        update();
    }
    public void resume(){
        if(current()==null){if(!queue.isEmpty())playIndex(0,true);return;}
        ensureForeground();if(audio.requestAudioFocus(focusRequest)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED){problem("Another app is using audio. Please try again.");leaveForeground();return;}
        if(currentFd==null){playIndex(index,true);return;}resumeOnFocus=false;player.play();session.setActive(true);update();
    }
    public void toggle(){if(isPlaying())pauseByUser();else resume();}
    public void pauseByUser(){resumeOnFocus=false;if(player!=null&&player.isPlaying())player.pause();savePosition();audio.abandonAudioFocusRequest(focusRequest);update();leaveForeground();}
    public void seek(long pos){if(player==null)return;player.setTime(Math.max(0,Math.min(pos,length())));savePositionAt(pos);update();}
    public void next(){if(queue.isEmpty())return;int n=nextIndex(true);if(n>=0)playIndex(n,true);}
    private void nextAfterEnd(){int n=nextIndex(repeat==1);if(n>=0)playIndex(n,false);else{audio.abandonAudioFocusRequest(focusRequest);update();leaveForeground();}}
    private int nextIndex(boolean wrap){if(queue.isEmpty())return -1;if(shuffle&&queue.size()>1){int n=random.nextInt(queue.size()-1);return n>=index?n+1:n;}int n=index+1;if(n<queue.size())return n;return wrap?0:-1;}
    public void previous(){if(queue.isEmpty())return;if(time()>4000){seek(0);return;}int n=shuffle&&lastIndex>=0?lastIndex:index-1;if(n<0)n=queue.size()-1;playIndex(n,true);}
    public void stopPlayback(){generation++;resumeOnFocus=false;savePosition();if(player!=null)player.stop();closeFd();audio.abandonAudioFocusRequest(focusRequest);session.setActive(false);leaveForeground();((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).cancel(NOTIFICATION_ID);stopSelf();update();}
    public void attachVideo(VLCVideoLayout layout){
        if(layout==null||player==null||attachedVideo==layout)return;
        if(attachedVideo!=null)player.detachViews();
        player.attachViews(layout,null,true,false);attachedVideo=layout;
    }
    public void detachVideo(VLCVideoLayout layout){if(layout!=null&&attachedVideo==layout&&player!=null){player.detachViews();attachedVideo=null;}}
    private void closeFd(){if(currentFd!=null){try{currentFd.close();}catch(Exception ignored){}currentFd=null;}}
    private void ensureForeground(){
        if(!foreground){Intent i=new Intent(this,PlaybackService.class).setAction(ACTION_WAKE);if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);promote();}
    }
    private void promote(){startForeground(NOTIFICATION_ID,notification());foreground=true;}
    private void leaveForeground(){if(foreground){stopForeground(STOP_FOREGROUND_DETACH);foreground=false;}}
    private PendingIntent command(String a,int code){Intent i=new Intent(this,PlaybackService.class).setAction(a);int f=PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE;return Build.VERSION.SDK_INT>=26?PendingIntent.getForegroundService(this,code,i,f):PendingIntent.getService(this,code,i,f);}
    private Notification notification(){
        Item item=current();String title=item==null?"Nova Afterglow":item.title;
        Intent open=new Intent(this,PlayerActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent content=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.MediaStyle style=new Notification.MediaStyle().setMediaSession(session.getSessionToken()).setShowActionsInCompactView(0,1,2);
        return new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_media_play).setContentTitle(title).setContentText("Nova Afterglow")
            .setContentIntent(content).setVisibility(Notification.VISIBILITY_PUBLIC).setOnlyAlertOnce(true).setOngoing(isPlaying())
            .addAction(android.R.drawable.ic_media_previous,"Previous",command(ACTION_PREV,1))
            .addAction(isPlaying()?android.R.drawable.ic_media_pause:android.R.drawable.ic_media_play,isPlaying()?"Pause":"Play",command(ACTION_TOGGLE,2))
            .addAction(android.R.drawable.ic_media_next,"Next",command(ACTION_NEXT,3)).setStyle(style).build();
    }
    private void updateSession(){
        if(session==null)return;Item item=current();
        session.setMetadata(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE,item==null?"Nova Afterglow":item.title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST,"Nova Afterglow").putLong(MediaMetadata.METADATA_KEY_DURATION,length()).build());
        long actions=PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PAUSE|PlaybackState.ACTION_PLAY_PAUSE|PlaybackState.ACTION_SKIP_TO_NEXT|PlaybackState.ACTION_SKIP_TO_PREVIOUS|PlaybackState.ACTION_SEEK_TO|PlaybackState.ACTION_STOP;
        session.setPlaybackState(new PlaybackState.Builder().setActions(actions).setState(isPlaying()?PlaybackState.STATE_PLAYING:PlaybackState.STATE_PAUSED,time(),isPlaying()?1:0).build());
    }
    private void update(){updateSession();if(foreground)((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(NOTIFICATION_ID,notification());if(listener!=null)listener.onStateChanged();}
    private void problem(String s){if(listener!=null)listener.onProblem(s);else android.util.Log.w("NovaAfterglow",s);}
    private SharedPreferences prefs(){return getSharedPreferences("afterglow",MODE_PRIVATE);}
    private void persist(){try{JSONArray a=new JSONArray();for(Item item:queue){JSONObject j=new JSONObject();j.put("uri",item.uri.toString());j.put("title",item.title);j.put("mime",item.mime);a.put(j);}prefs().edit().putString("queue",a.toString()).putInt("index",index).putInt("repeat",repeat).putBoolean("shuffle",shuffle).apply();}catch(Exception ignored){}}
    private void restoreQueue(){try{JSONArray a=new JSONArray(prefs().getString("queue","[]"));for(int n=0;n<a.length()&&n<5000;n++){JSONObject j=a.getJSONObject(n);queue.add(new Item(Uri.parse(j.getString("uri")),j.optString("title"),j.optString("mime")));}index=prefs().getInt("index",-1);if(index<0||index>=queue.size())index=-1;repeat=Math.max(0,Math.min(2,prefs().getInt("repeat",0)));shuffle=prefs().getBoolean("shuffle",false);}catch(Exception ignored){queue.clear();index=-1;}}
    private String posKey(Item item){try{byte[] digest=MessageDigest.getInstance("SHA-256").digest(item.uri.toString().getBytes(StandardCharsets.UTF_8));return "pos_"+Base64.encodeToString(digest,Base64.NO_WRAP|Base64.URL_SAFE);}catch(Exception e){return "pos_"+item.uri.hashCode();}}
    private long loadPosition(Item item){return Math.max(0,prefs().getLong(posKey(item),0));}
    private void savePosition(){if(current()!=null&&currentFd!=null){long t=time(),d=length();savePositionAt(d>0&&t>d-1500?0:t);}}
    private void savePositionAt(long t){if(current()!=null)prefs().edit().putLong(posKey(current()),Math.max(0,t)).apply();}
}
