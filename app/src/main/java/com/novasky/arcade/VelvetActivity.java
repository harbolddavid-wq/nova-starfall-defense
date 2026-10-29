package com.novasky.arcade;

import android.app.Activity;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/** Offline wrapper. WebView owns media audio focus; this bridge only supplies haptics. */
public class VelvetActivity extends Activity {
 private WebView web;
 private volatile boolean resumed;

 private final class NativeBridge {
  @JavascriptInterface public void haptic() {
   if (!resumed) return;
   runOnUiThread(() -> { if (web != null && resumed) web.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); });
  }
 }

 @Override public void onCreate(Bundle state) {
  super.onCreate(state);
  getWindow().getDecorView().setSystemUiVisibility(
    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN |
    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
  web = new WebView(this);
  web.setBackgroundColor(0xff16091f);
  web.setWebViewClient(new WebViewClient());
  web.setWebChromeClient(new WebChromeClient());
  WebSettings s = web.getSettings();
  s.setJavaScriptEnabled(true);
  s.setDomStorageEnabled(true);
  // The game starts audio only after its own player gesture. Allow the later
  // asynchronous impact/idle cues to play without another tap for each sound.
  s.setMediaPlaybackRequiresUserGesture(false);
  s.setAllowFileAccess(false);
  s.setAllowContentAccess(false);
  s.setCacheMode(WebSettings.LOAD_NO_CACHE);
  web.addJavascriptInterface(new NativeBridge(), "NativeBridge");
  setContentView(web);
  web.loadUrl("file:///android_asset/velvet.html");
 }
 @Override public void onPause() {
  resumed = false;
  if (web != null) {
   web.evaluateJavascript("window.onNativePause && window.onNativePause()", null);
   web.onPause();
  }
  super.onPause();
 }
 @Override public void onResume() {
  super.onResume();
  resumed = true;
  if (web != null) web.onResume();
 }
 @Override public void onDestroy() {
  if (web != null) { web.removeJavascriptInterface("NativeBridge"); web.destroy(); web = null; }
  super.onDestroy();
 }
}
