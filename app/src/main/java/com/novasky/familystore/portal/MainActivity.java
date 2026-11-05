package com.novasky.familystore.portal;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class MainActivity extends Activity {
    private static final String STORE_URL = "https://nova-family-store.hatchable.site";
    private boolean opened = false;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(16, 11, 32));
        getWindow().setNavigationBarColor(Color.rgb(16, 11, 32));
        FrameLayout root = new FrameLayout(this);
        root.addView(new StarField(this), new FrameLayout.LayoutParams(-1,-1));
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER);
        int horizontalPadding = dp(24);
        column.setPadding(horizontalPadding, dp(20), horizontalPadding, dp(20));
        FrameLayout.LayoutParams center = new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER);
        root.addView(column,center);

        TextView emblem = text("✦", 90, 0xFFE9D0FF, Typeface.BOLD);
        emblem.setShadowLayer(dp(24),0,0,0xCCAE83FF);
        column.addView(emblem);

        TextView title = text("NOVA", 38, 0xFFF9F3FF, Typeface.BOLD);
        title.setLetterSpacing(.14f);
        column.addView(title);
        TextView title2 = text("FAMILY STORE", 23, 0xFFD3ACFF, Typeface.BOLD);
        title2.setLetterSpacing(.1f);
        column.addView(title2);
        TextView tagline = text("Little worlds. Made together.", 16, 0xFFD0BEDF, Typeface.NORMAL);
        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(-1,-2);
        gap.topMargin=dp(23);
        column.addView(tagline,gap);
        TextView opening = text("Opening your collection…", 13, 0xFFB2A3C5, Typeface.NORMAL);
        LinearLayout.LayoutParams gap2 = new LinearLayout.LayoutParams(-1,-2);
        gap2.topMargin=dp(48);
        column.addView(opening,gap2);
        TextView hint = text("Tap anywhere to open now", 12, 0xFF8F7DA9, Typeface.NORMAL);
        LinearLayout.LayoutParams gap3 = new LinearLayout.LayoutParams(-1,-2);
        gap3.topMargin=dp(12);
        column.addView(hint,gap3);
        root.setOnClickListener(v -> openStore());
        setContentView(root);
        handler.postDelayed(this::openStore, 1750);
    }

    private TextView text(String value, int sp, int color, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setTypeface(Typeface.create("sans-serif",style));
        view.setGravity(Gravity.CENTER);
        return view;
    }

    private int dp(int value) {return (int)(getResources().getDisplayMetrics().density*value+.5f);}

    private void openStore() {
        if (opened || isFinishing()) return;
        opened = true;
        Intent browse = new Intent(Intent.ACTION_VIEW,Uri.parse(STORE_URL));
        browse.addCategory(Intent.CATEGORY_BROWSABLE);
        try {
            startActivity(browse);
            finish();
        } catch(ActivityNotFoundException ex) {
            opened=false;
            new AlertDialog.Builder(this)
                .setTitle("Browser needed")
                .setMessage("A browser is needed to open the Family Store. Install or enable your browser, then try again.")
                .setPositiveButton("OK", (dialog,which) -> finish())
                .show();
        }
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private static final class StarField extends View {
        private final Paint paint = new Paint(3);
        private final float[] starsX = new float[54];
        private final float[] starsY = new float[54];
        private final float[] sizes = new float[54];
        private final long started = android.os.SystemClock.uptimeMillis();

        StarField(Activity a) {
            super(a);
            java.util.Random random = new java.util.Random(130013L);
            for(int i=0;i<starsX.length;i++) {
                starsX[i]=random.nextFloat(); starsY[i]=random.nextFloat();
                sizes[i]=1f+random.nextFloat()*2.8f;
            }
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            paint.setShader(new LinearGradient(0,0,getWidth(),getHeight(),0xFF372057,0xFF090A18,Shader.TileMode.CLAMP));
            canvas.drawRect(0,0,getWidth(),getHeight(),paint);
            paint.setShader(null);
            float t=(android.os.SystemClock.uptimeMillis()-started)/1000f;
            for(int i=0;i<starsX.length;i++) {
                float twinkle=(float)Math.sin(t*2+i*.73f);
                paint.setColor(Color.argb(75+(int)(95*(.5f+twinkle*.5f)),215,182,255));
                canvas.drawCircle(starsX[i]*getWidth(),starsY[i]*getHeight(),sizes[i],paint);
            }
            if(t<3) postInvalidateDelayed(30);
        }
    }
}
