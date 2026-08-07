package com.game.bingo;

import static com.game.bingo.Settings.KEY_CURRENT_NAME;
import static com.game.bingo.Settings.KEY_SOUND_STATE;
import static com.game.bingo.Settings.PREF_NAME;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Window;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.BounceInterpolator;
import android.view.animation.TranslateAnimation;
import android.widget.Button;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.util.Objects;


public class Home extends AppCompatActivity {
    public static MediaPlayer mediaPlayer;
    private View mContentView;

    private boolean isSoundOn = true;


    @Override
    public void onBackPressed() {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.stop();
                mediaPlayer.release();
            } catch (Exception e) { e.printStackTrace(); }
            mediaPlayer = null;
        }
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Release static mediaPlayer to prevent music leaking after app cleared from recents
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.release();
            } catch (Exception e) { e.printStackTrace(); }
            mediaPlayer = null;
        }
    }

    public static Animation inFromBottomAnimation(long durationMillis) {
        Animation inFromBottom = new TranslateAnimation(
                Animation.RELATIVE_TO_PARENT, 0.0f, Animation.RELATIVE_TO_PARENT, 0.0f,
                Animation.RELATIVE_TO_PARENT, +1.0f, Animation.RELATIVE_TO_PARENT, 0.0f
        );
        inFromBottom.setDuration(durationMillis);
        return inFromBottom;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.home);
        Objects.requireNonNull(getSupportActionBar()).hide();
        Window window = getWindow();
        window.setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        window.setStatusBarColor(Color.TRANSPARENT);

        mContentView = findViewById(R.id.fullscreen_content);
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        if (wifiManager.isWifiEnabled())
            wifiManager.setWifiEnabled(false);

        ActivityCompat.requestPermissions(this, new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION}, 101);

        SharedPreferences prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        isSoundOn = prefs.getBoolean(KEY_SOUND_STATE, true);

        Button quit = findViewById(R.id.quit);
        Button start = findViewById(R.id.start);
        Button settings = findViewById(R.id.settings);
        ImageView imageView = findViewById(R.id.imageView);

        ObjectAnimator fadeImg1 = ObjectAnimator.ofFloat(imageView, "alpha", .3f, 1f);
        fadeImg1.setDuration(1000);

        fadeImg1.start();

        start.setAnimation(inFromBottomAnimation(1500));
        settings.setAnimation(inFromBottomAnimation(2000));
        quit.setAnimation(inFromBottomAnimation(2500));

        ObjectAnimator animY = ObjectAnimator.ofFloat(start, "translationY", -20f, 20f);
        animY.setDuration(1000);
        animY.setInterpolator(new BounceInterpolator());
        animY.setRepeatCount(100);
        animY.setRepeatMode(ValueAnimator.REVERSE);
        animY.start();

        quit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (wifiManager.isWifiEnabled())
                    wifiManager.setWifiEnabled(false);
                if (mediaPlayer != null) {
                    try {
                        mediaPlayer.stop();
                        mediaPlayer.release();
                    } catch (Exception e) { e.printStackTrace(); }
                    mediaPlayer = null;
                }
                finish();
            }
        });

        start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Load the currentName from SharedPreferences
                SharedPreferences prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
                String currentName = prefs.getString(KEY_CURRENT_NAME, null);

                // Check if WiFi is enabled
                WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                if (wifiManager != null && !wifiManager.isWifiEnabled()) {
                    // WiFi is not enabled, so turn it on
                    wifiManager.setWifiEnabled(true);
                }

                Intent intent;
                if (currentName == null || currentName.equals("Default Name")) {
                    // If currentName is null or "Default Name", go to Name.class
                    intent = new Intent(Home.this, Name.class);
                } else {
                    // If currentName is not null and not "Default Name", go to Connect.class
                    intent = new Intent(Home.this, Connect.class);
                }
                startActivity(intent, ActivityOptions.makeSceneTransitionAnimation(Home.this).toBundle());
            }
        });


        settings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(com.game.bingo.Home.this, Settings.class);
                startActivity(intent, ActivityOptions.makeSceneTransitionAnimation(com.game.bingo.Home.this).toBundle());
            }
        });

        // Release any existing player before creating a new one
        if (mediaPlayer != null) {
            try {
                mediaPlayer.stop();
                mediaPlayer.release();
            } catch (Exception e) { e.printStackTrace(); }
        }
        mediaPlayer = MediaPlayer.create(getApplicationContext(), R.raw.sound1);

        if (mediaPlayer != null) {
            // Mute or unmute the MediaPlayer based on the sound state
            if (isSoundOn) {
                mediaPlayer.setVolume(1.0f, 1.0f);
            } else {
                mediaPlayer.setVolume(0.0f, 0.0f);
            }
            mediaPlayer.setLooping(true);
            mediaPlayer.start();
        }

    }
}
