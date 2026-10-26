package com.claudefga.audiorec;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

/**
 * Starts recording as soon as the app opens. The single red button stops the
 * recording (and starts the Nextcloud upload, if an account is set up) and
 * closes the app. A small gear in the corner opens the Nextcloud settings.
 */
public class MainActivity extends Activity {

    private static final int REQ_PERMISSIONS = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.stop_button).setOnClickListener(v -> {
            // The service finishes the file, uploads it and then stops itself.
            startService(new Intent(this, RecorderService.class)
                    .setAction(RecorderService.ACTION_STOP));
            finishAndRemoveTask();
        });

        findViewById(R.id.settings_button).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startRecording();
        } else if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.POST_NOTIFICATIONS}, REQ_PERMISSIONS);
        } else {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_PERMISSIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_PERMISSIONS) return;

        for (int i = 0; i < permissions.length; i++) {
            if (Manifest.permission.RECORD_AUDIO.equals(permissions[i])) {
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    startRecording();
                } else {
                    Toast.makeText(this, R.string.permission_needed, Toast.LENGTH_LONG).show();
                    finishAndRemoveTask();
                }
                return;
            }
        }
    }

    private void startRecording() {
        startForegroundService(new Intent(this, RecorderService.class));
    }
}
