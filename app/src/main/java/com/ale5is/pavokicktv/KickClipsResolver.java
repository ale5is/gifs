package com.ale5is.pavokicktv;

import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class KickClipsResolver {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS)
            .followRedirects(true).build();

    public static final class Clip {
        public final String id, title, thumbnail, playback, date, duration, views;
        Clip(String id, String title, String thumbnail, String playback, String date, String duration, String views) {
            this.id=id; this.title=title; this.thumbnail=thumbnail; this.playback=playback;
            this.date=date; this.duration=duration; this.views=views;
        }
    }
    public interface Callback { void onSuccess(List<Clip> clips); void onError(String message); }
    private KickClipsResolver() {}

    public static void load(String channel, Callback callback) {
        new Thread(() -> {
            try {
                String url = "https://kick.com/api/v2/channels/" + channel + "/clips";
                Request request = new Request.Builder().url(url)
                        .header("User-Agent", KickStreamResolver.USER_AGENT)
                        .header("Accept", "application/json, text/plain, */*")
                        .header("Referer", "https://kick.com/" + channel + "/clips").build();
                List<Clip> clips = new ArrayList<>();
                try (Response response = CLIENT.newCall(request).execute()) {
                    if (response.code()==404) throw new IOException("No se encontró el canal o no tiene clips públicos.");
                    if (!response.isSuccessful()) throw new IOException("KICK rechazó los clips (HTTP " + response.code() + ").");
                    if (response.body()==null) throw new IOException("KICK devolvió una respuesta vacía.");
                    JSONObject root = new JSONObject(response.body().string());
                    JSONArray array = root.optJSONArray("clips");
                    JSONObject data = root.optJSONObject("data");
                    if (array==null && data!=null) array=data.optJSONArray("clips");
                    if (array==null) array=root.optJSONArray("data");
                    if (array==null) throw new IOException("KICK devolvió un formato de clips no reconocido.");
                    for (int i=0;i<array.length();i++) {
                        JSONObject item=array.optJSONObject(i);
                        if(item==null) continue;
                        String id=first(item,"id","slug");
                        String title=first(item,"title","clip_title");
                        String thumbnail=first(item,"thumbnail_url","thumbnail","thumbnail_src");
                        String playback=first(item,"video_url","playback_url","source");
                        String date=first(item,"created_at","createdAt");
                        String duration=first(item,"duration","duration_seconds","durationSeconds");
                        String views=first(item,"view_count","views","views_count");
                        if(title.length()==0) title="Clip de " + channel;
                        clips.add(new Clip(id,title,thumbnail,playback,date,formatDuration(duration),views));
                    }
                }
                if(clips.isEmpty()) throw new IOException("Este canal todavía no tiene clips públicos.");
                MAIN.post(() -> callback.onSuccess(clips));
            } catch(Exception e) {
                String message=e.getMessage()==null ? "No se pudieron cargar los clips de KICK." : e.getMessage();
                MAIN.post(() -> callback.onError(message));
            }
        },"KickClipsList").start();
    }

    public static void resolvePlayback(String id, CallbackPlayback callback) {
        new Thread(() -> {
            String url = resolveClip(id);
            if (url != null && url.startsWith("http") && url.contains(".m3u8")) {
                MAIN.post(() -> callback.onSuccess(url));
            } else {
                MAIN.post(() -> callback.onError("KICK no entregó la URL reproducible de este clip."));
            }
        }, "KickClipPlayback").start();
    }
    public interface CallbackPlayback { void onSuccess(String url); void onError(String message); }

    private static String resolveClip(String id) {
        try {
            Request request=new Request.Builder().url("https://kick.com/api/v2/clips/"+id)
                    .header("User-Agent",KickStreamResolver.USER_AGENT)
                    .header("Accept","application/json, text/plain, */*")
                    .header("Referer","https://kick.com/").build();
            try(Response response=CLIENT.newCall(request).execute()) {
                if(!response.isSuccessful() || response.body()==null) return "";
                JSONObject root=new JSONObject(response.body().string());
                JSONObject data=root.optJSONObject("data");
                JSONObject clip=root.optJSONObject("clip");
                String value=first(root,"video_url","playback_url","source");
                if(value.length()==0 && data!=null) value=first(data,"video_url","playback_url","source");
                if(value.length()==0 && clip!=null) value=first(clip,"video_url","playback_url","source");
                if(value.length()==0 && data!=null) { JSONObject nested=data.optJSONObject("clip"); if(nested!=null) value=first(nested,"video_url","playback_url","source"); }
                return value;
            }
        } catch(Exception ignored) { return ""; }
    }

    private static String first(JSONObject object,String... keys) {
        if(object==null) return "";
        for(String key:keys) {
            Object value=object.opt(key);
            if(value==null || value==JSONObject.NULL || value instanceof JSONObject || value instanceof JSONArray) continue;
            String text=String.valueOf(value).trim();
            if(text.length()>0 && !"0".equals(text)) return text;
        }
        return "";
    }
    private static String formatDuration(String raw) {
        try { long s=Long.parseLong(raw); return String.format("%d:%02d",s/60,s%60); }
        catch(Exception e) { return raw.length()==0 ? "Clip" : raw; }
    }
}
