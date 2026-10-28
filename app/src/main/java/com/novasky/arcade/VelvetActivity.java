package com.novasky.arcade;

import android.app.Activity;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/** Offline wrapper for the separate After Hours development install. */
public class VelvetActivity extends Activity {
 private WebView web;
 private AudioManager audioManager;
 private AudioFocusRequest focusRequest;
 private volatile boolean resumed;
 private volatile boolean focusHeld;

 private final AudioManager.OnAudioFocusChangeListener focusListener = change -> {
  boolean gained = change == AudioManager.AUDIOFOCUS_GAIN;
  focusHeld = gained;
  if (web != null) web.evaluateJavascript("window.onNativeAudioFocus && window.onNativeAudioFocus(" + gained + ")", null);
 };

 private final class NativeBridge {
  @JavascriptInterface public boolean acquireAudioFocus() {
   if (!resumed || audioManager == null || focusRequest == null) return false;
   if (focusHeld) return true;
   focusHeld = audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
   return focusHeld;
  }
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
  audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
  AudioAttributes attrs = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();
  focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
    .setAudioAttributes(attrs).setWillPauseWhenDucked(true)
    .setOnAudioFocusChangeListener(focusListener, new Handler(Looper.getMainLooper())).build();
  web = new WebView(this);
  web.setBackgroundColor(0xff16091f);
  web.setWebViewClient(new WebViewClient());
  web.setWebChromeClient(new WebChromeClient());
  WebSettings s = web.getSettings();
  s.setJavaScriptEnabled(true);
  s.setDomStorageEnabled(true);
  s.setMediaPlaybackRequiresUserGesture(true);
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
  if (audioManager != null && focusRequest != null) audioManager.abandonAudioFocusRequest(focusRequest);
  focusHeld = false;
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
