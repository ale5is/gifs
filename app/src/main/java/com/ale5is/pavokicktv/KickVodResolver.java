package com.ale5is.pavokicktv;

import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.MediaType;
import okhttp3.Response;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class KickVodResolver {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .followRedirects(true)
            .build();

    public static final class Vod {
        public final String uuid;
        public final String title;
        public final String date;
        public final String duration;
        public final String views;

        Vod(String uuid, String title, String date, String duration, String views) {
            this.uuid = uuid;
            this.title = title;
            this.date = date;
            this.duration = duration;
            this.views = views;
        }
    }

    public interface ListCallback {
        void onSuccess(List<Vod> videos);
        void onError(String message);
    }

    public interface PlaybackCallback {
        void onSuccess(String url);
        void onError(String message);
    }

    private KickVodResolver() {}

    public static void load(String channel, ListCallback callback) {
        new Thread(() -> {
            try {
                String endpoint = "https://kick.com/api/v2/channels/" + channel + "/videos";
                Request request = new Request.Builder().url(endpoint)
                        .header("User-Agent", KickStreamResolver.USER_AGENT)
                        .header("Accept", "application/json, text/plain, */*")
                        .header("Referer", "https://kick.com/" + channel)
                        .build();
                List<Vod> result = new ArrayList<>();
                try (Response response = CLIENT.newCall(request).execute()) {
                    if (response.code() == 404) throw new IOException("No se encontró el canal.");
                    if (!response.isSuccessful()) throw new IOException("KICK rechazó el historial (HTTP " + response.code() + ").");
                    if (response.body() == null) throw new IOException("KICK devolvió una respuesta vacía.");
                    JSONObject root = new JSONObject(response.body().string());
                    JSONArray videos = root.optJSONArray("videos");
                    if (videos == null) videos = root.optJSONArray("previous_livestreams");
                    if (videos == null) {
                        JSONObject data = root.optJSONObject("data");
                        if (data != null) {
                            videos = data.optJSONArray("videos");
                            if (videos == null) videos = data.optJSONArray("previous_livestreams");
                        }
                    }
                    if (videos == null) throw new IOException("KICK cambió el formato del historial.");
                    for (int i = 0; i < videos.length(); i++) {
                        JSONObject item = videos.optJSONObject(i);
                        if (item == null) continue;
                        String uuid = first(item, "uuid", "video_uuid", "id");
                        if (uuid.length() == 0) continue;
                        String title = first(item, "session_title", "title", "livestream_title");
                        if (title.length() == 0) title = "Directo anterior";
                        String date = first(item, "created_at", "start_time", "started_at", "createdAt");
                        String duration = first(item, "duration", "duration_ms", "duration_seconds");
                        String views = first(item, "view_count", "views", "viewer_count");
                        result.add(new Vod(uuid, title, date, formatDuration(duration, item), views));
                    }
                }
                if (result.isEmpty()) throw new IOException("Este canal no tiene directos anteriores disponibles.");
                MAIN.post(() -> callback.onSuccess(result));
            } catch (Exception error) {
                String message = error.getMessage() == null ? "No se pudo cargar el historial de KICK." : error.getMessage();
                MAIN.post(() -> callback.onError(message));
            }
        }, "KickVodList").start();
    }

    public static void resolvePlayback(String uuid, PlaybackCallback callback) {
        new Thread(() -> {
            try {
                String endpoint = "https://web.kick.com/api/v1/stream/" + uuid + "/playback";
                Request request = new Request.Builder().url(endpoint)
                        .post(RequestBody.create(JSON, "{}"))
                        .header("User-Agent", KickStreamResolver.USER_AGENT)
                        .header("Accept", "application/json, text/plain, */*")
                        .header("Origin", "https://kick.com")
                        .header("Referer", "https://kick.com/")
                        .build();
                String playback = "";
                try (Response response = CLIENT.newCall(request).execute()) {
                    if (!response.isSuccessful()) throw new IOException("KICK no pudo preparar el video (HTTP " + response.code() + ").");
                    if (response.body() == null) throw new IOException("KICK devolvió una respuesta vacía.");
                    JSONObject root = new JSONObject(response.body().string());
                    JSONObject playbackUrl = root.optJSONObject("playback_url");
                    if (playbackUrl != null) playback = playbackUrl.optString("vod", "");
                    if (playback.length() == 0) playback = root.optString("playback_url", "");
                    if (playback.length() == 0) {
                        JSONObject data = root.optJSONObject("data");
                        if (data != null) {
                            JSONObject nested = data.optJSONObject("playback_url");
                            if (nested != null) playback = nested.optString("vod", "");
                            if (playback.length() == 0) playback = data.optString("playback_url", "");
                        }
                    }
                    if (!playback.startsWith("https://")) throw new IOException("El video no está disponible o es exclusivo para suscriptores.");
                }
                String finalPlayback = playback;
                MAIN.post(() -> callback.onSuccess(finalPlayback));
            } catch (Exception error) {
                String message = error.getMessage() == null ? "No se pudo abrir este video." : error.getMessage();
                MAIN.post(() -> callback.onError(message));
            }
        }, "KickVodPlayback").start();
    }

    private static String first(JSONObject object, String... keys) {
        for (String key : keys) {
            Object value = object.opt(key);
            if (value != null && value != JSONObject.NULL) {
                String text = String.valueOf(value).trim();
                if (text.length() > 0 && !"0".equals(text)) return text;
            }
        }
        return "";
    }

    private static String formatDuration(String raw, JSONObject item) {
        try {
            long value = Long.parseLong(raw);
            if (item.has("duration_ms") || value > 100000) value /= 1000;
            long hours = value / 3600;
            long minutes = (value % 3600) / 60;
            long seconds = value % 60;
            return hours > 0 ? String.format("%d:%02d:%02d", hours, minutes, seconds)
                    : String.format("%d:%02d", minutes, seconds);
        } catch (Exception ignored) {
            return raw.length() == 0 ? "Duración desconocida" : raw;
        }
    }
}
