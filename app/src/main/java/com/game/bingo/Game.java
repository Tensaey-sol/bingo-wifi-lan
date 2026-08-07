package com.game.bingo;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TableLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.net.wifi.WifiManager;

import androidx.appcompat.app.ActionBar;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;


import static com.game.bingo.Home.mediaPlayer;
import static com.game.bingo.Settings.KEY_SOUND_STATE;
import static com.game.bingo.Settings.PREF_NAME;
import static com.game.bingo.Start.ignoreTurn;
import static com.game.bingo.Start.turn;


/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class Game extends Card {
    /**
     * Whether or not the system UI should be auto-hidden after
     * {@link #AUTO_HIDE_DELAY_MILLIS} milliseconds.
     */

    private static final boolean AUTO_HIDE = true;

    private boolean isSoundOn = true;
    int bingoSum = 0;
    int flag = 0;
    public static int currentTurn = 0;
    public static int bingoNum = 0;
    private boolean isRemoteClick = false;

    public static Animation inFromRightAnimation(long durationMillis) {
        Animation inFromRight = new TranslateAnimation(
                Animation.RELATIVE_TO_PARENT,  +1.0f, Animation.RELATIVE_TO_PARENT,  0.0f,
                Animation.RELATIVE_TO_PARENT,  0.0f, Animation.RELATIVE_TO_PARENT,   0.0f
        );
        inFromRight.setDuration(durationMillis);
        return inFromRight;
    }

    public static Animation inFromLeftAnimation(long durationMillis) {
        Animation inFromLeft = new TranslateAnimation(
                Animation.RELATIVE_TO_PARENT,  -1.0f, Animation.RELATIVE_TO_PARENT,  0.0f,
                Animation.RELATIVE_TO_PARENT,  0.0f, Animation.RELATIVE_TO_PARENT,   0.0f
        );
        inFromLeft.setDuration(durationMillis);
        return inFromLeft;
    }

    public static Animation inFromTopAnimation(long durationMillis) {
        Animation inFromTop = new TranslateAnimation(
                Animation.RELATIVE_TO_PARENT,  0.0f, Animation.RELATIVE_TO_PARENT,  0.0f,
                Animation.RELATIVE_TO_PARENT,  -1.0f, Animation.RELATIVE_TO_PARENT,   0.0f
        );
        inFromTop.setDuration(durationMillis);
        return inFromTop;
    }

    public static Animation inFromBottomAnimation(long durationMillis) {

        Animation inFromBottom = new TranslateAnimation(
                Animation.RELATIVE_TO_PARENT,  0.0f, Animation.RELATIVE_TO_PARENT,  0.0f,
                Animation.RELATIVE_TO_PARENT,  +1.0f, Animation.RELATIVE_TO_PARENT,   0.0f
        );
        inFromBottom.setDuration(durationMillis);
        return inFromBottom;
    }

    /**
     * If {@link #AUTO_HIDE} is set, the number of milliseconds to wait after
     * user interaction before hiding the system UI.
     */
    private static final int AUTO_HIDE_DELAY_MILLIS = 3000;

    /**
     * Some older devices needs a small delay between UI widget updates
     * and a change of the status and navigation bar.
     */
    private final Handler mHideHandler = new Handler();
    public View mContentView;
    private final Runnable mHidePart2Runnable = new Runnable() {
        @SuppressLint("InlinedApi")
        @Override
        public void run() {
            // Delayed removal of status and navigation bar

            // Note that some of these constants are new as of API 16 (Jelly Bean)
            // and API 19 (KitKat). It is safe to use them, as they are inlined
            // at compile-time and do nothing on earlier devices.
            mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }
    };
    private View mControlsView;
    private final Runnable mShowPart2Runnable = new Runnable() {
        @Override
        public void run() {
            // Delayed display of UI elements
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.hide();
            }
            mControlsView.setVisibility(View.VISIBLE);
        }
    };

    private final Runnable mHideRunnable = new Runnable() {
        @Override
        public void run() {
            hide();
        }
    };
    /**
     * Touch listener to use for in-layout UI controls to delay hiding the
     * system UI. This is to prevent the jarring behavior of controls going away
     * while interacting with activity UI.
     */
    private final View.OnTouchListener mDelayHideTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            switch (motionEvent.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    if (AUTO_HIDE) {
                        delayedHide(AUTO_HIDE_DELAY_MILLIS);
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    view.performClick();
                    break;
                default:
                    break;
            }
            return false;
        }
    };


    private boolean isGameOverTriggered = false;

    @Override
    public void onBackPressed() {
        // Stop game music and restart menu music
        if (mediaPlayer != null) {
            try {
                mediaPlayer.stop();
                mediaPlayer.release();
            } catch (Exception e) { e.printStackTrace(); }
        }
        mediaPlayer = MediaPlayer.create(getApplicationContext(), R.raw.sound1);
        if (mediaPlayer != null) {
            mediaPlayer.start();
            mediaPlayer.setLooping(true);
        }
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Stop any playing music when activity is destroyed (e.g. app cleared from recents)
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.release();
            } catch (Exception e) { e.printStackTrace(); }
            mediaPlayer = null;
        }
        LocalBroadcastManager.getInstance(this).unregisterReceiver(tempReceiver1);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(remoteBingoReceiver);
    }

    private final BroadcastReceiver remoteBingoReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String winnerName = intent.getStringExtra("winnerName");
            triggerGameOver(false, winnerName);
        }
    };

    private final BroadcastReceiver tempReceiver1 = new BroadcastReceiver() {
        @SuppressLint("SetTextI18n")
        @Override
        public void onReceive(Context context, Intent intent) {
            Button[][] button = new Button[5][5];
            button[0][0] = (Button) findViewById(R.id.b1);
            button[0][1] = (Button) findViewById(R.id.b2);
            button[0][2] = (Button) findViewById(R.id.b3);
            button[0][3] = (Button) findViewById(R.id.b4);
            button[0][4] = (Button) findViewById(R.id.b5);
            button[1][0] = (Button) findViewById(R.id.b6);
            button[1][1] = (Button) findViewById(R.id.b7);
            button[1][2] = (Button) findViewById(R.id.b8);
            button[1][3] = (Button) findViewById(R.id.b9);
            button[1][4] = (Button) findViewById(R.id.b10);
            button[2][0] = (Button) findViewById(R.id.b11);
            button[2][1] = (Button) findViewById(R.id.b12);
            button[2][2] = (Button) findViewById(R.id.b13);
            button[2][3] = (Button) findViewById(R.id.b14);
            button[2][4] = (Button) findViewById(R.id.b15);
            button[3][0] = (Button) findViewById(R.id.b16);
            button[3][1] = (Button) findViewById(R.id.b17);
            button[3][2] = (Button) findViewById(R.id.b18);
            button[3][3] = (Button) findViewById(R.id.b19);
            button[3][4] = (Button) findViewById(R.id.b20);
            button[4][0] = (Button) findViewById(R.id.b21);
            button[4][1] = (Button) findViewById(R.id.b22);
            button[4][2] = (Button) findViewById(R.id.b23);
            button[4][3] = (Button) findViewById(R.id.b24);
            button[4][4] = (Button) findViewById(R.id.b25);
            TextView bingo = (TextView) findViewById(R.id.bingo);
            TextView textView = (TextView) findViewById(R.id.textView4);
            L1:
            for (i = 0; i < 5; i++)
                for (j = 0; j < 5; j++) {
                    if (button[i][j].getText().equals(intent.getStringExtra("idMess"))) {
                        // Mark as remote so onClick skips turn increment & broadcast
                        isRemoteClick = true;
                        button[i][j].callOnClick();
                        isRemoteClick = false;

                        // Now advance currentTurn once (the onClick skipped it)
                        currentTurn = (currentTurn + 1) % count;
                        while(ignoreTurn.contains(currentTurn+""))
                            currentTurn = (currentTurn + 1) % count;

                        if(currentTurn == turn) {
                            bingo.setText("Your turn");
                            textView.setVisibility(View.INVISIBLE);
                        }
                        else {
                            bingo.setText("Not your turn");
                            if (!isGameOverTriggered) {
                                textView.setVisibility(View.VISIBLE);
                            }
                        }
                        break L1;
                    }
                }
        }
    };

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.game);

        currentTurn = 0;
        bingoNum = 0;
        mControlsView = findViewById(R.id.fullscreen_content_controls);
        mContentView = findViewById(R.id.fullscreen_content);
        TextView bingo = (TextView) findViewById(R.id.bingo);
        TextView textView = (TextView) findViewById(R.id.textView4);
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);

        LocalBroadcastManager.getInstance(this).registerReceiver(remoteBingoReceiver, new IntentFilter("remote_bingo"));

        SharedPreferences prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        isSoundOn = prefs.getBoolean(KEY_SOUND_STATE, true);

        if(currentTurn == turn) {
            bingo.setText("Your turn");
            textView.setVisibility(View.INVISIBLE);
        }
        else {
            bingo.setText("Not your turn");
            textView.setVisibility(View.VISIBLE);
        }

        textView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                while(ignoreTurn.contains(currentTurn+""))
                    currentTurn = (currentTurn + 1) % count;

                if(currentTurn != turn) {
                    textView.setVisibility(View.VISIBLE);
                    bingo.setText("Not your turn");
                }
                else {
                    textView.setVisibility(View.INVISIBLE);
                    bingo.setText("Your turn");
                }

            }
        });

        // Set up the user interaction to manually show or hide the system UI.
        mContentView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                hide();
            }
        });


        if (Connect.activity4 != null) {
            Connect.activity4.finish();
        }

        // Switch to game music safely
        if (mediaPlayer != null) {
            try {
                mediaPlayer.stop();
                mediaPlayer.release();
            } catch (Exception e) { e.printStackTrace(); }
        }
        mediaPlayer = MediaPlayer.create(getApplicationContext(), R.raw.sound2);
        if (mediaPlayer != null) {
            // Mute or unmute the MediaPlayer based on the sound state
            if (isSoundOn) {
                mediaPlayer.setVolume(1.0f, 1.0f);
            } else {
                mediaPlayer.setVolume(0.0f, 0.0f);
            }
            mediaPlayer.start();
            mediaPlayer.setLooping(true);
        }


        // Upon interacting with UI controls, delay any scheduled hide()
        // operations to prevent the jarring behavior of controls going away
        // while interacting with the UI.

        Button[][] button = new Button[5][5];
        TextView name = (TextView) findViewById(R.id.playername);
        ImageView imageView = (ImageView) findViewById(R.id.imageView7);
        ImageView imageView2 = (ImageView) findViewById(R.id.imageView10);
        Button bingoImg = (Button) findViewById(R.id.bingoImg);
        Button play = (Button) findViewById(R.id.playagain);
        Button quit = (Button) findViewById(R.id.quitbutton);
        TableLayout tableLayout = (TableLayout) findViewById(R.id.tableLayout);
        LocalBroadcastManager.getInstance(this).registerReceiver(tempReceiver1,new IntentFilter("data1"));

        bingoImg.setEnabled(false);
        play.setVisibility(View.INVISIBLE);
        quit.setVisibility(View.INVISIBLE);
        play.setEnabled(false);
        quit.setEnabled(false);

        name.setAnimation(inFromTopAnimation(500));
        imageView.setAnimation(inFromTopAnimation(500));
        bingo.setAnimation(inFromLeftAnimation(750));
        imageView2.setAnimation(inFromBottomAnimation(1000));
        tableLayout.setAnimation(inFromBottomAnimation(1000));

        name.setText(playerName.getText());

        button[0][0] = (Button) findViewById(R.id.b1);
        button[0][1] = (Button) findViewById(R.id.b2);
        button[0][2] = (Button) findViewById(R.id.b3);
        button[0][3] = (Button) findViewById(R.id.b4);
        button[0][4] = (Button) findViewById(R.id.b5);
        button[1][0] = (Button) findViewById(R.id.b6);
        button[1][1] = (Button) findViewById(R.id.b7);
        button[1][2] = (Button) findViewById(R.id.b8);
        button[1][3] = (Button) findViewById(R.id.b9);
        button[1][4] = (Button) findViewById(R.id.b10);
        button[2][0] = (Button) findViewById(R.id.b11);
        button[2][1] = (Button) findViewById(R.id.b12);
        button[2][2] = (Button) findViewById(R.id.b13);
        button[2][3] = (Button) findViewById(R.id.b14);
        button[2][4] = (Button) findViewById(R.id.b15);
        button[3][0] = (Button) findViewById(R.id.b16);
        button[3][1] = (Button) findViewById(R.id.b17);
        button[3][2] = (Button) findViewById(R.id.b18);
        button[3][3] = (Button) findViewById(R.id.b19);
        button[3][4] = (Button) findViewById(R.id.b20);
        button[4][0] = (Button) findViewById(R.id.b21);
        button[4][1] = (Button) findViewById(R.id.b22);
        button[4][2] = (Button) findViewById(R.id.b23);
        button[4][3] = (Button) findViewById(R.id.b24);
        button[4][4] = (Button) findViewById(R.id.b25);

        Intent intent1 = getIntent();

        for (i = 0; i < 5; i++)
            for (j = 0; j < 5; j++) {
                String str = intent1.getStringExtra("message" + button[i][j].getId());
                button[i][j].setText(str);
            }

        for (i = 0; i < 5; i++) {
            for (j = 0; j < 5; j++) {
                button[i][j].setOnClickListener(new View.OnClickListener() {
                    @SuppressLint({"SetTextI18n", "UseCompatLoadingForDrawables"})
                    @Override
                    public void onClick(View v) {

                        Button btn = (Button) findViewById(v.getId());
                        Drawable cross = getDrawable(R.drawable.ic_baseline_cancel_24);
                        if(btn.getForeground() != null)
                            bingo.setText("Already selected!");
                        else {
                            if (!isRemoteClick) {
                                // Turn guard: block clicks when it's not this player's turn
                                if (currentTurn != turn) {
                                    Toast.makeText(Game.this, "Wait for your turn!", Toast.LENGTH_SHORT).show();
                                    return;
                                }

                                currentTurn = (currentTurn + 1) % count;
                                while(ignoreTurn.contains(currentTurn+""))
                                    currentTurn = (currentTurn + 1) % count;

                                if(currentTurn != turn) {
                                    textView.setVisibility(View.VISIBLE);
                                    bingo.setText("Not your turn");
                                }
                                else {
                                    textView.setVisibility(View.INVISIBLE);
                                    bingo.setText("Your turn");
                                }

                                Intent intent = new Intent("data");
                                intent.putExtra("idMsg", btn.getText().toString());
                                LocalBroadcastManager.getInstance(com.game.bingo.Game.this).sendBroadcast(intent);
                            }

                            btn.setText("");
                            btn.setForeground(cross);

                            for (i = 0; i < 5; i++) {
                                flag = 0;
                                for (j = 0; j < 5; j++) {
                                    if (button[i][j].getForeground() != null)
                                        flag++;
                                }
                                if (flag == 5)
                                    bingoSum++;
                            }

                            for (j = 0; j < 5; j++) {
                                flag = 0;
                                for (i = 0; i < 5; i++) {
                                    if (button[i][j].getForeground() != null)
                                        flag++;
                                }
                                if (flag == 5)
                                    bingoSum++;
                            }

                            flag = 0;
                            for (i = 0; i < 5; i++) {
                                if (button[i][i].getForeground() != null)
                                    flag++;
                            }
                            if (flag == 5)
                                bingoSum++;

                            flag = 0;
                            for (i = 0; i < 5; i++) {
                                if (button[i][4 - i].getForeground() != null)
                                    flag++;
                            }
                            if (flag == 5)
                                bingoSum++;

                            switch (bingoSum) {
                                case 1:
                                    bingoImg.setText("B");
                                    break;
                                case 2:
                                    bingoImg.setText("BI");
                                    break;
                                case 3:
                                    bingoImg.setText("BIN");
                                    break;
                                case 4:
                                    bingoImg.setText("BING");
                                    break;
                            }
                            if (bingoSum >= 5 && !isGameOverTriggered) {
                                isGameOverTriggered = true;
                                ignoreTurn.add(turn+"");

                                Intent intent2 = new Intent("bingo");
                                intent2.putExtra("bingo","bingo "+turn+" "+playerName.getText());
                                LocalBroadcastManager.getInstance(com.game.bingo.Game.this).sendBroadcast(intent2);

                                triggerGameOver(true, null);
                            }
                            bingoSum = 0;
                        } // end else (not already selected)
                    }
                });
            }
        }
    }


    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        // Trigger the initial hide() shortly after the activity has been
        // created, to briefly hint to the user that UI controls
        // are available.
        hide();
    }

    private void hide() {
        // Hide UI first
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }
        mControlsView.setVisibility(View.GONE);

        // Schedule a runnable to remove the status and navigation bar after a delay
        mHideHandler.removeCallbacks(mShowPart2Runnable);
        mHideHandler.postDelayed(mHidePart2Runnable, 0);
    }

    /**
     * Schedules a call to hide() in delay milliseconds, canceling any
     * previously scheduled calls.
     */
    private void delayedHide(int delayMillis) {
        mHideHandler.removeCallbacks(mHideRunnable);
        mHideHandler.postDelayed(mHideRunnable, delayMillis);
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    public void triggerGameOver(boolean isWinner, String winnerName) {
        // Prevent double-trigger
        if (isFinishing() || isDestroyed()) return;

        TextView bingo = (TextView) findViewById(R.id.bingo);
        Button bingoImgBtn = (Button) findViewById(R.id.bingoImg);
        Button textView = (Button) findViewById(R.id.textView4);
        Button play = (Button) findViewById(R.id.playagain);
        Button quit = (Button) findViewById(R.id.quitbutton);

        bingo.setText("");
        bingoImgBtn.setText("");
        bingoImgBtn.setForeground(getDrawable(R.drawable.hiclipart_com__1_));

        // Switch to victory music safely
        if (mediaPlayer != null) {
            try {
                mediaPlayer.stop();
                mediaPlayer.release();
            } catch (Exception e) { e.printStackTrace(); }
        }
        mediaPlayer = MediaPlayer.create(getApplicationContext(), R.raw.sound3);
        if (mediaPlayer != null) {
            if (isSoundOn) {
                mediaPlayer.setVolume(1.0f, 1.0f);
            } else {
                mediaPlayer.setVolume(0.0f, 0.0f);
            }
            mediaPlayer.start();
        }

        textView.setVisibility(View.INVISIBLE);

        play.setEnabled(true);
        quit.setEnabled(true);
        play.setVisibility(View.VISIBLE);
        quit.setVisibility(View.VISIBLE);
        play.setAnimation(inFromLeftAnimation(1000));
        quit.setAnimation(inFromRightAnimation(1000));
        play.setText("PLAY AGAIN");
        quit.setText("QUIT");

        // Build the button grid once outside the loop
        Button[][] button = new Button[5][5];
        button[0][0] = (Button) findViewById(R.id.b1);
        button[0][1] = (Button) findViewById(R.id.b2);
        button[0][2] = (Button) findViewById(R.id.b3);
        button[0][3] = (Button) findViewById(R.id.b4);
        button[0][4] = (Button) findViewById(R.id.b5);
        button[1][0] = (Button) findViewById(R.id.b6);
        button[1][1] = (Button) findViewById(R.id.b7);
        button[1][2] = (Button) findViewById(R.id.b8);
        button[1][3] = (Button) findViewById(R.id.b9);
        button[1][4] = (Button) findViewById(R.id.b10);
        button[2][0] = (Button) findViewById(R.id.b11);
        button[2][1] = (Button) findViewById(R.id.b12);
        button[2][2] = (Button) findViewById(R.id.b13);
        button[2][3] = (Button) findViewById(R.id.b14);
        button[2][4] = (Button) findViewById(R.id.b15);
        button[3][0] = (Button) findViewById(R.id.b16);
        button[3][1] = (Button) findViewById(R.id.b17);
        button[3][2] = (Button) findViewById(R.id.b18);
        button[3][3] = (Button) findViewById(R.id.b19);
        button[3][4] = (Button) findViewById(R.id.b20);
        button[4][0] = (Button) findViewById(R.id.b21);
        button[4][1] = (Button) findViewById(R.id.b22);
        button[4][2] = (Button) findViewById(R.id.b23);
        button[4][3] = (Button) findViewById(R.id.b24);
        button[4][4] = (Button) findViewById(R.id.b25);

        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 5; j++) {
                if ((i == 0 || i == 2 || i == 4) && j == 0) {
                    button[i][j].setText("");
                    button[i][j].setForeground(getDrawable(R.drawable.b_icon));
                    button[i][j].setClickable(false);
                } else if ((i == 0 || i == 2 || i == 4) && j == 1) {
                    button[i][j].setText("");
                    button[i][j].setForeground(getDrawable(R.drawable.i_icon));
                    button[i][j].setClickable(false);
                } else if ((i == 0 || i == 2 || i == 4) && j == 2) {
                    button[i][j].setText("");
                    button[i][j].setForeground(getDrawable(R.drawable.n_icon));
                    button[i][j].setClickable(false);
                } else if ((i == 0 || i == 2 || i == 4) && j == 3) {
                    button[i][j].setText("");
                    button[i][j].setForeground(getDrawable(R.drawable.g_icon));
                    button[i][j].setClickable(false);
                } else if ((i == 0 || i == 2 || i == 4) && j == 4) {
                    button[i][j].setText("");
                    button[i][j].setForeground(getDrawable(R.drawable.o_icon));
                    button[i][j].setClickable(false);
                } else if ((i == 1 || i == 3) && (j >= 0 && j <= 4)) {
                    button[i][j].setText("");
                    button[i][j].setForeground(getDrawable(R.drawable.full_exclamation));
                    button[i][j].setClickable(false);
                }
            }
        }

        // PLAY AGAIN: reset game state and go back to Card for a new round
        play.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Reset all game state for a fresh round
                Game.currentTurn = 0;
                Game.bingoNum = 0;
                Card.isReplay = true; // Preserve Card.count and start turn across replays
                if (Start.ignoreTurn != null) {
                    Start.ignoreTurn.clear();
                } else {
                    Start.ignoreTurn = new java.util.ArrayList<>();
                }

                // Stop victory music and restart menu music
                if (mediaPlayer != null) {
                    try {
                        mediaPlayer.stop();
                        mediaPlayer.release();
                    } catch (Exception e) { e.printStackTrace(); }
                    mediaPlayer = null;
                }

                // Navigate to Card to arrange a new board
                Intent intent = new Intent(Game.this, Card.class);
                startActivity(intent);
                finish();
            }
        });

        quit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Stop music before quitting
                if (mediaPlayer != null) {
                    try {
                        mediaPlayer.stop();
                        mediaPlayer.release();
                    } catch (Exception e) { e.printStackTrace(); }
                    mediaPlayer = null;
                }
                finishAffinity();
            }
        });

        if (isWinner) {
            bingoNum++;
            if(bingoNum == 1)
                Toast.makeText(Game.this, "You won! BINGO!", Toast.LENGTH_SHORT).show();
            else if(bingoNum % 10 == 1)
                Toast.makeText(Game.this, "You got "+bingoNum+"st position!", Toast.LENGTH_SHORT).show();
            else if(bingoNum % 10 == 2)
                Toast.makeText(Game.this, "You got "+bingoNum+"nd position!", Toast.LENGTH_SHORT).show();
            else if(bingoNum % 10 == 3)
                Toast.makeText(Game.this, "You got "+bingoNum+"rd position!", Toast.LENGTH_SHORT).show();
            else
                Toast.makeText(Game.this, "You got "+bingoNum+"th position!", Toast.LENGTH_SHORT).show();
        } else {
            if (winnerName != null) {
                Toast.makeText(Game.this, winnerName + " won! BINGO!", Toast.LENGTH_SHORT).show();
            }
        }
    }
}