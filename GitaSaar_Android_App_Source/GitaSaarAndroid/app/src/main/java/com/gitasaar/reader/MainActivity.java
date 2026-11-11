package com.gitasaar.reader;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Gita Saar — online-required, chapter-based Bhagavad Gita reading experience.
 *  Google AdMob TEST units only; supply real IDs and complete consent setup before release. */
public final class MainActivity extends Activity {
    private static final int BG=Color.rgb(255,250,241), WHITE=Color.WHITE;
    private static final int INK=Color.rgb(67,42,30), MUTED=Color.rgb(131,106,88);
    private static final int SAFFRON=Color.rgb(216,101,30), GOLD=Color.rgb(248,177,75);
    private static final int LIGHT=Color.rgb(255,237,211), BORDER=Color.rgb(241,224,204);
    private static final String TEST_BANNER="ca-app-pub-3940256099942544/9214589741";
    private static final String[] NAMES={
        "अर्जुनविषादयोग","सांख्ययोग","कर्मयोग","ज्ञानकर्मसन्यासयोग","कर्मसन्यासयोग",
        "ध्यानयोग","ज्ञानविज्ञानयोग","अक्षरब्रह्मयोग","राजविद्याराजगुह्ययोग","विभूतियोग",
        "विश्वरूपदर्शनयोग","भक्तियोग","क्षेत्रक्षेत्रज्ञविभागयोग","गुणत्रयविभागयोग",
        "पुरुषोत्तमयोग","दैवासुरसम्पद्विभागयोग","श्रद्धात्रयविभागयोग","मोक्षसन्यासयोग"};
    private static final int[] COUNTS={47,72,43,42,29,47,30,28,34,42,55,20,34,27,20,24,28,78};

