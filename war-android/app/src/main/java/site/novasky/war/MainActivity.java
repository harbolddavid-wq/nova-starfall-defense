package site.novasky.war;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Insets;
import android.view.WindowInsets;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.widget.FrameLayout;

public final class MainActivity extends Activity {
    private WebView game;
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(7,18,34));
        getWindow().setNavigationBarColor(Color.rgb(7,18,34));
        getWindow().getDecorView().setSystemUiVisibility(0);
        FrameLayout safeRoot = new FrameLayout(this);
        safeRoot.setBackgroundColor(Color.rgb(7,18,34));
        game = new WebView(this);
        game.setBackgroundColor(Color.rgb(7,18,34));
        safeRoot.addView(game,new FrameLayout.LayoutParams(-1,-1));
        safeRoot.setOnApplyWindowInsetsListener((view,insets)->{
            final int l,t,r,b;
            if(Build.VERSION.SDK_INT>=30){
                Insets bars=insets.getInsets(WindowInsets.Type.statusBars()|WindowInsets.Type.navigationBars()|WindowInsets.Type.displayCutout());
                l=bars.left;t=bars.top;r=bars.right;b=bars.bottom;
            }else{
                l=insets.getSystemWindowInsetLeft();t=insets.getSystemWindowInsetTop();r=insets.getSystemWindowInsetRight();b=insets.getSystemWindowInsetBottom();
            }
            view.setPadding(l,t,r,b);
            return insets;
        });
        setContentView(safeRoot);safeRoot.requestApplyInsets();
        WebSettings settings=game.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true); // needed only for bundled /android_asset/ images and scripts
        settings.setAllowContentAccess(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        if(Build.VERSION.SDK_INT>=16){
            settings.setAllowFileAccessFromFileURLs(false);
            settings.setAllowUniversalAccessFromFileURLs(false);
        }
        game.setWebChromeClient(new WebChromeClient());
        game.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return true;}
        });
        game.loadUrl("file:///android_asset/index.html");
    }
    @Override public void onBackPressed(){
        if(game!=null&&game.canGoBack())game.goBack();
        else super.onBackPressed();
    }
    @Override protected void onDestroy(){if(game!=null){game.destroy();game=null;}super.onDestroy();}
}