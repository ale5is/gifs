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

public final class KickVodResolver {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
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
        public final String thumbnail;
        public final String source;

        Vod(String uuid, String title, String date, String duration, String views,
            String thumbnail, String source) {
            this.uuid = uuid;
            this.title = title;
            this.date = date;
            this.duration = duration;
            this.views = views;
            this.thumbnail = thumbnail;
            this.source = source;
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
                    String body = response.body().string().trim();
                    JSONArray videos = parseVideoArray(body);
                    if (videos == null) throw new IOException("KICK devolvió un formato de historial que no reconocemos.");
                    for (int i = 0; i < videos.length(); i++) {
                        JSONObject item = videos.optJSONObject(i);
                        if (item == null) continue;
                        JSONObject video = item.optJSONObject("video");
                        String uuid = first(item, "uuid", "video_uuid");
                        if (uuid.length() == 0 && video != null) uuid = first(video, "uuid", "id");
                        if (uuid.length() == 0) {
                            String id = first(item, "id");
                            if (isVideoId(id)) uuid = id;
                        }
                        if (uuid.length() == 0) continue;

                        JSONObject livestream = item.optJSONObject("livestream");
                        if (livestream == null) livestream = video == null ? item : video;
                        String title = first(item, "session_title", "title", "livestream_title");
                        if (title.length() == 0) title = first(livestream, "session_title", "title");
                        if (title.length() == 0) title = "Directo anterior";

                        String date = first(livestream, "start_time", "started_at");
                        if (date.length() == 0) date = first(item, "created_at", "start_time", "started_at");
                        String duration = first(livestream, "duration");
                        if (duration.length() == 0) duration = first(item, "duration", "duration_ms", "duration_seconds");
                        String views = first(item, "views", "view_count", "viewer_count");
                        if (views.length() == 0) views = first(livestream, "views", "viewer_count");
                        String source = first(item, "source", "playback_url");
                        if (source.length() == 0 && video != null) source = first(video, "source", "playback_url");
                        String thumbnail = thumbnail(item, livestream);
                        result.add(new Vod(uuid, title, date, formatDuration(duration, item, livestream),
                                views.length() == 0 ? "—" : views, thumbnail, source));
                    }
                }
                if (result.isEmpty()) throw new IOException("No se encontraron emisiones públicas para este canal.");
                MAIN.post(() -> callback.onSuccess(result));
            } catch (Exception error) {
                String message = error.getMessage() == null ? "No se pudo cargar el historial de KICK." : error.getMessage();
                MAIN.post(() -> callback.onError(message));
            }
        }, "KickVodList").start();
    }

    private static JSONArray parseVideoArray(String body) throws Exception {
        if (body.startsWith("[")) return new JSONArray(body);
        JSONObject root = new JSONObject(body);
        JSONArray videos = root.optJSONArray("videos");
        if (videos == null) videos = root.optJSONArray("data");
        if (videos == null) videos = root.optJSONArray("previous_livestreams");
        if (videos == null) {
            JSONObject data = root.optJSONObject("data");
            if (data != null) {
                videos = data.optJSONArray("videos");
                if (videos == null) videos = data.optJSONArray("previous_livestreams");
                if (videos == null) videos = data.optJSONArray("data");
            }
        }
        return videos;
    }

    public static void resolvePlayback(String uuid, String source, PlaybackCallback callback) {
        new Thread(() -> {
            try {
                String playback = "";
                if (uuid != null && uuid.length() > 0) {
                    String endpoint = "https://kick.com/api/v1/video/" + uuid;
                    Request request = new Request.Builder().url(endpoint)
                            .header("User-Agent", KickStreamResolver.USER_AGENT)
                            .header("Accept", "application/json, text/plain, */*")
                            .header("Referer", "https://kick.com/")
                            .build();
                    try (Response response = CLIENT.newCall(request).execute()) {
                        if (response.isSuccessful() && response.body() != null) {
                            JSONObject root = new JSONObject(response.body().string());
                            playback = first(root, "source");
                        }
                    } catch (Exception ignored) {
                    }
                }
                if (playback.length() == 0) playback = buildPlaylistUrl(source);
                if (!playback.startsWith("https://") || !playback.contains(".m3u8")) {
                    throw new IOException("KICK no entregó la lista HLS de este video. Puede estar eliminado, restringido o en procesamiento.");
                }
                String finalPlayback = playback;
                MAIN.post(() -> callback.onSuccess(finalPlayback));
            } catch (Exception error) {
                String message = error.getMessage() == null ? "No se pudo abrir este video." : error.getMessage();
                MAIN.post(() -> callback.onError(message));
            }
        }, "KickVodPlayback").start();
    }

    private static String buildPlaylistUrl(String source) {
        if (source == null) return "";
        String value = source.trim();
        if (value.length() == 0) return "";
        if (value.contains(".m3u8")) return value;
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        if (value.endsWith("/media/hls")) return value + "/master.m3u8";
        return value + "/media/hls/master.m3u8";
    }

    private static String thumbnail(JSONObject item, JSONObject livestream) {
        String value = first(item, "thumbnail_url", "thumbnail_src", "thumbnailUrl");
        if (value.length() == 0) value = thumbnailValue(item.opt("thumbnail"));
        if (value.length() == 0) value = first(livestream, "thumbnail_url", "thumbnail_src", "thumbnailUrl");
        if (value.length() == 0) value = thumbnailValue(livestream.opt("thumbnail"));
        JSONObject video = item.optJSONObject("video");
        if (value.length() == 0 && video != null) {
            value = first(video, "thumbnail_url", "thumbnail_src", "thumbnailUrl");
            if (value.length() == 0) value = thumbnailValue(video.opt("thumbnail"));
        }
        return value;
    }

    private static String thumbnailValue(Object value) {
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            return first(object, "src", "url", "source", "url_template");
        }
        if (value instanceof String) {
            String text = ((String) value).trim();
            if (text.startsWith("http://") || text.startsWith("https://")) return text;
        }
        return "";
    }

