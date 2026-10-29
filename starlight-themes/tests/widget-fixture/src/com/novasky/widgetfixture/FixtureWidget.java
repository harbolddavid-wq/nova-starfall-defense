package com.novasky.widgetfixture;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.widget.RemoteViews;

/** A test-only provider; never packaged in the launcher APK. */
public final class FixtureWidget extends AppWidgetProvider {
    static void update(Context context, AppWidgetManager manager, int id) {
        int layout=context.getResources().getIdentifier("widget","layout",context.getPackageName());
        manager.updateAppWidget(id,new RemoteViews(context.getPackageName(),layout));
    }
    @Override public void onUpdate(Context context,AppWidgetManager manager,int[] ids) {
        for(int id:ids)update(context,manager,id);
    }
}
