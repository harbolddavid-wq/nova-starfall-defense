package com.novasky.phonelab.fixture;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {
    private int count=0;
    private boolean night=false;
    private TextView countLabel;
    private TextView stateLabel;
    private LinearLayout root;

    private int dp(int n) { return (int)(getResources().getDisplayMetrics().density*n+0.5f); }

    private TextView label(String value,int size) {
        TextView text=new TextView(this);
        text.setText(value);text.setTextSize(size);text.setTextColor(Color.WHITE);
        text.setGravity(Gravity.CENTER);text.setPadding(dp(10),dp(13),dp(10),dp(13));
        return text;
    }

    private Button control(String title){
        Button b=new Button(this);
        b.setText(title);b.setTextSize(19);
        b.setAllCaps(false);b.setMinimumHeight(dp(64));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(72));
        p.setMargins(dp(0),dp(12),dp(0),dp(12));
        b.setLayoutParams(p);
        return b;
    }
    private void update() {
        countLabel.setText("BUTTON PRESSES: "+count);
        stateLabel.setText(night ? "MODE: NIGHT" : "MODE: EMBER");
        root.setBackgroundColor(night?Color.rgb(13,23,53):Color.rgb(96,23,48));
    }
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(20,20,26));
        getWindow().setNavigationBarColor(Color.rgb(20,20,26));
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24),dp(24),dp(24),dp(20));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        scroll.addView(root);setContentView(scroll);
        TextView heading=label("NOVA PHONE LAB",28);heading.setTypeface(null,Typeface.BOLD);
        root.addView(heading);
        root.addView(label("REAL APK BUTTON + LIVE CONTROL TEST",16));
        countLabel=label("",24);countLabel.setTypeface(null,Typeface.BOLD);
        root.addView(countLabel);
        Button press=control("PRESS ME +1");press.setId(101);
        press.setOnClickListener(v->{count++;update();});
        root.addView(press);
        Button mode=control("TOGGLE COLOR");
        mode.setId(102);mode.setOnClickListener(v->{night=!night;update();});
        root.addView(mode);
        Button reset=control("RESET COUNTER");
        reset.setId(103);reset.setOnClickListener(v->{count=0;update();});
        root.addView(reset);
        stateLabel=label("",22);
        root.addView(stateLabel);
        root.addView(label("Swipe to scroll. Every button must show an actual change.",16));
        for(int i=1;i<=14;i++){
            TextView item=label("TEST ROW "+i+" — SCROLL VERIFIED",17);
            root.addView(item);
        }
        update();
    }
}
