package com.game.bingo;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.transition.Fade;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.Objects;


public class Settings extends AppCompatActivity {

    private String currentName;

    static final String PREF_NAME = "MyPrefs";
    static final String KEY_CURRENT_NAME = "currentName";
    static final String KEY_SOUND_STATE = "soundState";
    private boolean isSoundOn = true;


    public static Animation inFromRightAnimation(long durationMillis) {
        Animation inFromRight = new TranslateAnimation(
                Animation.RELATIVE_TO_PARENT,  +1.0f, Animation.RELATIVE_TO_PARENT,  0.0f,
                Animation.RELATIVE_TO_PARENT,  0.0f, Animation.RELATIVE_TO_PARENT,   0.0f
        );
        inFromRight.setDuration(durationMillis);
        return inFromRight;
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        Objects.requireNonNull(getSupportActionBar()).hide();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setEnterTransition(new Fade());
        window.setExitTransition(new Fade());

        setContentView(R.layout.settings);

        SharedPreferences prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        currentName = prefs.getString(KEY_CURRENT_NAME, "Default Name");
        isSoundOn = prefs.getBoolean(KEY_SOUND_STATE, true);
        updateSoundButton();


        Button change = (Button) findViewById(R.id.changeNameButton);
        Button soundButton = (Button) findViewById(R.id.soundButton);
        Button back = (Button) findViewById(R.id.back);
        TextView label = (TextView) findViewById(R.id.soundLabel);

        change.setAnimation(inFromRightAnimation(250));
        soundButton.setAnimation(inFromRightAnimation(500));
        label.setAnimation(inFromRightAnimation(500));
        back.setAnimation(inFromRightAnimation(750));

        back.setOnClickListener(v -> finish());
        change.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Show custom dialog for name change
                showNameChangeDialog();
            }
        });
        soundButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleSound(v);
                showRestartDialog();
            }
        });
    }

    private void showNameChangeDialog() {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);
        dialog.setContentView(R.layout.dialog_change_name);

        final EditText newNameEditText = dialog.findViewById(R.id.editTextNewName);
        Button saveButton = dialog.findViewById(R.id.buttonSave);
        Button cancelButton = dialog.findViewById(R.id.buttonCancel);

        // Set the current name in the EditText
        newNameEditText.setText(currentName);

        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Save the new name to SharedPreferences
                String newName = newNameEditText.getText().toString().trim();
                if (!newName.isEmpty()) {
                    currentName = newName;
                    saveCurrentNameToPrefs(currentName);
                }
                dialog.dismiss();
            }
        });

        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        dialog.show();
    }

    private void saveCurrentNameToPrefs(String name) {
        // Save the currentName to SharedPreferences
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_CURRENT_NAME, name);
        editor.apply();
    }



    public void toggleSound(View view) {
        isSoundOn = !isSoundOn;

        // Save sound state to SharedPreferences
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(KEY_SOUND_STATE, isSoundOn);
        editor.apply();

        // Update the button text
        updateSoundButton();


    }

    private void updateSoundButton() {
        Button soundButton = findViewById(R.id.soundButton);
        soundButton.setText(isSoundOn ? "ON" : "OFF");
    }

    @Override
    public void onBackPressed() {
    }
    private void showRestartDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Restart app to take effect")
                .setPositiveButton("Restart", (dialog, id) -> restartApp());

        // Set the dialog to be non-cancelable
        AlertDialog alertDialog = builder.create();
        alertDialog.setCancelable(false);

        // Show the dialog
        alertDialog.show();
    }

    private void restartApp() {
        Intent intent = getBaseContext().getPackageManager().getLaunchIntentForPackage(getBaseContext().getPackageName());
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
            System.exit(0);
        }
    }


}