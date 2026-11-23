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
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
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
    private View centerFocus;
    private boolean controlsHiddenByCenter;
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

        centerFocus=new View(this);
        centerFocus.setFocusable(true);centerFocus.setFocusableInTouchMode(true);
        centerFocus.setClickable(true);centerFocus.setContentDescription("Zona central: ocultar o mostrar controles");
        centerFocus.setBackgroundColor(Color.TRANSPARENT);
        FrameLayout.LayoutParams centerParams=new FrameLayout.LayoutParams(dp(76),dp(76),Gravity.CENTER);
        frame.addView(centerFocus,centerParams);
        centerFocus.setOnFocusChangeListener((view,focused)->{
            if(focused)hideHudFromCenter();
        });
        centerFocus.setOnClickListener(v->showHudFromCenter());

        topBar=new LinearLayout(this);topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);topBar.setPadding(8,5,8,5);
        topBar.setBackgroundColor(0x99000000);
        Button back=button("VOLVER");back.setOnClickListener(v->finish());topBar.addView(back);
        TextView name=new TextView(this);name.setText("  "+(vodTitle!=null?vodTitle:channel));
        name.setTextColor(Color.WHITE);name.setTextSize(14);
        topBar.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        Button quality=button("CALIDAD");quality.setOnClickListener(this::showQualityMenu);topBar.addView(quality);
        Button speed=button("VELOCIDAD");speed.setOnClickListener(this::showSpeedMenu);topBar.addView(speed);
        Button volDown=button("VOL −");volDown.setOnClickListener(v->changeVolume(false));topBar.addView(volDown);
        Button volUp=button("VOL +");volUp.setOnClickListener(v->changeVolume(true));topBar.addView(volUp);
        FrameLayout.LayoutParams topParams=new FrameLayout.LayoutParams(-1,-2,Gravity.TOP);frame.addView(topBar,topParams);

        status=new TextView(this);status.setText("Conectando con KICK...");
        status.setTextColor(Color.WHITE);status.setTextSize(16);status.setGravity(Gravity.CENTER);
        frame.addView(status,new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER));
        setContentView(frame);

        for(int i=0;i<topBar.getChildCount();i++){
            View child=topBar.getChildAt(i);
            if(child.isFocusable())child.setNextFocusDownId(centerFocus.getId());
        }
        playerView.setControllerVisibilityListener(visibility->{
            if(topBar!=null && !controlsHiddenByCenter)topBar.setVisibility(visibility);
        });

        trackSelector=new DefaultTrackSelector(this);
        applyQuality(getSharedPreferences(PREFS,MODE_PRIVATE).getInt(PREF_QUALITY,0),false);
        DefaultHttpDataSource.Factory dataSourceFactory=new DefaultHttpDataSource.Factory()
                .setUserAgent(KickStreamResolver.USER_AGENT).setConnectTimeoutMs(15000)
                .setReadTimeoutMs(20000).setAllowCrossProtocolRedirects(true);
        player=new ExoPlayer.Builder(this).setTrackSelector(trackSelector)
                .setMediaSourceFactory(new com.google.android.exoplayer2.source.DefaultMediaSourceFactory(dataSourceFactory)).build();
        playerView.setPlayer(player);
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
    private Button button(String label){Button b=new Button(this);b.setText(label);b.setTextSize(11);b.setFocusable(true);return b;}

    private void hideHudFromCenter(){
        controlsHiddenByCenter=true;
        if(playerView!=null)playerView.hideController();
        if(topBar!=null)topBar.setVisibility(View.GONE);
    }
    private void showHudFromCenter(){
        controlsHiddenByCenter=false;
        if(topBar!=null)topBar.setVisibility(View.VISIBLE);
        if(playerView!=null)playerView.showController();
        View play=playerView==null?null:playerView.findViewById(com.google.android.exoplayer2.ui.R.id.exo_play);
        if(play==null && playerView!=null)play=playerView.findViewById(com.google.android.exoplayer2.ui.R.id.exo_pause);
        if(play!=null)play.requestFocus();
        else if(topBar!=null && topBar.getChildCount()>0)topBar.getChildAt(0).requestFocus();
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
    private void changeVolume(boolean up){
        AudioManager audio=(AudioManager)getSystemService(Context.AUDIO_SERVICE);
        if(audio!=null)audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                up?AudioManager.ADJUST_RAISE:AudioManager.ADJUST_LOWER,AudioManager.FLAG_SHOW_UI);
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        int key=event.getKeyCode();
        if(event.getAction()==KeyEvent.ACTION_DOWN && centerFocus!=null && centerFocus.hasFocus()){
            if(key==KeyEvent.KEYCODE_DPAD_CENTER || key==KeyEvent.KEYCODE_ENTER){
                showHudFromCenter();return true;
            }
            if(key==KeyEvent.KEYCODE_DPAD_UP){
                showHudFromCenter();return true;
            }
            if(key==KeyEvent.KEYCODE_DPAD_DOWN || key==KeyEvent.KEYCODE_DPAD_LEFT || key==KeyEvent.KEYCODE_DPAD_RIGHT){
                hideHudFromCenter();return true;
            }
        }
        if(event.getAction()==KeyEvent.ACTION_DOWN && (key==KeyEvent.KEYCODE_VOLUME_UP || key==KeyEvent.KEYCODE_VOLUME_DOWN))
            return super.dispatchKeyEvent(event);
        if(playerView!=null && !playerView.isControllerVisible()){
            if(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER||key==KeyEvent.KEYCODE_DPAD_LEFT
                    ||key==KeyEvent.KEYCODE_DPAD_RIGHT||key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN){
                controlsHiddenByCenter=false;playerView.showController();
                if(topBar!=null)topBar.setVisibility(View.VISIBLE);return true;
            }
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
