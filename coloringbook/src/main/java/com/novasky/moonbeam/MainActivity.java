package com.novasky.moonbeam;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int INK = Color.rgb(48, 38, 62);
    private static final int CREAM = Color.rgb(250, 245, 236);
    private static final int PURPLE = Color.rgb(100, 65, 144);
    private static final String[] PAGES = {"Moon Moth", "Dragon Egg", "Haunted Castle", "Magic Garden", "Cosmic Cat", "Dragon’s Reliquary", "Gargoyle Cathedral", "The Bone Crown", "Raven’s Omen", "The Thirteenth Door",
"Wolf at the Chapel","The Spider Queen","Pumpkin Monarch","Reaper's Waltz","The Fanged Oracle","Batwing Masquerade",
"Tower of Thorns","The Lost Graveyard","Midnight Watchtower","Witch's Crossing","Crypt of the Rose","The Black Gate",
"Potion of No Return","The Forbidden Grimoire","Crystal Seer","The Cursed Compass","Serpent's Chalice","The Moonblade",
"Angel of Ashes","The Bleeding Valentine","Fairy in the Briars","The Siren's Crown","The Raven Bride","The Haunted Heart",
"Octopus Observatory","The Star-Eater","Dragon in the Deep","A Ghost's Tea Party","The Carnival of Bones","The Last Moon Garden",
"Colorbook Dragon","Dragon of the Hollow","Hand of the Oracle","The Watchful Owl","The Mystic Lotus","Night Butterfly Tattoo","The Locked Heart","Moon Poppies","The Octopus Carnival","The Moonstruck Horse","The Unimpressed Donkey","Deepwater Dream"};
private static final String[] GROUPS={"First Sketches","Gothic Beginnings","Night Creatures","Haunted Places","Arcane Relics","Strange & Lovely","Moonlit Oddities","Illustrated Gallery"};
private static final int[] GROUP_START={0,5,10,16,22,28,34,40,52};
    private static final int[] COLORS = {0xffe84965,0xffff974a,0xffffce4c,0xff80c76d,0xff3bc5b4,0xff47a9e6,0xff6a76dd,0xffa773d1,0xffe77eb8,0xff915c38,0xff333347,0xffffffff};
    private final Bitmap[] kenney = new Bitmap[3];
    private final Bitmap[] kronbits = new Bitmap[2];
    private final ExecutorService saves = Executors.newSingleThreadExecutor();
    private DrawingView paper;
    private TextView pageTitle, sizeLabel, tip;
    private Button undoButton, redoButton;
    private LinearLayout toolStrip;
    private int pageIndex = 0;
    private Tool activeTool = Tool.BRUSH;
    private int chosenColor = COLORS[0];
    private int chosenSize = 28;
    private int stampIndex = 0;
    private int sparkleIndex = 0;
    private boolean mirrorMagic=false;
    private enum Tool { BRUSH, ERASER, FILL, STAMP, SPARKLE, RAINBOW, GHOSTLIGHT }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(CREAM);
        getWindow().setNavigationBarColor(CREAM);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        String[] a={"kenney_blue_star.png","kenney_green_star.png","kenney_silver_star.png"};
        String[] b={"kronbits_star.png","kronbits_filigree.png"};
        for(int i=0;i<a.length;i++) kenney[i]=readAsset(a[i]);
        for(int i=0;i<b.length;i++) kronbits[i]=readAsset(b[i]);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(CREAM);
        root.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets n=insets.getInsets(WindowInsets.Type.systemBars());root.setPadding(dp(10),n.top+dp(5),dp(10),n.bottom+dp(6));return insets;});
        setContentView(root);
        LinearLayout header=row();header.setGravity(Gravity.CENTER_VERTICAL);root.addView(header,new LinearLayout.LayoutParams(-1,dp(49)));
        TextView brand=text("✦ Moonbeam",19,true);brand.setTextColor(PURPLE);header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        header.addView(button("Magic",v->magicMenu()),new LinearLayout.LayoutParams(dp(57),dp(41)));
        header.addView(button("Pages",v->choosePage()),new LinearLayout.LayoutParams(dp(74),dp(41)));
        header.addView(button("Save JPG",v->exportJpeg()),new LinearLayout.LayoutParams(dp(101),dp(41)));
        LinearLayout second=row();second.setGravity(Gravity.CENTER_VERTICAL);root.addView(second,new LinearLayout.LayoutParams(-1,dp(44)));
        pageTitle=text(PAGES[0],16,true);second.addView(pageTitle,new LinearLayout.LayoutParams(0,-2,1));
        undoButton=button("↶ Undo",v->{paper.undo();refreshTip();});
        redoButton=button("Redo ↷",v->{paper.redo();refreshTip();});
        second.addView(undoButton,new LinearLayout.LayoutParams(dp(83),dp(37)));
        second.addView(redoButton,new LinearLayout.LayoutParams(dp(82),dp(37)));
        second.addView(button("Fit",v->{paper.fit();Toast.makeText(this,"Full page",Toast.LENGTH_SHORT).show();}),new LinearLayout.LayoutParams(dp(48),dp(37)));
        paper=new DrawingView();root.addView(paper,new LinearLayout.LayoutParams(-1,0,1));
        HorizontalScrollView toolScroll=new HorizontalScrollView(this);toolScroll.setHorizontalScrollBarEnabled(false);root.addView(toolScroll,new LinearLayout.LayoutParams(-1,dp(58)));
        toolStrip=row();toolStrip.setGravity(Gravity.CENTER_VERTICAL);toolScroll.addView(toolStrip);rebuildTools();
        HorizontalScrollView paletteScroll=new HorizontalScrollView(this);paletteScroll.setHorizontalScrollBarEnabled(false);root.addView(paletteScroll,new LinearLayout.LayoutParams(-1,dp(48)));
        LinearLayout palette=row();palette.setGravity(Gravity.CENTER_VERTICAL);paletteScroll.addView(palette);
        for(int color:COLORS){View swatch=new View(this);GradientDrawable bg=new GradientDrawable();bg.setShape(GradientDrawable.OVAL);bg.setColor(color);bg.setStroke(dp(2),color==chosenColor?PURPLE:0xffd5c9c1);swatch.setBackground(bg);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(32),dp(32));lp.setMargins(dp(3),0,dp(3),0);palette.addView(swatch,lp);swatch.setOnClickListener(v->{chosenColor=color;paper.setInk(color);for(int j=0;j<palette.getChildCount();j++){View child=palette.getChildAt(j);GradientDrawable d=(GradientDrawable)child.getBackground();d.setStroke(dp(2),child==v?PURPLE:0xffd5c9c1);}refreshTip();});}
        LinearLayout sizes=row();sizes.setGravity(Gravity.CENTER_VERTICAL);root.addView(sizes,new LinearLayout.LayoutParams(-1,dp(44)));
        sizeLabel=text("Size 28",13,true);sizes.addView(sizeLabel,new LinearLayout.LayoutParams(dp(73),-2));
        SeekBar slider=new SeekBar(this);slider.setMax(116);slider.setProgress(chosenSize-4);sizes.addView(slider,new LinearLayout.LayoutParams(0,dp(42),1));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean u){chosenSize=p+4;paper.setSize(chosenSize);sizeLabel.setText("Size "+chosenSize);}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});
        tip=text("Draw on the page • choose a tool below",12,false);tip.setTextColor(0xff756b7c);tip.setGravity(Gravity.CENTER);root.addView(tip,new LinearLayout.LayoutParams(-1,dp(28)));
        paper.setInk(chosenColor);paper.setSize(chosenSize);paper.setPage(0);loadPage();refreshTip();
    }
    private Bitmap readAsset(String name){try(InputStream in=getAssets().open(name)){return BitmapFactory.decodeStream(in);}catch(Exception e){return null;}}
    private int dp(float n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);return l;}
    private TextView text(String t,int sp,boolean bold){TextView v=new TextView(this);v.setText(t);v.setTextSize(sp);v.setTextColor(INK);v.setGravity(Gravity.CENTER_VERTICAL);if(bold)v.setTypeface(null,1);return v;}
    private Button button(String s,View.OnClickListener listener){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(12);b.setPadding(dp(3),0,dp(3),0);b.setOnClickListener(listener);return b;}
    private void rebuildTools(){toolStrip.removeAllViews();addTool("✎ Brush",Tool.BRUSH);addTool("⌫ Erase",Tool.ERASER);addTool("▧ Fill",Tool.FILL);addTool("★ Stamps",Tool.STAMP);addTool("✦ Sparkle",Tool.SPARKLE);addTool("🌈 Rainbow",Tool.RAINBOW);addTool("☾ Ghostlight",Tool.GHOSTLIGHT);
        Button mirror=button(mirrorMagic?"⇋ Mirror ON":"⇋ Mirror OFF",v->{mirrorMagic=!mirrorMagic;paper.setMirror(mirrorMagic);rebuildTools();refreshTip();Toast.makeText(this,mirrorMagic?"Mirror Magic: both sides paint together":"Mirror Magic off",Toast.LENGTH_SHORT).show();});
        mirror.setTextColor(mirrorMagic?Color.WHITE:INK);mirror.setBackgroundTintList(ColorStateList.valueOf(mirrorMagic?0xff3c8d8a:0xffe6dbe8));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(114),dp(46));lp.setMargins(dp(3),0,dp(3),0);toolStrip.addView(mirror,lp);
        Button plus=button("Zoom +",v->{paper.zoomBy(1.45f);});Button minus=button("Zoom −",v->{paper.zoomBy(1f/1.45f);});
        for(Button b:new Button[]{plus,minus}){LinearLayout.LayoutParams zp=new LinearLayout.LayoutParams(dp(83),dp(46));zp.setMargins(dp(3),0,dp(3),0);toolStrip.addView(b,zp);}
    }
    private void addTool(String name,Tool tool){Button b=button(name,v->{activeTool=tool;paper.setTool(tool);rebuildTools();if(tool==Tool.STAMP)selectStamp();else if(tool==Tool.SPARKLE)selectSparkle();refreshTip();});b.setTextColor(activeTool==tool?Color.WHITE:INK);b.setBackgroundTintList(ColorStateList.valueOf(activeTool==tool?PURPLE:0xffe6dbe8));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(94),dp(46));lp.setMargins(dp(3),0,dp(3),0);toolStrip.addView(b,lp);}
    private void choosePage(){String[] labels=new String[GROUPS.length];for(int i=0;i<GROUPS.length;i++)labels[i]=GROUPS[i]+"  ·  "+(GROUP_START[i+1]-GROUP_START[i])+" pages";
        new AlertDialog.Builder(this).setTitle("Moonbeam's art library · "+PAGES.length+" pages").setItems(labels,(d,i)->showPageGroup(i)).setNeutralButton("Art credits",(d,w)->showArtCredits()).setNegativeButton("Close",null).show();}
    private void showPageGroup(int group){
        ScrollView scroll=new ScrollView(this);LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);list.setPadding(dp(8),dp(6),dp(8),dp(8));scroll.addView(list);
        final AlertDialog[] dialog={null};
        for(int i=GROUP_START[group];i<GROUP_START[group+1];i++){final int page=i;LinearLayout item=row();item.setGravity(Gravity.CENTER_VERTICAL);item.setPadding(dp(5),dp(5),dp(5),dp(5));
            if(i>=5){ImageView preview=new ImageView(this);preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
                try(InputStream in=getAssets().open("pages/page_"+i+".png")){BitmapFactory.Options opts=new BitmapFactory.Options();opts.inSampleSize=4;Bitmap bmp=BitmapFactory.decodeStream(in,null,opts);preview.setImageBitmap(bmp);}catch(Exception ignored){}
                item.addView(preview,new LinearLayout.LayoutParams(dp(82),dp(96)));
            }else{TextView classic=text("✦",32,true);classic.setGravity(Gravity.CENTER);classic.setTextColor(PURPLE);item.addView(classic,new LinearLayout.LayoutParams(dp(82),dp(96)));}
            TextView label=text(PAGES[i]+(i==pageIndex?"  ✓":""),15,true);label.setPadding(dp(12),0,0,0);item.addView(label,new LinearLayout.LayoutParams(0,-1,1));
            item.setBackgroundColor(i==pageIndex?0xffeee3f3:(i%2==0?0xfffaf5ec:0xffffffff));
            item.setOnClickListener(v->{if(page!=pageIndex){savePage();pageIndex=page;paper.setPage(page);loadPage();pageTitle.setText(PAGES[page]);refreshTip();}dialog[0].dismiss();});
            list.addView(item,new LinearLayout.LayoutParams(-1,dp(104)));
        }
        dialog[0]=new AlertDialog.Builder(this).setTitle(GROUPS[group]).setView(scroll).setNegativeButton("Back",(d,w)->choosePage()).create();dialog[0].show();
    }
    private void magicMenu(){String[] options={"🌈 Rainbow Trail · shifting-color paint","☾ Ghostlight · spectral glowing ink","⇋ Mirror Magic · "+(mirrorMagic?"turn OFF":"turn ON"),"Zoom guide · pinch, two-finger pan, Fit"};
        new AlertDialog.Builder(this).setTitle("Moonbeam's secret paint box").setItems(options,(d,which)->{
            if(which==0||which==1){activeTool=which==0?Tool.RAINBOW:Tool.GHOSTLIGHT;paper.setTool(activeTool);rebuildTools();refreshTip();Toast.makeText(this,which==0?"Rainbow Trail ready":"Ghostlight ready",Toast.LENGTH_SHORT).show();}
            else if(which==2){mirrorMagic=!mirrorMagic;paper.setMirror(mirrorMagic);rebuildTools();refreshTip();}
            else new AlertDialog.Builder(this).setTitle("Zoom & pan").setMessage("Pinch with two fingers to zoom. Drag with two fingers to move around. One finger always paints or fills. Use Zoom + / Zoom − in the tool row, or Fit at the top, to return to the full page. JPEG export always saves the entire picture.").setPositiveButton("Got it",null).show();
        }).setNegativeButton("Close",null).show();}
    private void showArtCredits(){new AlertDialog.Builder(this).setTitle("Gothic page artwork").setMessage("Gothic icon pages use adapted contours from Game-icons.net by Lorc and Delapouite, CC BY 3.0. Illustrated Gallery pages are adapted from Pavel Kukov's coloring-pages collection (CC BY-SA 4.0), with upstream public-domain sources acknowledged. Derived gallery images remain CC BY-SA 4.0. Source URLs, creators, SHA-256, modifications and original SVGs are in the source archive. Kenney stamps and Kronbits sparkles are CC0.").setPositiveButton("OK",null).show();}
    private void refreshHistoryButtons(){if(paper==null)return;if(undoButton!=null){boolean can=paper.canUndo();undoButton.setEnabled(can);undoButton.setAlpha(can?1f:.42f);}if(redoButton!=null){boolean can=paper.canRedo();redoButton.setEnabled(can);redoButton.setAlpha(can?1f:.42f);}}
    private void selectStamp(){LinearLayout choices=row();choices.setGravity(Gravity.CENTER);String[] labels={"Blue","Green","Silver"};for(int i=0;i<kenney.length;i++){final int index=i;LinearLayout item=new LinearLayout(this);item.setOrientation(LinearLayout.VERTICAL);item.setPadding(dp(8),dp(8),dp(8),dp(8));ImageView im=new ImageView(this);if(kenney[i]!=null)im.setImageBitmap(kenney[i]);item.addView(im,new LinearLayout.LayoutParams(dp(72),dp(72)));TextView t=text(labels[i],12,false);t.setGravity(Gravity.CENTER);item.addView(t);choices.addView(item);item.setOnClickListener(v->{stampIndex=index;paper.setStamp(index);Toast.makeText(this,labels[index]+" star stamp selected",Toast.LENGTH_SHORT).show();});}new AlertDialog.Builder(this).setTitle("Kenney stamps • tap to select").setView(choices).setPositiveButton("Done",null).show();}
    private void selectSparkle(){LinearLayout choices=row();choices.setGravity(Gravity.CENTER);String[] labels={"Star","Filigree"};for(int i=0;i<kronbits.length;i++){final int index=i;LinearLayout item=new LinearLayout(this);item.setOrientation(LinearLayout.VERTICAL);item.setPadding(dp(10),dp(10),dp(10),dp(10));ImageView im=new ImageView(this);if(kronbits[i]!=null){im.setImageBitmap(kronbits[i]);im.setBackgroundColor(PURPLE);}item.addView(im,new LinearLayout.LayoutParams(dp(90),dp(90)));TextView t=text(labels[i],12,false);t.setGravity(Gravity.CENTER);item.addView(t);choices.addView(item);item.setOnClickListener(v->{sparkleIndex=index;paper.setSparkle(index);Toast.makeText(this,labels[index]+" sparkle selected",Toast.LENGTH_SHORT).show();});}new AlertDialog.Builder(this).setTitle("Kronbits texture brush").setView(choices).setPositiveButton("Done",null).show();}
    private void refreshTip(){if(tip!=null)tip.setText((activeTool==Tool.FILL?"Tap a closed area to fill":activeTool==Tool.STAMP?"Kenney star stamps":activeTool==Tool.SPARKLE?"Kronbits sparkle textures":activeTool==Tool.RAINBOW?"Rainbow: paint with shifting colors":activeTool==Tool.GHOSTLIGHT?"Ghostlight: paint a spectral halo":activeTool==Tool.ERASER?"Erase your paint":"Draw on the page")+"  ·  Pinch to zoom · 2 fingers to pan"+(mirrorMagic?" · MIRROR":""));}
    private File pageFile(){return new File(getFilesDir(),"moonbeam_page_"+pageIndex+".png");}
    private void savePage(){if(paper==null)return;final Bitmap copy=paper.paintLayer.copy(Bitmap.Config.ARGB_8888,false);final File file=pageFile();saves.execute(()->{try(FileOutputStream out=new FileOutputStream(file)){copy.compress(Bitmap.CompressFormat.PNG,100,out);}catch(Exception ignored){}finally{copy.recycle();}});}
    private void loadPage(){try{File f=pageFile();if(f.exists()){Bitmap b=BitmapFactory.decodeFile(f.getAbsolutePath());if(b!=null){paper.restorePaint(b);b.recycle();}}}catch(Exception ignored){}}
    @Override protected void onPause(){savePage();super.onPause();}
    @Override protected void onDestroy(){saves.shutdown();super.onDestroy();}
    private void exportJpeg(){try{Bitmap image=paper.renderExport();String name="Moonbeam_"+new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(new Date())+".jpg";ContentValues values=new ContentValues();values.put(MediaStore.Images.Media.DISPLAY_NAME,name);values.put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg");values.put(MediaStore.Images.Media.RELATIVE_PATH,Environment.DIRECTORY_PICTURES+"/Moonbeam Coloring");values.put(MediaStore.Images.Media.IS_PENDING,1);Uri uri=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);if(uri==null)throw new IllegalStateException("Gallery insert failed");try(OutputStream out=getContentResolver().openOutputStream(uri)){if(out==null||!image.compress(Bitmap.CompressFormat.JPEG,95,out))throw new IllegalStateException("JPEG write failed");}catch(Exception e){getContentResolver().delete(uri,null,null);throw e;}values.clear();values.put(MediaStore.Images.Media.IS_PENDING,0);getContentResolver().update(uri,values,null,null);image.recycle();savePage();new AlertDialog.Builder(this).setTitle("Saved to your gallery!").setMessage("Pictures / Moonbeam Coloring / "+name).setPositiveButton("Great!",null).show();}catch(Exception e){new AlertDialog.Builder(this).setTitle("Couldn't save JPEG").setMessage(e.getMessage()).setPositiveButton("OK",null).show();}}

    private final class DrawingView extends View {
        private static final int W=720,H=900;
        private Bitmap paintLayer,lineLayer;
        private Canvas paintCanvas;
        private final Paint draw=new Paint(3);
        private final ArrayDeque<Bitmap> undo=new ArrayDeque<>(),redo=new ArrayDeque<>();
        private Tool tool=Tool.BRUSH;
        private int ink=COLORS[0],stamp=0,sparkle=0;
        private float brushSize=28,scale,offX,offY,lastX,lastY;
        private float zoom=1f,panX=0,panY=0,pinchDistance=1f,pinchZoom=1f,pinchAnchorX=0,pinchAnchorY=0,rainbowHue=0;
        private boolean mirror=false,gestureMode=false;
        private boolean drawing=false,strokeHasSnapshot=false;
        DrawingView(){super(MainActivity.this);setLayerType(View.LAYER_TYPE_SOFTWARE,null);paintLayer=Bitmap.createBitmap(W,H,Bitmap.Config.ARGB_8888);paintCanvas=new Canvas(paintLayer);lineLayer=Bitmap.createBitmap(W,H,Bitmap.Config.ARGB_8888);}
        void setInk(int c){ink=c;}
        void setSize(int s){brushSize=s;}
        void setTool(Tool t){tool=t;}
        void setStamp(int i){stamp=i;}
        void setSparkle(int i){sparkle=i;}
        void setMirror(boolean enabled){mirror=enabled;invalidate();}
        void fit(){zoom=1f;panX=panY=0;invalidate();}
        void zoomBy(float factor){zoom=Math.max(1f,Math.min(7f,zoom*factor));clampPan();invalidate();}
        void setPage(int page){fit();paintLayer.eraseColor(Color.TRANSPARENT);lineLayer.eraseColor(Color.TRANSPARENT);buildPage(page);clearHistory();invalidate();refreshHistoryButtons();}
        void restorePaint(Bitmap b){if(b.getWidth()!=W||b.getHeight()!=H)return;paintLayer.eraseColor(Color.TRANSPARENT);paintCanvas.drawBitmap(b,0,0,null);invalidate();}
        void clearHistory(){for(Bitmap b:undo)b.recycle();for(Bitmap b:redo)b.recycle();undo.clear();redo.clear();refreshHistoryButtons();}
        boolean canUndo(){return !undo.isEmpty();}
        boolean canRedo(){return !redo.isEmpty();}
        void snapshot(ArrayDeque<Bitmap> dst){Bitmap copy=paintLayer.copy(Bitmap.Config.ARGB_8888,true);if(copy==null)throw new IllegalStateException("Unable to capture editable canvas history");dst.addLast(copy);while(dst.size()>12)dst.removeFirst().recycle();}
        void begin(){snapshot(undo);strokeHasSnapshot=true;refreshHistoryButtons();}
        void commit(){strokeHasSnapshot=false;for(Bitmap b:redo)b.recycle();redo.clear();refreshHistoryButtons();savePage();}
        void cancelStroke(){if(!drawing)return;drawing=false;if(strokeHasSnapshot&&!undo.isEmpty()){Bitmap old=undo.removeLast();swap(old);}strokeHasSnapshot=false;refreshHistoryButtons();}
        void undo(){if(undo.isEmpty())return;snapshot(redo);Bitmap b=undo.removeLast();swap(b);refreshHistoryButtons();savePage();}
        void redo(){if(redo.isEmpty())return;snapshot(undo);Bitmap b=redo.removeLast();swap(b);refreshHistoryButtons();savePage();}
        void swap(Bitmap b){if(b==null)throw new IllegalArgumentException("Missing history snapshot");if(!b.isMutable())throw new IllegalStateException("History snapshot must be mutable");Bitmap previous=paintLayer;paintLayer=b;paintCanvas=new Canvas(paintLayer);previous.recycle();invalidate();}
        @Override protected void onDraw(Canvas c){super.onDraw(c);c.drawColor(CREAM);float availW=getWidth()-dp(8),availH=getHeight()-dp(8);float fit=Math.min(availW/W,availH/H);scale=fit*zoom;offX=(getWidth()-W*scale)/2+panX;offY=(getHeight()-H*scale)/2+panY;Paint shadow=new Paint(3);shadow.setColor(0xffd5c7d0);c.drawRoundRect(offX+dp(3),offY+dp(5),offX+W*scale+dp(3),offY+H*scale+dp(5),dp(9),dp(9),shadow);c.save();c.translate(offX,offY);c.scale(scale,scale);c.drawColor(Color.TRANSPARENT);c.drawRect(0,0,W,H,white());c.drawBitmap(paintLayer,0,0,null);c.drawBitmap(lineLayer,0,0,null);if(mirror){Paint guide=new Paint(3);guide.setColor(0x8845a5a0);guide.setStrokeWidth(2f/zoom);guide.setPathEffect(new android.graphics.DashPathEffect(new float[]{14,10},0));c.drawLine(W/2f,0,W/2f,H,guide);}c.restore();if(zoom>1.01f){Paint p=new Paint(3);p.setColor(PURPLE);p.setTextSize(dp(13));c.drawText(String.format(Locale.US,"%.1fx",zoom),dp(15),dp(23),p);}}
        private Paint white(){Paint p=new Paint();p.setColor(Color.WHITE);return p;}
        private float distance(MotionEvent e){float dx=e.getX(0)-e.getX(1),dy=e.getY(0)-e.getY(1);return Math.max(1f,(float)Math.hypot(dx,dy));}
        private void clampPan(){if(zoom<=1.001f){panX=panY=0;return;}float fit=Math.min((getWidth()-dp(8))/(float)W,(getHeight()-dp(8))/(float)H);float fullW=W*fit*zoom,fullH=H*fit*zoom;
            float limX=Math.max(0,(fullW-getWidth())/2+dp(48)),limY=Math.max(0,(fullH-getHeight())/2+dp(48));
            panX=Math.max(-limX,Math.min(limX,panX));panY=Math.max(-limY,Math.min(limY,panY));
        }
        @Override public boolean onTouchEvent(MotionEvent e){
            int act=e.getActionMasked();
            if(act==MotionEvent.ACTION_POINTER_DOWN&&e.getPointerCount()>=2){
                cancelStroke();gestureMode=true;pinchDistance=distance(e);pinchZoom=zoom;
                float midX=(e.getX(0)+e.getX(1))/2f,midY=(e.getY(0)+e.getY(1))/2f;
                pinchAnchorX=(midX-offX)/scale;pinchAnchorY=(midY-offY)/scale;return true;
            }
            if(gestureMode){
                if(act==MotionEvent.ACTION_MOVE&&e.getPointerCount()>=2){
                    zoom=Math.max(1f,Math.min(7f,pinchZoom*distance(e)/pinchDistance));
                    float midX=(e.getX(0)+e.getX(1))/2f,midY=(e.getY(0)+e.getY(1))/2f;
                    float fit=Math.min((getWidth()-dp(8))/(float)W,(getHeight()-dp(8))/(float)H),newScale=fit*zoom;
                    panX=midX-pinchAnchorX*newScale-(getWidth()-W*newScale)/2f;
                    panY=midY-pinchAnchorY*newScale-(getHeight()-H*newScale)/2f;
                    clampPan();invalidate();return true;
                }
                if(act==MotionEvent.ACTION_UP||act==MotionEvent.ACTION_CANCEL){gestureMode=false;invalidate();}
                return true;
            }
            float x=(e.getX()-offX)/scale,y=(e.getY()-offY)/scale;
            boolean inside=x>=0&&y>=0&&x<W&&y<H;
            if(act==MotionEvent.ACTION_DOWN){
                if(!inside)return true;
                lastX=x;lastY=y;drawing=true;
                if(tool!=Tool.FILL){begin();rainbowHue=(rainbowHue+29f)%360f;mark(x,y);invalidate();}
                return true;
            }
            if(act==MotionEvent.ACTION_MOVE&&drawing){
                if(tool==Tool.FILL)return true;
                x=Math.max(0,Math.min(W-1,x));y=Math.max(0,Math.min(H-1,y));
                float dx=x-lastX,dy=y-lastY,d=(float)Math.hypot(dx,dy);
                if(tool==Tool.BRUSH||tool==Tool.ERASER||tool==Tool.RAINBOW||tool==Tool.GHOSTLIGHT){int steps=Math.max(1,(int)(d/9f));for(int i=1;i<=steps;i++){float ax=lastX+dx*(i-1)/steps,ay=lastY+dy*(i-1)/steps,bx=lastX+dx*i/steps,by=lastY+dy*i/steps;stroke(ax,ay,bx,by);}}
                else{int steps=Math.max(1,(int)(d/Math.max(12,brushSize*.8f)));for(int i=1;i<=steps;i++)mark(lastX+dx*i/steps,lastY+dy*i/steps);}
                lastX=x;lastY=y;invalidate();return true;
            }
            if(act==MotionEvent.ACTION_UP&&drawing){
                drawing=false;
                if(tool==Tool.FILL){if(inside){begin();floodFill((int)x,(int)y);if(mirror&&Math.abs(W-1-2*x)>1)floodFill((int)(W-1-x),(int)y);commit();} }
                else commit();
                invalidate();return true;
            }
            if(act==MotionEvent.ACTION_CANCEL){cancelStroke();invalidate();return true;}
            return true;
        }
        private void mark(float x,float y){
            if(tool==Tool.BRUSH||tool==Tool.ERASER||tool==Tool.RAINBOW||tool==Tool.GHOSTLIGHT)stroke(x,y,x+.01f,y+.01f);
            else if(tool==Tool.STAMP){drawStamp(kenney[stamp],x,y,brushSize*2.4f,false);if(mirror)drawStamp(kenney[stamp],W-x,y,brushSize*2.4f,false);}
            else if(tool==Tool.SPARKLE){drawStamp(kronbits[sparkle],x,y,brushSize*2.2f,true);if(mirror)drawStamp(kronbits[sparkle],W-x,y,brushSize*2.2f,true);}
        }
        private void stroke(float x1,float y1,float x2,float y2){
            int color=ink;
            if(tool==Tool.RAINBOW){float dist=(float)Math.hypot(x2-x1,y2-y1);rainbowHue=(rainbowHue+Math.max(2f,dist*1.7f))%360f;color=Color.HSVToColor(new float[]{rainbowHue,.86f,.96f});}
            drawSegment(x1,y1,x2,y2,color);
            if(mirror)drawSegment(W-x1,y1,W-x2,y2,color);
        }
        private void drawSegment(float x1,float y1,float x2,float y2,int color){
            Paint p=new Paint(3);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);p.setStyle(Paint.Style.STROKE);
            if(tool==Tool.GHOSTLIGHT){
                p.setColor(color);p.setStrokeWidth(brushSize*3.4f);p.setAlpha(36);paintCanvas.drawLine(x1,y1,x2,y2,p);
                p.setStrokeWidth(brushSize*2.1f);p.setAlpha(70);paintCanvas.drawLine(x1,y1,x2,y2,p);
                p.setStrokeWidth(brushSize*.72f);p.setAlpha(210);paintCanvas.drawLine(x1,y1,x2,y2,p);
            }else{
                p.setStrokeWidth(brushSize);if(tool==Tool.ERASER)p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));else p.setColor(color);
                paintCanvas.drawLine(x1,y1,x2,y2,p);
            }
        }
        private void drawStamp(Bitmap b,float x,float y,float width,boolean tint){if(b==null)return;float height=width*b.getHeight()/b.getWidth();Paint p=new Paint(3);p.setFilterBitmap(true);if(tint)p.setColorFilter(new PorterDuffColorFilter(ink,PorterDuff.Mode.SRC_IN));paintCanvas.drawBitmap(b,null,new RectF(x-width/2,y-height/2,x+width/2,y+height/2),p);}
        private void floodFill(int x,int y){int[] boundary=new int[W*H],pix=new int[W*H];lineLayer.getPixels(boundary,0,W,0,0,W,H);paintLayer.getPixels(pix,0,W,0,0,W,H);int start=y*W+x,target=pix[start];if(Color.alpha(boundary[start])>35||target==ink)return;int[] q=new int[W*H];boolean[] seen=new boolean[W*H];int head=0,tail=0;q[tail++]=start;seen[start]=true;while(head<tail){int i=q[head++];if(Color.alpha(boundary[i])>35||pix[i]!=target)continue;pix[i]=ink;int xx=i%W,yy=i/W;if(xx>0&&!seen[i-1]){seen[i-1]=true;q[tail++]=i-1;}if(xx<W-1&&!seen[i+1]){seen[i+1]=true;q[tail++]=i+1;}if(yy>0&&!seen[i-W]){seen[i-W]=true;q[tail++]=i-W;}if(yy<H-1&&!seen[i+W]){seen[i+W]=true;q[tail++]=i+W;}}paintLayer.setPixels(pix,0,W,0,0,W,H);invalidate();}
        Bitmap renderExport(){Bitmap result=Bitmap.createBitmap(W,H,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(result);c.drawColor(Color.WHITE);c.drawBitmap(paintLayer,0,0,null);c.drawBitmap(lineLayer,0,0,null);return result;}
        private Paint line(){Paint p=new Paint(3);p.setColor(INK);p.setStrokeWidth(6);p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);return p;}
        private void path(Canvas c,Paint p,float...xy){Path a=new Path();a.moveTo(xy[0],xy[1]);for(int i=2;i<xy.length;i+=2)a.lineTo(xy[i],xy[i+1]);a.close();c.drawPath(a,p);}
        private void star(Canvas c,Paint p,float cx,float cy,float r){Path a=new Path();for(int i=0;i<10;i++){double angle=-Math.PI/2+i*Math.PI/5;float rr=i%2==0?r:r*.43f;float x=cx+(float)Math.cos(angle)*rr,y=cy+(float)Math.sin(angle)*rr;if(i==0)a.moveTo(x,y);else a.lineTo(x,y);}a.close();c.drawPath(a,p);}
        private void buildPage(int page){Canvas c=new Canvas(lineLayer);Paint p=line();if(page>=5){try(InputStream in=getAssets().open("pages/page_"+page+".png")){Bitmap b=BitmapFactory.decodeStream(in);if(b!=null){c.drawBitmap(b,0,0,null);b.recycle();}}catch(Exception e){android.util.Log.e("Moonbeam","Could not load gothic coloring page "+page,e);}return;}switch(page){
        case 0: // Moon Moth
          c.drawCircle(360,435,262,p);c.drawCircle(360,435,221,p);
          c.drawOval(new RectF(168,340,357,615),p);c.drawOval(new RectF(363,340,552,615),p);
          c.drawOval(new RectF(210,480,354,665),p);c.drawOval(new RectF(366,480,510,665),p);
          c.drawOval(new RectF(337,361,383,654),p);c.drawCircle(360,332,38,p);
          c.drawLine(350,300,315,254,p);c.drawLine(370,300,405,254,p);
          c.drawCircle(276,452,34,p);c.drawCircle(444,452,34,p);c.drawCircle(274,562,25,p);c.drawCircle(446,562,25,p);
          star(c,p,145,155,53);star(c,p,576,179,39);star(c,p,110,740,34);star(c,p,612,727,45);
          c.drawCircle(135,280,19,p);c.drawCircle(581,629,16,p);break;
        case 1: // Dragon egg
          c.drawOval(new RectF(168,135,552,805),p);
          for(int row=0;row<6;row++){int cy=270+row*78;int count=row%2==0?4:3;for(int j=0;j<count;j++){int cx=(row%2==0?250:296)+j*75;float nx=(cx-360)/192f,ny=(cy-470)/335f;if(nx*nx+ny*ny<.70f)c.drawArc(new RectF(cx-35,cy-28,cx+35,cy+44),0,180,false,p);}}
          c.drawOval(new RectF(270,177,450,295),p);star(c,p,360,230,47);
          c.drawOval(new RectF(300,710,420,778),p);
          for(int i=0;i<5;i++){float cx=135+i*110;c.drawCircle(cx,842,15,p);}break;
        case 2: // Haunted castle
          c.drawCircle(570,155,79,p);c.drawCircle(570,155,49,p);
          c.drawRect(195,402,525,800,p);c.drawRect(113,331,224,800,p);c.drawRect(496,331,607,800,p);
          path(c,p,105,331,168,225,231,331);path(c,p,489,331,552,225,615,331);
          for(int i=0;i<5;i++){float xx=206+i*62;c.drawRect(xx,365,xx+34,406,p);}
          for(int i=0;i<3;i++){float xx=133+i*29;c.drawRect(xx,302,xx+20,330,p);c.drawRect(526+i*22,302,542+i*22,330,p);}
          c.drawRoundRect(new RectF(299,591,421,800),55,55,p);c.drawCircle(398,701,7,p);
          for(int x:new int[]{251,466})for(int y:new int[]{472,579}){c.drawRoundRect(new RectF(x-23,y-32,x+23,y+32),22,22,p);c.drawLine(x-23,y,x+23,y,p);}
          c.drawLine(76,800,644,800,p);star(c,p,90,137,33);star(c,p,312,146,26);break;
        case 3: // Magic garden
          c.drawCircle(360,420,67,p);
          for(int i=0;i<8;i++){double a=i*Math.PI/4;float x=360+(float)Math.cos(a)*135,y=420+(float)Math.sin(a)*135;c.drawOval(new RectF(x-57,y-76,x+57,y+76),p);}
          c.drawCircle(360,420,31,p);c.drawLine(360,570,360,812,p);
          c.drawOval(new RectF(228,643,364,720),p);c.drawOval(new RectF(358,691,502,765),p);
          for(int x:new int[]{100,600}){c.drawCircle(x,716,43,p);c.drawCircle(x,716,17,p);c.drawLine(x,759,x,829,p);}
          star(c,p,135,168,44);star(c,p,589,190,35);c.drawCircle(360,138,42,p);break;
        default: // Cosmic cat
          c.drawCircle(360,457,227,p);path(c,p,184,337,205,163,319,255);path(c,p,401,255,515,163,536,337);
          c.drawOval(new RectF(260,403,329,467),p);c.drawOval(new RectF(391,403,460,467),p);
          path(c,p,334,505,386,505,360,537);c.drawArc(new RectF(300,509,360,571),0,170,false,p);c.drawArc(new RectF(360,509,420,571),10,170,false,p);
          for(int i=0;i<3;i++){int y=513+i*25;c.drawLine(274,y,132,y-14,p);c.drawLine(446,y,588,y-14,p);}
          c.drawOval(new RectF(225,673,495,822),p);c.drawCircle(112,160,68,p);c.drawCircle(112,160,40,p);
          star(c,p,609,172,43);star(c,p,550,781,32);break;
        }}
    }
}