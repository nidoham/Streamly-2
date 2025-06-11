package com.nidoham.streamlyvid.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;

import com.nidoham.streamlyvid.PlayerActivity;
import com.nidoham.streamlyvid.R;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class PlayerService extends Service implements Player.Listener {
    
    private static final String TAG = "PlayerService";
    private static final String CHANNEL_ID = "PlayerServiceChannel";
    private static final int NOTIFICATION_ID = 1001;
    private static final String PREFS_NAME = "StreamlyAppPrefs";
    
    // Actions for notification buttons
    public static final String ACTION_PLAY_PAUSE = "com.nidoham.streamlyvid.PLAY_PAUSE";
    public static final String ACTION_NEXT = "com.nidoham.streamlyvid.NEXT";
    public static final String ACTION_PREVIOUS = "com.nidoham.streamlyvid.PREVIOUS";
    public static final String ACTION_STOP = "com.nidoham.streamlyvid.STOP";
    
    // Service actions
    public static final String ACTION_START_PLAYBACK = "com.nidoham.streamlyvid.START_PLAYBACK";
    public static final String ACTION_PAUSE_PLAYBACK = "com.nidoham.streamlyvid.PAUSE_PLAYBACK";
    public static final String ACTION_RESUME_PLAYBACK = "com.nidoham.streamlyvid.RESUME_PLAYBACK";
    public static final String ACTION_STOP_PLAYBACK = "com.nidoham.streamlyvid.STOP_PLAYBACK";
    public static final String ACTION_SEEK_TO = "com.nidoham.streamlyvid.SEEK_TO";
    
    // Broadcast actions for activity communication
    public static final String BROADCAST_PLAYBACK_STATE = "com.nidoham.streamlyvid.PLAYBACK_STATE";
    public static final String BROADCAST_MEDIA_CHANGED = "com.nidoham.streamlyvid.MEDIA_CHANGED";
    public static final String BROADCAST_POSITION_UPDATE = "com.nidoham.streamlyvid.POSITION_UPDATE";
    
    // Core components
    private ExoPlayer player;
    private MediaSessionCompat mediaSession;
    private NotificationManager notificationManager;
    private AudioManager audioManager;
    private TelephonyManager telephonyManager;
    private SharedPreferences prefs;
    
    // State variables
    private String currentMediaPath;
    private String currentMediaTitle;
    private String currentMediaArtist = "Unknown Artist";
    private boolean isAudioOnly = false;
    private boolean wasPlayingBeforeCall = false;
    private boolean wasPlayingBeforeHeadsetDisconnect = false;
    
    // Audio focus
    private AudioFocusRequest audioFocusRequest;
    private boolean hasAudioFocus = false;
    
    // Handlers
    private Handler positionUpdateHandler = new Handler(Looper.getMainLooper());
    private Runnable positionUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            if (player != null && player.isPlaying()) {
                broadcastPositionUpdate();
                positionUpdateHandler.postDelayed(this, 1000);
            }
        }
    };
    
    // Binder for activity communication
    private final IBinder binder = new PlayerServiceBinder();
    
    public class PlayerServiceBinder extends Binder {
        public PlayerService getService() {
            return PlayerService.this;
        }
    }
    
    // Phone state listener for call interruptions
    private PhoneStateListener phoneStateListener = new PhoneStateListener() {
        @Override
        public void onCallStateChanged(int state, String phoneNumber) {
            switch (state) {
                case TelephonyManager.CALL_STATE_RINGING:
                case TelephonyManager.CALL_STATE_OFFHOOK:
                    if (player != null && player.isPlaying()) {
                        wasPlayingBeforeCall = true;
                        pausePlayback();
                    }
                    break;
                case TelephonyManager.CALL_STATE_IDLE:
                    if (wasPlayingBeforeCall && player != null) {
                        resumePlayback();
                        wasPlayingBeforeCall = false;
                    }
                    break;
            }
        }
    };
    
    // Headset receiver
    private BroadcastReceiver headsetReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (AudioManager.ACTION_HEADSET_PLUG.equals(intent.getAction())) {
                int state = intent.getIntExtra("state", -1);
                switch (state) {
                    case 0: // Headset disconnected
                        if (player != null && player.isPlaying()) {
                            wasPlayingBeforeHeadsetDisconnect = true;
                            pausePlayback();
                        }
                        break;
                    case 1: // Headset connected
                        // Don't auto-resume, let user decide
                        break;
                }
            }
        }
    };
    
    // Notification action receiver
    private BroadcastReceiver notificationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action != null) {
                switch (action) {
                    case ACTION_PLAY_PAUSE:
                        togglePlayPause();
                        break;
                    case ACTION_NEXT:
                        // Implement next track logic
                        broadcastAction("NEXT_TRACK");
                        break;
                    case ACTION_PREVIOUS:
                        // Implement previous track logic
                        broadcastAction("PREVIOUS_TRACK");
                        break;
                    case ACTION_STOP:
                        stopPlayback();
                        break;
                }
            }
        }
    };
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service created");
        
        initializeComponents();
        createNotificationChannel();
        initializePlayer();
        initializeMediaSession();
        registerReceivers();
        requestAudioFocus();
    }
    
    private void initializeComponents() {
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        
        // Register phone state listener
        if (telephonyManager != null) {
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
        }
    }
    
    private void initializePlayer() {
        try {
            player = new ExoPlayer.Builder(this).build();
            player.addListener(this);
            Log.d(TAG, "Player initialized");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing player", e);
        }
    }
    
    private void initializeMediaSession() {
        mediaSession = new MediaSessionCompat(this, TAG);
        mediaSession.setCallback(new MediaSessionCallback());
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
                             MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        
        // Set initial playback state
        updatePlaybackState(PlaybackStateCompat.STATE_NONE);
        mediaSession.setActive(true);
    }
    
    private void registerReceivers() {
        // Register headset receiver
        IntentFilter headsetFilter = new IntentFilter(AudioManager.ACTION_HEADSET_PLUG);
        registerReceiver(headsetReceiver, headsetFilter);
        
        // Register notification action receiver
        IntentFilter notificationFilter = new IntentFilter();
        notificationFilter.addAction(ACTION_PLAY_PAUSE);
        notificationFilter.addAction(ACTION_NEXT);
        notificationFilter.addAction(ACTION_PREVIOUS);
        notificationFilter.addAction(ACTION_STOP);
        registerReceiver(notificationReceiver, notificationFilter);
    }
    
    private void requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();
            
            audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(audioAttributes)
                    .setAcceptsDelayedFocusGain(true)
                    .setOnAudioFocusChangeListener(this::onAudioFocusChange)
                    .build();
            
            int result = audioManager.requestAudioFocus(audioFocusRequest);
            hasAudioFocus = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
        } else {
            int result = audioManager.requestAudioFocus(
                    this::onAudioFocusChange,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN
            );
            hasAudioFocus = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
        }
    }
    
    private void onAudioFocusChange(int focusChange) {
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_GAIN:
                hasAudioFocus = true;
                if (player != null && !player.isPlaying() && wasPlayingBeforeCall) {
                    resumePlayback();
                }
                if (player != null) {
                    player.setVolume(1.0f);
                }
                break;
            case AudioManager.AUDIOFOCUS_LOSS:
                hasAudioFocus = false;
                if (player != null && player.isPlaying()) {
                    pausePlayback();
                }
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                if (player != null && player.isPlaying()) {
                    pausePlayback();
                }
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                if (player != null) {
                    player.setVolume(0.3f);
                }
                break;
        }
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (action != null) {
                switch (action) {
                    case ACTION_START_PLAYBACK:
                        handleStartPlayback(intent);
                        break;
                    case ACTION_PAUSE_PLAYBACK:
                        pausePlayback();
                        break;
                    case ACTION_RESUME_PLAYBACK:
                        resumePlayback();
                        break;
                    case ACTION_STOP_PLAYBACK:
                        stopPlayback();
                        break;
                    case ACTION_SEEK_TO:
                        long position = intent.getLongExtra("position", 0);
                        seekTo(position);
                        break;
                }
            }
        }
        
        return START_STICKY; // Restart service if killed
    }
    
    private void handleStartPlayback(Intent intent) {
        currentMediaPath = intent.getStringExtra("mediaPath");
        currentMediaTitle = intent.getStringExtra("mediaTitle");
        currentMediaArtist = intent.getStringExtra("mediaArtist");
        isAudioOnly = intent.getBooleanExtra("isAudioOnly", false);
        
        if (currentMediaPath != null) {
            prepareMedia(currentMediaPath);
            saveCurrentMediaInfo();
        }
    }
    
    private void prepareMedia(String mediaPath) {
        try {
            if (player != null) {
                MediaItem mediaItem = MediaItem.fromUri(Uri.parse(mediaPath));
                player.setMediaItem(mediaItem);
                player.prepare();
                player.setPlayWhenReady(true);
                
                updateMediaSessionMetadata();
                Log.d(TAG, "Media prepared: " + mediaPath);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error preparing media", e);
        }
    }
    
    private void updateMediaSessionMetadata() {
        if (mediaSession != null) {
            MediaMetadataCompat.Builder metadataBuilder = new MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentMediaTitle)
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, currentMediaArtist)
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, 
                            player != null ? player.getDuration() : 0);
            
            // Add album art if available
            Bitmap albumArt = getAlbumArt();
            if (albumArt != null) {
                metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, albumArt);
            }
            
            mediaSession.setMetadata(metadataBuilder.build());
        }
    }
    
    private Bitmap getAlbumArt() {
        // Try to get album art from media or use default
        try {
            // For now, return a default icon
            return BitmapFactory.decodeResource(getResources(), R.drawable.ic_music_note);
        } catch (Exception e) {
            return null;
        }
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Media Player",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Media playback controls");
            channel.setShowBadge(false);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            notificationManager.createNotificationChannel(channel);
        }
    }
    
    private void updateNotification() {
        if (currentMediaTitle == null) return;
        
        Intent activityIntent = new Intent(this, PlayerActivity.class);
        activityIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent activityPendingIntent = PendingIntent.getActivity(
                this, 0, activityIntent, 
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? 
                PendingIntent.FLAG_IMMUTABLE : PendingIntent.FLAG_UPDATE_CURRENT
        );
        
        // Create notification actions
        NotificationCompat.Action playPauseAction = new NotificationCompat.Action(
                player != null && player.isPlaying() ? 
                android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                player != null && player.isPlaying() ? "Pause" : "Play",
                createPendingIntent(ACTION_PLAY_PAUSE)
        );
        
        NotificationCompat.Action previousAction = new NotificationCompat.Action(
                android.R.drawable.ic_media_previous,
                "Previous",
                createPendingIntent(ACTION_PREVIOUS)
        );
        
        NotificationCompat.Action nextAction = new NotificationCompat.Action(
                android.R.drawable.ic_media_next,
                "Next",
                createPendingIntent(ACTION_NEXT)
        );
        
        NotificationCompat.Action stopAction = new NotificationCompat.Action(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                createPendingIntent(ACTION_STOP)
        );
        
        // Build notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(currentMediaTitle)
                .setContentText(currentMediaArtist)
                .setSmallIcon(R.drawable.ic_music_note)
                .setLargeIcon(getAlbumArt())
                .setContentIntent(activityPendingIntent)
                .setDeleteIntent(createPendingIntent(ACTION_STOP))
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(player != null && player.isPlaying())
                .setShowWhen(false)
                .addAction(previousAction)
                .addAction(playPauseAction)
                .addAction(nextAction)
                .addAction(stopAction)
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(mediaSession.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2)
                        .setShowCancelButton(true)
                        .setCancelButtonIntent(createPendingIntent(ACTION_STOP)));
        
        Notification notification = builder.build();
        
        if (player != null && player.isPlaying()) {
            startForeground(NOTIFICATION_ID, notification);
        } else {
            notificationManager.notify(NOTIFICATION_ID, notification);
            stopForeground(false);
        }
    }
    
    private PendingIntent createPendingIntent(String action) {
        Intent intent = new Intent(action);
        return PendingIntent.getBroadcast(
                this, action.hashCode(), intent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? 
                PendingIntent.FLAG_IMMUTABLE : PendingIntent.FLAG_UPDATE_CURRENT
        );
    }
    
    // Playback control methods
    public void togglePlayPause() {
        if (player != null) {
            if (player.isPlaying()) {
                pausePlayback();
            } else {
                resumePlayback();
            }
        }
    }
    
    public void pausePlayback() {
        if (player != null && player.isPlaying()) {
            player.setPlayWhenReady(false);
            updatePlaybackState(PlaybackStateCompat.STATE_PAUSED);
            updateNotification();
            broadcastPlaybackState(false);
            positionUpdateHandler.removeCallbacks(positionUpdateRunnable);
        }
    }
    
    public void resumePlayback() {
        if (player != null && hasAudioFocus) {
            player.setPlayWhenReady(true);
            updatePlaybackState(PlaybackStateCompat.STATE_PLAYING);
            updateNotification();
            broadcastPlaybackState(true);
            positionUpdateHandler.post(positionUpdateRunnable);
        }
    }
    
    public void stopPlayback() {
        if (player != null) {
            player.stop();
            player.clearMediaItems();
            updatePlaybackState(PlaybackStateCompat.STATE_STOPPED);
            broadcastPlaybackState(false);
            positionUpdateHandler.removeCallbacks(positionUpdateRunnable);
            clearCurrentMediaInfo();
            stopForeground(true);
            stopSelf();
        }
    }
    
    public void seekTo(long position) {
        if (player != null) {
            player.seekTo(position);
            broadcastPositionUpdate();
        }
    }
    
    // State management
    private void updatePlaybackState(int state) {
        if (mediaSession != null) {
            long actions = PlaybackStateCompat.ACTION_PLAY_PAUSE |
                          PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                          PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                          PlaybackStateCompat.ACTION_STOP |
                          PlaybackStateCompat.ACTION_SEEK_TO;
            
            long position = player != null ? player.getCurrentPosition() : 0;
            float speed = player != null && player.isPlaying() ? 1.0f : 0.0f;
            
            PlaybackStateCompat playbackState = new PlaybackStateCompat.Builder()
                    .setActions(actions)
                    .setState(state, position, speed)
                    .build();
            
            mediaSession.setPlaybackState(playbackState);
        }
    }
    
    private void saveCurrentMediaInfo() {
        prefs.edit()
                .putString("current_media_path", currentMediaPath)
                .putString("current_media_title", currentMediaTitle)
                .putString("current_media_artist", currentMediaArtist)
                .putBoolean("is_audio_only", isAudioOnly)
                .putLong("last_position", player != null ? player.getCurrentPosition() : 0)
                .apply();
    }
    
    private void clearCurrentMediaInfo() {
        prefs.edit()
                .remove("current_media_path")
                .remove("current_media_title")
                .remove("current_media_artist")
                .remove("is_audio_only")
                .remove("last_position")
                .apply();
    }
    
    // Broadcasting methods
    private void broadcastPlaybackState(boolean isPlaying) {
        Intent intent = new Intent(BROADCAST_PLAYBACK_STATE);
        intent.putExtra("isPlaying", isPlaying);
        intent.putExtra("mediaPath", currentMediaPath);
        intent.putExtra("mediaTitle", currentMediaTitle);
        sendBroadcast(intent);
    }
    
    private void broadcastPositionUpdate() {
        if (player != null) {
            Intent intent = new Intent(BROADCAST_POSITION_UPDATE);
            intent.putExtra("currentPosition", player.getCurrentPosition());
            intent.putExtra("duration", player.getDuration());
            sendBroadcast(intent);
        }
    }
    
    private void broadcastAction(String action) {
        Intent intent = new Intent(action);
        sendBroadcast(intent);
    }
    
    // Player event listeners
    @Override
    public void onPlaybackStateChanged(int playbackState) {
        switch (playbackState) {
            case Player.STATE_READY:
                updateMediaSessionMetadata();
                updateNotification();
                break;
            case Player.STATE_ENDED:
                updatePlaybackState(PlaybackStateCompat.STATE_STOPPED);
                broadcastAction("MEDIA_ENDED");
                break;
        }
    }
    
    @Override
    public void onIsPlayingChanged(boolean isPlaying) {
        updatePlaybackState(isPlaying ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED);
        updateNotification();
        broadcastPlaybackState(isPlaying);
        
        if (isPlaying) {
            positionUpdateHandler.post(positionUpdateRunnable);
        } else {
            positionUpdateHandler.removeCallbacks(positionUpdateRunnable);
        }
        
        saveCurrentMediaInfo();
    }
    
    @Override
    public void onPlayerError(PlaybackException error) {
        Log.e(TAG, "Player error: " + error.getMessage());
        updatePlaybackState(PlaybackStateCompat.STATE_ERROR);
        broadcastAction("PLAYER_ERROR");
    }
    
    // MediaSession callback
    private class MediaSessionCallback extends MediaSessionCompat.Callback {
        @Override
        public void onPlay() {
            resumePlayback();
        }
        
        @Override
        public void onPause() {
            pausePlayback();
        }
        
        @Override
        public void onStop() {
            stopPlayback();
        }
        
        @Override
        public void onSkipToNext() {
            broadcastAction("NEXT_TRACK");
        }
        
        @Override
        public void onSkipToPrevious() {
            broadcastAction("PREVIOUS_TRACK");
        }
        
        @Override
        public void onSeekTo(long pos) {
            seekTo(pos);
        }
    }
    
    // Public methods for activity communication
    public boolean isPlaying() {
        return player != null && player.isPlaying();
    }
    
    public long getCurrentPosition() {
        return player != null ? player.getCurrentPosition() : 0;
    }
    
    public long getDuration() {
        return player != null ? player.getDuration() : 0;
    }
    
    public String getCurrentMediaPath() {
        return currentMediaPath;
    }
    
    public String getCurrentMediaTitle() {
        return currentMediaTitle;
    }
    
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service destroyed");
        
        // Clean up
        positionUpdateHandler.removeCallbacks(positionUpdateRunnable);
        
        if (player != null) {
            player.release();
            player = null;
        }
        
        if (mediaSession != null) {
            mediaSession.release();
            mediaSession = null;
        }
        
        // Release audio focus
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
            audioManager.abandonAudioFocusRequest(audioFocusRequest);
        } else {
            audioManager.abandonAudioFocus(this::onAudioFocusChange);
        }
        
        // Unregister receivers
        try {
            unregisterReceiver(headsetReceiver);
            unregisterReceiver(notificationReceiver);
        } catch (Exception e) {
            Log.e(TAG, "Error unregistering receivers", e);
        }
        
        // Stop phone state listener
        if (telephonyManager != null) {
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
        }
        
        stopForeground(true);
    }
}