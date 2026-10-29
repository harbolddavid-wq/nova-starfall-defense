package com.novasky.afterglow;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

final class Ui {
    static final int INK=Color.rgb(14,16,31), PANEL=Color.rgb(30,31,51), CREAM=Color.rgb(250,237,220), MUTED=Color.rgb(190,186,202), ACCENT=Color.rgb(242,192,157), BLUE=Color.rgb(133,190,235);
    static int dp(Context c,float n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
    static GradientDrawable round(int color,int radius,int border){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(radius);if(border!=0)d.setStroke(2,border);return d;}
    static TextView text(Context c,String s,int sp,int color,boolean bold){TextView t=new TextView(c);t.setText(s);t.setTextSize(sp);t.setTextColor(color);if(bold)t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));t.setIncludeFontPadding(false);return t;}
    static Button button(Context c,String s,boolean bright){Button b=new Button(c);b.setText(s);b.setTextSize(14);b.setAllCaps(false);b.setMinHeight(dp(c,48));b.setTextColor(bright?INK:CREAM);b.setBackground(round(bright?ACCENT:PANEL,dp(c,15),bright?0:0xff51546e));b.setPadding(dp(c,12),0,dp(c,12),0);return b;}
    static LinearLayout.LayoutParams lp(Context c,int width,int height){return new LinearLayout.LayoutParams(width<0?width:dp(c,width),height<0?height:dp(c,height));}
    static void gap(LinearLayout parent,int height){View v=new View(parent.getContext());parent.addView(v,lp(parent.getContext(),1,height));}
    static LinearLayout row(Context c){LinearLayout l=new LinearLayout(c);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private Ui(){}
}
