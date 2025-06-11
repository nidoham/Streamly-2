package com.nidoham.streamlyvid;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.PictureInPictureParams;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.util.Rational;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.TimeBar;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.nidoham.streamlyvid.databinding.ActivityPlayerBinding;
import com.nidoham.streamlyvid.model.VideoModel;

import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;
import java.util.Locale;

public class PlayerActivity extends AppCompatActivity implements Player.Listener, View.OnClickListener {

    private static final String TAG = "PlayerActivity";
    private ActivityPlayerBinding binding;
    private ExoPlayer player;

    // SharedPreferences keys
    private static final String PREFS_NAME = "StreamlyAppPrefs";
    private static final String KEY_IS_PLAYING_GLOBAL = "isPlayingState";
    private static final String KEY_LAST_VIDEO_PATH = "lastVideoPath";
    private static final String KEY_LAST_VIDEO_TITLE = "lastVideoTitle";
    private static final String KEY_LAST_POSITION = "lastVideoPosition";
    private static final String KEY_ORIENTATION_PREFERENCE = "orientationPreference";
    private static final String KEY_BRIGHTNESS_LEVEL = "brightnessLevel";
    private static final String KEY_VOLUME_LEVEL = "volumeLevel";
    
    private SharedPreferences sharedPreferences;
    private String videoPath;
    private String videoTitle;

    // Controls visibility variables
    private boolean controlsVisible = true;
    private boolean isLocked = false;
    private final Handler hideControlsHandler = new Handler(Looper.getMainLooper());
    private final Runnable hideControlsRunnable = () -> hidePlayerControls();
    private static final int HIDE_CONTROLS_DELAY = 3000;

    // Gesture and playback speed variables
    private GestureDetector gestureDetector;
    private float currentPlaybackSpeed = 1.0f;
    private final float[] playbackSpeeds = {0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f};
    private int currentSpeedIndex = 2;
    
    // Playlist related variables
    private List<VideoModel> playlistVideos = new ArrayList<>();
    private int currentVideoIndex = 0;
    private boolean isPlaylistVisible = false;
    
    // Screen orientation related variables
    private boolean isTV = false;
    private int userPreferredOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
    
    // Volume and brightness control
    private AudioManager audioManager;
    private int maxVolume;
    private int currentVolume;
    private float currentBrightness;
    private boolean isVolumeControlVisible = false;
    private boolean isBrightnessControlVisible = false;
    private float startX, startY;
    private static final float SWIPE_THRESHOLD = 30;
    private static final int BRIGHTNESS_MAX = 255;
    private boolean canWriteSettings = false;
    
    // Picture-in-Picture mode tracking
    private boolean isInPipMode = false;
    private boolean isActivityInForeground = true;
    
    // TV specific permission request code
    private static final int TV_STORAGE_PERMISSION_REQUEST_CODE = 1001;
    
