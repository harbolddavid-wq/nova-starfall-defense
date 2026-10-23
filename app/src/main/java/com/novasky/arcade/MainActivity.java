package com.novasky.arcade;
import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
public class MainActivity extends Activity {
    private WebView game;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
            View.SYSTEM_UI_FLAG_FULLSCREEN |
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        game = new WebView(this);
        game.setBackgroundColor(0xff050816);
        game.setWebViewClient(new WebViewClient());
        game.setWebChromeClient(new WebChromeClient());
        WebSettings settings = game.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        setContentView(game);
        game.loadUrl("file:///android_asset/index.html");
    }
    @Override protected void onPause(){ super.onPause(); if(game!=null)game.onPause(); }
    @Override protected void onResume(){ super.onResume(); if(game!=null)game.onResume(); }
    @Override protected void onDestroy(){ if(game!=null){game.destroy();game=null;} super.onDestroy(); }
}
