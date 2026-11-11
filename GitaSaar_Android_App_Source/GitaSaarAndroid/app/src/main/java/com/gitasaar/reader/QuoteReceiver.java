package com.gitasaar.reader;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.AsyncTask;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;

public final class QuoteReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent) {
        if(!context.getSharedPreferences(QuoteScheduler.PREFS,Context.MODE_PRIVATE)
            .getBoolean(QuoteScheduler.ENABLED,false))return;
        // Always schedule next morning even if notification permission is off.
        QuoteScheduler.schedule(context);
        PendingResult pending=goAsync();
        new Thread(()->{
            try{ showNotification(context); }
            finally{ pending.finish(); }
        },"gita-notify").start();
    }
    private void showNotification(Context context) {
        if(Build.VERSION.SDK_INT>=33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;
        String title="आज का गीता संदेश";
        String body="आज का श्लोक पढ़ने के लिए गीता सार खोलें।";
        try {
            File file=new File(context.getFilesDir(),"gita_quote_index.json");
            if(file.isFile()) {
                byte[] bytes=new byte[(int)file.length()];
                try(FileInputStream in=new FileInputStream(file)) {
                    int read=0,n;while(read<bytes.length&&(n=in.read(bytes,read,bytes.length-read))>0)read+=n;
                }
                JSONArray quotes=new JSONArray(new String(bytes,StandardCharsets.UTF_8));
                if(quotes.length()>0){
                    JSONObject quote=quotes.optJSONObject(QuoteScheduler.dailyIndex(quotes.length()));
                    if(quote!=null){
                        title="गीता संदेश · "+quote.optString("reference","");
                        body=quote.optString("hindi",body);
                    }
                }
            }
        }catch(Exception ignored) { }
        NotificationManager manager=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        if(manager==null)return;
        String channel="gita_daily_quotes";
        if(Build.VERSION.SDK_INT>=26)manager.createNotificationChannel(new NotificationChannel(
            channel,"रोज़ का गीता संदेश",NotificationManager.IMPORTANCE_DEFAULT));
        Intent open=new Intent(context,MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi=PendingIntent.getActivity(context,102,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        android.app.Notification.Builder builder= Build.VERSION.SDK_INT>=26
            ?new android.app.Notification.Builder(context,channel)
            :new android.app.Notification.Builder(context);
        builder.setContentTitle(title).setContentText(body)
            .setStyle(new android.app.Notification.BigTextStyle().bigText(body))
            .setSmallIcon(R.drawable.ic_notification).setContentIntent(pi)
            .setAutoCancel(true).setShowWhen(true);
        manager.notify(501,builder.build());
    }
}
