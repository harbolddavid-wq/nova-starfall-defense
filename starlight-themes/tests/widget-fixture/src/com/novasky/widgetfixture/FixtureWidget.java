package com.novasky.widgetfixture;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Build;
import android.appwidget.AppWidgetProviderInfo;
import android.widget.RemoteViews;

/** A test-only provider; never packaged in the launcher APK. */
public final class FixtureWidget extends AppWidgetProvider {
    @Override public void onReceive(Context context,Intent intent) {
        if("com.novasky.widgetfixture.SET_PREVIEW".equals(intent.getAction()) && Build.VERSION.SDK_INT>=35){
            int layout=context.getResources().getIdentifier("preview_generated","layout",context.getPackageName());
            AppWidgetManager.getInstance(context).setWidgetPreview(new ComponentName(context,FixtureWidget.class),AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN,new RemoteViews(context.getPackageName(),layout));
            return;
        }
        super.onReceive(context,intent);
    }
    static void update(Context context, AppWidgetManager manager, int id) {
        int layout=context.getResources().getIdentifier("widget","layout",context.getPackageName());
        manager.updateAppWidget(id,new RemoteViews(context.getPackageName(),layout));
    }
    @Override public void onUpdate(Context context,AppWidgetManager manager,int[] ids) {
        for(int id:ids)update(context,manager,id);
    }
}
