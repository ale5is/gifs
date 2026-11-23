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
import android.widget.Toast;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import java.net.URL;
import java.util.List;

public class ChannelHistoryActivity extends Activity {
    public static final String EXTRA_CHANNEL = "channel";
    private LinearLayout list;
    private String channel;
    private boolean liveDisplayed;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        channel=getIntent().getStringExtra(EXTRA_CHANNEL);
        if(channel==null || channel.trim().length()==0){finish();return;}
        buildUi();loadVideos();
    }
    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}
    private TextView text(String value,int size,boolean bold){
        TextView t=new TextView(this);t.setText(value);t.setTextColor(Color.WHITE);t.setTextSize(size);
        if(bold)t.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);
        return t;
    }
    private void addButtonFocus(Button button) {
        button.setFocusable(true);
        button.setFocusableInTouchMode(true);
        final android.graphics.drawable.Drawable normalBackground = button.getBackground();
        button.setOnFocusChangeListener((view, focused) -> {
            if (focused) {
                android.graphics.drawable.GradientDrawable selected = new android.graphics.drawable.GradientDrawable();
                selected.setColor(0xFF263D50);
                selected.setCornerRadius(dp(6));
                selected.setStroke(dp(3), 0xFF42B8F4);
                view.setBackground(selected);
                view.setScaleX(1.04f);
                view.setScaleY(1.04f);
            } else {
                view.setBackground(normalBackground);
                view.setScaleX(1f);
                view.setScaleY(1f);
            }
        });
    }
    private void buildUi(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(30),dp(16),dp(30),dp(16));root.setBackgroundColor(Color.rgb(15,17,20));
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        Button back=new Button(this);back.setText("VOLVER");back.setOnClickListener(v->finish());addButtonFocus(back);header.addView(back);
        header.addView(text("  VIDEOS ANTERIORES · "+channel,21,true),new LinearLayout.LayoutParams(0,-2,1));
        Button clips=new Button(this);clips.setText("CLIPS");addButtonFocus(clips);clips.setOnClickListener(v->{
            Intent i=new Intent(this,ChannelClipsActivity.class);i.putExtra(EXTRA_CHANNEL,channel);startActivity(i);
        });header.addView(clips);root.addView(header);
        root.addView(text("Selecciona una emisión para reproducirla",13,false));
        ScrollView scroll=new ScrollView(this);list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);scroll.addView(list);
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,0,1);sp.topMargin=dp(10);root.addView(scroll,sp);
        setContentView(root);
    }
    private void loadVideos(){
        list.removeAllViews();
        TextView loading=text("Comprobando directo y videos...",17,false);
        list.addView(loading);
        KickVodResolver.checkLive(channel,live->{
            if(isFinishing())return;
            list.removeAllViews();
            liveDisplayed=live;
            if(live)addLiveVideo();
            loadPreviousVideos();
        });
    }
    private void loadPreviousVideos(){
        KickVodResolver.load(channel,new KickVodResolver.ListCallback(){
            @Override public void onSuccess(List<KickVodResolver.Vod> videos){
                if(isFinishing())return;
                TextView heading=text("VIDEOS ANTERIORES · "+videos.size(),13,true);
                list.addView(heading);
                for(KickVodResolver.Vod vod:videos)addVideo(vod);
                if(liveDisplayed && list.getChildCount()>0)list.getChildAt(0).requestFocus();
                else if(list.getChildCount()>1)list.getChildAt(1).requestFocus();
                else if(list.getChildCount()>0)list.getChildAt(0).requestFocus();
            }
            @Override public void onError(String message){
                if(isFinishing())return;
                list.addView(text(message,15,false));
                Button retry=new Button(ChannelHistoryActivity.this);retry.setText("REINTENTAR");addButtonFocus(retry);retry.setOnClickListener(v->loadVideos());list.addView(retry);
                Toast.makeText(ChannelHistoryActivity.this,message,Toast.LENGTH_LONG).show();
            }
        });
    }
    private void addLiveVideo(){
        LinearLayout row=new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12),dp(12),dp(12),dp(12));
        row.setBackgroundColor(Color.rgb(31,35,40));
        TextView title=text("●  EN VIVO · "+channel,18,true);
        SpannableString liveTitle=new SpannableString(title.getText());
        liveTitle.setSpan(new ForegroundColorSpan(Color.rgb(50,210,100)),0,1,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        title.setText(liveTitle);
        row.addView(title,new LinearLayout.LayoutParams(0,dp(48),1));
        TextView hint=text("VER DIRECTO",13,true);
        row.addView(hint);
        row.setFocusable(true);
        row.setClickable(true);
        final android.graphics.drawable.Drawable normal=row.getBackground();
        row.setOnFocusChangeListener((view,focused)->{
            if(focused){
                android.graphics.drawable.GradientDrawable selected=new android.graphics.drawable.GradientDrawable();
                selected.setColor(0xFF263D50);
                selected.setCornerRadius(dp(7));
                selected.setStroke(dp(2),0xFF42B8F4);
                view.setBackground(selected);
            }else view.setBackground(normal);
        });
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,dp(76));
        params.bottomMargin=dp(10);
        list.addView(row,0,params);
        row.setOnClickListener(v->{
            Intent intent=new Intent(this,StreamPlayerActivity.class);
            intent.putExtra(StreamPlayerActivity.EXTRA_CHANNEL,channel);
            startActivity(intent);
        });
        row.requestFocus();
    }
    private void addVideo(KickVodResolver.Vod vod){
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(7),dp(6),dp(7),dp(6));row.setBackgroundColor(Color.rgb(31,35,40));
        ImageView image=new ImageView(this);image.setBackgroundColor(Color.rgb(55,60,66));image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        row.addView(image,new LinearLayout.LayoutParams(dp(190),dp(106)));
        if(vod.thumbnail!=null && vod.thumbnail.startsWith("http"))loadImage(image,vod.thumbnail);
        LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);info.setPadding(dp(12),0,0,0);
        info.addView(text(vod.title,16,true));info.addView(text(vod.date+"  ·  "+vod.duration+"  ·  "+vod.views+" vistas",12,false));
        row.addView(info,new LinearLayout.LayoutParams(0,-2,1));
        row.setFocusable(true);row.setClickable(true);
        row.setFocusableInTouchMode(false);
        final android.graphics.drawable.Drawable normalBackground=row.getBackground();
        row.setOnFocusChangeListener((view,focused)->{
            if(focused){
                android.graphics.drawable.GradientDrawable selected=new android.graphics.drawable.GradientDrawable();
                selected.setColor(0xFF263D50);
                selected.setCornerRadius(dp(7));
                selected.setStroke(dp(3),0xFF42B8F4);
                view.setBackground(selected);
                view.setScaleX(1.015f);
                view.setScaleY(1.015f);
            }else{
                view.setBackground(normalBackground);
                view.setScaleX(1f);
                view.setScaleY(1f);
            }
        });
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(120));p.topMargin=dp(7);list.addView(row,p);
        row.setOnClickListener(v->{
            Intent intent=new Intent(this,StreamPlayerActivity.class);
            intent.putExtra(StreamPlayerActivity.EXTRA_CHANNEL,channel);
            intent.putExtra(StreamPlayerActivity.EXTRA_VOD_UUID,vod.uuid);
            intent.putExtra(StreamPlayerActivity.EXTRA_VOD_TITLE,vod.title);
            intent.putExtra("vod_source",vod.source);
            startActivity(intent);
        });
    }
    private void loadImage(ImageView target,String url){
        target.setTag(url);
        new Thread(()->{
            try{
                java.net.HttpURLConnection c=(java.net.HttpURLConnection)new URL(url).openConnection();
                c.setConnectTimeout(10000);c.setReadTimeout(15000);
                c.setRequestProperty("User-Agent",KickStreamResolver.USER_AGENT);c.connect();
                android.graphics.Bitmap bitmap=BitmapFactory.decodeStream(c.getInputStream());c.disconnect();
                if(bitmap!=null)runOnUiThread(()->{if(url.equals(target.getTag()))target.setImageBitmap(bitmap);});
            }catch(Exception ignored){}
        },"KickVodThumbnail").start();
    }
}
