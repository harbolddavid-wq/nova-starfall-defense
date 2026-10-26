package com.novasky.arcade;
import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
public class VelvetActivity extends Activity {
 private WebView web;
 @Override public void onCreate(Bundle state) {
  super.onCreate(state);
  getWindow().getDecorView().setSystemUiVisibility(
    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
    View.SYSTEM_UI_FLAG_FULLSCREEN |
    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
    View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
  web=new WebView(this);
  web.setBackgroundColor(0xff16091f);
  web.setWebViewClient(new WebViewClient());
  web.setWebChromeClient(new WebChromeClient());
  WebSettings s=web.getSettings();
  s.setJavaScriptEnabled(true);
  s.setDomStorageEnabled(true);
  s.setMediaPlaybackRequiresUserGesture(false);
  s.setAllowFileAccess(false);
  s.setAllowContentAccess(false);
  s.setCacheMode(WebSettings.LOAD_NO_CACHE);
  setContentView(web);
  web.loadUrl("file:///android_asset/velvet.html");
 }
 @Override public void onPause(){super.onPause();if(web!=null)web.onPause();}
 @Override public void onResume(){super.onResume();if(web!=null)web.onResume();}
 @Override public void onDestroy(){if(web!=null){web.destroy();web=null;}super.onDestroy();}
}