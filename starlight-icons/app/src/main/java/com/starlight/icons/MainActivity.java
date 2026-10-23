package com.starlight.icons;
import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;

public class MainActivity extends Activity {
  static final int BG=0xff141020, PANEL=0xff29203c, INK=0xfff9edff, PURPLE=0xffbf99fa;
  static final String[] SHAPES={"Star","Moon","Crystal","Moth","Heart","Flower","Cat","Flame","Planet","Sparkle"};
  static final int[] COLORS={0xffb99aff,0xffff96c8,0xff75d8eb,0xffffcf84,0xff90e6c1,0xffff9b8c};
  final ArrayList<ResolveInfo> apps=new ArrayList<>();
  ResolveInfo selected; Bitmap icon; String selectedName;
  LinearLayout root,body; DrawPad painter;
  int lastRequest=0;
  int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
  TextView label(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(INK);return t;}
  LinearLayout vertical(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
  void add(LinearLayout p,View v){p.addView(v,new LinearLayout.LayoutParams(-1,-2));}
  Button button(String text,Runnable r){Button b=new Button(this);b.setAllCaps(false);b.setText(text);b.setTextColor(INK);
    b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(PANEL));b.setOnClickListener(v->r.run());return b;}
  void toast(String x){Toast.makeText(this,x,Toast.LENGTH_LONG).show();}
  @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);loadApps();home();}
  void screen(String name,String desc){root=vertical();root.setBackgroundColor(BG);setContentView(root);
    TextView title=label("✦  "+name,23);title.setTextColor(PURPLE);title.setTypeface(null,1);title.setPadding(dp(16),dp(18),dp(16),dp(8));add(root,title);
    TextView sub=label(desc,12);sub.setPadding(dp(16),0,dp(16),dp(12));add(root,sub);
    ScrollView scroll=new ScrollView(this);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
    body=vertical();body.setPadding(dp(12),dp(6),dp(12),dp(26));scroll.addView(body);}
  void loadApps(){apps.clear();Intent launch=new Intent(Intent.ACTION_MAIN);launch.addCategory(Intent.CATEGORY_LAUNCHER);
    PackageManager pm=getPackageManager();for(ResolveInfo r:pm.queryIntentActivities(launch,0)){
      if(r.activityInfo!=null&&!r.activityInfo.packageName.equals(getPackageName()))apps.add(r);}
    Collections.sort(apps,(a,b)->a.loadLabel(pm).toString().compareToIgnoreCase(b.loadLabel(pm).toString()));}
  void home(){screen("STARLIGHT ICONS","Long-press an app below to give it a magical home-screen shortcut.");
    add(body,label("60 designs • Create your own • No ads • No extra apps",14));
    add(body,label("Note: Android creates a new Home shortcut. Your original app icon stays unchanged.",12));
    add(body,button("↻ Refresh installed apps",()->{loadApps();home();}));
    GridLayout g=new GridLayout(this);g.setColumnCount(4);add(body,g);
    if(apps.isEmpty())add(body,label("No apps found. Tap Refresh.",16));
    int cell=(getResources().getDisplayMetrics().widthPixels-dp(40))/4;
    for(ResolveInfo r:apps){String name=r.loadLabel(getPackageManager()).toString();
      LinearLayout item=vertical();item.setPadding(dp(4),dp(9),dp(4),0);item.setBackgroundColor(PANEL);
      ImageView img=new ImageView(this);img.setScaleType(ImageView.ScaleType.FIT_CENTER);
      try{img.setImageDrawable(r.loadIcon(getPackageManager()));}catch(Exception ex){img.setImageResource(android.R.drawable.sym_def_app_icon);}
      LinearLayout.LayoutParams pic=new LinearLayout.LayoutParams(dp(46),dp(48));pic.gravity=Gravity.CENTER;item.addView(img,pic);
      TextView txt=label(name,10);txt.setGravity(Gravity.CENTER);txt.setMaxLines(2);item.addView(txt,new LinearLayout.LayoutParams(-1,dp(41)));
      GridLayout.LayoutParams p=new GridLayout.LayoutParams();p.width=cell;p.height=dp(110);p.setMargins(dp(2),dp(2),dp(2),dp(2));g.addView(item,p);
      View.OnClickListener click=v->{selected=r;selectedName=name;menu();};item.setOnClickListener(click);
      item.setOnLongClickListener(v->{click.onClick(v);return true;});
    }}
  void menu(){new AlertDialog.Builder(this).setTitle("Customize "+selectedName).setItems(new String[]{"✦ Change icon — 60 styles","✎ Create your own","▶ Open original app"},(d,n)->{
     if(n==0)gallery();if(n==1)editor();if(n==2){try{startActivity(launchIntent());}catch(Exception e){toast("Could not launch app");}}}).show();}
  Intent launchIntent(){Intent i=new Intent(Intent.ACTION_MAIN);i.addCategory(Intent.CATEGORY_LAUNCHER);
    i.setComponent(new ComponentName(selected.activityInfo.packageName,selected.activityInfo.name));
    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);return i;}
  void gallery(){screen("CHOOSE AN ICON","Select a design for "+selectedName+". Android will offer to add it to Home.");
    add(body,button("← Back to installed apps",this::home));
    GridLayout g=new GridLayout(this);g.setColumnCount(4);add(body,g);
    int width=(getResources().getDisplayMetrics().widthPixels-dp(40))/4;
    for(int n=0;n<60;n++){int which=n;Bitmap b=makeIcon(n);
      LinearLayout tile=vertical();tile.setPadding(dp(3),dp(6),dp(3),dp(4));tile.setBackgroundColor(PANEL);
      ImageView image=new ImageView(this);image.setImageBitmap(b);
      LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(64),dp(64));p.gravity=Gravity.CENTER;tile.addView(image,p);
      TextView cap=label(SHAPES[n%10]+" "+(n/10+1),10);cap.setGravity(Gravity.CENTER);add(tile,cap);
      GridLayout.LayoutParams gp=new GridLayout.LayoutParams();gp.width=width;gp.height=dp(105);gp.setMargins(dp(2),dp(2),dp(2),dp(2));g.addView(tile,gp);
      tile.setOnClickListener(v->{icon=makeIcon(which);confirm();});
    }}
  void confirm(){new AlertDialog.Builder(this).setTitle("Add custom icon for "+selectedName+"?")
    .setMessage("This requests a new shortcut on your Samsung home screen; it cannot replace the original icon inside the app drawer.")
    .setPositiveButton("Add to Home",(d,w)->pin()).setNeutralButton("Save PNG",(d,w)->export()).setNegativeButton("Back",null).show();}
  String id(){return "starlight_"+Integer.toHexString((selected.activityInfo.packageName+"/"+selected.activityInfo.name).hashCode());}
  void pin(){try{ShortcutManager m=getSystemService(ShortcutManager.class);
    if(m==null||!m.isRequestPinShortcutSupported()){toast("This launcher does not accept pinned shortcuts.");return;}
    String shortName=selectedName.length()>30?selectedName.substring(0,30):selectedName;
    ShortcutInfo s=new ShortcutInfo.Builder(this,id()).setShortLabel(shortName)
      .setIcon(Icon.createWithBitmap(Bitmap.createScaledBitmap(icon,192,192,true)))
      .setIntent(launchIntent()).build();
    boolean ok=m.requestPinShortcut(s,null);
    toast(ok?"Confirm Add on your Home screen if prompted.":"Launcher declined the shortcut request.");
  }catch(Exception ex){toast("Could not add shortcut: "+ex.getMessage());}}
  void export(){if(icon==null){toast("Pick or draw an icon first.");return;}
    Intent it=new Intent(Intent.ACTION_CREATE_DOCUMENT);it.addCategory(Intent.CATEGORY_OPENABLE);
    it.setType("image/png");it.putExtra(Intent.EXTRA_TITLE,"starlight-icon.png");lastRequest=2;startActivityForResult(it,2);}
  void editor(){screen("CREATE YOUR OWN","Draw, erase, undo, import a photo, then make a home-screen shortcut.");
    painter=new DrawPad(this);add(body,painter);painter.getLayoutParams().height=dp(305);
    add(body,label("Brush colors",14));
    LinearLayout swatches=new LinearLayout(this);add(body,swatches);
    int[] colors={0xffffffff,0xfff4a7dc,0xffb99aff,0xff7dd9e9,0xffffd178,0xff80e8b0,0xffff8b87,0xff22202f};
    for(int c:colors){View sw=new View(this);sw.setBackgroundColor(c);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(38),1);p.setMargins(dp(2),dp(4),dp(2),dp(5));swatches.addView(sw,p);
      sw.setOnClickListener(v->painter.setColor(c));}
    LinearLayout tools=new LinearLayout(this);add(body,tools);
    for(String action:new String[]{"Draw","Erase","Undo","Clear"}){Button b=button(action,()->{
      if(action.equals("Draw"))painter.erase=false;if(action.equals("Erase"))painter.erase=true;
      if(action.equals("Undo"))painter.undo();if(action.equals("Clear"))painter.clear();
    });b.setTextSize(11);tools.addView(b,new LinearLayout.LayoutParams(0,dp(49),1));}
    add(body,button("▣ Import photo",()->{Intent it=new Intent(Intent.ACTION_OPEN_DOCUMENT);it.addCategory(Intent.CATEGORY_OPENABLE);it.setType("image/*");lastRequest=1;startActivityForResult(it,1);}));
    add(body,button("✦ Add my icon to Home",()->{icon=painter.bitmap.copy(Bitmap.Config.ARGB_8888,false);confirm();}));
    add(body,button("↓ Export my icon as PNG",()->{icon=painter.bitmap.copy(Bitmap.Config.ARGB_8888,false);export();}));
    add(body,button("← Back to apps",this::home));}
  @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);
    if(result!=RESULT_OK||data==null||data.getData()==null)return;
    try{Uri uri=data.getData();
      if(req==1 && painter!=null){try(InputStream in=getContentResolver().openInputStream(uri)){
        Bitmap b=BitmapFactory.decodeStream(in);if(b==null)throw new IOException("Image unreadable");painter.importArt(b);}}
      if(req==2 && icon!=null){try(OutputStream out=getContentResolver().openOutputStream(uri)){
        if(out==null)throw new IOException("Cannot open file");icon.compress(Bitmap.CompressFormat.PNG,100,out);toast("PNG saved.");}}
    }catch(Exception ex){toast("Image error: "+ex.getMessage());}}
  Bitmap makeIcon(int n){Bitmap bitmap=Bitmap.createBitmap(256,256,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(bitmap);
    Paint p=new Paint(3);int tone=COLORS[n/10],shape=n%10;
    p.setShader(new LinearGradient(0,0,256,256,0xff282038,Color.rgb(Color.red(tone)/3,Color.green(tone)/3,Color.blue(tone)/3),Shader.TileMode.CLAMP));
    c.drawRoundRect(4,4,252,252,55,55,p);p.setShader(null);p.setColor(0x66ffffff);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(4);c.drawRoundRect(12,12,244,244,49,49,p);p.setStyle(Paint.Style.FILL);p.setColor(tone);
    switch(shape){
      case 0:star(c,p,128,128,85,38,5);break;
      case 1:c.drawCircle(120,127,73,p);p.setColor(0xff282038);c.drawCircle(156,97,70,p);break;
      case 2:{Path d=new Path();d.moveTo(128,35);d.lineTo(205,120);d.lineTo(128,218);d.lineTo(51,120);d.close();c.drawPath(d,p);break;}
      case 3:{c.drawOval(28,70,125,175,p);c.drawOval(131,70,228,175,p);c.drawOval(75,144,129,219,p);c.drawOval(127,144,181,219,p);p.setColor(0xff2b1b38);c.drawOval(117,93,139,193,p);break;}
      case 4:{Path h=new Path();h.moveTo(128,211);h.cubicTo(74,165,35,131,35,92);h.cubicTo(35,42,104,37,128,79);h.cubicTo(157,37,221,42,221,92);h.cubicTo(221,131,185,165,128,211);c.drawPath(h,p);break;}
      case 5:for(int t=0;t<8;t++){double a=t*Math.PI/4;c.drawCircle(128+(float)Math.cos(a)*49,128+(float)Math.sin(a)*49,35,p);}p.setColor(0xffffe2a2);c.drawCircle(128,128,37,p);break;
      case 6:{Path cat=new Path();cat.moveTo(60,105);cat.lineTo(58,53);cat.lineTo(104,77);cat.quadTo(128,66,151,77);cat.lineTo(198,53);cat.lineTo(196,105);cat.cubicTo(228,205,157,219,128,219);cat.cubicTo(98,219,27,205,60,105);cat.close();c.drawPath(cat,p);break;}
      case 7:{Path flame=new Path();flame.moveTo(128,219);flame.cubicTo(52,205,52,136,102,85);flame.cubicTo(116,129,130,95,134,34);flame.cubicTo(215,116,219,194,128,219);c.drawPath(flame,p);break;}
      case 8:c.drawCircle(130,127,63,p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(15);c.save();c.rotate(-23,128,128);c.drawOval(30,105,226,151,p);c.restore();p.setStyle(Paint.Style.FILL);break;
      default:star(c,p,135,125,80,19,4);star(c,p,58,69,24,8,4);star(c,p,194,188,26,9,4);
    }
    p.setColor(0x99ffffff);star(c,p,216,39,11,3,4);return bitmap;}
  void star(Canvas c,Paint p,float x,float y,float ro,float ri,int points){Path path=new Path();
    for(int k=0;k<points*2;k++){double a=-Math.PI/2+k*Math.PI/points;float radius=k%2==0?ro:ri;
      float px=x+(float)Math.cos(a)*radius,py=y+(float)Math.sin(a)*radius;
      if(k==0)path.moveTo(px,py);else path.lineTo(px,py);}path.close();c.drawPath(path,p);}
  class DrawPad extends View {
    Bitmap bitmap=Bitmap.createBitmap(512,512,Bitmap.Config.ARGB_8888);Canvas draw=new Canvas(bitmap);
    Paint stroke=new Paint(3);ArrayDeque<Bitmap> stack=new ArrayDeque<>();int color=0xffffffff;boolean erase=false;float x,y;
    DrawPad(Context context){super(context);stroke.setColor(color);stroke.setStrokeWidth(23);stroke.setStrokeCap(Paint.Cap.ROUND);stroke.setStrokeJoin(Paint.Join.ROUND);}
    void remember(){if(stack.size()==12)stack.removeFirst();stack.addLast(bitmap.copy(Bitmap.Config.ARGB_8888,true));}
    void undo(){if(!stack.isEmpty()){bitmap=stack.removeLast();draw=new Canvas(bitmap);invalidate();}}
    void clear(){remember();draw.drawColor(Color.TRANSPARENT,PorterDuff.Mode.CLEAR);invalidate();}
    void setColor(int c){color=c;erase=false;}
    void importArt(Bitmap src){remember();draw.drawColor(Color.TRANSPARENT,PorterDuff.Mode.CLEAR);
      int s=Math.min(src.getWidth(),src.getHeight());int left=(src.getWidth()-s)/2,top=(src.getHeight()-s)/2;
      draw.drawBitmap(src,new Rect(left,top,left+s,top+s),new Rect(0,0,512,512),new Paint(3));invalidate();}
    @Override protected void onDraw(Canvas c){super.onDraw(c);int side=Math.min(getWidth(),getHeight());float left=(getWidth()-side)/2f,top=(getHeight()-side)/2f;
      Paint p=new Paint(3);p.setColor(PANEL);c.drawRoundRect(left,top,left+side,top+side,20,20,p);
      c.drawBitmap(bitmap,null,new RectF(left,top,left+side,top+side),p);}
    @Override public boolean onTouchEvent(MotionEvent e){float side=Math.min(getWidth(),getHeight());float left=(getWidth()-side)/2f,top=(getHeight()-side)/2f;
      float px=Math.max(0,Math.min(511,(e.getX()-left)*512/side)),py=Math.max(0,Math.min(511,(e.getY()-top)*512/side));
      if(e.getActionMasked()==MotionEvent.ACTION_DOWN){remember();x=px;y=py;getParent().requestDisallowInterceptTouchEvent(true);return true;}
      if(e.getActionMasked()==MotionEvent.ACTION_MOVE||e.getActionMasked()==MotionEvent.ACTION_UP){
        stroke.setColor(color);stroke.setXfermode(erase?new PorterDuffXfermode(PorterDuff.Mode.CLEAR):null);draw.drawLine(x,y,px,py,stroke);
        stroke.setXfermode(null);x=px;y=py;invalidate();
        if(e.getActionMasked()==MotionEvent.ACTION_UP)getParent().requestDisallowInterceptTouchEvent(false);return true;}
      return true;}
  }
}