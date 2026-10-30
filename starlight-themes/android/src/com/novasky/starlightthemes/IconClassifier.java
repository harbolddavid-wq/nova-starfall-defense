package com.novasky.starlightthemes;

import java.util.Locale;

/** Select an optional illustrated icon without changing the app's launch identity. */
public final class IconClassifier {
    private IconClassifier() {}
    public static String classify(String packageName,String label) {
        String p=packageName==null?"":packageName.toLowerCase(Locale.ROOT);
        String n=label==null?"":label.toLowerCase(Locale.ROOT).trim();
        String all=p+" "+n;
        if(all.contains("starlight") && (all.contains("dream") || all.contains("draw")))return "dream";
        if(all.contains("starlight") && all.contains("atlas"))return "atlas";
        if(all.contains("starlight") && all.contains("notes"))return "notes";
        if(has(all,"camera","camcorder"))return "camera";
        if(has(all,"calendar","calendario"))return "calendar";
        if(has(all,"clock","alarm","timer"))return "clock";
        if(has(all,"weather","forecast"))return "weather";
        if(has(all,"settings","preferences"))return "settings";
        if(has(all,"messaging","messages","sms","whatsapp","messenger","telegram","gmail","email","mailbox"))return "messages";
        if(has(all,"dialer","telephone") || n.equals("phone") || n.startsWith("phone "))return "phone";
        if(has(all,"browser","chrome","firefox","sbrowser","opera","brave"))return "browser";
        if(has(all,"gallery","photos","photo gallery"))return "gallery";
        if(has(all,"spotify","music","audio player","soundcloud"))return "music";
        if(has(all,"youtube","netflix","tiktok","video","twitch"))return "video";
        if(has(all,"reader","kindle","ebook","books","pdf"))return "reader";
        if(has(all,"notepad","notes","memo"))return "notes";
        if(has(all,"files","file manager","documents","downloads"))return "files";
        if(has(all,"search"))return "search";
        return "frame";
    }
    private static boolean has(String text,String... words) {
        for(String word:words)if(text.contains(word))return true;
        return false;
    }
}
