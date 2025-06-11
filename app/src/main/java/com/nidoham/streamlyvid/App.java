package com.nidoham.streamlyvid;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Notification;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;
import androidx.media3.exoplayer.offline.DownloadManager;

import java.io.File;
import java.util.concurrent.Executors;

/**
 * Application class for StreamlyVid - Handles global initialization and configuration
 */
@UnstableApi
public class App extends Application {
    
    private static final String TAG = "StreamlyVidApp";
    private static final String PREFS_NAME = "StreamlyAppPrefs";
    
    // Notification channels
    private static final String MEDIA_CHANNEL_ID = "PlayerServiceChannel";
    private static final String DOWNLOAD_CHANNEL_ID = "DownloadChannel";
    private static final String ERROR_CHANNEL_ID = "ErrorChannel";
    
    // Cache settings
    private static final long CACHE_SIZE = 100 * 1024 * 1024; // 100MB
    private static final String CACHE_DIR = "media_cache";
    
    // Singleton instances
    private static App instance;
    private static SimpleCache cache;
    private static DownloadManager downloadManager;
    private static StandaloneDatabaseProvider databaseProvider;
    
    // Shared components
    private SharedPreferences sharedPreferences;
    private NotificationManager notificationManager;
    
    // Application state
    private boolean isInBackground = false;
    private long backgroundTime = 0;
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        instance = this;
        
        Log.i(TAG, "StreamlyVid Application starting...");
        
        // Initialize core components
        initializeSharedPreferences();
        initializeNotificationChannels();
        initializeCache();
        
        // Register lifecycle tracker
        registerActivityLifecycleCallbacks(new AppLifecycleTracker());
        
        Log.i(TAG, "StreamlyVid Application initialized successfully");
    }
    
    private void initializeSharedPreferences() {
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        
        // Initialize default preferences
        if (sharedPreferences.getBoolean("first_run", true)) {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            
            // Default settings
            editor.putBoolean("background_playback", true);
            editor.putBoolean("wifi_only_streaming", false);
            editor.putFloat("playback_speed", 1.0f);
            editor.putInt("seek_increment", 10); // 10 seconds
            editor.putBoolean("pip_enabled", true);
            editor.putBoolean("gesture_controls", true);
            
            editor.putBoolean("first_run", false);
            editor.apply();
            Log.i(TAG, "Default preferences initialized");
        }
    }
    
    private void initializeNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            
            // Media playback channel
            NotificationChannel mediaChannel = new NotificationChannel(
                    MEDIA_CHANNEL_ID,
                    "Media Player",
                    NotificationManager.IMPORTANCE_LOW
            );
            mediaChannel.setDescription("Media playback controls and status");
            mediaChannel.setShowBadge(false);
            mediaChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            mediaChannel.enableVibration(false);
            mediaChannel.setSound(null, null);
            
            // Download channel
            NotificationChannel downloadChannel = new NotificationChannel(
                    DOWNLOAD_CHANNEL_ID,
                    "Downloads",
                    NotificationManager.IMPORTANCE_LOW
            );
            downloadChannel.setDescription("Download progress and status");
            downloadChannel.setShowBadge(true);
            
            // Error channel
            NotificationChannel errorChannel = new NotificationChannel(
                    ERROR_CHANNEL_ID,
                    "Errors",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            errorChannel.setDescription("Error notifications and alerts");
            errorChannel.setShowBadge(true);
            
            // Create all channels
            notificationManager.createNotificationChannel(mediaChannel);
            notificationManager.createNotificationChannel(downloadChannel);
            notificationManager.createNotificationChannel(errorChannel);
            
            Log.i(TAG, "Notification channels created");
        }
    }
    
    private void initializeCache() {
        try {
            File cacheDir = new File(getCacheDir(), CACHE_DIR);
            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }
            
            databaseProvider = new StandaloneDatabaseProvider(this);
            cache = new SimpleCache(
                    cacheDir,
                    new LeastRecentlyUsedCacheEvictor(CACHE_SIZE),
                    databaseProvider
            );
            
            // Create DownloadManager correctly without using Builder
            DefaultHttpDataSource.Factory httpDataSourceFactory = new DefaultHttpDataSource.Factory()
                    .setUserAgent("StreamlyVid/1.0")
                    .setConnectTimeoutMs(30000)
                    .setReadTimeoutMs(30000);
                    
            downloadManager = new DownloadManager(
                    this,
                    databaseProvider,
                    cache,
                    httpDataSourceFactory,
                    Executors.newFixedThreadPool(3)
            );
            
            Log.i(TAG, "Media cache and download manager initialized");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize cache", e);
        }
    }
    
    // Static getters for global access
    public static App getInstance() {
        return instance;
    }
    
    public static SimpleCache getCache() {
        return cache;
    }
    
    public static DownloadManager getDownloadManager() {
        return downloadManager;
    }
    
    // Utility methods
    public SharedPreferences getAppPreferences() {
        return sharedPreferences;
    }
    
    // Background state management
    public boolean isInBackground() {
        return isInBackground;
    }
    
    public long getBackgroundTime() {
        return backgroundTime;
    }
    
    public void setInBackground(boolean inBackground) {
        this.isInBackground = inBackground;
        if (inBackground) {
            backgroundTime = System.currentTimeMillis();
        } else {
            backgroundTime = 0;
        }
        Log.d(TAG, "App background state changed: " + inBackground);
    }
    
    // Notification channel IDs
    public static String getMediaChannelId() {
        return MEDIA_CHANNEL_ID;
    }
    
    public static String getDownloadChannelId() {
        return DOWNLOAD_CHANNEL_ID;
    }
    
    public static String getErrorChannelId() {
        return ERROR_CHANNEL_ID;
    }
}