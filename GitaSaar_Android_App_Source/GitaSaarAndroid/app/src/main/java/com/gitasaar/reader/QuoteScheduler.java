package com.gitasaar.reader;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.List;
import java.time.LocalDate;

public final class QuoteScheduler {
    private QuoteScheduler() { }
    public static final String PREFS="gita_saar";
    public static final String ENABLED="daily_notifications_enabled";

    // Selected teaching-oriented verses; daily messages avoid narrative-only verses.
    private static final int[][] FEATURED = {
        {2,14},{2,20},{2,47},{2,48},{2,50},{2,55},{2,70},
        {3,8},{3,19},{3,30},{4,7},{4,8},{4,38},{5,10},
        {6,5},{6,6},{6,26},{6,35},{7,7},{9,22},{9,27},
        {10,10},{12,13},{12,14},{12,15},{16,1},{18,66}
    };
    public static GitaVerse todayVerse(List<GitaVerse> verses) {
        if(verses.isEmpty())return null;
        int offset=dailyIndex(FEATURED.length);
        for(int i=0;i<FEATURED.length;i++) {
            int[] target=FEATURED[(offset+i)%FEATURED.length];
            for(GitaVerse v:verses)if(v.chapter==target[0]&&v.number==target[1])return v;
        }
        return verses.get(0);
    }
    public static void saveQuotes(Context context, List<GitaVerse> verses) {
        try {
            JSONArray array=new JSONArray();
            for(int[] ref:FEATURED) {
                GitaVerse item=null;
                for(GitaVerse verse:verses) if(verse.chapter==ref[0]&&verse.number==ref[1]) {item=verse; break;}
                if(item==null)continue;
                JSONObject row=new JSONObject();
                row.put("reference",item.reference());
                row.put("hindi",trim(item.hindi,150));
                row.put("sanskrit",trim(item.sanskrit,160));
                array.put(row);
            }
            try(FileOutputStream output=new FileOutputStream(new File(context.getFilesDir(),"gita_quote_index.json"))) {
                output.write(array.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch(Exception ignored) { }
    }
    public static int dailyIndex(int size) {
        long today=LocalDate.now().toEpochDay();
        return size == 0 ? 0 : (int)Math.floorMod(today, size);
    }
    private static String trim(String text,int len) {
        if(text==null)return "";
        return text.length()>len ? text.substring(0,len)+"…" : text;
    }
    public static void schedule(Context context) {
        if(!context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getBoolean(ENABLED,false))return;
        Intent intent=new Intent(context,QuoteReceiver.class);
        PendingIntent pi=PendingIntent.getBroadcast(context,101,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Calendar c=Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY,8);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);
        if(c.getTimeInMillis()<=System.currentTimeMillis())c.add(Calendar.DAY_OF_YEAR,1);
        AlarmManager alarm=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        if(alarm!=null) {
            if(Build.VERSION.SDK_INT>=23)alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,c.getTimeInMillis(),pi);
            else alarm.set(AlarmManager.RTC_WAKEUP,c.getTimeInMillis(),pi);
        }
    }
    public static void cancel(Context context) {
        Intent intent=new Intent(context,QuoteReceiver.class);
        PendingIntent pi=PendingIntent.getBroadcast(context,101,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        AlarmManager alarm=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        if(alarm!=null)alarm.cancel(pi);
    }
}
