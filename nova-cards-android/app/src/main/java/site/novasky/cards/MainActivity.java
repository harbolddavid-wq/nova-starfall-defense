package site.novasky.cards;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.graphics.Color;
import android.graphics.Insets;
import android.os.Build;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class MainActivity extends Activity {
    private WebView cards;
    private static final String BASE = "https://nova-cards.invalid/";

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Reserve Android's real system-bar insets before WebView hit testing.
        // Edge-to-edge is enforced for target SDK 35+ on Android 15/16.
        FrameLayout safeRoot = new FrameLayout(this);
        safeRoot.setBackgroundColor(Color.rgb(7, 20, 27));
        cards = new WebView(this);
        cards.setBackgroundColor(Color.rgb(7, 20, 27));
        safeRoot.addView(cards, new FrameLayout.LayoutParams(-1, -1));
        safeRoot.setOnApplyWindowInsetsListener((view, insets) -> {
            final int top, bottom, left, right;
            if (Build.VERSION.SDK_INT >= 30) {
                Insets bars = insets.getInsets(WindowInsets.Type.statusBars()
                        | WindowInsets.Type.navigationBars()
                        | WindowInsets.Type.displayCutout());
                top = bars.top;
                bottom = bars.bottom;
                left = bars.left;
                right = bars.right;
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
                left = insets.getSystemWindowInsetLeft();
                right = insets.getSystemWindowInsetRight();
            }
            view.setPadding(left, top, right, bottom);
            return insets;
        });
        setContentView(safeRoot);
        safeRoot.requestApplyInsets();
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
