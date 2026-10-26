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
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Foreground service that records from the microphone and saves the result
 * as an .m4a file in Music/audiorec (via MediaStore). When the recording is
 * stopped and a Nextcloud account is set up, the file is uploaded; the
 * service stays alive until the upload has finished.
 */
public class RecorderService extends Service {

    static final String ACTION_STOP = "com.claudefga.audiorec.STOP";

    private static final String TAG = "RecorderService";
    private static final String CHANNEL_ID = "recording";
    private static final String RESULT_CHANNEL_ID = "result";
    private static final int NOTIFICATION_ID = 1;
    private static final int RESULT_NOTIFICATION_ID = 2;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ExecutorService uploader;
    private int pendingUploads = 0;

    private MediaRecorder recorder;
    private ParcelFileDescriptor pfd;
    private Uri uri;
    private String fileName;

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(
                CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW));
        nm.createNotificationChannel(new NotificationChannel(
                RESULT_CHANNEL_ID, getString(R.string.result_channel_name), NotificationManager.IMPORTANCE_DEFAULT));
        uploader = Executors.newSingleThreadExecutor();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            handleStop();
            return START_NOT_STICKY;
        }

        // Must always be called after startForegroundService(), even if already recording.
        startForeground(NOTIFICATION_ID, buildNotification(true),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE | ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);

        if (recorder == null) {
            try {
                startRecording();
            } catch (Exception e) {
                Log.e(TAG, "Could not start recording", e);
                finishRecording();
                Toast.makeText(this, R.string.recording_failed, Toast.LENGTH_LONG).show();
                maybeStop();
            }
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        uploader.shutdown();
        finishRecording(); // safety net if the service is stopped some other way
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /** Finishes the file, uploads it if Nextcloud is set up, then stops when nothing is left to do. */
    private void handleStop() {
        String name = fileName;
        Uri finished = finishRecording();
        if (finished != null) {
            NextcloudSettings settings = NextcloudSettings.load(this);
            if (settings.isConfigured()) {
                startUpload(finished, name, settings);
            }
        }
        maybeStop();
    }

    private void startUpload(Uri file, String name, NextcloudSettings settings) {
        pendingUploads++;
        getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, buildNotification(false));

        uploader.execute(() -> {
            String error = null;
            try {
                NextcloudUploader.upload(getApplicationContext(), file, name, settings);
            } catch (Exception e) {
                Log.e(TAG, "Upload failed", e);
                error = e.getMessage() != null ? e.getMessage() : e.toString();
            }
            final String result = error;
            mainHandler.post(() -> {
                pendingUploads--;
                postResult(name, result);
                if (recorder != null) {
                    getSystemService(NotificationManager.class)
                            .notify(NOTIFICATION_ID, buildNotification(true));
                }
                maybeStop();
            });
        });
    }

    private void maybeStop() {
        if (recorder == null && pendingUploads == 0) {
            stopSelf();
        }
    }

    private Notification buildNotification(boolean recording) {
        Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(recording ? R.string.recording : R.string.uploading))
                .setOngoing(true);

        if (recording) {
            PendingIntent open = PendingIntent.getActivity(this, 0,
                    new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
            PendingIntent stop = PendingIntent.getService(this, 1,
                    new Intent(this, RecorderService.class).setAction(ACTION_STOP),
                    PendingIntent.FLAG_IMMUTABLE);
            builder.setContentIntent(open)
                    .addAction(new Notification.Action.Builder(
                            Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
                            getString(R.string.stop), stop).build());
        }
        return builder.build();
    }

    private void postResult(String name, String error) {
        Notification notification = new Notification.Builder(this, RESULT_CHANNEL_ID)
                .setSmallIcon(error == null
                        ? android.R.drawable.stat_sys_upload_done
                        : android.R.drawable.stat_notify_error)
                .setContentTitle(getString(error == null
                        ? R.string.upload_done : R.string.upload_failed))
                .setContentText(error == null
                        ? name : getString(R.string.upload_failed_detail, error))
                .setStyle(new Notification.BigTextStyle().bigText(error == null
                        ? name : getString(R.string.upload_failed_detail, error)))
                .setAutoCancel(true)
                .build();
        getSystemService(NotificationManager.class).notify(RESULT_NOTIFICATION_ID, notification);
    }

    @SuppressWarnings("deprecation")
    private void startRecording() throws IOException {
        String name = "REC_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        fileName = name + ".m4a";

        ContentValues values = new ContentValues();
        values.put(MediaStore.Audio.Media.DISPLAY_NAME, fileName);
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

    /** Stops the recorder and publishes the file. Returns its Uri, or null if nothing was kept. */
    private Uri finishRecording() {
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
        Uri result = null;
        if (uri != null) {
            if (ok) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Audio.Media.IS_PENDING, 0);
                getContentResolver().update(uri, values, null, null);
                result = uri;
            } else {
                getContentResolver().delete(uri, null, null);
            }
            uri = null;
        }
        return result;
    }
}