    private enum Screen{ HOME, CHAPTERS, CHAPTER, VERSE, SEARCH, SAVED, SETTINGS }
    private Screen current=Screen.HOME;
    private int selectedChapter=1;
    private GitaVerse selectedVerse;
    private LinearLayout root, content, body;
    private FrameLayout bodyFrame;
    private AdView banner;
    private GitaRepository repository;
    private SharedPreferences prefs;
    private ConnectivityManager networkManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private boolean loading, hasConnectivity, adsStarted;
    private String errorMessage;
    private ConsentInformation consent;
    private Set<String> bookmarks=new HashSet<>();

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        if(Build.VERSION.SDK_INT>=26) getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        prefs=getSharedPreferences(QuoteScheduler.PREFS,MODE_PRIVATE);
        bookmarks=new HashSet<>(prefs.getStringSet("bookmarks",new HashSet<>()));
        repository=new GitaRepository(this);
        networkManager=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
        createRoot();
        QuoteScheduler.schedule(this);
        registerNetworkWatcher();
        refreshConnection();
    }
    @Override protected void onResume(){super.onResume();if(root!=null) refreshConnection();}
    @Override protected void onDestroy(){
        if(networkCallback!=null&&networkManager!=null) {
            try{networkManager.unregisterNetworkCallback(networkCallback);}catch(Exception ignored){}
        }
        if(banner!=null)banner.destroy();
        super.onDestroy();
    }
    private void createRoot() {
        root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);
        setContentView(root);
        if(Build.VERSION.SDK_INT>=30){
            getWindow().setDecorFitsSystemWindows(false);
            root.setOnApplyWindowInsetsListener((v,insets)->{
                android.graphics.Insets i=insets.getInsets(WindowInsets.Type.systemBars());
                root.setPadding(0,i.top,0,i.bottom);return insets;
            });
        }
    }
    private boolean isOnline(){
        if(networkManager==null)return false;
        try{
            Network active=networkManager.getActiveNetwork();
            NetworkCapabilities c=networkManager.getNetworkCapabilities(active);
            return c!=null && c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        }catch(Exception e){return false;}
    }
    private void registerNetworkWatcher(){
        if(networkManager==null)return;
        networkCallback=new ConnectivityManager.NetworkCallback(){
            @Override public void onAvailable(Network n){runOnUiThread(()->refreshConnection());}
            @Override public void onLost(Network n){runOnUiThread(()->refreshConnection());}
            @Override public void onCapabilitiesChanged(Network n,NetworkCapabilities c){runOnUiThread(()->refreshConnection());}
        };
        try{networkManager.registerDefaultNetworkCallback(networkCallback);}catch(Exception ignored){}
    }
    private void refreshConnection(){
        boolean online=isOnline();
        if(!online){
            hasConnectivity=false;
            showOffline();
            return;
        }
        if(!hasConnectivity){
            hasConnectivity=true;
            render();
            if(repository.getAll().isEmpty()&&!loading)loadGita();
            if(!adsStarted)initializeAds();
        }
    }
    private void loadGita(){
        loading=true; errorMessage=null;render();
        repository.load((verses,error)->{
            loading=false;
            errorMessage=error;
            if(isOnline())render();else showOffline();
        });
    }

    private LinearLayout stack(){
        LinearLayout l=new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }
    private LinearLayout line(){
        LinearLayout l=new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }
    private int dp(float size){return (int)(getResources().getDisplayMetrics().density*size+0.5f);}
    private GradientDrawable shape(int fill,float radius){
        GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(radius));return d;
    }
    private GradientDrawable bordered(int fill,float radius,int stroke){
        GradientDrawable d=shape(fill,radius);d.setStroke(dp(1),stroke);return d;
    }
    private GradientDrawable gradient(float radius){
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{Color.rgb(255,226,183),Color.rgb(253,195,125),Color.rgb(237,131,51)});
        d.setCornerRadius(dp(radius));return d;
    }
    private TextView text(String value,float size,int color,boolean bold){
        TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(color);
        v.setIncludeFontPadding(true);v.setLineSpacing(dp(2),1.1f);
        if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return v;
    }
    private void topPad(View v,int left,int top,int right,int bottom){v.setPadding(dp(left),dp(top),dp(right),dp(bottom));}
    private LinearLayout.LayoutParams lp(int w,int h){return new LinearLayout.LayoutParams(w<0?w:dp(w),h<0?h:dp(h));}
    private LinearLayout.LayoutParams gap(int top){
        LinearLayout.LayoutParams p=lp(-1,-2);p.topMargin=dp(top);return p;
    }
    private void titleBar(String title,String subtitle, boolean back){
        LinearLayout head=line();topPad(head,20,12,18,14);head.setBackgroundColor(BG);
        if(back){
            TextView b=text("‹",34,SAFFRON,true);b.setGravity(Gravity.CENTER);
            head.addView(b,lp(42,46));b.setOnClickListener(v->goBack());
        }
        LinearLayout t=stack();
        TextView name=text(title,22,INK,true);t.addView(name);
        if(subtitle!=null&&!subtitle.isEmpty()){
            TextView detail=text(subtitle,12,MUTED,false);t.addView(detail);
        }
        LinearLayout.LayoutParams pt=new LinearLayout.LayoutParams(0,-2,1);
        head.addView(t,pt);
        TextView om=text("ॐ",28,SAFFRON,true);om.setGravity(Gravity.CENTER);
        head.addView(om,lp(44,44));root.addView(head,lp(-1,-2));
    }
    private void nav(){
        LinearLayout menu=line();topPad(menu,9,7,9,9);
        menu.setBackground(bordered(WHITE,0,BORDER));
        String[] names={"आज","अध्याय","खोजें","सेव","सेटिंग"};
        String[] icons={"✦","☷","⌕","♥","⚙"};
        Screen[] screens={Screen.HOME,Screen.CHAPTERS,Screen.SEARCH,Screen.SAVED,Screen.SETTINGS};
        for(int i=0;i<names.length;i++){
            int idx=i;Screen s=screens[i];
            LinearLayout item=stack();item.setGravity(Gravity.CENTER);
            boolean active=current==s || (current==Screen.CHAPTER&&s==Screen.CHAPTERS);
            TextView ic=text(icons[i],22,active?SAFFRON:MUTED,false);ic.setGravity(Gravity.CENTER);
            TextView label=text(names[i],11,active?SAFFRON:MUTED,active);label.setGravity(Gravity.CENTER);
            item.addView(ic);item.addView(label);
            menu.addView(item,new LinearLayout.LayoutParams(0,dp(50),1));
            item.setOnClickListener(v->{current=s;render();});
        }
        root.addView(menu,lp(-1,-2));
    }
    private void showOffline(){
        root.removeAllViews();
        LinearLayout holder=stack();holder.setGravity(Gravity.CENTER);topPad(holder,24,18,24,18);
        TextView glyph=text("ॐ",66,SAFFRON,true);glyph.setGravity(Gravity.CENTER);
        holder.addView(glyph,lp(-1,-2));
        TextView heading=text("इंटरनेट कनेक्शन चाहिए",25,INK,true);heading.setGravity(Gravity.CENTER);
        holder.addView(heading,gap(20));
        TextView detail=text("गीता सार सिर्फ इंटरनेट चालू होने पर खुलेगा। मोबाइल डेटा या Wi-Fi ऑन करें और दोबारा कोशिश करें।",16,MUTED,false);
        detail.setGravity(Gravity.CENTER);holder.addView(detail,gap(12));
        TextView retry=button("फिर से कोशिश करें",true);holder.addView(retry,gap(24));
        retry.setOnClickListener(v->refreshConnection());
        root.addView(holder,new LinearLayout.LayoutParams(-1,0,1));
        TextView bottom=text("श्रीमद्भगवद्गीता • हर दिन एक नई सीख",12,MUTED,false);
        bottom.setGravity(Gravity.CENTER);root.addView(bottom,lp(-1,52));
    }
    private void render(){
        if(!isOnline()){showOffline();return;}
        root.removeAllViews();
        boolean goBack=current==Screen.CHAPTER||current==Screen.VERSE;
        String title="गीता सार",subtitle="श्रीमद्भगवद्गीता";
        switch(current){
            case CHAPTERS: title="18 अध्याय";subtitle="संपूर्ण भगवद्गीता";break;
            case CHAPTER: title="अध्याय "+selectedChapter;subtitle=NAMES[selectedChapter-1];break;
            case VERSE: title=selectedVerse==null?"श्लोक":selectedVerse.reference();subtitle="श्लोक और आसान हिंदी अर्थ";break;
            case SEARCH: title="श्लोक खोजें";subtitle="शब्द या 2:47 लिखकर खोजें";break;
            case SAVED: title="मेरे पसंदीदा श्लोक";subtitle="आपके सेव किए हुए श्लोक";break;
            case SETTINGS: title="सेटिंग्स";subtitle="नोटिफिकेशन और जानकारी";break;
            default:break;
        }
        titleBar(title,subtitle,goBack);
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);scroll.setClipToPadding(false);
        body=stack();topPad(body,18,8,18,24);scroll.addView(body);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        if(loading){renderLoading();}
        else if(errorMessage!=null){renderError();}
        else if(repository.getAll().isEmpty()){renderLoading();}
        else switch(current){
            case HOME:showHome();break;
            case CHAPTERS:showChapters();break;
            case CHAPTER:showChapter();break;
            case VERSE:showVerse();break;
            case SEARCH:showSearch();break;
            case SAVED:showSaved();break;
            case SETTINGS:showSettings();break;
        }
        if(banner!=null&&banner.getParent()!=null)((ViewGroup)banner.getParent()).removeView(banner);
        if(banner!=null)root.addView(banner,lp(-1,50));
        nav();
    }
    private TextView button(String text,boolean filled){
        TextView v=text(text,15,filled?WHITE:SAFFRON,true);
        v.setGravity(Gravity.CENTER);topPad(v,18,12,18,12);
        v.setBackground(filled?shape(SAFFRON,14):bordered(WHITE,14,BORDER));
        return v;
    }
    private LinearLayout card(){
        LinearLayout c=stack();topPad(c,17,17,17,17);
        c.setBackground(bordered(WHITE,20,BORDER));return c;
    }
    private void renderLoading(){
        LinearLayout splash=stack();splash.setGravity(Gravity.CENTER);
        TextView icon=text("ॐ",64,SAFFRON,true);icon.setGravity(Gravity.CENTER);splash.addView(icon);
        TextView msg=text("गीता के 700 श्लोक लोड हो रहे हैं…",17,INK,true);
        msg.setGravity(Gravity.CENTER);splash.addView(msg,gap(18));
        TextView hint=text("पहली बार इंटरनेट से पूरा डेटा डाउनलोड होगा।",13,MUTED,false);
        hint.setGravity(Gravity.CENTER);splash.addView(hint,gap(9));
        body.addView(splash,new LinearLayout.LayoutParams(-1,dp(350)));
    }
    private void renderError(){
        LinearLayout c=card();
        c.addView(text("गीता डेटा अभी नहीं खुल सका",21,INK,true));
        c.addView(text(errorMessage==null?"नेटवर्क की समस्या है":errorMessage,14,MUTED,false),gap(10));
        TextView retry=button("दोबारा लोड करें",true);c.addView(retry,gap(18));
        retry.setOnClickListener(v->loadGita());body.addView(c,gap(25));
    }
    private void showHome(){
        LinearLayout hero=stack();topPad(hero,20,23,20,22);hero.setBackground(gradient(23));
        TextView prefix=text("॥ श्रीमद्भगवद्गीता ॥",16,INK,true);hero.addView(prefix);
        hero.addView(text("हर दिन गीता से\nएक नई सीख",29,INK,true),gap(12));
        hero.addView(text("18 अध्याय • 700 श्लोक • सरल हिंदी अर्थ",13,INK,false),gap(8));
        TextView explore=button("गीता पढ़ना शुरू करें  →",true);
        LinearLayout.LayoutParams px=gap(18);hero.addView(explore,px);
        explore.setOnClickListener(v->{current=Screen.CHAPTERS;render();});
        body.addView(hero);
        GitaVerse today=QuoteScheduler.todayVerse(repository.getAll());
        if(today!=null){
            LinearLayout quote=card();
            TextView eyebrow=text("✦  आज का गीता संदेश",15,SAFFRON,true);quote.addView(eyebrow);
            quote.addView(text(today.reference(),12,MUTED,false),gap(5));
            quote.addView(text(today.sanskrit,19,INK,true),gap(17));
            quote.addView(text(today.hindi,15,MUTED,false),gap(13));
            quote.addView(verseActions(today),gap(20));
            body.addView(quote,gap(17));
            TextView open=button("पूरा श्लोक पढ़ें",false);body.addView(open,gap(12));
            open.setOnClickListener(v->{selectedVerse=today;current=Screen.VERSE;render();});
        }
        if(!prefs.getBoolean(QuoteScheduler.ENABLED,false)) {
            LinearLayout reminders=card();
            reminders.addView(text("🔔  सुबह गीता का संदेश पाएँ",17,INK,true));
            reminders.addView(text("हर दिन लगभग 8 बजे एक नया श्लोक और हिंदी अर्थ।",13,MUTED,false),gap(6));
            TextView activate=button("Daily Notification चालू करें",true);
            reminders.addView(activate,gap(12));
            activate.setOnClickListener(v->enableNotifications());
            body.addView(reminders,gap(16));
        }
        TextView chapterLabel=text("अध्याय पढ़ें",21,INK,true);body.addView(chapterLabel,gap(27));
        for(int chapter=1;chapter<=3;chapter++) addChapterCard(chapter);
        TextView all=button("सभी 18 अध्याय देखें  →",false);body.addView(all,gap(12));
        all.setOnClickListener(v->{current=Screen.CHAPTERS;render();});
    }
    private void addChapterCard(int chapter){
        LinearLayout card=card();LinearLayout row=line();
        TextView badge=text(String.format(Locale.US,"%02d",chapter),21,SAFFRON,true);
        badge.setGravity(Gravity.CENTER);badge.setBackground(shape(LIGHT,13));row.addView(badge,lp(57,57));
        LinearLayout info=stack();topPad(info,12,0,4,0);
        info.addView(text("अध्याय " +chapter,13,MUTED,false));
        info.addView(text(NAMES[chapter-1],16,INK,true),gap(3));
        info.addView(text(COUNTS[chapter-1]+" श्लोक",12,MUTED,false),gap(3));
        row.addView(info,new LinearLayout.LayoutParams(0,-2,1));
        TextView arrow=text("›",29,SAFFRON,true);row.addView(arrow);
        card.addView(row);body.addView(card,gap(11));
        card.setOnClickListener(v->{selectedChapter=chapter;current=Screen.CHAPTER;render();});
    }
    private void showChapters(){
        TextView label=text("कुरुक्षेत्र से जीवन की सीख तक",18,INK,true);body.addView(label,gap(5));
        body.addView(text("अपनी पसंद का अध्याय चुनें और एक-एक श्लोक हिंदी अर्थ के साथ पढ़ें।",14,MUTED,false),gap(6));
        for(int ch=1;ch<=18;ch++)addChapterCard(ch);
    }
    private void showChapter(){
        LinearLayout head=stack();topPad(head,18,15,18,15);head.setBackground(shape(LIGHT,17));
        head.addView(text("अध्याय "+selectedChapter+" · "+NAMES[selectedChapter-1],19,INK,true));
        head.addView(text(COUNTS[selectedChapter-1]+" श्लोक",13,MUTED,false),gap(4));
        body.addView(head);
        int count=0;
        for(GitaVerse verse:repository.getAll()){
            if(verse.chapter!=selectedChapter)continue;
            count++;
            LinearLayout item=card();
            item.addView(text(verse.reference(),13,SAFFRON,true));
            TextView sans=text(cut(verse.sanskrit,120),16,INK,true);item.addView(sans,gap(9));
            item.addView(text(cut(verse.hindi,135),13,MUTED,false),gap(7));
            TextView action=text("पूरा पढ़ें  ›",13,SAFFRON,true);item.addView(action,gap(11));
            body.addView(item,gap(12));
            item.setOnClickListener(v->{selectedVerse=verse;current=Screen.VERSE;render();});
        }
        if(count==0)body.addView(text("इस अध्याय के श्लोक अभी उपलब्ध नहीं हैं।",14,MUTED,false),gap(15));
    }
    private String cut(String s,int n){if(s==null)return "";return s.length()>n?s.substring(0,n)+"…":s;}
    private void showVerse(){
        if(selectedVerse==null)return;
        LinearLayout s=card();
        s.addView(text("श्रीकृष्ण का संदेश",14,SAFFRON,true));
        s.addView(text(selectedVerse.reference(),13,MUTED,false),gap(3));
        s.addView(text(selectedVerse.sanskrit,22,INK,true),gap(23));
        View hr=new View(this);hr.setBackgroundColor(BORDER);
        s.addView(hr,gap(20));s.addView(text("आसान हिंदी अर्थ",16,SAFFRON,true),gap(20));
        s.addView(text(selectedVerse.hindi,17,INK,false),gap(11));
        s.addView(verseActions(selectedVerse),gap(27));body.addView(s,gap(8));
        TextView chapter=button("अध्याय "+selectedVerse.chapter+" के बाकी श्लोक",false);
        body.addView(chapter,gap(15));
        chapter.setOnClickListener(v->{selectedChapter=selectedVerse.chapter;current=Screen.CHAPTER;render();});
    }
    private LinearLayout verseActions(GitaVerse v){
        LinearLayout row=line();
        TextView save=button(bookmarks.contains(v.id())?"♥  सेव हो गया":"♡  सेव करें",false);
        LinearLayout.LayoutParams a=new LinearLayout.LayoutParams(0,-2,1);a.rightMargin=dp(9);row.addView(save,a);
        save.setOnClickListener(view->{
            if(bookmarks.contains(v.id()))bookmarks.remove(v.id());else bookmarks.add(v.id());
            prefs.edit().putStringSet("bookmarks",new HashSet<>(bookmarks)).apply();
            save.setText(bookmarks.contains(v.id())?"♥  सेव हो गया":"♡  सेव करें");
        });
        TextView share=button("↗  शेयर",false);row.addView(share,new LinearLayout.LayoutParams(0,-2,1));
        share.setOnClickListener(view->{
            Intent intent=new Intent(Intent.ACTION_SEND);intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT,v.reference()+"\n\n"+v.sanskrit+"\n\nहिंदी अर्थ:\n"+v.hindi+"\n\nगीता सार");
            startActivity(Intent.createChooser(intent,"श्लोक शेयर करें"));
        });
        return row;
    }
    private void showSearch(){
        EditText search=new EditText(this);search.setSingleLine(true);
        search.setTextSize(16);search.setTextColor(INK);search.setHintTextColor(MUTED);
        search.setHint("शब्द, अर्थ या 2:47 लिखें");topPad(search,16,13,16,13);
        search.setBackground(bordered(WHITE,15,BORDER));body.addView(search);
        TextView description=text("पूरी गीता में संस्कृत और हिंदी अर्थ से खोजें।",13,MUTED,false);
        body.addView(description,gap(12));
        LinearLayout results=stack();body.addView(results,gap(12));
        TextWatcher watcher=new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int st,int c,int af){}
            public void onTextChanged(CharSequence s,int st,int before,int count){showResults(results,s.toString());}
            public void afterTextChanged(Editable e){}
        };
        search.addTextChangedListener(watcher);
        showResults(results,"");
    }
    private void showResults(LinearLayout target,String value){
        target.removeAllViews();String q=value.trim().toLowerCase(Locale.ROOT);
        if(q.isEmpty()){
            target.addView(text("उदाहरण: कर्म, मन, योग, 2:47",15,MUTED,false),gap(12));return;
        }
        int shown=0,total=0;
        for(GitaVerse verse:repository.getAll()){
            boolean matches=verse.sanskrit.toLowerCase(Locale.ROOT).contains(q)
              ||verse.hindi.toLowerCase(Locale.ROOT).contains(q)
              ||(verse.chapter+":"+verse.number).equals(q)
              ||(verse.chapter+"-"+verse.number).equals(q);
            if(!matches)continue;
            total++;
            if(shown++>=60)continue;
            LinearLayout item=card();
            item.addView(text(verse.reference(),13,SAFFRON,true));
            item.addView(text(cut(verse.sanskrit,100),16,INK,true),gap(5));
            item.addView(text(cut(verse.hindi,110),13,MUTED,false),gap(6));
            target.addView(item,gap(10));
            item.setOnClickListener(v->{selectedVerse=verse;current=Screen.VERSE;render();});
        }
        TextView count=text(total==0?"कोई श्लोक नहीं मिला":total+" परिणाम"+(total>60?" · पहले 60 दिख रहे हैं":""),13,MUTED,false);
        target.addView(count,0,gap(5));
    }
    private void showSaved(){
        if(bookmarks.isEmpty()){
            LinearLayout empty=card();
            empty.addView(text("♡",52,SAFFRON,false));
            empty.addView(text("अभी कोई श्लोक सेव नहीं है",20,INK,true),gap(12));
            empty.addView(text("किसी भी श्लोक पर ‘सेव करें’ दबाएँ। वह यहाँ दिखाई देगा।",14,MUTED,false),gap(6));
            body.addView(empty,gap(17));return;
        }
        for(GitaVerse verse:repository.getAll()){
            if(!bookmarks.contains(verse.id()))continue;
            LinearLayout item=card();item.addView(text(verse.reference(),13,SAFFRON,true));
            item.addView(text(cut(verse.sanskrit,120),17,INK,true),gap(7));
            item.addView(text(cut(verse.hindi,150),13,MUTED,false),gap(8));
            body.addView(item,gap(11));
            item.setOnClickListener(v->{selectedVerse=verse;current=Screen.VERSE;render();});
        }
    }
    private void showSettings(){
        LinearLayout notif=card();
        notif.addView(text("🔔  रोज़ का गीता संदेश",19,INK,true));
        notif.addView(text("हर सुबह लगभग 8 बजे श्लोक और उसका हिंदी अर्थ नोटिफिकेशन में पाएँ। समय फोन की बैटरी सेटिंग पर निर्भर है।",14,MUTED,false),gap(9));
        boolean enabled=prefs.getBoolean(QuoteScheduler.ENABLED,false);
        TextView toggle=button(enabled?"नोटिफिकेशन बंद करें":"नोटिफिकेशन चालू करें",true);
        notif.addView(toggle,gap(16));body.addView(notif);
        toggle.setOnClickListener(v->{
            if(prefs.getBoolean(QuoteScheduler.ENABLED,false)){
                prefs.edit().putBoolean(QuoteScheduler.ENABLED,false).apply();QuoteScheduler.cancel(this);render();
            }else enableNotifications();
        });
        LinearLayout priv=card();
        priv.addView(text("डेटा और प्राइवेसी",18,INK,true));
        priv.addView(text("गीता के श्लोक HTTPS से डाउनलोड होकर फोन में cache होते हैं। App पढ़ने के लिए इंटरनेट चाहिए। सेव किए श्लोक आपके फोन में रहते हैं। Ads दिखाने के लिए Google AdMob इस्तेमाल होता है।",14,MUTED,false),gap(9));
        TextView privacy=button("Ad privacy options",false);priv.addView(privacy,gap(14));
        privacy.setOnClickListener(v->{
            if(consent!=null && consent.getPrivacyOptionsRequirementStatus()==ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED){
                UserMessagingPlatform.showPrivacyOptionsForm(this,e->{if(e!=null)toast("Privacy form unavailable");});
            }else toast("इस क्षेत्र के लिए privacy form की ज़रूरत नहीं है।");
        });
        body.addView(priv,gap(14));
        LinearLayout credits=card();credits.addView(text("श्लोक स्रोत",18,INK,true));
        credits.addView(text("Bhagavad Gita Dataset, Adiyogi Arts (MIT License). मूल संस्कृत श्लोक प्राचीन ग्रंथ हैं। हिंदी अर्थ स्रोत के अनुसार हैं।",14,MUTED,false),gap(9));
        body.addView(credits,gap(14));
    }
    private void enableNotifications(){
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},51);return;
        }
        prefs.edit().putBoolean(QuoteScheduler.ENABLED,true).apply();
        QuoteScheduler.schedule(this);toast("सुबह 8 बजे के लिए गीता संदेश चालू किया गया।");render();
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results){
        super.onRequestPermissionsResult(code,permissions,results);
        if(code==51 && results.length>0 && results[0]==PackageManager.PERMISSION_GRANTED)enableNotifications();
        else if(code==51)toast("नोटिफिकेशन की अनुमति नहीं मिली।");
    }
    private void initializeAds(){
        adsStarted=true;
        consent=UserMessagingPlatform.getConsentInformation(this);
        ConsentRequestParameters params=new ConsentRequestParameters.Builder().build();
        consent.requestConsentInfoUpdate(this,params,()->{
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(this,e->{
                if(consent.canRequestAds())loadBanner();
            });
            if(consent.canRequestAds())loadBanner();
        },e->{if(consent.canRequestAds())loadBanner();});
    }
    private void loadBanner(){
        if(banner!=null||isFinishing()||isDestroyed())return;
        MobileAds.initialize(this,status->{});
        banner=new AdView(this);
        banner.setAdSize(AdSize.BANNER);
        banner.setAdUnitId(TEST_BANNER);
        banner.loadAd(new AdRequest.Builder().build());
        if(isOnline())render();
    }
    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_SHORT).show();}
    private void goBack(){
        if(current==Screen.VERSE&&selectedVerse!=null){selectedChapter=selectedVerse.chapter;current=Screen.CHAPTER;}
        else if(current==Screen.CHAPTER)current=Screen.CHAPTERS;
        else current=Screen.HOME;
        render();
    }
    @Override @Deprecated public void onBackPressed(){
        if(current!=Screen.HOME){goBack();return;}
        super.onBackPressed();
    }
}
