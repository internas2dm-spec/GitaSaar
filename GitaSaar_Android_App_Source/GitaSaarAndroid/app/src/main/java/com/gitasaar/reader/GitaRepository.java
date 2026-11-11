package com.gitasaar.reader;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Downloads the open-source Bhagavad Gita, including Hindi meanings, over HTTPS.
 *  Data is cached to improve performance, but MainActivity STILL requires internet to open.
 *  Source: https://github.com/AdiyogiArts/gita-json (MIT). */
public final class GitaRepository {
    public interface Callback { void complete(List<GitaVerse> verses, String error); }
    private static final String[][] DATA_URLS = {
        {"https://cdn.jsdelivr.net/gh/AdiyogiArts/gita-json@main/base.json",
         "https://raw.githubusercontent.com/AdiyogiArts/gita-json/main/base.json"},
        {"https://cdn.jsdelivr.net/gh/AdiyogiArts/gita-json@main/hi.json",
         "https://raw.githubusercontent.com/AdiyogiArts/gita-json/main/hi.json"}
    };
    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private List<GitaVerse> verses = Collections.emptyList();
    private boolean loading;

    public GitaRepository(Context context) { this.context = context.getApplicationContext(); }
    public List<GitaVerse> getAll() { return verses; }
    public GitaVerse get(int chapter, int number) {
        for (GitaVerse verse : verses) if (verse.chapter == chapter && verse.number == number) return verse;
        return null;
    }

    public void load(Callback callback) {
        if (!verses.isEmpty()) { handler.post(() -> callback.complete(verses, null)); return; }
        if (loading) return;
        loading = true;
        executor.execute(() -> {
            String error = null;
            List<GitaVerse> loaded = Collections.emptyList();
            try {
                JSONObject base = new JSONObject(readOrDownload("gita_base.json", DATA_URLS[0]));
                JSONObject hindi = new JSONObject(readOrDownload("gita_hi.json", DATA_URLS[1]));
                TreeMap<String, RawVerse> entries = new TreeMap<>();
                harvest(base, entries, false);
                harvest(hindi, entries, true);
                ArrayList<GitaVerse> all = new ArrayList<>();
                for (RawVerse item : entries.values()) {
                    if (item.chapter < 1 || item.chapter > 18 || item.verse < 1 || item.sanskrit.isEmpty()) continue;
                    String meaning = item.hindi.isEmpty() ? "इस श्लोक का हिंदी अर्थ फिलहाल उपलब्ध नहीं है।" : item.hindi;
                    all.add(new GitaVerse(item.chapter, item.verse,
                        item.chapterName.isEmpty() ? "अध्याय " + item.chapter : item.chapterName,
                        item.sanskrit, meaning));
                }
                all.sort(Comparator.comparingInt((GitaVerse v) -> v.chapter).thenComparingInt(v -> v.number));
                if (all.size() < 690 || all.size() > 710) throw new IOException("Dataset में " + all.size() + " श्लोक मिले, 700 होने चाहिए।");
                loaded = Collections.unmodifiableList(all);
                QuoteScheduler.saveQuotes(context, loaded);
            } catch (Exception e) { error = e.getMessage(); }
            List<GitaVerse> result = loaded;
            String failed = error;
            handler.post(() -> {
                loading = false;
                if (failed == null) verses = result;
                callback.complete(result, failed);
            });
        });
    }

    private static final class RawVerse {
        int chapter, verse;
        String chapterName="", sanskrit="", hindi="";
    }

