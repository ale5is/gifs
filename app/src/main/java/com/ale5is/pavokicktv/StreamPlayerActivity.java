package com.ale5is.pavokicktv;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;
import android.content.pm.ActivityInfo;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;

public class StreamPlayerActivity extends Activity {
    public static final String EXTRA_CHANNEL = "channel";
    public static final String EXTRA_VOD_UUID = "vod_uuid";
    public static final String EXTRA_VOD_TITLE = "vod_title";
    private ExoPlayer player;
    private DefaultTrackSelector trackSelector;
    private PlayerView playerView;
    private TextView status;
    private LinearLayout topBar;
    private String channel;
    private String vodUuid;
    private String vodTitle;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        channel = getIntent().getStringExtra(EXTRA_CHANNEL);
        vodUuid = getIntent().getStringExtra(EXTRA_VOD_UUID);
        vodTitle = getIntent().getStringExtra(EXTRA_VOD_TITLE);
        if (channel == null || channel.trim().length() == 0) {
            finish();
            return;
        }
        buildPlayerUi();
        resolveAndPlay();
    }

    private void buildPlayerUi() {
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(Color.BLACK);

        playerView = new PlayerView(this);
        playerView.setUseController(true);
        playerView.setControllerAutoShow(true);
        playerView.setControllerHideOnTouch(true);
        playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS);
        frame.addView(playerView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(12, 8, 12, 8);
        topBar.setBackgroundColor(0x99000000);

        Button back = new Button(this);
        back.setText("VOLVER");
        back.setOnClickListener(v -> finish());
        back.setFocusable(true);
        topBar.addView(back);

        TextView name = new TextView(this);
        name.setText("  " + (vodTitle != null ? vodTitle : channel));
        name.setTextColor(Color.WHITE);
        name.setTextSize(16);
        topBar.addView(name, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        Button quality = new Button(this);
        quality.setText("CALIDAD");
        quality.setOnClickListener(this::showQualityMenu);
        quality.setFocusable(true);
        topBar.addView(quality);

        FrameLayout.LayoutParams topParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP);
        frame.addView(topBar, topParams);

        status = new TextView(this);
        status.setText("Conectando con KICK...");
        status.setTextColor(Color.WHITE);
        status.setTextSize(16);
        status.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams statusParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        frame.addView(status, statusParams);
        setContentView(frame);
        topBar.setVisibility(View.VISIBLE);
        playerView.setControllerVisibilityListener(visibility -> {
            if (topBar != null) topBar.setVisibility(visibility);
        });

        trackSelector = new DefaultTrackSelector(this);
        DefaultHttpDataSource.Factory dataSourceFactory = new DefaultHttpDataSource.Factory()
                .setUserAgent(KickStreamResolver.USER_AGENT)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(20000)
                .setAllowCrossProtocolRedirects(true);
        player = new ExoPlayer.Builder(this)
                .setTrackSelector(trackSelector)
                .setMediaSourceFactory(new com.google.android.exoplayer2.source.DefaultMediaSourceFactory(dataSourceFactory))
                .build();
        playerView.setPlayer(player);
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_READY) status.setVisibility(View.GONE);
                if (playbackState == Player.STATE_BUFFERING) {
                    status.setText("Cargando directo...");
                    status.setVisibility(View.VISIBLE);
                }
                if (playbackState == Player.STATE_ENDED) {
                    status.setText("El directo terminó.");
                    status.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                status.setText("No se pudo reproducir el directo. Puede que KICK haya cambiado su API.");
                status.setVisibility(View.VISIBLE);
            }
        });
    }

    private void resolveAndPlay() {
        if (vodUuid != null && vodUuid.length() > 0) {
            status.setText("Preparando video anterior...");
            KickVodResolver.resolvePlayback(vodUuid, getIntent().getStringExtra("vod_source"), new KickVodResolver.PlaybackCallback() {
                @Override
                public void onSuccess(String url) {
                    runOnUiThread(() -> playResolvedUrl(url));
                }

                @Override
                public void onError(String message) {
                    runOnUiThread(() -> showPlaybackError(message));
                }
            });
            return;
        }
        KickStreamResolver.resolve(channel, new KickStreamResolver.Callback() {
            @Override
            public void onSuccess(String url) {
                runOnUiThread(() -> playResolvedUrl(url));
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> showPlaybackError(message));
            }
        });
    }

    private void playResolvedUrl(String url) {
        if (isFinishing() || player == null) return;
        MediaItem item = new MediaItem.Builder()
                .setUri(url)
                .setMimeType(com.google.android.exoplayer2.util.MimeTypes.APPLICATION_M3U8)
                .build();
        player.setMediaItem(item);
        player.prepare();
        player.setPlayWhenReady(true);
        status.setText(vodUuid != null ? "Iniciando video..." : "Iniciando directo...");
        status.setVisibility(View.VISIBLE);
    }

    private void showPlaybackError(String message) {
        if (isFinishing()) return;
        status.setText(message);
        status.setVisibility(View.VISIBLE);
        Toast.makeText(StreamPlayerActivity.this, message, Toast.LENGTH_LONG).show();
    }

    private void showQualityMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 0, 0, "Automática");
        menu.getMenu().add(0, 1080, 1, "Hasta 1080p");
        menu.getMenu().add(0, 720, 2, "Hasta 720p");
        menu.getMenu().add(0, 480, 3, "Hasta 480p");
        menu.getMenu().add(0, 360, 4, "Hasta 360p");
        menu.setOnMenuItemClickListener(item -> {
            int height = item.getItemId();
            DefaultTrackSelector.Parameters.Builder parameters = trackSelector.buildUponParameters();
            if (height == 0) {
                parameters.setMaxVideoSize(Integer.MAX_VALUE, Integer.MAX_VALUE);
            } else {
                parameters.setMaxVideoSize(Integer.MAX_VALUE, height);
            }
            trackSelector.setParameters(parameters);
            Toast.makeText(this, item.getTitle(), Toast.LENGTH_SHORT).show();
            return true;
        });
        menu.show();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (playerView != null && !playerView.isControllerVisible()) {
            int key = event.getKeyCode();
            if (key == KeyEvent.KEYCODE_DPAD_CENTER
                    || key == KeyEvent.KEYCODE_ENTER
                    || key == KeyEvent.KEYCODE_DPAD_LEFT
                    || key == KeyEvent.KEYCODE_DPAD_RIGHT
                    || key == KeyEvent.KEYCODE_DPAD_UP
                    || key == KeyEvent.KEYCODE_DPAD_DOWN) {
                playerView.showController();
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onBackPressed() {
        finish();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (player != null) player.pause();
    }

    @Override
    protected void onDestroy() {
        if (playerView != null) playerView.setPlayer(null);
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDestroy();
    }
}
