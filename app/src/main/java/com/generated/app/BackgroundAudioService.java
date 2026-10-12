package com.generated.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.widget.Toast;

import java.util.HashMap;
import java.util.Map;

public class BackgroundAudioService extends Service implements
        MediaPlayer.OnPreparedListener,
        MediaPlayer.OnErrorListener,
        MediaPlayer.OnCompletionListener {

    public static final String ACTION_START = "com.generated.app.ACTION_START";
    public static final String ACTION_TOGGLE = "com.generated.app.ACTION_TOGGLE";
    public static final String ACTION_STOP = "com.generated.app.ACTION_STOP";

    public static final String EXTRA_URL = "EXTRA_URL";
    public static final String EXTRA_TITLE = "EXTRA_TITLE";
    public static final String EXTRA_CHANNEL = "EXTRA_CHANNEL";
    public static final String EXTRA_VIDEO_ID = "EXTRA_VIDEO_ID";

    private static final String CHANNEL_ID = "safe_tube_bg_audio";
    private static final int NOTIFICATION_ID = 1001;

    private MediaPlayer mediaPlayer;
    private PowerManager.WakeLock wakeLock;
    private String currentTitle = "";
    private String currentChannel = "";
    private String currentVideoId = "";
    private String currentStreamUrl = "";
    private boolean isPreparing = false;
    private static boolean isServiceRunning = false;
    private static boolean isPlayingMusic = false;

    public static boolean isRunning() {
        return isServiceRunning;
    }

    public static boolean isPlaying() {
        return isPlayingMusic;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SafeTube:BackgroundAudioWakeLock");
            wakeLock.setReferenceCounted(false);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            return START_NOT_STICKY;
        }

        String action = intent.getAction();
        if (ACTION_START.equals(action)) {
            String url = intent.getStringExtra(EXTRA_URL);
            currentTitle = intent.getStringExtra(EXTRA_TITLE);
            currentChannel = intent.getStringExtra(EXTRA_CHANNEL);
            currentVideoId = intent.getStringExtra(EXTRA_VIDEO_ID);
            currentStreamUrl = url;

            if (currentTitle == null || currentTitle.isEmpty()) currentTitle = "שיר";
            if (currentChannel == null) currentChannel = "";

            startForeground(NOTIFICATION_ID, buildNotification(false, "טוען שמע לרקע..."));
            playAudio(url);

        } else if (ACTION_TOGGLE.equals(action)) {
            if (mediaPlayer != null) {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.pause();
                    isPlayingMusic = false;
                    updateNotification(false);
                } else {
                    mediaPlayer.start();
                    isPlayingMusic = true;
                    updateNotification(true);
                }
            }
        } else if (ACTION_STOP.equals(action)) {
            stopAudio();
            stopSelf();
        }

        return START_NOT_STICKY;
    }

    private void playAudio(final String streamUrl) {
        if (streamUrl == null || streamUrl.isEmpty()) {
            Toast.makeText(this, "קישור שמע אינו תקין", Toast.LENGTH_SHORT).show();
            stopSelf();
            return;
        }

        if (wakeLock != null && !wakeLock.isHeld()) {
            wakeLock.acquire(180 * 60 * 1000L); // Max 3 hours safeguard
        }

        releasePlayer();

        try {
            mediaPlayer = new MediaPlayer();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build());
            } else {
                mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            }

            mediaPlayer.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
            mediaPlayer.setOnPreparedListener(this);
            mediaPlayer.setOnErrorListener(this);
            mediaPlayer.setOnCompletionListener(this);

            Map<String, String> headers = new HashMap<>();
            headers.put("User-Agent", DownloadHelper.USER_AGENT);
            headers.put("Accept", "*/*");
            headers.put("Referer", "https://www.youtube.com/");
            headers.put("Origin", "https://www.youtube.com");

            mediaPlayer.setDataSource(getApplicationContext(), Uri.parse(streamUrl), headers);

            isPreparing = true;
            mediaPlayer.prepareAsync();
            isServiceRunning = true;
        } catch (Exception e) {
            e.printStackTrace();
            isPreparing = false;
            // Try fallback without custom headers
            try {
                mediaPlayer = new MediaPlayer();
                mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                mediaPlayer.setDataSource(streamUrl);
                mediaPlayer.setOnPreparedListener(this);
                mediaPlayer.setOnErrorListener(this);
                mediaPlayer.setOnCompletionListener(this);
                isPreparing = true;
                mediaPlayer.prepareAsync();
                isServiceRunning = true;
            } catch (Exception ex2) {
                ex2.printStackTrace();
                isPreparing = false;
                Toast.makeText(this, "שגיאה בטעינת האודיו", Toast.LENGTH_LONG).show();
                stopSelf();
            }
        }
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        isPreparing = false;
        mp.start();
        isPlayingMusic = true;
        updateNotification(true);
        Toast.makeText(this, "האזנה ברקע פועלת כעת גם עם מסך כבוי!", Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        isPreparing = false;
        isPlayingMusic = false;
        Toast.makeText(this, "שגיאת השמעה בהאזנה ברקע", Toast.LENGTH_SHORT).show();
        stopSelf();
        return true;
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        isPlayingMusic = false;
        updateNotification(false);
        stopAudio();
        stopSelf();
    }

    private void updateNotification(boolean playing) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(NOTIFICATION_ID, buildNotification(playing, playing ? "מנגן ברקע" : "מושהה ברקע"));
        }
    }

    private Notification buildNotification(boolean isPlaying, String statusText) {
        Intent openIntent = new Intent(this, PlayerActivity.class);
        openIntent.putExtra("VIDEO_ID", currentVideoId);
        openIntent.putExtra("VIDEO_TITLE", currentTitle);
        openIntent.putExtra("CHANNEL_TITLE", currentChannel);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentPendingIntent = PendingIntent.getActivity(this, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0));

        Intent toggleIntent = new Intent(this, BackgroundAudioService.class);
        toggleIntent.setAction(ACTION_TOGGLE);
        PendingIntent togglePendingIntent = PendingIntent.getService(this, 1, toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0));

        Intent stopIntent = new Intent(this, BackgroundAudioService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPendingIntent = PendingIntent.getService(this, 2, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0));

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        builder.setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(currentTitle)
                .setContentText(currentChannel + " • " + statusText)
                .setContentIntent(contentPendingIntent)
                .setOngoing(isPlaying)
                .setAutoCancel(false);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            Notification.Action toggleAction = new Notification.Action.Builder(
                    isPlaying ? R.drawable.ic_pause : R.drawable.ic_play_arrow,
                    isPlaying ? "השהה" : "נגן",
                    togglePendingIntent).build();

            Notification.Action stopAction = new Notification.Action.Builder(
                    R.drawable.ic_stop,
                    "עצור",
                    stopPendingIntent).build();

            builder.addAction(toggleAction);
            builder.addAction(stopAction);
        }

        return builder.build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "SafeTube Background Audio",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("מאפשר השמעת מוזיקה ופודקאסטים גם כשהמסך כבוי");
            channel.setSound(null, null);
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }

    private void stopAudio() {
        isPlayingMusic = false;
        isServiceRunning = false;
        releasePlayer();
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        stopForeground(true);
    }

    private void releasePlayer() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.reset();
                mediaPlayer.release();
            } catch (Exception ignored) {}
            mediaPlayer = null;
        }
    }

    @Override
    public void onDestroy() {
        stopAudio();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
