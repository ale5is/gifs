package com.ale5is.pavokicktv;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.View;
import android.view.WindowManager;
import android.view.Gravity;
import android.view.KeyEvent;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.SslErrorHandler;
import android.net.http.SslError;
import android.view.ViewGroup;
import android.content.pm.ActivityInfo;
import android.graphics.Typeface;
import android.util.TypedValue;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private static final String PLAYER_BASE = "https://player.kick.com/";
    private static final Pattern CHANNEL_PATTERN = Pattern.compile("[A-Za-z0-9_]{1,25}");
    private LinearLayout root;
    private LinearLayout playerBar;
    private EditText channelInput;
    private WebView webView;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private FrameLayout fullScreenContainer;
    private boolean expanded;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        showHome();
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView makeText(String value, int size) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextColor(Color.WHITE);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        return text;
    }

    private void applyImmersiveMode(boolean enabled) {
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        if (enabled) {
            flags |= View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        }
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    private void showHome() {
        expanded = false;
        applyImmersiveMode(false);
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(36), dp(20), dp(36), dp(20));
        root.setBackgroundColor(Color.rgb(15, 17, 20));

        TextView title = makeText("PavoKick TV", 30);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView subtitle = makeText("Mira directos de KICK en tu televisor", 16);
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subParams.topMargin = dp(8);
        root.addView(subtitle, subParams);

        channelInput = new EditText(this);
        channelInput.setSingleLine(true);
        channelInput.setTextColor(Color.WHITE);
        channelInput.setHintTextColor(Color.LTGRAY);
        channelInput.setHint("Nombre del canal de KICK");
        channelInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        channelInput.setSelectAllOnFocus(true);
        channelInput.setFocusable(true);
        channelInput.setFocusableInTouchMode(true);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(dp(420), dp(58));
        inputParams.topMargin = dp(28);
        root.addView(channelInput, inputParams);

        Button watch = new Button(this);
        watch.setText("VER DIRECTO");
        watch.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(dp(260), dp(58));
        buttonParams.topMargin = dp(16);
        root.addView(watch, buttonParams);
        watch.setOnClickListener(v -> openChannel());

        TextView note = makeText("Versión experimental · Android 5.1 o superior", 12);
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        noteParams.topMargin = dp(24);
        root.addView(note, noteParams);

        setContentView(root);
        channelInput.requestFocus();
    }

    private void openChannel() {
        String channel = channelInput.getText().toString().trim();
        if (!CHANNEL_PATTERN.matcher(channel).matches()) {
            channelInput.setError("Escribe un nombre de canal válido");
            return;
        }

        expanded = false;
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);

        playerBar = new LinearLayout(this);
        playerBar.setGravity(Gravity.CENTER_VERTICAL);
        playerBar.setPadding(dp(8), 0, dp(8), 0);
        playerBar.setBackgroundColor(Color.rgb(20, 22, 25));

        TextView channelName = makeText("KICK  ·  " + channel, 16);
        channelName.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        playerBar.addView(channelName, new LinearLayout.LayoutParams(0, dp(48), 1));

        Button expand = new Button(this);
        expand.setText("PANTALLA COMPLETA");
        expand.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        expand.setOnClickListener(v -> setExpanded(true));
        playerBar.addView(expand, new LinearLayout.LayoutParams(dp(190), dp(44)));

        Button back = new Button(this);
        back.setText("VOLVER");
        back.setOnClickListener(v -> showHome());
        playerBar.addView(back, new LinearLayout.LayoutParams(dp(110), dp(44)));
        root.addView(playerBar);

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
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
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
        setContentView(root);

        String playerUrl = PLAYER_BASE + channel
                + "?autoplay=true&muted=false&allowfullscreen=true";
        webView.loadUrl(playerUrl);
        webView.requestFocus();
    }

    private void setExpanded(boolean value) {
        if (webView == null || playerBar == null || customView != null || expanded == value) return;
        expanded = value;
        if (value) {
            root.removeView(playerBar);
        } else {
            root.addView(playerBar, 0);
        }
        applyImmersiveMode(value);
        webView.requestFocus();
    }

    private void hideCustomView() {
        if (customView == null) return;
        fullScreenContainer.removeView(customView);
        customView = null;
        setContentView(root);
        applyImmersiveMode(expanded);
        if (customViewCallback != null) customViewCallback.onCustomViewHidden();
        customViewCallback = null;
        webView.requestFocus();
    }

    @Override
    public void onBackPressed() {
        if (customView != null) {
            hideCustomView();
            return;
        }
        if (expanded) {
            setExpanded(false);
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
        if (keyCode == KeyEvent.KEYCODE_BACK && (expanded || customView != null)) {
            onBackPressed();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
