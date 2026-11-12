package com.novasky.holodeck;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.ActivityTestRule;
import androidx.test.platform.app.InstrumentationRegistry;
import android.os.SystemClock;
import org.junit.*;
import org.junit.runner.RunWith;
import java.util.concurrent.*;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class UpgradeTest {
 @Rule public ActivityTestRule<MainActivity> rule=new ActivityTestRule<>(MainActivity.class);
 String js(String code)throws Exception{CountDownLatch d=new CountDownLatch(1);String[] result={null};InstrumentationRegistry.getInstrumentation().runOnMainSync(()->rule.getActivity().getPlayer().evaluateJavascript(code,v->{result[0]=v;d.countDown();}));assertTrue(d.await(10,TimeUnit.SECONDS));return result[0];}
 @Test public void priorInstalledSkySurvivesHostUpdate()throws Exception{
  InstrumentationRegistry.getInstrumentation().runOnMainSync(()->rule.getActivity().launch());long end=SystemClock.uptimeMillis()+20000;
  while(SystemClock.uptimeMillis()<end&&!"true".equals(js("!!window.Starwell")))SystemClock.sleep(100);
  assertEquals("The real prior-version saved star must survive the APK update","1",js("Starwell.snapshot().lit"));
  assertEquals("\"https://starwell.holodeck.invalid\"",js("location.origin"));
 }
}
