package com.novasky.starlightthemes;

import java.util.Locale;

/** Conservative widget discovery hints; the provider remains the source of descriptions. */
public final class WidgetFinder {
    private WidgetFinder() {}

    private static String lower(String value) {
        return value==null ? "" : value.toLowerCase(Locale.ROOT);
    }

    public static boolean isAudio(boolean appSaysAudio,String packageName,String appName,String label,String description) {
        if(appSaysAudio)return true;
        String all=lower(packageName)+" "+lower(appName)+" "+lower(label)+" "+lower(description);
        for(String term:new String[]{"music","spotify","soundcloud","podcast","pandora","deezer",
                "siriusxm","iheartradio","tunein","tidal","shazam","audio player","music player"})
            if(all.contains(term))return true;
        return false;
    }

    public static boolean matches(String query,String packageName,String appName,String label,String description,boolean audio) {
        String q=lower(query).trim();
        if(q.isEmpty())return true;
        if(audio && (q.equals("music") || q.equals("audio")))return true;
        return (lower(packageName)+" "+lower(appName)+" "+lower(label)+" "+lower(description)).contains(q);
    }
}
