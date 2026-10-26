package com.claudefga.audiorec;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Icon;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Foreground service that records from the microphone and saves the result
 * as an .m4a file in Music/audiorec (via MediaStore).
 */
public class RecorderService extends Service {

    static final String ACTION_STOP = "com.claudefga.audiorec.STOP";

    private static final String TAG = "RecorderService";
    private static final String CHANNEL_ID = "recording";
    private static final int NOTIFICATION_ID = 1;

    private MediaRecorder recorder;
    private ParcelFileDescriptor pfd;
    private Uri uri;

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }

        // Must always be called after startForegroundService(), even if already recording.
        startForeground(NOTIFICATION_ID, buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);

        if (recorder == null) {
            try {
                startRecording();
            } catch (Exception e) {
                Log.e(TAG, "Could not start recording", e);
                finishRecording();
                Toast.makeText(this, R.string.recording_failed, Toast.LENGTH_LONG).show();
                stopSelf();
            }
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        finishRecording();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private Notification buildNotification() {
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 1,
                new Intent(this, RecorderService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE);

        Notification.Action stopAction = new Notification.Action.Builder(
                Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
                getString(R.string.stop), stop).build();

        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.recording))
                .setContentIntent(open)
                .addAction(stopAction)
                .setOngoing(true)
                .build();
    }

    @SuppressWarnings("deprecation")
    private void startRecording() throws IOException {
        String name = "REC_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());

        ContentValues values = new ContentValues();
        values.put(MediaStore.Audio.Media.DISPLAY_NAME, name + ".m4a");
        values.put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4");
        values.put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/audiorec");
        values.put(MediaStore.Audio.Media.IS_PENDING, 1);

        uri = getContentResolver().insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) throw new IOException("MediaStore insert failed");
        pfd = getContentResolver().openFileDescriptor(uri, "w");
        if (pfd == null) throw new IOException("Could not open output file");

        recorder = Build.VERSION.SDK_INT >= 31 ? new MediaRecorder(this) : new MediaRecorder();
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        recorder.setAudioEncodingBitRate(128000);
        recorder.setAudioSamplingRate(44100);
        recorder.setOutputFile(pfd.getFileDescriptor());
        recorder.prepare();
        recorder.start();
    }

    private void finishRecording() {
        boolean ok = recorder != null;
        if (recorder != null) {
            try {
                recorder.stop();
            } catch (RuntimeException e) {
                ok = false; // e.g. stopped immediately, nothing recorded
            }
            recorder.release();
            recorder = null;
        }
        if (pfd != null) {
            try {
                pfd.close();
            } catch (IOException ignored) {
            }
            pfd = null;
        }
        if (uri != null) {
            if (ok) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Audio.Media.IS_PENDING, 0);
                getContentResolver().update(uri, values, null, null);
            } else {
                getContentResolver().delete(uri, null, null);
            }
            uri = null;
        }
    }
}
