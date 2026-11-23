package com.ale5is.pavokicktv;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.media.AudioManager;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.PopupWindow;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;

public class StreamPlayerActivity extends Activity {
    public static final String EXTRA_CHANNEL = "channel";
    public static final String EXTRA_VOD_UUID = "vod_uuid";
    public static final String EXTRA_VOD_TITLE = "vod_title";
    public static final String EXTRA_MEDIA_URL = "media_url";
    private static final String PREFS = "pavokicktv";
    private static final String PREF_QUALITY = "playback_quality";

    private ExoPlayer player;
    private DefaultTrackSelector trackSelector;
    private PlayerView playerView;
    private TextView status;
    private LinearLayout topBar;
    private SeekBar volumeSlider;
    private PopupWindow volumePopup;
    private View volumeButton;
    private final java.util.Set<View> highlightedViews=new java.util.HashSet<>();
    private boolean controlsHiddenByCenter;
    private int navigationZone=0;
    private String channel, vodUuid, vodTitle, directMediaUrl;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        channel=getIntent().getStringExtra(EXTRA_CHANNEL);
        vodUuid=getIntent().getStringExtra(EXTRA_VOD_UUID);
        vodTitle=getIntent().getStringExtra(EXTRA_VOD_TITLE);
        directMediaUrl=getIntent().getStringExtra(EXTRA_MEDIA_URL);
        if(channel==null || channel.trim().length()==0){finish();return;}
        buildPlayerUi();resolveAndPlay();
    }

    private void buildPlayerUi() {
        FrameLayout frame=new FrameLayout(this);frame.setBackgroundColor(Color.BLACK);
        playerView=new PlayerView(this);playerView.setUseController(true);
        playerView.setControllerAutoShow(true);playerView.setControllerHideOnTouch(true);
        playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS);
        frame.addView(playerView,new FrameLayout.LayoutParams(-1,-1));

 
        topBar=new LinearLayout(this);topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);topBar.setPadding(8,5,8,5);
        topBar.setBackgroundColor(0x99000000);
        Button back=button("VOLVER");back.setOnClickListener(v->finish());topBar.addView(back);
        TextView name=new TextView(this);name.setText("  "+(vodTitle!=null?vodTitle:channel));
        name.setTextColor(Color.WHITE);name.setTextSize(14);
        topBar.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        Button quality=button("HD");quality.setContentDescription("Calidad de video");quality.setTextSize(12);quality.setOnClickListener(this::showQualityMenu);topBar.addView(quality);
        Button speed=button("1x");speed.setContentDescription("Velocidad de reproducción");speed.setTextSize(12);speed.setOnClickListener(this::showSpeedMenu);topBar.addView(speed);
        Button speaker=button("VOL");
        speaker.setTextSize(12);
        speaker.setContentDescription("Volumen");
        volumeButton=speaker;
        speaker.setOnClickListener(v->toggleVolumeSlider(speaker));
        topBar.addView(speaker);
        FrameLayout.LayoutParams topParams=new FrameLayout.LayoutParams(-1,-2,Gravity.TOP);frame.addView(topBar,topParams);

        status=new TextView(this);status.setText("Conectando con KICK...");
        status.setTextColor(Color.WHITE);status.setTextSize(16);status.setGravity(Gravity.CENTER);
        frame.addView(status,new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER));
        setContentView(frame);

        for(int i=0;i<topBar.getChildCount();i++){
            View child=topBar.getChildAt(i);
            if(child.isFocusable())addFocusHighlight(child);
        }
        int[] playbackControlIds = {
                com.google.android.exoplayer2.ui.R.id.exo_play,
                com.google.android.exoplayer2.ui.R.id.exo_pause,
                com.google.android.exoplayer2.ui.R.id.exo_ffwd,
                com.google.android.exoplayer2.ui.R.id.exo_rew,
                com.google.android.exoplayer2.ui.R.id.exo_prev,
                com.google.android.exoplayer2.ui.R.id.exo_next,
                com.google.android.exoplayer2.ui.R.id.exo_settings
        };
        for(int id:playbackControlIds){
            View control=playerView.findViewById(id);
            if(control!=null && control.isFocusable())addFocusHighlight(control);
        }
        playerView.setControllerVisibilityListener(visibility->{
            if(topBar!=null)topBar.setVisibility(navigationZone==0?View.VISIBLE:View.GONE);
        });

        trackSelector=new DefaultTrackSelector(this);
        applyQuality(getSharedPreferences(PREFS,MODE_PRIVATE).getInt(PREF_QUALITY,0),false);
        DefaultHttpDataSource.Factory dataSourceFactory=new DefaultHttpDataSource.Factory()
                .setUserAgent(KickStreamResolver.USER_AGENT).setConnectTimeoutMs(15000)
                .setReadTimeoutMs(20000).setAllowCrossProtocolRedirects(true);
        player=new ExoPlayer.Builder(this).setTrackSelector(trackSelector)
                .setMediaSourceFactory(new com.google.android.exoplayer2.source.DefaultMediaSourceFactory(dataSourceFactory)).build();
        playerView.setPlayer(player);
        playerView.post(()->attachPlayerControlHighlights());
        player.addListener(new Player.Listener(){
            @Override public void onPlaybackStateChanged(int state){
                if(state==Player.STATE_READY)status.setVisibility(View.GONE);
                if(state==Player.STATE_BUFFERING){status.setText("Cargando video...");status.setVisibility(View.VISIBLE);}
                if(state==Player.STATE_ENDED){status.setText("La reproducción terminó.");status.setVisibility(View.VISIBLE);}
            }
            @Override public void onPlayerError(PlaybackException error){
                status.setText("No se pudo reproducir el contenido. KICK puede haber cambiado su API.");
                status.setVisibility(View.VISIBLE);
            }
        });
    }
    private int dp(float value){return (int)(value*getResources().getDisplayMetrics().density+0.5f);}
    private void addFocusHighlight(View view){
        if(view==null || highlightedViews.contains(view))return;
        highlightedViews.add(view);
        final android.graphics.drawable.Drawable original=view.getBackground();
        view.setOnFocusChangeListener((v,focused)->{
            if(focused){
                navigationZone=(v.getParent()==topBar || isDescendantOf(v,topBar))?0:2;
                controlsHiddenByCenter=false;
                if(topBar!=null)topBar.setVisibility(navigationZone==0?View.VISIBLE:View.GONE);
                android.graphics.drawable.GradientDrawable outline=new android.graphics.drawable.GradientDrawable();
                outline.setColor(0x6633B5E5);outline.setCornerRadius(dp(6));
                outline.setStroke(dp(4),0xFFFFFFFF);v.setBackground(outline);
                v.setScaleX(1.08f);v.setScaleY(1.08f);
            }else{
                v.setBackground(original);v.setScaleX(1f);v.setScaleY(1f);
            }
        });
    }
    private boolean isDescendantOf(View child,ViewGroup parent){
        android.view.ViewParent p=child.getParent();
        while(p!=null){if(p==parent)return true;if(!(p instanceof android.view.View))break;p=((android.view.View)p).getParent();}
        return false;
    }
    private void attachPlayerControlHighlights(){
        int[] ids={com.google.android.exoplayer2.ui.R.id.exo_play,com.google.android.exoplayer2.ui.R.id.exo_pause,
                com.google.android.exoplayer2.ui.R.id.exo_ffwd,com.google.android.exoplayer2.ui.R.id.exo_rew,
                com.google.android.exoplayer2.ui.R.id.exo_prev,com.google.android.exoplayer2.ui.R.id.exo_next,
                com.google.android.exoplayer2.ui.R.id.exo_settings};
        for(int id:ids){View v=playerView.findViewById(id);if(v!=null)addFocusHighlight(v);}
    }
    private Button button(String label){Button b=new Button(this);b.setText(label);b.setTextSize(11);b.setFocusable(true);return b;}

    private void hideHud(){
        controlsHiddenByCenter=true;navigationZone=1;
        if(playerView!=null)playerView.hideController();
        if(topBar!=null)topBar.setVisibility(View.GONE);
    }
    private void showTopZone(){
        controlsHiddenByCenter=false;navigationZone=0;
        if(topBar!=null)topBar.setVisibility(View.VISIBLE);
        if(playerView!=null)playerView.hideController();
        if(topBar!=null && topBar.getChildCount()>0)topBar.getChildAt(0).requestFocus();
    }
    private void showBottomZone(){
        controlsHiddenByCenter=false;navigationZone=2;
        if(topBar!=null)topBar.setVisibility(View.GONE);
        if(playerView!=null)playerView.showController();
        View target=playerView==null?null:playerView.findViewById(player!=null && player.isPlaying()
                ?com.google.android.exoplayer2.ui.R.id.exo_pause:com.google.android.exoplayer2.ui.R.id.exo_play);
        if(target==null && playerView!=null)target=playerView.findViewById(com.google.android.exoplayer2.ui.R.id.exo_play);
        if(target!=null)target.requestFocus();
    }

    private void resolveAndPlay(){
        if(directMediaUrl!=null && directMediaUrl.startsWith("http")){playResolvedUrl(directMediaUrl);return;}
        if(vodUuid!=null && vodUuid.length()>0){
            status.setText("Preparando video anterior...");
            KickVodResolver.resolvePlayback(vodUuid,getIntent().getStringExtra("vod_source"),new KickVodResolver.PlaybackCallback(){
                @Override public void onSuccess(String url){runOnUiThread(()->playResolvedUrl(url));}
                @Override public void onError(String message){runOnUiThread(()->showPlaybackError(message));}
            });return;
        }
        KickStreamResolver.resolve(channel,new KickStreamResolver.Callback(){
            @Override public void onSuccess(String url){runOnUiThread(()->playResolvedUrl(url));}
            @Override public void onError(String message){runOnUiThread(()->showPlaybackError(message));}
        });
    }
    private void playResolvedUrl(String url){
        if(isFinishing()||player==null)return;
        MediaItem item=new MediaItem.Builder().setUri(url)
                .setMimeType(com.google.android.exoplayer2.util.MimeTypes.APPLICATION_M3U8).build();
        player.setMediaItem(item);player.prepare();player.setPlayWhenReady(true);
        status.setText("Iniciando reproducción...");status.setVisibility(View.VISIBLE);
    }
    private void showPlaybackError(String message){
        if(isFinishing())return;status.setText(message);status.setVisibility(View.VISIBLE);
        Toast.makeText(this,message,Toast.LENGTH_LONG).show();
    }
    private void showQualityMenu(View anchor){
        PopupMenu menu=new PopupMenu(this,anchor);
        menu.getMenu().add(0,0,0,"Automática");
        menu.getMenu().add(0,1080,1,"Hasta 1080p");
        menu.getMenu().add(0,720,2,"Hasta 720p");
        menu.getMenu().add(0,480,3,"Hasta 480p");
        menu.getMenu().add(0,360,4,"Hasta 360p");
        menu.setOnMenuItemClickListener(item->{applyQuality(item.getItemId(),true);return true;});menu.show();
    }
    private void applyQuality(int height,boolean save){
        if(trackSelector==null)return;
        DefaultTrackSelector.Parameters.Builder p=trackSelector.buildUponParameters();
        if(height==0)p.setMaxVideoSize(Integer.MAX_VALUE,Integer.MAX_VALUE);
        else p.setMaxVideoSize(Integer.MAX_VALUE,height);
        trackSelector.setParameters(p);
        if(save)getSharedPreferences(PREFS,MODE_PRIVATE).edit().putInt(PREF_QUALITY,height).apply();
        String label=height==0?"Automática":"Hasta "+height+"p";
        if(save)Toast.makeText(this,"Calidad guardada: "+label,Toast.LENGTH_SHORT).show();
    }
    private void showSpeedMenu(View anchor){
        PopupMenu menu=new PopupMenu(this,anchor);
        menu.getMenu().add(0,50,0,"0,5×");menu.getMenu().add(0,75,1,"0,75×");
        menu.getMenu().add(0,100,2,"1× Normal");menu.getMenu().add(0,125,3,"1,25×");
        menu.getMenu().add(0,150,4,"1,5×");menu.getMenu().add(0,200,5,"2×");
        menu.setOnMenuItemClickListener(item->{
            if(player!=null){float rate=item.getItemId()/100f;player.setPlaybackParameters(new PlaybackParameters(rate));
                Toast.makeText(this,"Velocidad: "+item.getTitle(),Toast.LENGTH_SHORT).show();}
            return true;
        });menu.show();
    }
    private void toggleVolumeSlider(View anchor){
        if(volumePopup!=null && volumePopup.isShowing()){
            volumePopup.dismiss();
            return;
        }
        LinearLayout panel=new LinearLayout(this);
        panel.setOrientation(LinearLayout.HORIZONTAL);
        panel.setGravity(Gravity.CENTER_VERTICAL);
        panel.setPadding(dp(12),dp(8),dp(12),dp(8));
        panel.setBackgroundColor(0xEE202020);
        TextView icon=new TextView(this);
        icon.setText("VOL");icon.setTextColor(Color.WHITE);icon.setTextSize(11);
        icon.setGravity(Gravity.CENTER);
        panel.addView(icon,new LinearLayout.LayoutParams(dp(36),dp(48)));
        volumeSlider=new SeekBar(this);
        AudioManager audio=(AudioManager)getSystemService(Context.AUDIO_SERVICE);
        int maxVolume=audio==null?15:audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        volumeSlider.setMax(Math.max(1,maxVolume));
        volumeSlider.setPadding(dp(8),0,dp(8),0);
        volumeSlider.setThumbOffset(dp(12));
        if(audio!=null)volumeSlider.setProgress(audio.getStreamVolume(AudioManager.STREAM_MUSIC));
        volumeSlider.setFocusable(true);
        volumeSlider.setContentDescription("Nivel de volumen");
        volumeSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar seekBar,int progress,boolean fromUser){
                if(fromUser){
                    AudioManager manager=(AudioManager)getSystemService(Context.AUDIO_SERVICE);
                    if(manager!=null){
                        manager.setStreamVolume(AudioManager.STREAM_MUSIC,progress,0);
                    }
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar){}
            @Override public void onStopTrackingTouch(SeekBar seekBar){}
        });
        FrameLayout sliderContainer=new FrameLayout(this);
        panel.addView(sliderContainer,new LinearLayout.LayoutParams(dp(180),dp(48)));
        FrameLayout.LayoutParams sliderLayout=new FrameLayout.LayoutParams(-1,dp(40),Gravity.CENTER_VERTICAL);
        sliderContainer.addView(volumeSlider,sliderLayout);
        volumePopup=new PopupWindow(panel,dp(250),dp(64),true);
        volumePopup.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0xEE202020));
        volumePopup.setOutsideTouchable(true);
        volumePopup.setOnDismissListener(()->{
            volumePopup=null;
            if(volumeButton!=null && navigationZone==0)volumeButton.requestFocus();
        });
        volumePopup.showAsDropDown(anchor,0,dp(4));
        volumeSlider.requestFocus();
    }
    private void changeVolume(boolean up){
        AudioManager audio=(AudioManager)getSystemService(Context.AUDIO_SERVICE);
        if(audio!=null)audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                up?AudioManager.ADJUST_RAISE:AudioManager.ADJUST_LOWER,AudioManager.FLAG_SHOW_UI);
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        int key=event.getKeyCode();
        if(event.getAction()==KeyEvent.ACTION_DOWN){
            if(key==KeyEvent.KEYCODE_VOLUME_UP || key==KeyEvent.KEYCODE_VOLUME_DOWN)
                return super.dispatchKeyEvent(event);
            if(key==KeyEvent.KEYCODE_DPAD_UP){
                if(navigationZone==2){hideHud();return true;}
                if(navigationZone==1){showTopZone();return true;}
            }
            if(key==KeyEvent.KEYCODE_DPAD_DOWN){
                if(navigationZone==0){hideHud();return true;}
                if(navigationZone==1){showBottomZone();return true;}
            }
            if(navigationZone==1 && (key==KeyEvent.KEYCODE_DPAD_CENTER || key==KeyEvent.KEYCODE_ENTER))
                return true;
            if(navigationZone==1 && (key==KeyEvent.KEYCODE_DPAD_LEFT || key==KeyEvent.KEYCODE_DPAD_RIGHT))
                return true;
        }
        return super.dispatchKeyEvent(event);
    }
    @Override public void onBackPressed(){finish();}
    @Override protected void onStop(){super.onStop();if(player!=null)player.pause();}
    @Override protected void onDestroy(){
        if(playerView!=null)playerView.setPlayer(null);
        if(player!=null){player.release();player=null;}super.onDestroy();
    }
}
