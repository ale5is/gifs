package com.ale5is.pavokicktv;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.net.URL;
import java.util.List;

public class ChannelClipsActivity extends Activity {
    private LinearLayout list;
    private String channel;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        channel=getIntent().getStringExtra(ChannelHistoryActivity.EXTRA_CHANNEL);
        if(channel==null || channel.trim().length()==0){finish();return;}
        buildUi();
        load();
    }
    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}
    private TextView text(String value,int size,boolean bold){
        TextView t=new TextView(this);t.setText(value);t.setTextColor(Color.WHITE);t.setTextSize(size);
        if(bold)t.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);
        return t;
    }
    private void buildUi(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(30),dp(16),dp(30),dp(16));root.setBackgroundColor(Color.rgb(15,17,20));
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        Button back=new Button(this);back.setText("VOLVER");back.setOnClickListener(v->finish());header.addView(back);
        header.addView(text("  CLIPS · "+channel,22,true),new LinearLayout.LayoutParams(0,-2,1));root.addView(header);
        ScrollView scroll=new ScrollView(this);list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);scroll.addView(list);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
    }
    private void load(){
        list.removeAllViews();list.addView(text("Cargando clips de KICK...",17,false));
        KickClipsResolver.load(channel,new KickClipsResolver.Callback(){
            @Override public void onSuccess(List<KickClipsResolver.Clip> clips){
                if(isFinishing())return;list.removeAllViews();list.addView(text(clips.size()+" clips encontrados",13,false));
                for(KickClipsResolver.Clip clip:clips)addClip(clip);
                if(list.getChildCount()>1)list.getChildAt(1).requestFocus();
            }
            @Override public void onError(String message){
                if(isFinishing())return;list.removeAllViews();list.addView(text(message,17,false));
                Button retry=new Button(ChannelClipsActivity.this);retry.setText("REINTENTAR");retry.setOnClickListener(v->load());list.addView(retry);
            }
        });
    }
    private void addClip(KickClipsResolver.Clip clip){
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(8),dp(6),dp(8),dp(6));
        row.setBackgroundColor(Color.rgb(31,35,40));
        ImageView image=new ImageView(this);image.setBackgroundColor(Color.rgb(55,60,66));
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        row.addView(image,new LinearLayout.LayoutParams(dp(190),dp(106)));
        if(clip.thumbnail!=null && clip.thumbnail.startsWith("http"))loadImage(image,clip.thumbnail);
        LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);info.setPadding(dp(12),0,0,0);
        info.addView(text(clip.title,16,true));
        info.addView(text(clip.date+"  ·  "+clip.duration+"  ·  "+clip.views+" vistas",12,false));
        row.addView(info,new LinearLayout.LayoutParams(0,-2,1));
        row.setFocusable(true);row.setClickable(true);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(120));p.topMargin=dp(7);list.addView(row,p);
        row.setOnClickListener(v->{
            if(clip.playback==null || !clip.playback.startsWith("http") || !clip.playback.contains(".m3u8")){
                android.widget.Toast.makeText(this,"KICK no entregó la URL reproducible de este clip.",android.widget.Toast.LENGTH_LONG).show();return;
            }
            Intent intent=new Intent(this,StreamPlayerActivity.class);
            intent.putExtra(StreamPlayerActivity.EXTRA_CHANNEL,channel);
            intent.putExtra(StreamPlayerActivity.EXTRA_VOD_TITLE,clip.title);
            intent.putExtra(StreamPlayerActivity.EXTRA_MEDIA_URL,clip.playback);
            startActivity(intent);
        });
    }
    private void loadImage(ImageView target,String url){
        target.setTag(url);
        new Thread(()->{
            try{
                java.net.HttpURLConnection connection=(java.net.HttpURLConnection)new URL(url).openConnection();
                connection.setConnectTimeout(10000);connection.setReadTimeout(15000);
                connection.setRequestProperty("User-Agent",KickStreamResolver.USER_AGENT);
                connection.connect();
                android.graphics.Bitmap bitmap=BitmapFactory.decodeStream(connection.getInputStream());
                connection.disconnect();
                if(bitmap!=null)runOnUiThread(()->{if(url.equals(target.getTag()))target.setImageBitmap(bitmap);});
            }catch(Exception ignored){}
        },"KickClipThumbnail").start();
    }
}