    // Flag to track if we're switching to a new video
    private boolean isSwitchingToNewVideo = false;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Initialize view binding
        binding = ActivityPlayerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        
        // Set status bar color to pure black
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.setStatusBarColor(Color.BLACK);
        }

        // Set status bar icons to light (for better contrast on black background)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getWindow().getDecorView().setSystemUiVisibility(0); // 0 means light icons (default)
        }
    
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        userPreferredOrientation = sharedPreferences.getInt(KEY_ORIENTATION_PREFERENCE, ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);

        // Initialize audio manager for volume control
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        
        // Check if we can write system settings (for brightness)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            canWriteSettings = Settings.System.canWrite(this);
            if (!canWriteSettings) {
                Log.d(TAG, "No permission to write system settings, using window brightness");
            }
        }
        
        // Initialize brightness
        initializeBrightness();
        
        // Check if device is a TV
        isTV = getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK);
        
        // If we're on a TV device, request storage permissions
        if (isTV) {
            requestTVStoragePermissions();
        }
        
        // Get video info from intent
        videoPath = getIntent().getStringExtra("videoPath");
        videoTitle = getIntent().getStringExtra("videoTitle");
        
        // Get playlist from intent if available
        if (getIntent().hasExtra("playlist")) {
            currentVideoIndex = getIntent().getIntExtra("currentIndex", 0);
        } else {
            // If no playlist is provided, create a single item playlist with current video
            if (videoPath != null) {
                VideoModel currentVideo = new VideoModel();
                currentVideo.setId(System.currentTimeMillis());
                currentVideo.setTitle(videoTitle != null ? videoTitle : "Unknown Video");
                currentVideo.setDisplayName(videoTitle != null ? videoTitle : "Unknown Video");
                currentVideo.setData(videoPath);
                currentVideo.setUri(Uri.parse(videoPath));
                
                playlistVideos.add(currentVideo);
            }
        }

        if (videoPath == null && !playlistVideos.isEmpty()) {
            // If no specific video path is provided, use the first video from the playlist
            VideoModel firstVideo = playlistVideos.get(0);
            videoPath = firstVideo.getData();
            videoTitle = firstVideo.getTitle();
        }

        if (videoPath == null) {
            Toast.makeText(this, "Invalid video path", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        if (videoTitle == null) {
            videoTitle = "Unknown Video";
        }
        
        setupClickListeners();
        initializePlayer();
        setupGestureDetector();
        setupPlaylistView();
        setupVolumeAndBrightnessControls();
        applyOrientationSettings();

        binding.videoTitle.setText(videoTitle);
        updatePlayPauseButton();
        updateNavigationButtons();
        showPlayerControls();
        
        // Check if we need to restore state after orientation change
        if (savedInstanceState != null) {
            long tempPosition = sharedPreferences.getLong("temp_position", -1);
            boolean tempWasPlaying = sharedPreferences.getBoolean("temp_was_playing", false);
            
            if (tempPosition >= 0 && player != null) {
                player.seekTo(tempPosition);
                if (tempWasPlaying) {
                    player.play();
                }
                
                // Clear temporary state
                sharedPreferences.edit()
                    .remove("temp_position")
                    .remove("temp_was_playing")
                    .apply();
            }
        } else {
            // If this is a fresh start (not orientation change), start paused
            if (player != null) {
                player.setPlayWhenReady(false);
                updatePlayPauseButton();
            }
        }
    }
    
    private void requestTVStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                }, TV_STORAGE_PERMISSION_REQUEST_CODE);
            }
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == TV_STORAGE_PERMISSION_REQUEST_CODE) {
            // Check if permissions were granted
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            
            if (!allGranted) {
                // Show a message about permissions
                Toast.makeText(this, "Storage permissions are required to play videos on Android TV", 
                        Toast.LENGTH_LONG).show();
                
                // Show more detailed error dialog
                showTVSpecificErrorDialog("Storage permissions are required to play videos on Android TV. " +
                        "Please grant storage permissions in the app settings.");
            }
        }
    }
    
    private void initializeBrightness() {
        try {
            // First try to get system brightness
            ContentResolver contentResolver = getContentResolver();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.System.canWrite(this)) {
                currentBrightness = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS);
                canWriteSettings = true;
            } else {
                // If we don't have permission, get current window brightness
                WindowManager.LayoutParams layoutParams = getWindow().getAttributes();
                if (layoutParams.screenBrightness < 0) { // -1 means use system default
                    // Try to read system brightness without permission (read-only)
                    try {
                        currentBrightness = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS);
                    } catch (Settings.SettingNotFoundException e) {
                        currentBrightness = BRIGHTNESS_MAX / 2; // Default to 50% if can't read
                    }
                } else {
                    currentBrightness = layoutParams.screenBrightness * BRIGHTNESS_MAX;
                }
            }
        } catch (Settings.SettingNotFoundException e) {
            currentBrightness = BRIGHTNESS_MAX / 2; // 50% default brightness
            Log.e(TAG, "Error getting brightness", e);
        }
        
        // Load saved brightness and volume if available
        int savedBrightness = sharedPreferences.getInt(KEY_BRIGHTNESS_LEVEL, (int)currentBrightness);
        int savedVolume = sharedPreferences.getInt(KEY_VOLUME_LEVEL, currentVolume);
        
        if (savedBrightness > 0) {
            currentBrightness = savedBrightness;
            setBrightness(savedBrightness);
        }
        
        if (savedVolume >= 0) {
            currentVolume = savedVolume;
            setVolume(savedVolume);
        }
    }
    
    private void setupVolumeAndBrightnessControls() {
        // Setup volume seekbar
        binding.volumeSeekbar.setMax(maxVolume);
        binding.volumeSeekbar.setProgress(currentVolume);
        binding.volumePercentage.setText(getVolumePercentage(currentVolume));
        
        binding.volumeSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    setVolume(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                hideControlsHandler.removeCallbacks(hideControlsRunnable);
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                scheduleHideControls();
            }
        });
        
        // Setup brightness seekbar
        binding.brightnessSeekbar.setMax(BRIGHTNESS_MAX);
        binding.brightnessSeekbar.setProgress((int)currentBrightness);
        binding.brightnessPercentage.setText(getBrightnessPercentage((int)currentBrightness));
        
        binding.brightnessSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    setBrightness(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                hideControlsHandler.removeCallbacks(hideControlsRunnable);
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                scheduleHideControls();
            }
        });
    }
    
    private void setVolume(int volume) {
        if (volume < 0) volume = 0;
        if (volume > maxVolume) volume = maxVolume;
        
        currentVolume = volume;
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0);
        
        // Update UI
        binding.volumeSeekbar.setProgress(volume);
        binding.volumePercentage.setText(getVolumePercentage(volume));
        
        // Update volume icon based on volume level
        ImageView volumeIcon = binding.volumeIcon;
        if (volume == 0) {
            volumeIcon.setImageResource(android.R.drawable.ic_lock_silent_mode);
        } else if (volume < maxVolume / 2) {
            volumeIcon.setImageResource(android.R.drawable.ic_lock_silent_mode_off);
        } else {
            volumeIcon.setImageResource(android.R.drawable.ic_lock_silent_mode_off);
        }
        
        // Save volume setting
        sharedPreferences.edit().putInt(KEY_VOLUME_LEVEL, volume).apply();
    }
    
    private void setBrightness(int brightness) {
        if (brightness < 0) brightness = 0;
        if (brightness > BRIGHTNESS_MAX) brightness = BRIGHTNESS_MAX;
        
        currentBrightness = brightness;
        
        // Update UI
        binding.brightnessSeekbar.setProgress(brightness);
        binding.brightnessPercentage.setText(getBrightnessPercentage(brightness));
        
        // Apply brightness to window
        WindowManager.LayoutParams layoutParams = getWindow().getAttributes();
        layoutParams.screenBrightness = brightness / (float)BRIGHTNESS_MAX;
        getWindow().setAttributes(layoutParams);
        
        // If we have permission, update system brightness too
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && canWriteSettings) {
            try {
                Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, brightness);
            } catch (Exception e) {
                Log.e(TAG, "Error setting system brightness", e);
            }
        }
        
        // Save brightness setting
        sharedPreferences.edit().putInt(KEY_BRIGHTNESS_LEVEL, brightness).apply();
    }
    
    private String getVolumePercentage(int volume) {
        return (volume * 100 / maxVolume) + "%";
    }
    
    private String getBrightnessPercentage(int brightness) {
        return (brightness * 100 / BRIGHTNESS_MAX) + "%";
    }
    
    private void updateVolumeUI() {
        binding.volumeSeekbar.setProgress(currentVolume);
        binding.volumePercentage.setText(getVolumePercentage(currentVolume));
        
        // Update volume icon based on volume level
        ImageView volumeIcon = binding.volumeIcon;
        if (currentVolume == 0) {
            volumeIcon.setImageResource(android.R.drawable.ic_lock_silent_mode);
        } else if (currentVolume < maxVolume / 2) {
            volumeIcon.setImageResource(android.R.drawable.ic_lock_silent_mode_off);
        } else {
            volumeIcon.setImageResource(android.R.drawable.ic_lock_silent_mode_off);
        }
    }
    
    private void updateBrightnessUI() {
        binding.brightnessSeekbar.setProgress((int)currentBrightness);
        binding.brightnessPercentage.setText(getBrightnessPercentage((int)currentBrightness));
    }
    
    private void showVolumeControl() {
        binding.volumeControlContainer.setVisibility(View.VISIBLE);
        isVolumeControlVisible = true;
        updateVolumeUI();
        
        // Hide after delay
        new Handler().postDelayed(() -> {
            if (isVolumeControlVisible) {
                binding.volumeControlContainer.setVisibility(View.GONE);
                isVolumeControlVisible = false;
            }
        }, 3000);
    }
    
    private void showBrightnessControl() {
        binding.brightnessControlContainer.setVisibility(View.VISIBLE);
        isBrightnessControlVisible = true;
        updateBrightnessUI();
        
        // Hide after delay
        new Handler().postDelayed(() -> {
            if (isBrightnessControlVisible) {
                binding.brightnessControlContainer.setVisibility(View.GONE);
                isBrightnessControlVisible = false;
            }
        }, 3000);
    }
    
    private void setupPlaylistView() {
        // Initialize playlist recycler view
        binding.qualityRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        updateNavigationButtons();
    }
    
    private void togglePlaylistVisibility() {
        isPlaylistVisible = !isPlaylistVisible;
        if (isPlaylistVisible) {
            binding.qualitySelectorDialog.setVisibility(View.VISIBLE);
            hideControlsHandler.removeCallbacks(hideControlsRunnable);
        } else {
            binding.qualitySelectorDialog.setVisibility(View.GONE);
            scheduleHideControls();
        }
    }
    
    private void applyOrientationSettings() {
        if (isTV) {
            // For TV devices, always use landscape
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            hideSystemUI(); // Always hide system UI on TV
        } else {
            // For phone/tablet, use user preference or sensor
            setRequestedOrientation(userPreferredOrientation);
            
            // Check current orientation and hide system UI if in landscape
            int orientation = getResources().getConfiguration().orientation;
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                hideSystemUI();
            } else {
                showSystemUI();
            }
            
            // Only show PiP button on compatible devices (Android 8.0+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
                binding.btnPipMode.setVisibility(View.VISIBLE);
            } else {
                binding.btnPipMode.setVisibility(View.GONE);
            }
        }
    }
    
    private void hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // For Android 11+ (API 30+)
            getWindow().setDecorFitsSystemWindows(false);
            getWindow().getInsetsController().hide(
                    android.view.WindowInsets.Type.statusBars() | 
                    android.view.WindowInsets.Type.navigationBars());
            getWindow().getInsetsController().setSystemBarsBehavior(
                    android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // For Android 5.0+ (API 21+)
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            getWindow().getDecorView().setSystemUiVisibility(flags);
        }
    }
    
    private void showSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // For Android 11+ (API 30+)
            getWindow().setDecorFitsSystemWindows(true);
            getWindow().getInsetsController().show(
                    android.view.WindowInsets.Type.statusBars() | 
                    android.view.WindowInsets.Type.navigationBars());
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // For Android 5.0+ (API 21+)
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    private void updateSpeedLabel() {
        binding.tvPlaybackSpeed.setText(String.format(Locale.getDefault(), "%.1fx", currentPlaybackSpeed));
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupClickListeners() {
        binding.btnBack.setOnClickListener(this);
        binding.btnPlayPause.setOnClickListener(this);
        binding.btnLockScreenEdge.setOnClickListener(this);
        binding.btnUnlock.setOnClickListener(this);
        binding.btnScreenOrientationMx.setOnClickListener(this);
        binding.btnAspectRatio.setOnClickListener(this);
        binding.btnCenterSpeedIcon.setOnClickListener(this);
        binding.btnCenterScreenshot.setOnClickListener(this);
        binding.btnPlaylist.setOnClickListener(this);
        binding.btnPipMode.setOnClickListener(this);
        
        // Add listeners for previous and next buttons
        binding.btnPrevious.setOnClickListener(this);
        binding.btnNext.setOnClickListener(this);

        binding.timeBar.addListener(new TimeBar.OnScrubListener() {
            @Override
            public void onScrubStart(@NonNull TimeBar timeBar, long position) {
                hideControlsHandler.removeCallbacks(hideControlsRunnable);
            }

            @Override
            public void onScrubMove(@NonNull TimeBar timeBar, long position) {
                if (player != null) {
                    binding.tvCurrentTime.setText(formatTime(position));
                }
            }

            @Override
            public void onScrubStop(@NonNull TimeBar timeBar, long position, boolean cancelled) {
                if (player != null && !cancelled) {
                    player.seekTo(position);
                }
                scheduleHideControls();
            }
        });
    }

    private void initializePlayer() {
        try {
            player = new ExoPlayer.Builder(this).build();
            binding.playerView.setPlayer(player);
            
            // Set fit resize mode by default for better video quality
            binding.playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
            
            player.addListener(this);
            
            // Simplified media preparation for both TV and phone
            prepareMedia(videoPath);
            
            // Start paused by default, require user action to play
            // If we're switching to a new video, play automatically
            player.setPlayWhenReady(isSwitchingToNewVideo);
            updateGlobalPlayingState(isSwitchingToNewVideo, videoPath, videoTitle);
            
            // Reset the switching flag
            isSwitchingToNewVideo = false;
        } catch (Exception e) {
            Toast.makeText(this, "Error initializing player: " + e.getMessage(), Toast.LENGTH_LONG).show();
            Log.e(TAG, "Error initializing player", e);
            updateGlobalPlayingState(false, null, null);
            finish();
        }
    }
    
    private void prepareMedia(String path) {
        try {
            Uri videoUri = Uri.parse(path);
            
            // Use a simple approach for all devices
            MediaItem mediaItem = MediaItem.fromUri(videoUri);
            player.setMediaItem(mediaItem);
            player.prepare();
            
            // Log successful preparation
            Log.d(TAG, "Successfully prepared media: " + path);
        } catch (Exception e) {
            Log.e(TAG, "Error preparing media", e);
            if (isTV) {
                showTVSpecificErrorDialog(e.getMessage());
            } else {
                Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    private void showTVSpecificErrorDialog(String errorMessage) {
        // Hide default error overlay
        binding.errorOverlay.setVisibility(View.GONE);
        
        // Create a custom dialog for TV users - use default theme instead of AppCompat theme
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Android TV Playback Error");
        builder.setMessage("There was an error playing this video on Android TV:\n\n" + 
                errorMessage + "\n\n" +
                "Suggestions:\n" +
                "• Move videos to internal storage\n" +
                "• Use a content provider app\n" +
                "• Try a different video format (MP4/H.264)\n" +
                "• Check that the app has storage permissions");
        
        builder.setPositiveButton("Retry", (dialog, which) -> {
            dialog.dismiss();
            initializePlayer();
        });
        
        builder.setNegativeButton("Close", (dialog, which) -> {
            dialog.dismiss();
            finish();
        });
        
        AlertDialog dialog = builder.create();
        dialog.show();
        
        // Make dialog more TV-friendly
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupGestureDetector() {
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
                if (!isLocked) {
                    togglePlayerControlsVisibility();
                }
                return true;
            }

            @Override
            public boolean onDoubleTap(@NonNull MotionEvent e) {
                if (player == null || isLocked) return false;
                float screenWidth = binding.gestureOverlay.getWidth();
                if (e.getX() < screenWidth / 2) {
                    // Show rewind indicator
                    showDoubleTapIndicator(false);
                    player.seekTo(Math.max(0, player.getCurrentPosition() - 10000));
                } else {
                    // Show forward indicator
                    showDoubleTapIndicator(true);
                    player.seekTo(Math.min(player.getDuration(), player.getCurrentPosition() + 10000));
                }
                showPlayerControls();
                return true;
            }
        });

        binding.gestureOverlay.setOnTouchListener(new View.OnTouchListener() {
            private float initialX, initialY;
            private boolean isGestureActive = false;
            private boolean isVolumeGesture = false;
            private boolean isBrightnessGesture = false;
            private int initialVolume;
            private float initialBrightness;
            private static final int GESTURE_NONE = 0;
            private static final int GESTURE_VOLUME = 1;
            private static final int GESTURE_BRIGHTNESS = 2;
            private int activeGesture = GESTURE_NONE;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                // Let gesture detector handle taps
                gestureDetector.onTouchEvent(event);
                
                // Don't handle gestures if locked
                if (isLocked) {
                    return true;
                }
                
                float screenWidth = binding.gestureOverlay.getWidth();
                float screenHeight = binding.gestureOverlay.getHeight();
                
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = event.getX();
                        initialY = event.getY();
                        isGestureActive = false;
                        activeGesture = GESTURE_NONE;
                        initialVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                        initialBrightness = currentBrightness;
                        break;
                        
                    case MotionEvent.ACTION_MOVE:
                        if (!isGestureActive) {
                            // Determine if this is a vertical gesture
                            float deltaX = Math.abs(event.getX() - initialX);
                            float deltaY = Math.abs(event.getY() - initialY);
                            
                            if (deltaY > SWIPE_THRESHOLD && deltaY > deltaX * 2) {
                                isGestureActive = true;
                                
                                // Determine if this is volume or brightness based on x position
                                if (initialX < screenWidth / 2) {
                                    activeGesture = GESTURE_BRIGHTNESS;
                                    showBrightnessControl();
                                } else {
                                    activeGesture = GESTURE_VOLUME;
                                    showVolumeControl();
                                }
                            }
                        }
                        
                        if (isGestureActive) {
                            // Calculate change based on gesture movement
                            float deltaY = initialY - event.getY(); // positive = up, negative = down
                            float percentChange = deltaY / (screenHeight / 2); // use half screen for full range
                            
                            if (activeGesture == GESTURE_VOLUME) {
                                // Volume: change by percentage of max volume
                                int volumeChange = Math.round(percentChange * maxVolume);
                                int newVolume = Math.max(0, Math.min(maxVolume, initialVolume + volumeChange));
                                setVolume(newVolume);
                            } else if (activeGesture == GESTURE_BRIGHTNESS) {
                                // Brightness: change by percentage of max brightness
                                int brightnessChange = Math.round(percentChange * BRIGHTNESS_MAX);
                                int newBrightness = Math.max(0, Math.min(BRIGHTNESS_MAX, (int)(initialBrightness + brightnessChange)));
                                setBrightness(newBrightness);
                            }
                        }
                        break;
                        
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        isGestureActive = false;
                        activeGesture = GESTURE_NONE;
                        break;
                }
                
                return true;
            }
        });
    }
    
    private void showDoubleTapIndicator(boolean isForward) {
        // Show appropriate double tap indicator
        if (isForward) {
            binding.doubleTapOverlayRight.setVisibility(View.VISIBLE);
            new Handler().postDelayed(() -> binding.doubleTapOverlayRight.setVisibility(View.GONE), 800);
        } else {
            binding.doubleTapOverlayLeft.setVisibility(View.VISIBLE);
            new Handler().postDelayed(() -> binding.doubleTapOverlayLeft.setVisibility(View.GONE), 800);
        }
    }

    private void togglePlayerControlsVisibility() {
        if (controlsVisible) {
            hidePlayerControls();
        } else {
            showPlayerControls();
        }
    }

    private void showPlayerControls() {
        if (isLocked) return;
        binding.topBar.setVisibility(View.VISIBLE);
        binding.bottomControls.setVisibility(View.VISIBLE);
        binding.centerControlsScrollView.setVisibility(View.VISIBLE);
        controlsVisible = true;
        scheduleHideControls();
    }

    private void hidePlayerControls() {
        binding.topBar.setVisibility(View.GONE);
        binding.bottomControls.setVisibility(View.GONE);
        binding.centerControlsScrollView.setVisibility(View.GONE);
        controlsVisible = false;
        hideControlsHandler.removeCallbacks(hideControlsRunnable);
    }

    private void scheduleHideControls() {
        hideControlsHandler.removeCallbacks(hideControlsRunnable);
        if (player != null && player.isPlaying() && controlsVisible && !isLocked) {
            hideControlsHandler.postDelayed(hideControlsRunnable, HIDE_CONTROLS_DELAY);
        }
    }

    private void updatePlayPauseButton() {
        if (player != null && player.isPlaying()) {
            binding.btnPlayPause.setImageResource(android.R.drawable.ic_media_pause);
        } else {
            binding.btnPlayPause.setImageResource(android.R.drawable.ic_media_play);
        }
    }
    
    private void updateNavigationButtons() {
        // Enable/disable previous button based on playlist position
        binding.btnPrevious.setEnabled(currentVideoIndex > 0);
        binding.btnPrevious.setAlpha(currentVideoIndex > 0 ? 1.0f : 0.5f);
        
        // Enable/disable next button based on playlist position
        binding.btnNext.setEnabled(currentVideoIndex < playlistVideos.size() - 1);
        binding.btnNext.setAlpha(currentVideoIndex < playlistVideos.size() - 1 ? 1.0f : 0.5f);
    }

    private String formatTime(long timeMs) {
        if (timeMs == Long.MIN_VALUE || timeMs < 0) timeMs = 0;
        long totalSeconds = timeMs / 1000;
        long seconds = totalSeconds % 60;
        long minutes = (totalSeconds / 60) % 60;
        long hours = totalSeconds / 3600;
        StringBuilder formatBuilder = new StringBuilder();
        Formatter formatter = new Formatter(formatBuilder, Locale.getDefault());
        if (hours > 0) {
            return formatter.format("%d:%02d:%02d", hours, minutes, seconds).toString();
        } else {
            return formatter.format("%02d:%02d", minutes, seconds).toString();
        }
    }

    private void updateGlobalPlayingState(boolean isPlaying, @Nullable String path, @Nullable String title) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(KEY_IS_PLAYING_GLOBAL, isPlaying);
        if (isPlaying && path != null && title != null) {
            editor.putString(KEY_LAST_VIDEO_PATH, path);
            editor.putString(KEY_LAST_VIDEO_TITLE, title);
        } else if (!isPlaying) {
            // Optional: clear last video info
        }
        editor.apply();
    }

    private void saveLastPosition() {
        if (player != null && sharedPreferences != null) {
            sharedPreferences.edit().putLong(KEY_LAST_POSITION, player.getCurrentPosition()).apply();
        }
    }
    
    private void playVideoAtCurrentIndex() {
        if (currentVideoIndex >= 0 && currentVideoIndex < playlistVideos.size()) {
            VideoModel video = playlistVideos.get(currentVideoIndex);
            videoPath = video.getData();
            videoTitle = video.getTitle();
            
            // Update UI
            binding.videoTitle.setText(videoTitle);
            
            // Set the flag to indicate we're switching to a new video
            isSwitchingToNewVideo = true;
            
            // Release current player and create new one
            if (player != null) {
                player.release();
            }
            
            initializePlayer();
            updateNavigationButtons();
        }
    }
    
    private void playNextVideo() {
        if (currentVideoIndex < playlistVideos.size() - 1) {
            currentVideoIndex++;
            playVideoAtCurrentIndex();
        }
    }
    
    private void playPreviousVideo() {
        if (currentVideoIndex > 0) {
            currentVideoIndex--;
            playVideoAtCurrentIndex();
        }
    }
    
    private void takeScreenshot() {
        // Screenshot functionality implementation
        try {
            // Get current frame from video surface
            binding.playerView.setDrawingCacheEnabled(true);
            Bitmap bitmap = binding.playerView.getDrawingCache();
            
            if (bitmap != null) {
                // Save bitmap to gallery
                String fileName = "Screenshot_" + System.currentTimeMillis() + ".jpg";
                String path = MediaStore.Images.Media.insertImage(
                    getContentResolver(), bitmap, fileName, "Video screenshot");
                
                if (path != null) {
                    Toast.makeText(this, "Screenshot saved to gallery", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Failed to save screenshot", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Unable to capture screenshot", Toast.LENGTH_SHORT).show();
            }
            
            binding.playerView.setDrawingCacheEnabled(false);
        } catch (Exception e) {
            Log.e(TAG, "Error taking screenshot", e);
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    @RequiresApi(api = Build.VERSION_CODES.O)
    public void enterPictureInPictureMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                // Get current video dimensions for proper ratio
                VideoSize videoSize = player.getVideoSize();
                int width = videoSize.width > 0 ? videoSize.width : 16;
                int height = videoSize.height > 0 ? videoSize.height : 9;
                
                // Create rational aspect ratio based on video dimensions
                Rational aspectRatio = new Rational(width, height);
                
                // Create PiP params with auto-enter PiP mode disabled
                PictureInPictureParams.Builder paramsBuilder = new PictureInPictureParams.Builder()
                    .setAspectRatio(aspectRatio);
                
                // Set flag to track PiP mode
                isInPipMode = true;
                
                // Make sure video is playing before entering PiP
                if (!player.isPlaying()) {
                    player.play();
                    updatePlayPauseButton();
                }
                
                // Enter PiP mode with configured params
                enterPictureInPictureMode(paramsBuilder.build());
            } catch (Exception e) {
                Log.e(TAG, "Error entering PiP mode", e);
                Toast.makeText(this, "PiP mode not supported on this device: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "PiP requires Android 8.0 or higher", Toast.LENGTH_SHORT).show();
        }
    }

    private void toggleLockScreen() {
        isLocked = !isLocked;
        if (isLocked) {
            hidePlayerControls();
            binding.lockOverlay.setVisibility(View.VISIBLE);
            binding.btnLockScreenEdge.setImageResource(R.drawable.ic_unlock);
        } else {
            binding.lockOverlay.setVisibility(View.GONE);
            showPlayerControls();
            binding.btnLockScreenEdge.setImageResource(R.drawable.ic_lock);
        }
    }

    private void cyclePlaybackSpeed() {
        if (player == null) return;
        currentSpeedIndex = (currentSpeedIndex + 1) % playbackSpeeds.length;
        currentPlaybackSpeed = playbackSpeeds[currentSpeedIndex];
        player.setPlaybackSpeed(currentPlaybackSpeed);
        updateSpeedLabel();
        Toast.makeText(this, "Speed: " + currentPlaybackSpeed + "X", Toast.LENGTH_SHORT).show();
    }

    private void toggleScreenOrientation() {
        int currentOrientation = getResources().getConfiguration().orientation;
        
        if (isTV) {
            // Orientation change not supported on TV devices
            Toast.makeText(this, "Orientation change not supported on TV devices", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            userPreferredOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
            showSystemUI(); // Show system UI in portrait
            
            // Force layout refresh
            recreateActivity();
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            userPreferredOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
            hideSystemUI(); // Hide system UI in landscape
            
            // Force layout refresh
            recreateActivity();
        }
        
        // Save user preference
        sharedPreferences.edit().putInt(KEY_ORIENTATION_PREFERENCE, userPreferredOrientation).apply();
    }

    private void cycleAspectRatio() {
        if (binding.playerView == null) return;
        int currentMode = binding.playerView.getResizeMode();
        int nextMode;
        switch (currentMode) {
            case AspectRatioFrameLayout.RESIZE_MODE_FIT:
                nextMode = AspectRatioFrameLayout.RESIZE_MODE_FILL;
                Toast.makeText(this, "Aspect Ratio: Fill", Toast.LENGTH_SHORT).show();
                break;
            case AspectRatioFrameLayout.RESIZE_MODE_FILL:
                nextMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM;
                Toast.makeText(this, "Aspect Ratio: Zoom", Toast.LENGTH_SHORT).show();
                break;
            case AspectRatioFrameLayout.RESIZE_MODE_ZOOM:
                // Add fixed width/height modes for better quality on some videos
                nextMode = AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH;
                Toast.makeText(this, "Aspect Ratio: Fixed Width", Toast.LENGTH_SHORT).show();
                break;
            case AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH:
                nextMode = AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT;
                Toast.makeText(this, "Aspect Ratio: Fixed Height", Toast.LENGTH_SHORT).show();
                break;
            case AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT:
            default:
                nextMode = AspectRatioFrameLayout.RESIZE_MODE_FIT;
                Toast.makeText(this, "Aspect Ratio: Fit", Toast.LENGTH_SHORT).show();
                break;
        }
        binding.playerView.setResizeMode(nextMode);
    }

    @Override
    public void onPlaybackStateChanged(int playbackState) {
        if (player == null) return;
        if (playbackState == Player.STATE_READY) {
            long duration = player.getDuration();
            if (duration > 0 && duration != Long.MIN_VALUE) { 
                binding.timeBar.setDuration(duration);
                binding.tvTotalTime.setText(formatTime(duration));
                long lastPosition = sharedPreferences.getLong(KEY_LAST_POSITION + "_" + videoPath, 0); // path-specific key
                if (lastPosition > 0 && lastPosition < duration) {
                    player.seekTo(lastPosition);
                    sharedPreferences.edit().remove(KEY_LAST_POSITION + "_" + videoPath).apply();
                }
            }
        }
        if (playbackState == Player.STATE_ENDED) {
            // Auto-play next video if available
            if (currentVideoIndex < playlistVideos.size() - 1) {
                // If in PiP mode, directly play next video
                if (isInPipMode) {
                    playNextVideo();
                } else {
                    // In normal mode, show next video countdown
                    showNextVideoCountdown();
                }
            } else {
                updateGlobalPlayingState(false, null, null);
            }
        }
        updatePlayPauseButton();
    }
    
    private void showNextVideoCountdown() {
        if (currentVideoIndex >= playlistVideos.size() - 1) return;
        
        binding.nextEpisodeFloatingContainer.setVisibility(View.VISIBLE);
        final int[] countdown = {10}; // 10 second countdown
        
        final Handler countdownHandler = new Handler();
        final Runnable countdownRunnable = new Runnable() {
            @Override
            public void run() {
                countdown[0]--;
                binding.nextEpisodeCountdown.setText(String.valueOf(countdown[0]));
                
                if (countdown[0] <= 0) {
                    binding.nextEpisodeFloatingContainer.setVisibility(View.GONE);
                    playNextVideo();
                } else {
                    countdownHandler.postDelayed(this, 1000);
                }
            }
        };
        
        binding.btnCancelAutoplay.setOnClickListener(v -> {
            countdownHandler.removeCallbacks(countdownRunnable);
            binding.nextEpisodeFloatingContainer.setVisibility(View.GONE);
        });
        
        countdownHandler.post(countdownRunnable);
    }

    @Override
    public void onIsPlayingChanged(boolean isPlaying) {
        updatePlayPauseButton();
        if (isPlaying) {
            scheduleHideControls();
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            updateGlobalPlayingState(true, videoPath, videoTitle);
            timeUpdateHandler.post(timeUpdateRunnable);
        } else {
            hideControlsHandler.removeCallbacks(hideControlsRunnable);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            timeUpdateHandler.removeCallbacks(timeUpdateRunnable);
        }
    }

    @Override
    public void onPositionDiscontinuity(
            @NonNull Player.PositionInfo oldPosition,
            @NonNull Player.PositionInfo newPosition,
            int reason) {
        if (player == null) return;
        binding.tvCurrentTime.setText(formatTime(newPosition.positionMs));
        binding.timeBar.setPosition(newPosition.positionMs);
    }

    private final Handler timeUpdateHandler = new Handler(Looper.getMainLooper());
    private final Runnable timeUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            if (player != null && player.isPlaying()) {
                long currentPosition = player.getCurrentPosition();
                binding.tvCurrentTime.setText(formatTime(currentPosition));
                binding.timeBar.setPosition(currentPosition);
                timeUpdateHandler.postDelayed(this, 1000);
            }
        }
    };

    @Override
    public void onPlayerError(@NonNull PlaybackException error) {
        Log.e("PlayerActivity", "Player Error: " + error.getErrorCodeName(), error);
        
        // For TV devices, show more detailed error dialog
        if (isTV) {
            showTVSpecificErrorDialog("Playback Error: " + error.getErrorCodeName() + "\n" + error.getMessage());
        } else {
            // For phones, show standard error overlay
            Toast.makeText(this, "Player Error: " + error.getErrorCodeName() + " - " + error.getMessage(), Toast.LENGTH_LONG).show();
            updateGlobalPlayingState(false, null, null);
            
            // Show error overlay with retry option
            binding.errorOverlay.setVisibility(View.VISIBLE);
            binding.errorMessage.setText("Playback Error: " + error.getErrorCodeName());
            binding.btnRetry.setOnClickListener(v -> {
                binding.errorOverlay.setVisibility(View.GONE);
                initializePlayer();
            });
        }
    }

    @Override
    public void onVideoSizeChanged(@NonNull VideoSize videoSize) {
        Log.d("PlayerActivity", "Video size changed: " + videoSize.width + "x" + videoSize.height);
        
        // Adjust player view based on video dimensions
        if (videoSize.width > 0 && videoSize.height > 0) {
            // Calculate aspect ratio
            float videoAspectRatio = (float) videoSize.width / videoSize.height;
            
            // Get current resize mode
            int currentResizeMode = binding.playerView.getResizeMode();
            
            // If we're in a fixed mode, keep it, otherwise adjust based on ratio
            if (currentResizeMode != AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH && 
                currentResizeMode != AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT) {
                
                // For very wide videos (like 21:9), use fixed height to avoid letterboxing
                if (videoAspectRatio > 2.0f) {
                    binding.playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT);
                } 
                // For very tall videos, use fixed width
                else if (videoAspectRatio < 0.5f) {
                    binding.playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH);
                }
                // Otherwise keep current mode or use FIT by default
                else if (currentResizeMode == AspectRatioFrameLayout.RESIZE_MODE_FILL ||
                         currentResizeMode == AspectRatioFrameLayout.RESIZE_MODE_ZOOM) {
                    // Keep current mode
                } else {
                    binding.playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
                }
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        isActivityInForeground = true;
        if (player == null) {
            initializePlayer(); // Ensure player is initialized when activity is started/restarted
        }
        if (player != null && player.isPlaying()) {
             timeUpdateHandler.post(timeUpdateRunnable);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        isActivityInForeground = true;
        if (player != null) {
            boolean playWhenReady = sharedPreferences.getBoolean("playWhenReadyState_" + videoPath, false);
            player.setPlayWhenReady(playWhenReady);
        }
        showPlayerControls();
        
        // Refresh volume in case it changed outside the app
        currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        updateVolumeUI();
        
        // Apply appropriate UI visibility based on orientation
        int orientation = getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE && !isTV) {
            hideSystemUI();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        isActivityInForeground = false;
        timeUpdateHandler.removeCallbacks(timeUpdateRunnable);
        if (player != null) {
            saveLastPosition(); // Save position before pausing
            sharedPreferences.edit().putBoolean("playWhenReadyState_" + videoPath, player.getPlayWhenReady()).apply();
            
            // Always pause when going to background if not in PiP mode
            // This prevents unwanted background playback
            if (!isInPipMode) {
                player.setPlayWhenReady(false);
                updateGlobalPlayingState(false, videoPath, videoTitle);
            }
        }
    }
    
    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        
        isInPipMode = isInPictureInPictureMode;
        
        if (isInPictureInPictureMode) {
            // Hide all controls in PiP mode
            hidePlayerControls();
            binding.lockOverlay.setVisibility(View.GONE);
            binding.qualitySelectorDialog.setVisibility(View.GONE);
            binding.nextEpisodeFloatingContainer.setVisibility(View.GONE);
            binding.brightnessControlContainer.setVisibility(View.GONE);
            binding.volumeControlContainer.setVisibility(View.GONE);
            
            // Make sure video is playing in PiP mode
            if (player != null && !player.isPlaying()) {
                player.play();
                updatePlayPauseButton();
            }
        } else {
            // Show controls when exiting PiP mode
            showPlayerControls();
            
            // If activity is not in foreground, pause playback when exiting PiP mode
            if (!isActivityInForeground && player != null) {
                player.pause();
                updateGlobalPlayingState(false, null, null);
            }
        }
    }
    
    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        
        // Handle system UI visibility on orientation change
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            // In landscape, hide system UI and make controls more compact
            hideSystemUI();
            binding.bottomControls.setPadding(8, 16, 8, 8);
        } else {
            // In portrait, show system UI and make controls more visible
            showSystemUI();
            binding.bottomControls.setPadding(8, 24, 8, 16);
        }
    }
    
    // Method to recreate activity while preserving player state
    private void recreateActivity() {
        // Save current player state
        long currentPosition = 0;
        boolean wasPlaying = false;
        if (player != null) {
            currentPosition = player.getCurrentPosition();
            wasPlaying = player.isPlaying();
            player.pause();
        }
        
        // Save state in shared preferences
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putLong("temp_position", currentPosition);
        editor.putBoolean("temp_was_playing", wasPlaying);
        editor.apply();
        
        // Recreate activity to refresh layout
        recreate();
    }

    @Override
    public void onClick(View v) {
        if (player == null && v.getId() != R.id.btn_back && v.getId() != R.id.btn_unlock && v.getId() != R.id.btn_lock_screen_edge) {
            if ((v.getId() != R.id.btn_center_speed_icon) && (v.getId() != R.id.btn_center_screenshot)) {
                 Toast.makeText(this, "Player not ready", Toast.LENGTH_SHORT).show();
                 return;
            }
        }

        int id = v.getId();
        if (id == R.id.btn_back) {
            onBackPressed();
        } else if (id == R.id.btn_play_pause && player != null) {
            if (player.isPlaying()) {
                player.pause();
            } else {
                player.play();
            }
            updatePlayPauseButton();
            scheduleHideControls();
        } else if (id == R.id.btn_lock_screen_edge) {
            toggleLockScreen();
        } else if (id == R.id.btn_unlock) {
            toggleLockScreen();
        } else if (id == R.id.btn_screen_orientation_mx) {
            toggleScreenOrientation();
            scheduleHideControls();
        } else if (id == R.id.btn_aspect_ratio) {
            cycleAspectRatio();
            scheduleHideControls();
        } else if (id == R.id.btn_center_speed_icon && player != null) {
            cyclePlaybackSpeed();
            scheduleHideControls();
        } else if (id == R.id.btn_center_screenshot) {
            takeScreenshot();
            scheduleHideControls();
        } else if (id == R.id.btn_playlist) {
            togglePlaylistVisibility();
        } else if (id == R.id.btn_previous) {
            playPreviousVideo();
            scheduleHideControls();
        } else if (id == R.id.btn_next) {
            playNextVideo();
            scheduleHideControls();
        } else if (id == R.id.btn_pip_mode) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                enterPictureInPictureMode();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (player != null) {
            updateGlobalPlayingState(false, null, null);
            player.release();
            player = null;
        }
        hideControlsHandler.removeCallbacks(hideControlsRunnable);
        timeUpdateHandler.removeCallbacks(timeUpdateRunnable);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        binding = null; // Clear view binding
    }

    @Override
    public void onBackPressed() {
        if (isLocked) {
            toggleLockScreen();
            return;
        }
        
        if (binding.qualitySelectorDialog.getVisibility() == View.VISIBLE) {
            binding.qualitySelectorDialog.setVisibility(View.GONE);
            isPlaylistVisible = false;
            return;
        }
        
        if (binding.errorOverlay.getVisibility() == View.VISIBLE) {
            binding.errorOverlay.setVisibility(View.GONE);
            return;
        }
        
        if (binding.brightnessControlContainer.getVisibility() == View.VISIBLE) {
            binding.brightnessControlContainer.setVisibility(View.GONE);
            isBrightnessControlVisible = false;
            return;
        }
        
        if (binding.volumeControlContainer.getVisibility() == View.VISIBLE) {
            binding.volumeControlContainer.setVisibility(View.GONE);
            isVolumeControlVisible = false;
            return;
        }
        
        // Make sure player is stopped when exiting
        if (player != null) {
            player.pause();
        }
        updateGlobalPlayingState(false, null, null);
        super.onBackPressed();
    }
}
