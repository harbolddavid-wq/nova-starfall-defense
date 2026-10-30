package com.novasky.widgetfixture;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** A predictable configuration screen for exercising system result handling. */
public final class ConfigActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        final int id=getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,AppWidgetManager.INVALID_APPWIDGET_ID);
        if(id==AppWidgetManager.INVALID_APPWIDGET_ID)FixtureWidget.publishPreview(this);
        setResult(RESULT_CANCELED);
        LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);layout.setGravity(Gravity.CENTER);
        TextView title=new TextView(this);title.setText("Starlight QA widget configuration");layout.addView(title);
        Button ok=new Button(this);ok.setText("Finish setup");layout.addView(ok);
        Button cancel=new Button(this);cancel.setText("Cancel setup");layout.addView(cancel);
        ok.setOnClickListener(v->{
            if(id!=AppWidgetManager.INVALID_APPWIDGET_ID) {
                FixtureWidget.update(this,AppWidgetManager.getInstance(this),id);
                setResult(RESULT_OK,new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id));
            }
            finish();
        });
        cancel.setOnClickListener(v->finish());
        setContentView(layout);
    }
}
