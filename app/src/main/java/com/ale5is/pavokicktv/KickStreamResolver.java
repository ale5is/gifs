package com.ale5is.pavokicktv;

import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public final class KickStreamResolver {
    public static final String USER_AGENT = "Mozilla/5.0 (Linux; Android 5.1; TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/70.0.3538.80 Safari/537.36";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .build();

    public interface Callback {
        void onSuccess(String url);
        void onError(String message);
    }

    private KickStreamResolver() {}

    public static void resolve(String channel, Callback callback) {
        new Thread(() -> {
            try {
                String endpoint = "https://kick.com/api/v2/channels/" + channel;
                Request request = new Request.Builder()
                        .url(endpoint)
                        .header("User-Agent", USER_AGENT)
                        .header("Accept", "application/json, text/plain, */*")
                        .header("Referer", "https://kick.com/")
                        .build();
                String playbackUrl;
                try (Response response = CLIENT.newCall(request).execute()) {
                    if (response.code() == 404) throw new IOException("No se encontró el canal.");
                    if (!response.isSuccessful()) throw new IOException("KICK rechazó la consulta (HTTP " + response.code() + ").");
                    if (response.body() == null) throw new IOException("KICK devolvió una respuesta vacía.");
                    JSONObject json = new JSONObject(response.body().string());
                    JSONObject livestream = json.optJSONObject("livestream");
                    if (livestream == null) throw new IOException("El canal está desconectado.");
                    playbackUrl = json.optString("playback_url", "");
                    if (playbackUrl.length() == 0 || !playbackUrl.startsWith("https://")) {
                        throw new IOException("KICK no devolvió una URL HLS válida.");
                    }
                }
                final String result = playbackUrl;
                MAIN.post(() -> callback.onSuccess(result));
            } catch (Exception error) {
                final String message = error.getMessage() == null ? "Error al consultar KICK." : error.getMessage();
                MAIN.post(() -> callback.onError(message));
            }
        }, "KickStreamResolver").start();
    }
}
