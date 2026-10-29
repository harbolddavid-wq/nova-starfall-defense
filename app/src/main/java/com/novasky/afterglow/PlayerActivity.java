package com.novasky.afterglow;

import android.animation.ObjectAnimator;
import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import org.videolan.libvlc.util.VLCVideoLayout;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class PlayerActivity extends Activity implements PlaybackService.Listener {
    private static final int PICK_FILES=101,PICK_FOLDER=102;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService scan=Executors.newSingleThreadExecutor();
    private PlaybackService service;
    private boolean bound=false,binding=false,visible=false,resumed=false,dragging=false,handledView=false;
    private final ArrayList<PlaybackService.Item> pendingItems=new ArrayList<>();
    private boolean pendingPlay=false;
    private FrameLayout stage;
    private VLCVideoLayout video;
    private LinearLayout artwork;
    private ImageView planet;
    private ObjectAnimator orbit;
    private TextView title,subtitle,clock,queueSummary;
    private Button play,repeat,shuffle,cinema,queueButton;
    private SeekBar seek;
    private AlertDialog queueDialog;
    private ListView queueList;
    private ArrayAdapter<String> queueAdapter;
    private String lastQueueKey="";

    private final ServiceConnection connection=new ServiceConnection(){
        @Override public void onServiceConnected(android.content.ComponentName n, IBinder b){
            service=((PlaybackService.LocalBinder)b).service();bound=true;service.setListener(PlayerActivity.this);
            if(!pendingItems.isEmpty()){service.addItems(new ArrayList<>(pendingItems),pendingPlay);pendingItems.clear();pendingPlay=false;}
            if(!handledView&&Intent.ACTION_VIEW.equals(getIntent().getAction())){handledView=true;handleExternal(getIntent());}
            refresh();
        }
        @Override public void onServiceDisconnected(android.content.ComponentName n){bound=false;service=null;}
    };
    private final Runnable tick=new Runnable(){@Override public void run(){if(visible){refreshProgress();handler.postDelayed(this,600);}}};

    @Override public void onCreate(Bundle state){super.onCreate(state);if(state!=null)handledView=state.getBoolean("handledView");
        getWindow().setStatusBarColor(Ui.INK);getWindow().setNavigationBarColor(Ui.INK);build();}
    @Override protected void onStart(){super.onStart();visible=true;binding=bindService(new Intent(this,PlaybackService.class),connection,Context.BIND_AUTO_CREATE);handler.post(tick);}
    @Override protected void onResume(){super.onResume();resumed=true;if(service!=null){service.setListener(this);refresh();}}
    @Override protected void onPause(){resumed=false;if(service!=null)service.detachVideo(video);if(orbit!=null&&orbit.isStarted())orbit.pause();super.onPause();}
    @Override protected void onStop(){visible=false;handler.removeCallbacks(tick);if(binding){if(service!=null){service.removeListener(this);service.detachVideo(video);}unbindService(connection);binding=false;bound=false;service=null;}super.onStop();}
    @Override protected void onDestroy(){scan.shutdownNow();if(orbit!=null)orbit.cancel();super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle out){out.putBoolean("handledView",handledView);super.onSaveInstanceState(out);}
    @Override protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);handledView=false;if(service!=null&&Intent.ACTION_VIEW.equals(i.getAction())){handledView=true;handleExternal(i);}}

    private void build(){
        GradientDrawable sky=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0xff101425,0xff1a182d,0xff0e101e});
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackground(sky);
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(Ui.dp(this,22),Ui.dp(this,18),Ui.dp(this,22),Ui.dp(this,30));scroll.addView(body);setContentView(scroll);
        LinearLayout top=Ui.row(this);TextView mark=Ui.text(this,"✦  N O V A",13,Ui.ACCENT,true);top.addView(mark,new LinearLayout.LayoutParams(0,-2,1));
        Button about=Ui.button(this,"ⓘ",false);about.setContentDescription("About Afterglow");top.addView(about,Ui.lp(this,52,48));about.setOnClickListener(v->about());body.addView(top);
        Ui.gap(body,7);TextView brand=Ui.text(this,"Afterglow",34,Ui.CREAM,true);brand.setTypeface(Typeface.create("serif",Typeface.BOLD));body.addView(brand);
        Ui.gap(body,5);body.addView(Ui.text(this,"YOUR MUSIC  ·  YOUR STORIES",11,Ui.MUTED,true));Ui.gap(body,18);

        stage=new FrameLayout(this);stage.setBackground(Ui.round(0xff1c2033,Ui.dp(this,20),0xff4c5369));stage.setClipToOutline(true);body.addView(stage,Ui.lp(this,-1,245));
        artwork=new LinearLayout(this);artwork.setGravity(Gravity.CENTER);artwork.setOrientation(LinearLayout.VERTICAL);stage.addView(artwork,new FrameLayout.LayoutParams(-1,-1));
        FrameLayout artSpace=new FrameLayout(this);artwork.addView(artSpace,new LinearLayout.LayoutParams(-1,-1));
        ImageView desk=asset("art/paper_desk.png");if(desk!=null){desk.setScaleType(ImageView.ScaleType.CENTER_CROP);desk.setAlpha(.45f);desk.setColorFilter(0xff526387,PorterDuff.Mode.MULTIPLY);artSpace.addView(desk,new FrameLayout.LayoutParams(-1,-1));}
        ImageView glow=asset("art/energy.png");if(glow!=null){glow.setAlpha(.26f);FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(Ui.dp(this,230),Ui.dp(this,230),Gravity.CENTER);artSpace.addView(glow,p);}
        planet=asset("art/neptune.png");if(planet!=null){planet.setContentDescription("A blue planet surrounded by a soft glow");FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(Ui.dp(this,196),Ui.dp(this,196),Gravity.CENTER);artSpace.addView(planet,p);orbit=ObjectAnimator.ofFloat(planet,View.ROTATION,0,360);orbit.setDuration(180000);orbit.setRepeatCount(ObjectAnimator.INFINITE);orbit.setInterpolator(new android.view.animation.LinearInterpolator());}
        else {TextView fallback=Ui.text(this,"✦",90,Ui.BLUE,false);fallback.setGravity(Gravity.CENTER);artSpace.addView(fallback,new FrameLayout.LayoutParams(-1,-1));}
        ImageView flare=asset("art/flare.png");if(flare!=null){flare.setAlpha(.55f);FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(Ui.dp(this,65),Ui.dp(this,65),Gravity.TOP|Gravity.END);p.setMargins(0,Ui.dp(this,16),Ui.dp(this,16),0);artSpace.addView(flare,p);}
        video=new VLCVideoLayout(this);video.setVisibility(View.GONE);stage.addView(video,new FrameLayout.LayoutParams(-1,-1));
        Ui.gap(body,18);title=Ui.text(this,"Nothing playing yet",21,Ui.CREAM,true);title.setSingleLine(true);title.setEllipsize(TextUtils.TruncateAt.END);body.addView(title);
        Ui.gap(body,6);subtitle=Ui.text(this,"Choose music or a video to begin",13,Ui.MUTED,false);body.addView(subtitle);Ui.gap(body,12);
        seek=new SeekBar(this);seek.setMax(10000);seek.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));seek.setThumbTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));body.addView(seek,Ui.lp(this,-1,34));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar s){dragging=true;}public void onStopTrackingTouch(SeekBar s){if(service!=null&&service.length()>0)service.seek(service.length()*s.getProgress()/10000);dragging=false;}public void onProgressChanged(SeekBar s,int p,boolean fromUser){if(fromUser&&service!=null)clock.setText(format(service.length()*p/10000)+"  /  "+format(service.length()));}});
        clock=Ui.text(this,"0:00  /  0:00",12,Ui.MUTED,false);clock.setGravity(Gravity.END);body.addView(clock);Ui.gap(body,8);
        LinearLayout controls=Ui.row(this);controls.setGravity(Gravity.CENTER);Button prev=Ui.button(this,"◀◀",false);prev.setContentDescription("Previous track");controls.addView(prev,Ui.lp(this,72,52));
        play=Ui.button(this,"▶",true);play.setTextSize(23);play.setContentDescription("Play or pause");LinearLayout.LayoutParams pm=Ui.lp(this,86,58);pm.setMargins(Ui.dp(this,12),0,Ui.dp(this,12),0);controls.addView(play,pm);
        Button next=Ui.button(this,"▶▶",false);next.setContentDescription("Next track");controls.addView(next,Ui.lp(this,72,52));body.addView(controls);
        prev.setOnClickListener(v->{if(service!=null)service.previous();});play.setOnClickListener(v->{if(service!=null)service.toggle();});next.setOnClickListener(v->{if(service!=null)service.next();});Ui.gap(body,12);
        LinearLayout modes=Ui.row(this);shuffle=Ui.button(this,"Shuffle off",false);repeat=Ui.button(this,"Repeat off",false);modes.addView(shuffle,new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));LinearLayout.LayoutParams rm=new LinearLayout.LayoutParams(0,Ui.dp(this,48),1);rm.setMargins(Ui.dp(this,10),0,0,0);modes.addView(repeat,rm);body.addView(modes);
        shuffle.setOnClickListener(v->{if(service!=null)service.toggleShuffle();});repeat.setOnClickListener(v->{if(service!=null)service.cycleRepeat();});
        cinema=Ui.button(this,"⛶   Full-screen video",false);cinema.setVisibility(View.GONE);Ui.gap(body,10);body.addView(cinema,Ui.lp(this,-1,48));cinema.setOnClickListener(v->startActivity(new Intent(this,CinemaActivity.class)));
        Ui.gap(body,18);LinearLayout add=Ui.row(this);Button files=Ui.button(this,"＋  Add files",true);Button folder=Ui.button(this,"Add folder",false);add.addView(files,new LinearLayout.LayoutParams(0,Ui.dp(this,52),1));LinearLayout.LayoutParams f=new LinearLayout.LayoutParams(0,Ui.dp(this,52),1);f.setMargins(Ui.dp(this,10),0,0,0);add.addView(folder,f);body.addView(add);
        files.setOnClickListener(v->pickFiles());folder.setOnClickListener(v->pickFolder());Ui.gap(body,15);
        queueButton=Ui.button(this,"Queue  ·  0 files",false);body.addView(queueButton,Ui.lp(this,-1,50));queueButton.setOnClickListener(v->showQueue());Ui.gap(body,8);
        queueSummary=Ui.text(this,"Your chosen files stay on your device.",12,Ui.MUTED,false);queueSummary.setGravity(Gravity.CENTER);body.addView(queueSummary);
    }
    private ImageView asset(String name){try{BitmapFactory.Options o=new BitmapFactory.Options();o.inPreferredConfig=Bitmap.Config.ARGB_8888;Bitmap b=BitmapFactory.decodeStream(getAssets().open(name),null,o);if(b==null)return null;ImageView v=new ImageView(this);v.setImageBitmap(b);v.setScaleType(ImageView.ScaleType.FIT_CENTER);return v;}catch(Exception e){return null;}}
    @Override public void onStateChanged(){refresh();}
    @Override public void onProblem(String message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
    private void refresh(){if(service==null)return;PlaybackService.Item item=service.current();title.setText(item==null?"Nothing playing yet":item.title);subtitle.setText(item==null?"Choose music or a video to begin":item.video?"VIDEO  ·  YOUR QUEUE":"AUDIO  ·  YOUR QUEUE");
        boolean isVideo=item!=null&&item.video;artwork.setVisibility(isVideo?View.GONE:View.VISIBLE);video.setVisibility(isVideo?View.VISIBLE:View.GONE);cinema.setVisibility(isVideo?View.VISIBLE:View.GONE);
        if(resumed&&isVideo)service.attachVideo(video);else service.detachVideo(video);
        play.setText(service.isPlaying()?"Ⅱ":"▶");play.setContentDescription(service.isPlaying()?"Pause":"Play");
        repeat.setText(service.repeat()==0?"Repeat off":service.repeat()==1?"Repeat all":"Repeat one");shuffle.setText(service.shuffle()?"Shuffle on":"Shuffle off");
        queueButton.setText("Queue  ·  "+service.items().size()+" file"+(service.items().size()==1?"":"s"));
        queueSummary.setText(service.items().isEmpty()?"Your chosen files stay on your device.":"Tap Queue to choose or remove a file.");
        if(orbit!=null){if(service.isPlaying()&&!isVideo&&resumed){if(!orbit.isStarted())orbit.start();else if(orbit.isPaused())orbit.resume();}else if(orbit.isStarted()&&!orbit.isPaused())orbit.pause();}
        if(isVideo&&service.isPlaying())getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        refreshProgress();refreshQueueDialog();
    }
    private void refreshProgress(){if(service==null)return;long t=service.time(),d=service.length();if(!dragging){seek.setProgress(d>0?(int)Math.min(10000,t*10000/d):0);clock.setText(format(t)+"  /  "+format(d));}seek.setEnabled(d>0);}
    private static String format(long ms){long s=Math.max(0,ms)/1000;long h=s/3600,m=(s%3600)/60;return h>0?String.format(Locale.US,"%d:%02d:%02d",h,m,s%60):String.format(Locale.US,"%d:%02d",m,s%60);}
    private void pickFiles(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);try{startActivityForResult(i,PICK_FILES);}catch(Exception e){onProblem("No file picker is available on this phone.");}}
    private void pickFolder(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);try{startActivityForResult(i,PICK_FOLDER);}catch(Exception e){onProblem("No folder picker is available on this phone.");}}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null)return;
        if(request==PICK_FILES){ArrayList<PlaybackService.Item> items=new ArrayList<>();if(data.getClipData()!=null){for(int i=0;i<data.getClipData().getItemCount();i++)addPicked(items,data.getClipData().getItemAt(i).getUri(),data.getFlags());}else if(data.getData()!=null)addPicked(items,data.getData(),data.getFlags());enqueue(items,true);}
        else if(request==PICK_FOLDER&&data.getData()!=null){Uri tree=data.getData();persistPermission(tree,data.getFlags());scanFolder(tree);}}
    private void persistPermission(Uri uri,int flags){int read=flags&Intent.FLAG_GRANT_READ_URI_PERMISSION;if(read==0)return;try{getContentResolver().takePersistableUriPermission(uri,read);}catch(Exception ignored){}}
    private void enqueue(List<PlaybackService.Item> items,boolean playFirst){if(items.isEmpty())return;if(service!=null)service.addItems(items,playFirst);else{pendingItems.addAll(items);pendingPlay|=playFirst;}}
    private void addPicked(List<PlaybackService.Item> out,Uri uri,int flags){if(uri==null)return;persistPermission(uri,flags);out.add(new PlaybackService.Item(uri,displayName(uri),safeType(uri)));}
    private void handleExternal(Intent intent){Uri uri=intent.getData();if(uri==null||service==null)return;persistPermission(uri,intent.getFlags());service.addItems(Collections.singletonList(new PlaybackService.Item(uri,displayName(uri),intent.getType())),true);}
    private String safeType(Uri uri){try{return getContentResolver().getType(uri);}catch(Exception e){return "";}}
    private String displayName(Uri uri){try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())return c.getString(0);}catch(Exception ignored){}String p=uri.getLastPathSegment();return p==null?"Untitled":p;}
    private void scanFolder(Uri tree){Toast.makeText(this,"Looking for music and videos…",Toast.LENGTH_SHORT).show();scan.execute(()->{
        ArrayList<PlaybackService.Item> found=new ArrayList<>();ArrayDeque<String> dirs=new ArrayDeque<>();HashSet<String> visited=new HashSet<>();boolean cut=false;String issue=null;
        try{dirs.add(DocumentsContract.getTreeDocumentId(tree));while(!dirs.isEmpty()&&found.size()<5000){String id=dirs.removeFirst();if(!visited.add(id))continue;Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,id);
            try(Cursor c=getContentResolver().query(children,new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE},null,null,null)){
                if(c==null)continue;while(c.moveToNext()){String child=c.getString(0),name=c.getString(1),mime=c.getString(2);if(DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)){if(visited.size()+dirs.size()<1000)dirs.addLast(child);}else if(MediaKind.candidate(name,mime)){found.add(new PlaybackService.Item(DocumentsContract.buildDocumentUriUsingTree(tree,child),name,mime));if(found.size()>=5000){cut=true;break;}}}}
        }}catch(Exception e){issue="Couldn't read that folder. Try choosing individual files.";}
        final String error=issue;final boolean limited=cut;handler.post(()->{if(isFinishing())return;if(error!=null){onProblem(error);return;}found.sort((a,b)->a.title.compareToIgnoreCase(b.title));enqueue(found,!found.isEmpty());Toast.makeText(this,found.isEmpty()?"No music or videos found.":limited?"Added the first 5,000 files.":"Added "+found.size()+" files.",Toast.LENGTH_LONG).show();});
    });}
    private void showQueue(){if(service==null)return;queueList=new ListView(this);queueAdapter=new ArrayAdapter<>(this,android.R.layout.simple_list_item_1,new ArrayList<>());queueList.setAdapter(queueAdapter);lastQueueKey="";refreshQueueDialog();
        queueList.setOnItemClickListener((p,v,n,id)->{if(service!=null)service.playIndex(n,true);if(queueDialog!=null)queueDialog.dismiss();});
        queueList.setOnItemLongClickListener((p,v,n,id)->{new AlertDialog.Builder(this).setMessage("Remove this file from the queue? Your file stays on your device.").setPositiveButton("Remove",(d,w)->{if(service!=null)service.removeAt(n);}).setNegativeButton("Cancel",null).show();return true;});
        queueDialog=new AlertDialog.Builder(this).setTitle("Your queue  ·  long-press to remove").setView(queueList).setPositiveButton("Done",null).setNeutralButton("Clear queue",(d,w)->new AlertDialog.Builder(this).setMessage("Clear this queue? Your files will not be deleted.").setPositiveButton("Clear",(a,b)->{if(service!=null)service.clearQueue();}).setNegativeButton("Cancel",null).show()).create();queueDialog.show();}
    private void refreshQueueDialog(){if(queueAdapter==null||service==null)return;StringBuilder key=new StringBuilder().append(service.index()).append(':').append(service.items().size());for(PlaybackService.Item i:service.items())key.append('|').append(i.uri);String k=key.toString();if(k.equals(lastQueueKey))return;lastQueueKey=k;queueAdapter.clear();for(int n=0;n<service.items().size();n++){PlaybackService.Item i=service.items().get(n);queueAdapter.add((n==service.index()?"▶  ":"     ")+i.title);}queueAdapter.notifyDataSetChanged();}
    private void about(){new AlertDialog.Builder(this).setTitle("Nova Afterglow").setMessage("A personal player for music and video. Files are chosen by you and stay on your device.\n\nPlayback engine: LibVLC by VideoLAN (LGPL).\nArtwork: David's Neptune image; paper details by HumblePixel; light textures by Kronbits (CC0).\n\nSome protected, damaged or unusual files may need a different decoder.").setPositiveButton("Lovely",null).show();}
}
