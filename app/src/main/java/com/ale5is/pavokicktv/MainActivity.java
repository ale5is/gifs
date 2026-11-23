package com.ale5is.pavokicktv;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.http.SslError;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.FrameLayout;
import android.util.TypedValue;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private static final String PLAYER_BASE = "https://player.kick.com/";
    private static final String PREFS = "pavokicktv";
    private static final String FAVORITES = "favorites";
    private static final String RECENTS = "recents";
    private static final Pattern CHANNEL_PATTERN = Pattern.compile("[A-Za-z0-9_]{1,25}");
    private LinearLayout root;
    private EditText channelInput;
    private WebView webView;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private FrameLayout fullScreenContainer;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        showHome();
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView makeText(String value, int size, boolean bold) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextColor(Color.WHITE);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        if (bold) text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return text;
    }

    private Button makeButton(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        button.setFocusable(true);
        button.setFocusableInTouchMode(true);
        return button;
    }

    private void applyImmersiveMode(boolean enabled) {
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        if (enabled) {
            flags |= View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        }
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private List<String> readList(String key) {
        String raw = prefs().getString(key, "");
        List<String> values = new ArrayList<>();
        if (raw.length() == 0) return values;
        String[] parts = raw.split(",");
        for (String part : parts) {
            String value = part.trim();
            if (CHANNEL_PATTERN.matcher(value).matches() && !values.contains(value)) values.add(value);
        }
        return values;
    }

    private void saveList(String key, List<String> values) {
        StringBuilder raw = new StringBuilder();
        for (String value : values) {
            if (raw.length() > 0) raw.append(",");
            raw.append(value);
        }
        prefs().edit().putString(key, raw.toString()).apply();
    }

    private void addRecent(String channel) {
        List<String> values = readList(RECENTS);
        values.remove(channel);
        values.add(0, channel);
        while (values.size() > 8) values.remove(values.size() - 1);
        saveList(RECENTS, values);
    }

    private void showHome() {
        applyImmersiveMode(false);
        if (webView != null) {
            webView.stopLoading();
            webView.loadUrl("about:blank");
            webView.destroy();
            webView = null;
        }

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(42), dp(20), dp(42), dp(18));
        root.setBackgroundColor(Color.rgb(15, 17, 20));

        TextView title = makeText("PavoKick TV", 30, true);
        root.addView(title);

        TextView subtitle = makeText("Directos de KICK, sin cargar la página completa del canal", 15, false);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = dp(3);
        root.addView(subtitle, subtitleParams);

        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        channelInput = new EditText(this);
        channelInput.setSingleLine(true);
        channelInput.setTextColor(Color.WHITE);
        channelInput.setHintTextColor(Color.LTGRAY);
        channelInput.setHint("Escribe el nombre del canal");
        channelInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        channelInput.setSelectAllOnFocus(true);
        channelInput.setFocusable(true);
        channelInput.setFocusableInTouchMode(true);
        searchRow.addView(channelInput, new LinearLayout.LayoutParams(dp(390), dp(54)));

        Button watch = makeButton("VER DIRECTO");
        LinearLayout.LayoutParams watchParams = new LinearLayout.LayoutParams(dp(170), dp(52));
        watchParams.leftMargin = dp(12);
        searchRow.addView(watch, watchParams);
        watch.setOnClickListener(v -> openChannel(channelInput.getText().toString()));
        channelInput.setOnEditorActionListener((view, actionId, event) -> {
            openChannel(channelInput.getText().toString());
            return true;
        });

        Button favorite = makeButton("☆ GUARDAR");
        LinearLayout.LayoutParams favoriteParams = new LinearLayout.LayoutParams(dp(150), dp(52));
        favoriteParams.leftMargin = dp(8);
        searchRow.addView(favorite, favoriteParams);
        favorite.setOnClickListener(v -> toggleFavorite(channelInput.getText().toString()));
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        searchParams.topMargin = dp(22);
        root.addView(searchRow, searchParams);

        addChannelSection("TUS FAVORITOS", FAVORITES);
        addChannelSection("VISTOS RECIENTEMENTE", RECENTS);

        TextView footer = makeText("OK: abrir canal · Mantener OK: quitar de favoritos o historial · Android 5.1+", 12, false);
        LinearLayout.LayoutParams footerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        footerParams.topMargin = dp(12);
        root.addView(footer, footerParams);

        ScrollView page = new ScrollView(this);
        page.setFillViewport(true);
        page.addView(root);
        setContentView(page);
        channelInput.requestFocus();
    }

    private void addChannelSection(String heading, String key) {
        TextView label = makeText(heading, 14, true);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.topMargin = dp(20);
        root.addView(label, labelParams);

        HorizontalScrollView scroller = new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        List<String> channels = readList(key);
        if (channels.isEmpty()) {
            TextView empty = makeText(key.equals(FAVORITES)
                    ? "Todavía no guardaste canales" : "Los canales que abras aparecerán aquí", 13, false);
            LinearLayout.LayoutParams emptyParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(48));
            emptyParams.gravity = Gravity.CENTER_VERTICAL;
            row.addView(empty, emptyParams);
        } else {
            for (String channel : channels) {
                Button item = makeButton(channel);
                LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(dp(150), dp(48));
                itemParams.rightMargin = dp(8);
                row.addView(item, itemParams);
                item.setOnClickListener(v -> openChannel(channel));
                item.setOnLongClickListener(v -> {
                    removeChannel(key, channel);
                    return true;
                });
            }
        }
        scroller.addView(row);
        LinearLayout.LayoutParams scrollerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        scrollerParams.topMargin = dp(6);
        root.addView(scroller, scrollerParams);
    }

    private void removeChannel(String key, String channel) {
        List<String> values = readList(key);
        if (!values.remove(channel)) return;
        saveList(key, values);
        Toast.makeText(this, key.equals(FAVORITES)
                ? "Canal quitado de favoritos" : "Canal quitado del historial",
                Toast.LENGTH_SHORT).show();
        showHome();
    }

    private void toggleFavorite(String rawChannel) {
        String channel = rawChannel.trim();
        if (!CHANNEL_PATTERN.matcher(channel).matches()) {
            channelInput.setError("Escribe un nombre de canal válido");
            return;
        }
        List<String> values = readList(FAVORITES);
        if (values.contains(channel)) {
            values.remove(channel);
        } else {
            values.add(0, channel);
            while (values.size() > 12) values.remove(values.size() - 1);
        }
        saveList(FAVORITES, values);
        showHome();
        channelInput.setText(channel);
        channelInput.setSelection(channel.length());
    }

    private void openChannel(String rawChannel) {
        String channel = rawChannel.trim();
        if (!CHANNEL_PATTERN.matcher(channel).matches()) {
            if (channelInput != null) channelInput.setError("Escribe un nombre de canal válido");
            return;
        }
        addRecent(channel);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);

        webView = new WebView(this);
        webView.setBackgroundColor(Color.BLACK);
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(false);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.cancel();
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                customView = view;
                customViewCallback = callback;
                fullScreenContainer = new FrameLayout(MainActivity.this);
                fullScreenContainer.setBackgroundColor(Color.BLACK);
                fullScreenContainer.addView(view, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                applyImmersiveMode(true);
                setContentView(fullScreenContainer);
            }

            @Override
            public void onHideCustomView() {
                hideCustomView();
            }
        });

        root.addView(webView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        applyImmersiveMode(true);
        setContentView(root);
        webView.loadUrl(PLAYER_BASE + channel + "?autoplay=true&muted=false&allowfullscreen=true");
        webView.requestFocus();
    }

    private void hideCustomView() {
        if (customView == null) return;
        fullScreenContainer.removeView(customView);
        customView = null;
        setContentView(root);
        applyImmersiveMode(true);
        if (customViewCallback != null) customViewCallback.onCustomViewHidden();
        customViewCallback = null;
        if (webView != null) webView.requestFocus();
    }

    @Override
    public void onBackPressed() {
        if (customView != null) {
            hideCustomView();
            return;
        }
        if (webView != null) {
            showHome();
            return;
        }
        super.onBackPressed();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && customView != null) {
            onBackPressed();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.loadUrl("about:blank");
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
