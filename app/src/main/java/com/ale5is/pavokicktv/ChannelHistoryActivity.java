package com.ale5is.pavokicktv;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.List;

public class ChannelHistoryActivity extends Activity {
    public static final String EXTRA_CHANNEL = "channel";
    private LinearLayout list;
    private String channel;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        channel = getIntent().getStringExtra(EXTRA_CHANNEL);
        if (channel == null || channel.trim().length() == 0) {
            finish();
            return;
        }
        buildUi();
        loadVideos();
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView text(String value, int size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(Color.WHITE);
        view.setTextSize(size);
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        return view;
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(36), dp(18), dp(36), dp(18));
        root.setBackgroundColor(Color.rgb(15, 17, 20));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        Button back = new Button(this);
        back.setText("VOLVER");
        back.setFocusable(true);
        back.setOnClickListener(v -> finish());
        header.addView(back);

        TextView heading = text("  VIDEOS ANTERIORES · " + channel, 22, true);
        header.addView(heading, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        root.addView(header);

        TextView hint = text("Selecciona una emisión para reproducirla", 13, false);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintParams.topMargin = dp(4);
        root.addView(hint, hintParams);

        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
        scrollParams.topMargin = dp(12);
        root.addView(scroll, scrollParams);
        setContentView(root);
    }

    private void loadVideos() {
        list.removeAllViews();
        TextView loading = text("Cargando historial de KICK...", 17, false);
        list.addView(loading);
        KickVodResolver.load(channel, new KickVodResolver.ListCallback() {
            @Override
            public void onSuccess(List<KickVodResolver.Vod> videos) {
                if (isFinishing()) return;
                list.removeAllViews();
                TextView count = text(videos.size() + " emisiones encontradas", 13, false);
                list.addView(count);
                for (KickVodResolver.Vod vod : videos) addVideo(vod);
                if (list.getChildCount() > 1) list.getChildAt(1).requestFocus();
            }

            @Override
            public void onError(String message) {
                if (isFinishing()) return;
                list.removeAllViews();
                TextView error = text(message, 17, false);
                list.addView(error);
                Button retry = new Button(ChannelHistoryActivity.this);
                retry.setText("REINTENTAR");
                retry.setOnClickListener(v -> loadVideos());
                list.addView(retry);
                Toast.makeText(ChannelHistoryActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void addVideo(KickVodResolver.Vod vod) {
        Button item = new Button(this);
        item.setAllCaps(false);
        item.setText(vod.title + "\n" + vod.date + "  ·  " + vod.duration + "  ·  " + vod.views + " vistas");
        item.setTextSize(14);
        item.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        item.setMinHeight(dp(76));
        item.setFocusable(true);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(6);
        list.addView(item, params);
        item.setOnClickListener(v -> {
            Intent intent = new Intent(ChannelHistoryActivity.this, StreamPlayerActivity.class);
            intent.putExtra(StreamPlayerActivity.EXTRA_CHANNEL, channel);
            intent.putExtra(StreamPlayerActivity.EXTRA_VOD_UUID, vod.uuid);
            intent.putExtra(StreamPlayerActivity.EXTRA_VOD_TITLE, vod.title);
            startActivity(intent);
        });
    }
}
