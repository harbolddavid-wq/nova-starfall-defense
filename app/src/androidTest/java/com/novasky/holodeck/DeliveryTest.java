package com.novasky.holodeck;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.ActivityTestRule;
import androidx.test.platform.app.InstrumentationRegistry;
import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.concurrent.*;
import java.util.zip.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class DeliveryTest {
 @Rule public ActivityTestRule<MainActivity> rule=new ActivityTestRule<>(MainActivity.class);
 Instrumentation inst(){return InstrumentationRegistry.getInstrumentation();}MainActivity a(){return rule.getActivity();}
 void ui(Runnable r){inst().runOnMainSync(r);}void click(int id){ui(()->{View v=a().findViewById(id);assertNotNull("Missing native control "+id,v);v.performClick();});}
 String js(String code)throws Exception{CountDownLatch l=new CountDownLatch(1);String[] out={null};ui(()->a().getPlayer().evaluateJavascript(code,v->{out[0]=v;l.countDown();}));assertTrue(l.await(10,TimeUnit.SECONDS));return out[0];}
 JSONObject state()throws Exception{return new JSONObject(new JSONArray("["+js("JSON.stringify(Lanternwake.snapshot())")+"]").getString(0));}
 void ready()throws Exception{long end=SystemClock.uptimeMillis()+20000;while(SystemClock.uptimeMillis()<end){if("true".equals(js("!!window.Lanternwake && Lanternwake.snapshot().width>100")))return;SystemClock.sleep(100);}fail("Downloaded world did not start");}
 void shot(String name)throws Exception{SystemClock.sleep(350);Bitmap b=inst().getUiAutomation().takeScreenshot();try(FileOutputStream f=new FileOutputStream(new File(a().getExternalFilesDir(null),name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,f);}b.recycle();}
 String shell(String command)throws Exception{try(ParcelFileDescriptor fd=inst().getUiAutomation().executeShellCommand(command);InputStream in=new FileInputStream(fd.getFileDescriptor())){return new String(ExperienceStore.bytes(in,1024*1024),java.nio.charset.StandardCharsets.UTF_8);}}
 void move(double x,double y)throws Exception{JSONObject s=state(),r=s.getJSONObject("canvas"),b=s.getJSONObject("boat");float scale=a().getPlayer().getWidth()/(float)Double.parseDouble(js("innerWidth"));float sx=(float)(r.getDouble("x")+b.getDouble("x")*r.getDouble("width"))*scale,sy=(float)(r.getDouble("y")+b.getDouble("y")*r.getDouble("height"))*scale,tx=(float)(r.getDouble("x")+x*r.getDouble("width"))*scale,ty=(float)(r.getDouble("y")+y*r.getDouble("height"))*scale;long down=SystemClock.uptimeMillis();for(int i=0;i<=12;i++){final int n=i;final float px=sx+(tx-sx)*i/12f,py=sy+(ty-sy)*i/12f;ui(()->{MotionEvent e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),n==0?0:n==12?1:2,px,py,0);a().getPlayer().dispatchTouchEvent(e);e.recycle();});SystemClock.sleep(20);}long end=SystemClock.uptimeMillis()+8000;while(SystemClock.uptimeMillis()<end){JSONObject q=state();if(q.getBoolean("modal"))return;JSONObject boat=q.getJSONObject("boat");if(Math.hypot(boat.getDouble("x")-x,boat.getDouble("y")-y)<.04)return;SystemClock.sleep(100);}System.out.println("Boat drag failure: goal="+x+","+y+" state="+state());shot("delivery-drag-failure");fail("Boat did not respond to native drag");}
 byte[] fixture(String file)throws Exception{try(InputStream in=inst().getContext().getAssets().open("delivery/"+file)){return ExperienceStore.bytes(in,ExperienceStore.MAX_ZIP);}}
 @Test public void downloadAfterHostInstallPlayOfflineUpdateAndRollback()throws Exception{
  long installedAt=a().getPackageManager().getPackageInfo(a().getPackageName(),0).lastUpdateTime;
  try{a().getAssets().open("lanternwake/index.html");fail("Second world must not be bundled in host APK");}catch(FileNotFoundException expected){}catch(IOException expected){}
  click(MainActivity.DISCOVER);long end=SystemClock.uptimeMillis()+90000;while(SystemClock.uptimeMillis()<end&&(a().store.catalog==null||a().busy))SystemClock.sleep(150);assertNotNull("Signed online catalog",a().store.catalog);shot("10-discover-catalog");
  if(a().store.installed("lanternwake")==null){click(MainActivity.INSTALL_WORLD);end=SystemClock.uptimeMillis()+90000;while(SystemClock.uptimeMillis()<end&&(a().store.installed("lanternwake")==null||a().busy))SystemClock.sleep(150);}
  ExperienceStore.World first=a().store.installed("lanternwake");assertNotNull("Real package download",first);assertNotNull(first.directory);assertEquals(2,a().store.installed().size());assertEquals(installedAt,a().getPackageManager().getPackageInfo(a().getPackageName(),0).lastUpdateTime);shot("11-downloaded-library");
  click(MainActivity.OPEN_WORLD);ready();js("document.getElementById('continue').click()");assertEquals("\"https://lanternwake.holodeck.invalid\"",js("location.origin"));assertEquals("\"undefined\"",js("typeof Android"));
  move(.5,.36);move(.5,.25);JSONObject target=state().getJSONArray("lights").getJSONObject(0);move(target.getDouble("x"),target.getDouble("y"));assertTrue(state().getInt("total")>=1);int saved=state().getInt("total");shot("12-download-live-play");click(MainActivity.EXIT);
  try{
   shell("cmd connectivity airplane-mode enable");shell("svc wifi disable");shell("svc data disable");assertEquals("1",shell("settings get global airplane_mode_on").trim());
   click(MainActivity.OPEN_WORLD);ready();assertEquals(saved,state().getInt("total"));js("document.getElementById('continue').click()");
   for(int stage=state().getInt("stage");stage<3;stage++){
    move(.5,.36);move(.5,.25);int guard=0;
    while(!state().getBoolean("modal")&&guard++<8){JSONArray lights=state().getJSONArray("lights");JSONObject next=null;for(int i=0;i<lights.length();i++)if(!lights.getJSONObject(i).getBoolean("lit")){next=lights.getJSONObject(i);break;}if(next==null)break;move(next.getDouble("x"),next.getDouble("y"));}
    assertTrue("Passage must complete",state().getBoolean("modal"));js("document.getElementById('continue').click()");
   }
   assertTrue(state().getBoolean("completed"));assertEquals(9,state().getInt("total"));shot("13-offline-ending");click(MainActivity.EXIT);
   JSONObject envelope=new JSONObject(new String(fixture("catalog-v2.json"),java.nio.charset.StandardCharsets.UTF_8));JSONObject update=a().store.verify(envelope).getJSONArray("experiences").getJSONObject(0);ExperienceStore.World newer=a().store.installZip(update,fixture("lanternwake-v2.zip"));assertEquals(first.versionCode+1,newer.versionCode);
   ui(()->a().launch(newer));ready();assertTrue("World update must retain local save",state().getBoolean("completed"));assertEquals(9,state().getInt("total"));click(MainActivity.EXIT);a().store.rollback("lanternwake");assertEquals(first.versionCode,a().store.installed("lanternwake").versionCode);ui(()->a().launch(a().store.installed("lanternwake")));ready();assertTrue("Rollback must retain saved voyage",state().getBoolean("completed"));shot("14-offline-restored-version");click(MainActivity.EXIT);
   assertEquals("No APK reinstall for either world version",installedAt,a().getPackageManager().getPackageInfo(a().getPackageName(),0).lastUpdateTime);
  }finally{shell("cmd connectivity airplane-mode disable");shell("svc wifi enable");shell("svc data enable");}
 }
 @Test public void rejectTamperedCatalogAndUnsafePackageWithoutChangingInstalledWorld()throws Exception{
  JSONObject envelope=new JSONObject(new String(fixture("catalog-v2.json"),java.nio.charset.StandardCharsets.UTF_8));envelope.put("signature",android.util.Base64.encodeToString(new byte[256],android.util.Base64.NO_WRAP));try{a().store.verify(envelope);fail("Tampered catalog accepted");}catch(SecurityException expected){}
  JSONObject entry=new JSONObject("{\"id\":\"unsafe-world\",\"title\":\"Unsafe\",\"entry\":\"index.html\",\"runtime\":1,\"versionCode\":1,\"version\":\"1\"}");ByteArrayOutputStream out=new ByteArrayOutputStream();try(ZipOutputStream z=new ZipOutputStream(out)){z.putNextEntry(new ZipEntry("../escaped.txt"));z.write(new byte[]{1,2,3});z.closeEntry();}byte[] data=out.toByteArray();entry.put("bytes",data.length);entry.put("sha256",ExperienceStore.hash(data));try{a().store.installZip(entry,data);fail("Traversal package accepted");}catch(Exception expected){}assertNull(a().store.installed("unsafe-world"));assertFalse(new File(a().getFilesDir(),"escaped.txt").exists());assertFalse(new File(a().store.root,"unsafe-world/versions/escaped.txt").exists());assertNotNull(a().store.installed("starwell"));
  JSONObject valid=new JSONObject(new String(fixture("catalog-v2.json"),java.nio.charset.StandardCharsets.UTF_8));JSONObject bad=new JSONObject(a().store.verify(valid).getJSONArray("experiences").getJSONObject(0).toString());ExperienceStore.World before=a().store.installed("lanternwake");bad.put("versionCode",before==null?1:before.versionCode+1);bad.put("sha256",new String(new char[64]).replace('\0','0'));try{a().store.installZip(bad,fixture("lanternwake-v2.zip"));fail("Bad package hash accepted");}catch(SecurityException expected){}assertEquals(before==null?0:before.versionCode,a().store.installed("lanternwake")==null?0:a().store.installed("lanternwake").versionCode);
 }
}
