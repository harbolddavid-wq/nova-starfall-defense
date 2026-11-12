package com.novasky.holodeck;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.ActivityTestRule;
import androidx.test.platform.app.InstrumentationRegistry;
import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.SystemClock;
import android.view.*;
import android.webkit.WebView;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class HolodeckTest {
 @Rule public ActivityTestRule<MainActivity> rule=new ActivityTestRule<>(MainActivity.class);
 Instrumentation inst(){return InstrumentationRegistry.getInstrumentation();}
 MainActivity a(){return rule.getActivity();}
 void ui(Runnable r){inst().runOnMainSync(r);}
 void click(int id){ui(()->{View v=a().findViewById(id);assertNotNull(v);v.performClick();});}
 String js(String code)throws Exception{CountDownLatch done=new CountDownLatch(1);String[] result={null};ui(()->a().getPlayer().evaluateJavascript(code,v->{result[0]=v;done.countDown();}));assertTrue("JS callback",done.await(10,TimeUnit.SECONDS));return result[0];}
 JSONObject state()throws Exception{return new JSONObject(new JSONArray("["+js("JSON.stringify(Starwell.snapshot())")+"]").getString(0));}
 void ready()throws Exception{long end=SystemClock.uptimeMillis()+20000;while(SystemClock.uptimeMillis()<end){if("true".equals(js("!!window.Starwell && Starwell.snapshot().width>100 && Starwell.snapshot().height>100")))return;SystemClock.sleep(150);}fail("Experience failed to load");}
 void startFresh()throws Exception{ui(()->{a().getSharedPreferences("holodeck",0).edit().clear().commit();a().launch();});ready();js("document.getElementById('reset').click();document.getElementById('reset').click();document.getElementById('continue').click()");assertTrue(state().getBoolean("started"));assertEquals("Fresh UI reset",0,state().getInt("lit"));}
 void shot(String name)throws Exception{SystemClock.sleep(400);File out=new File(a().getExternalFilesDir(null),name+".png");Bitmap b=inst().getUiAutomation().takeScreenshot();try(FileOutputStream s=new FileOutputStream(out)){b.compress(Bitmap.CompressFormat.PNG,100,s);}b.recycle();}
 void dragNext()throws Exception{
  JSONObject s=state(),p=s.getJSONObject("well"),q=s.getJSONObject("target"),rect=s.getJSONObject("canvas");
  float scale=a().getPlayer().getWidth()/(float)Double.parseDouble(js("window.innerWidth"));
  float x=(float)(p.getDouble("x")+rect.getDouble("x"))*scale,y=(float)(p.getDouble("y")+rect.getDouble("y"))*scale,tx=(float)(q.getDouble("x")+rect.getDouble("x"))*scale,ty=(float)(q.getDouble("y")+rect.getDouble("y"))*scale;
  long down=SystemClock.uptimeMillis();
  for(int i=0;i<=12;i++){final int n=i;float f=i/12f;final float mx=x+(tx-x)*f,my=y+(ty-y)*f;ui(()->{MotionEvent e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),n==0?MotionEvent.ACTION_DOWN:n==12?MotionEvent.ACTION_UP:MotionEvent.ACTION_MOVE,mx,my,0);a().getPlayer().dispatchTouchEvent(e);e.recycle();});SystemClock.sleep(18);}
  SystemClock.sleep(100);
 }
 @Test public void completeExperienceWithRealTouchAndResume()throws Exception{
  shot("01-home");click(MainActivity.LIBRARY);shot("02-library");startFresh();shot("03-starwell-start");
  for(int chapter=0;chapter<3;chapter++){
   assertEquals(chapter,state().getInt("chapter"));
   for(int n=0;n<5;n++){dragNext();assertEquals("Drag must light a star",n+1,state().getInt("lit"));if(n==0&&chapter==0)assertEquals("running",state().getString("audio"));if(n==2&&chapter==0){shot("04-during-play");shot("04-motion-next");}}
   shot("05-chapter-"+chapter);js("document.getElementById('continue').click()");
  }
  assertTrue(state().getBoolean("ending"));
  js("document.getElementById('sky').dispatchEvent(new PointerEvent('pointerdown',{clientX:120,clientY:230,pointerId:9,bubbles:true}));");
  // Real drag test above covers native touch; epilogue star uses the same event listener.
  assertTrue(state().getInt("free")>0);shot("06-ending");click(MainActivity.EXIT);assertNull(a().getPlayer());click(MainActivity.LAUNCH);ready();assertTrue(state().getBoolean("ending"));js("document.getElementById('continue').click()");assertTrue(state().getInt("free")>0);
  ui(()->a().goBack());assertNull(a().getPlayer());shot("07-returned-library");
 }
 @Test public void settingsPersistAndReachRuntime()throws Exception{
  ui(()->{a().getSharedPreferences("holodeck",0).edit().clear().commit();a().show("settings");});click(MainActivity.SOUND);click(MainActivity.MOTION);assertFalse(a().sound());assertFalse(a().motion());click(MainActivity.HOME);click(MainActivity.SETTINGS);assertFalse(((android.widget.Switch)a().findViewById(MainActivity.SOUND)).isChecked());shot("08-settings");ui(()->a().launch());ready();assertFalse(state().getBoolean("sound"));assertFalse(state().getBoolean("motion"));
 }
 @Test public void sandboxRejectsOutsideResources()throws Exception{
  ui(()->a().launch());ready();ui(()->{
   WebView p=a().getPlayer();assertFalse(p.getSettings().getAllowFileAccess());assertFalse(p.getSettings().getAllowContentAccess());assertTrue(p.getSettings().getBlockNetworkLoads());
   for(String url:new String[]{"file:///etc/passwd","content://contacts","https://example.com/x","https://starwell.holodeck.invalid/../secrets","https://starwell.holodeck.invalid:444/index.html"})assertEquals(url,403,a().asset(Uri.parse(url)).getStatusCode());
   assertEquals(200,a().asset(Uri.parse("https://starwell.holodeck.invalid/starwell.js")).getStatusCode());
  });assertEquals("\"undefined\"",js("typeof Android"));assertEquals("\"https://starwell.holodeck.invalid\"",js("location.origin"));
 }
 @Test public void partialSaveAndPauseResume()throws Exception{
  startFresh();dragNext();assertEquals(1,state().getInt("lit"));ui(()->inst().callActivityOnPause(a()));SystemClock.sleep(150);assertTrue(state().getBoolean("paused"));assertEquals("suspended",state().getString("audio"));ui(()->inst().callActivityOnResume(a()));SystemClock.sleep(150);assertFalse(state().getBoolean("paused"));click(MainActivity.EXIT);click(MainActivity.LAUNCH);ready();assertEquals(1,state().getInt("lit"));
 }
 @Test public void rotationAndTapAlternative()throws Exception{
  startFresh();js("(()=>{let s=Starwell.snapshot(),c=document.getElementById('sky'),r=c.getBoundingClientRect(); for(let p of [s.well,s.target]){c.dispatchEvent(new PointerEvent('pointerdown',{clientX:r.x+p.x,clientY:r.y+p.y,pointerId:1,bubbles:true}));c.dispatchEvent(new PointerEvent('pointerup',{clientX:r.x+p.x,clientY:r.y+p.y,pointerId:1,bubbles:true}));}})()");assertEquals(1,state().getInt("lit"));ui(()->a().setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));SystemClock.sleep(1200);assertEquals(1,state().getInt("lit"));assertTrue(state().getDouble("width")>state().getDouble("height"));shot("09-landscape");ui(()->a().setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
 }
}
