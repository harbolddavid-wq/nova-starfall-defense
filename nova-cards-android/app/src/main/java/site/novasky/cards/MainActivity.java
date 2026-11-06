package site.novasky.cards;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.graphics.Color;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class MainActivity extends Activity {
    private WebView cards;
    private static final String BASE = "https://nova-cards.invalid/";

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        cards = new WebView(this);
        cards.setBackgroundColor(Color.rgb(7, 20, 27));
        setContentView(cards);
        WebSettings settings = cards.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        cards.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, android.webkit.WebResourceRequest request) {
                return true; // entirely offline: never follow external links
            }
        });
        cards.setWebChromeClient(new WebChromeClient());
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                getAssets().open("index.html"), StandardCharsets.UTF_8))) {
            StringBuilder html = new StringBuilder(60000);
            String line;
            while ((line = reader.readLine()) != null) html.append(line).append('\n');
            cards.loadDataWithBaseURL(BASE, html.toString(), "text/html", "UTF-8", null);
        } catch (Exception e) {
            cards.loadDataWithBaseURL(BASE,
                "<h1>Game resources failed to load.</h1>", "text/html", "UTF-8", null);
        }
    }

    @Override public void onBackPressed() {
        if (cards != null && cards.canGoBack()) cards.goBack();
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (cards != null) { cards.destroy(); cards = null; }
        super.onDestroy();
    }
}