    /** Handles both flat {"1-1":{...}} and nested {"chapters":[{"verses":[...] }]} formats. */
    private static void harvest(JSONObject root, Map<String, RawVerse> entries, boolean isHindi) {
        JSONArray chapters = root.optJSONArray("chapters");
        if (chapters != null) {
            for (int i = 0; i < chapters.length(); i++) {
                JSONObject chapter = chapters.optJSONObject(i);
                if (chapter == null) continue;
                int number = firstInt(chapter, "chapter_number", "chapter", "number");
                String name = firstString(chapter,"name_sanskrit","chapterName","name");
                JSONArray rows = chapter.optJSONArray("verses");
                if (rows == null) continue;
                for (int j=0;j<rows.length();j++) add(entries, rows.optJSONObject(j), number, j+1, name, isHindi);
            }
        }
        JSONArray verses = root.optJSONArray("verses");
        if (verses != null) {
            for (int i=0;i<verses.length();i++) add(entries, verses.optJSONObject(i), 0, 0, "", isHindi);
        }
        Iterator<String> keys = root.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (!key.matches("(?:BG)?\\d+[-._]\\d+")) continue;
            String[] p = key.replace("BG", "").split("[-._]");
            try {
                int ch = Integer.parseInt(p[0]), v = Integer.parseInt(p[1]);
                Object element = root.opt(key);
                if (element instanceof JSONObject) add(entries, (JSONObject) element, ch, v, "", isHindi);
                else if (element instanceof String && isHindi) {
                    RawVerse raw = entries.computeIfAbsent(ch+"-"+v,k->new RawVerse());
                    raw.chapter=ch; raw.verse=v; raw.hindi=(String)element;
                }
            } catch (Exception ignored) { }
        }
    }

    private static void add(Map<String,RawVerse> all, JSONObject obj, int chapter, int verse,
                            String chapterName, boolean isHindi) {
        if (obj == null) return;
        int ch=firstInt(obj,"chapter_number","chapter","chapterNumber","chapter_id");
        int no=firstInt(obj,"verse_number","verse","verseNumber","number");
        if (ch > 0) chapter=ch;
        if (no > 0) verse=no;
        if (chapter < 1 || chapter > 18 || verse < 1) return;
        String key=chapter+"-"+verse;
        RawVerse item=all.computeIfAbsent(key,k->new RawVerse());
        item.chapter=chapter; item.verse=verse;
        if (!chapterName.isEmpty()) item.chapterName=chapterName;
        String sanskrit=firstString(obj,"sanskrit","slok","shloka","text","devanagari");
        if (!sanskrit.isEmpty() && (item.sanskrit.isEmpty() || !isHindi)) item.sanskrit=sanskrit;
        if (isHindi) {
            String meaning=firstString(obj,"hindi","translation_hindi","meaning_hindi","bhavarth");
            if (meaning.isEmpty()) meaning=translate(obj.opt("translations"));
            if (meaning.isEmpty()) meaning=translate(obj.opt("translation"));
            if (meaning.isEmpty()) meaning=translate(obj.opt("meaning"));
            if (!meaning.isEmpty()) item.hindi=meaning;
        }
    }

    private static int firstInt(JSONObject obj, String... keys) {
        for (String key:keys) if (obj.has(key)) {
            try { int v=Integer.parseInt(obj.optString(key,"0")); if (v>0) return v; }
            catch (Exception ignored) { }
        }
        return 0;
    }
    private static String firstString(JSONObject obj,String... keys) {
        for (String key:keys) {
            String s=obj.optString(key,"").trim();
            if (!s.isEmpty() && !s.equals("null")) return s;
        }
        return "";
    }
    private static String translate(Object value) {
        if (value == null || value == JSONObject.NULL) return "";
        if (value instanceof String) return ((String)value).trim();
        if (value instanceof JSONObject) {
            JSONObject o=(JSONObject)value;
            for (String k:new String[]{"hindi","hi","translation","meaning","text","bhavarth","description"}) {
                String t=translate(o.opt(k)); if (!t.isEmpty()) return t;
            }
        }
        if (value instanceof JSONArray) {
            JSONArray a=(JSONArray)value;
            for (int i=0;i<a.length();i++) {
                String t=translate(a.opt(i));
                if (!t.isEmpty() && t.matches("(?s).*?[\\u0900-\\u097F].*")) return t;
            }
        }
        return "";
    }

    private String readOrDownload(String filename,String[] urls) throws Exception {
        File file=new File(context.getFilesDir(),filename);
        if (file.isFile() && file.length()>30_000) {
            try { String cached=readFile(file); if (new JSONObject(cached).length()>0) return cached; }
            catch (Exception ignored) { file.delete(); }
        }
        Exception last=null;
        for (String address:urls) {
            HttpURLConnection conn=null;
            try {
                conn=(HttpURLConnection)new URL(address).openConnection();
                conn.setRequestProperty("User-Agent","GitaSaarAndroid/1.0");
                conn.setConnectTimeout(12000); conn.setReadTimeout(30000);
                if(conn.getResponseCode()!=200) throw new IOException("HTTP "+conn.getResponseCode());
                ByteArrayOutputStream bytes=new ByteArrayOutputStream();
                try (InputStream stream=conn.getInputStream()) {
                    byte[] buffer=new byte[8192]; int n;
                    while ((n=stream.read(buffer))!=-1) {
                        bytes.write(buffer,0,n);
                        if(bytes.size()>20_000_000) throw new IOException("डेटा फ़ाइल बहुत बड़ी है");
                    }
                }
                String json=bytes.toString(StandardCharsets.UTF_8.name());
                if(new JSONObject(json).length()==0) throw new IOException("Empty data");
                File temp=new File(context.getFilesDir(),filename+".tmp");
                try(FileOutputStream out=new FileOutputStream(temp)){out.write(json.getBytes(StandardCharsets.UTF_8));}
                if(!temp.renameTo(file)) {
                    try(FileOutputStream out=new FileOutputStream(file)){out.write(json.getBytes(StandardCharsets.UTF_8));}
                    temp.delete();
                }
                return json;
            } catch (Exception e) {last=e;}
            finally { if(conn!=null) conn.disconnect(); }
        }
        throw new IOException("गीता डेटा डाउनलोड नहीं हुआ। इंटरनेट चेक करके दोबारा कोशिश करें। "
            +(last==null?"":last.getMessage()));
    }
    private static String readFile(File file) throws IOException {
        try(InputStream in=new FileInputStream(file);ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] b=new byte[8192];int n;
            while((n=in.read(b))!=-1)out.write(b,0,n);
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }
}
