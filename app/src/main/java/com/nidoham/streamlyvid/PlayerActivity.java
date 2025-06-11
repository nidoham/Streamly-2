package com.nidoham.streamlyvid;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.PictureInPictureParams;
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
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.TimeBar;

import com.nidoham.streamlyvid.databinding.ActivityPlayerBinding;
import com.nidoham.streamlyvid.model.VideoModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PlayerActivity extends AppCompatActivity implements Player.Listener, View.OnClickListener {

    private static final String TAG = "PlayerActivity";
    private static final String PREFS_NAME = "StreamlyAppPrefs";
    private static final int HIDE_CONTROLS_DELAY = 3000;
    private static final int BRIGHTNESS_MAX = 255;
    private static final float SWIPE_THRESHOLD = 30;

    // Core components
    private ActivityPlayerBinding binding;
    private ExoPlayer player;
    private SharedPreferences prefs;
    private GestureDetector gestureDetector;
    private AudioManager audioManager;

    // State variables
    private String videoPath, videoTitle;
    private List<VideoModel> playlist = new ArrayList<>();
    private int currentVideoIndex = 0;
    private boolean controlsVisible = true, isLocked = false, isInPipMode = false;
    private boolean isTV, canWriteSettings;
    
    // Playback controls
    private float currentPlaybackSpeed = 1.0f;
    private final float[] playbackSpeeds = {0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f};
    private int currentSpeedIndex = 2;
    
    // Audio/Video controls
    private int maxVolume, currentVolume;
    private float currentBrightness;
    
    // Handlers
    private final Handler controlsHandler = new Handler(Looper.getMainLooper());
    private final Handler timeHandler = new Handler(Looper.getMainLooper());
    private final Runnable hideControlsRunnable = this::hidePlayerControls;
    private final Runnable timeUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            if (player != null && player.isPlaying()) {
                updateTimeDisplay();
                timeHandler.postDelayed(this, 1000);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPlayerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        initializeComponents();
        setupFromIntent();
        setupUI();
        initializePlayer();
    }

    private void initializeComponents() {
        // Window setup
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(Color.BLACK);
        }

        // Core components
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        
        // Device checks
        isTV = getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK);
        canWriteSettings = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || 
                          Settings.System.canWrite(this);
        
        initializeBrightness();
        if (isTV) requestTVPermissions();
    }

    private void setupFromIntent() {
        videoPath = getIntent().getStringExtra("videoPath");
        videoTitle = getIntent().getStringExtra("videoTitle");
        
        // Handle playlist
        ArrayList<VideoModel> intentPlaylist = getIntent().getParcelableArrayListExtra("playlist");
        if (intentPlaylist != null && !intentPlaylist.isEmpty()) {
            playlist.addAll(intentPlaylist);
            currentVideoIndex = Math.max(0, Math.min(getIntent().getIntExtra("currentIndex", 0), 
                                                   playlist.size() - 1));
            updateVideoFromPlaylist();
        } else if (videoPath != null) {
            createSingleVideoPlaylist();
        }

        if (videoPath == null) {
            Toast.makeText(this, "Invalid video path", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void setupUI() {
        setupClickListeners();
        setupGestureDetector();
        setupVolumeAndBrightnessControls();
        setupTimeBar();
        
        binding.videoTitle.setText(videoTitle != null ? videoTitle : "Unknown Video");
        updatePlayPauseButton();
        updateNavigationButtons();
        applyOrientationSettings();
        showPlayerControls();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupGestureDetector() {
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
                if (!isLocked) togglePlayerControlsVisibility();
                return true;
            }

            @Override
            public boolean onDoubleTap(@NonNull MotionEvent e) {
                if (player == null || isLocked) return false;
                
                float screenWidth = binding.gestureOverlay.getWidth();
                long seekTime = e.getX() < screenWidth / 2 ? -10000 : 10000;
                
                player.seekTo(Math.max(0, Math.min(player.getDuration(), 
                                     player.getCurrentPosition() + seekTime)));
                showDoubleTapIndicator(seekTime > 0);
                showPlayerControls();
                return true;
            }
        });

        binding.gestureOverlay.setOnTouchListener(new SwipeGestureHandler());
    }

    private class SwipeGestureHandler implements View.OnTouchListener {
        private float initialX, initialY, initialVolume, initialBrightness;
        private boolean isGestureActive = false;
        private int gestureType = 0; // 0=none, 1=volume, 2=brightness

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            gestureDetector.onTouchEvent(event);
            if (isLocked) return true;

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    initialX = event.getX();
                    initialY = event.getY();
                    initialVolume = currentVolume;
                    initialBrightness = currentBrightness;
                    isGestureActive = false;
                    gestureType = 0;
                    break;

                case MotionEvent.ACTION_MOVE:
                    if (!isGestureActive) {
                        float deltaY = Math.abs(event.getY() - initialY);
                        float deltaX = Math.abs(event.getX() - initialX);
                        
                        if (deltaY > SWIPE_THRESHOLD && deltaY > deltaX * 2) {
                            isGestureActive = true;
                            gestureType = initialX < binding.gestureOverlay.getWidth() / 2 ? 2 : 1;
                            
                            if (gestureType == 1) showVolumeControl();
                            else showBrightnessControl();
                        }
                    }
                    
                    if (isGestureActive) {
                        float deltaY = initialY - event.getY();
                        float screenHeight = binding.gestureOverlay.getHeight();
                        float percentChange = deltaY / (screenHeight / 2);
                        
                        if (gestureType == 1) {
                            int newVolume = (int) Math.max(0, Math.min(maxVolume, 
                                                         initialVolume + percentChange * maxVolume));
                            setVolume(newVolume);
                        } else if (gestureType == 2) {
                            int newBrightness = (int) Math.max(0, Math.min(BRIGHTNESS_MAX, 
                                                              initialBrightness + percentChange * BRIGHTNESS_MAX));
                            setBrightness(newBrightness);
                        }
                    }
                    break;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    isGestureActive = false;
                    gestureType = 0;
                    break;
            }
            return true;
        }
    }

    private void setupVolumeAndBrightnessControls() {
        // Volume control
        binding.volumeSeekbar.setMax(maxVolume);
        binding.volumeSeekbar.setProgress(currentVolume);
        binding.volumeSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) setVolume(progress);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { pauseControlsHiding(); }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { scheduleHideControls(); }
        });

        // Brightness control
        binding.brightnessSeekbar.setMax(BRIGHTNESS_MAX);
        binding.brightnessSeekbar.setProgress((int) currentBrightness);
        binding.brightnessSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) setBrightness(progress);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { pauseControlsHiding(); }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { scheduleHideControls(); }
        });
    }

    private void setupTimeBar() {
        binding.timeBar.addListener(new TimeBar.OnScrubListener() {
            @Override
            public void onScrubStart(@NonNull TimeBar timeBar, long position) {
                pauseControlsHiding();
            }

            @Override
            public void onScrubMove(@NonNull TimeBar timeBar, long position) {
                if (player != null) binding.tvCurrentTime.setText(formatTime(position));
            }

            @Override
            public void onScrubStop(@NonNull TimeBar timeBar, long position, boolean cancelled) {
                if (player != null && !cancelled) player.seekTo(position);
                scheduleHideControls();
            }
        });
    }

    private void setupClickListeners() {
        int[] buttonIds = {R.id.btn_back, R.id.btn_play_pause, R.id.btn_lock_screen_edge,
                          R.id.btn_unlock, R.id.btn_screen_orientation_mx, R.id.btn_aspect_ratio,
                          R.id.btn_center_speed_icon, R.id.btn_center_screenshot, R.id.btn_playlist,
                          R.id.btn_pip_mode, R.id.btn_previous, R.id.btn_next};
        
        for (int id : buttonIds) {
            findViewById(id).setOnClickListener(this);
        }
    }

    private void initializePlayer() {
        try {
            player = new ExoPlayer.Builder(this).build();
            binding.playerView.setPlayer(player);
            binding.playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
            player.addListener(this);
            
            prepareMedia(videoPath);
            player.setPlayWhenReady(false); // Start paused
            updateGlobalPlayingState(false, videoPath, videoTitle);
        } catch (Exception e) {
            handlePlayerError("Error initializing player: " + e.getMessage());
        }
    }

    private void prepareMedia(String path) {
        try {
            MediaItem mediaItem = MediaItem.fromUri(Uri.parse(path));
            player.setMediaItem(mediaItem);
            player.prepare();
            Log.d(TAG, "Media prepared: " + path);
        } catch (Exception e) {
            handlePlayerError("Error preparing media: " + e.getMessage());
        }
    }

    // Control methods
    private void togglePlayerControlsVisibility() {
        if (controlsVisible) hidePlayerControls();
        else showPlayerControls();
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
        pauseControlsHiding();
    }

    private void scheduleHideControls() {
        pauseControlsHiding();
        if (player != null && player.isPlaying() && controlsVisible && !isLocked) {
            controlsHandler.postDelayed(hideControlsRunnable, HIDE_CONTROLS_DELAY);
        }
    }

    private void pauseControlsHiding() {
        controlsHandler.removeCallbacks(hideControlsRunnable);
    }

    // Playlist navigation
    private void playNextVideo() {
        if (currentVideoIndex < playlist.size() - 1) {
            currentVideoIndex++;
            switchToVideoAtIndex();
        } else {
            Toast.makeText(this, "No more videos in playlist", Toast.LENGTH_SHORT).show();
        }
    }

    private void playPreviousVideo() {
        if (currentVideoIndex > 0) {
            currentVideoIndex--;
            switchToVideoAtIndex();
        } else {
            Toast.makeText(this, "Already at first video", Toast.LENGTH_SHORT).show();
        }
    }

    private void switchToVideoAtIndex() {
        if (currentVideoIndex >= 0 && currentVideoIndex < playlist.size()) {
            updateVideoFromPlaylist();
            if (player != null) player.release();
            initializePlayer();
            updateNavigationButtons();
            
            Toast.makeText(this, String.format(Locale.getDefault(),
                "Playing: %s (%d of %d)", videoTitle, currentVideoIndex + 1, playlist.size()),
                Toast.LENGTH_SHORT).show();
        }
    }

    // Utility methods
    private void updateVideoFromPlaylist() {
        if (currentVideoIndex < playlist.size()) {
            VideoModel video = playlist.get(currentVideoIndex);
            videoPath = video.getData();
            videoTitle = video.getTitle();
            binding.videoTitle.setText(videoTitle);
        }
    }

    private void createSingleVideoPlaylist() {
        VideoModel video = new VideoModel();
        video.setId(System.currentTimeMillis());
        video.setTitle(videoTitle != null ? videoTitle : "Unknown Video");
        video.setData(videoPath);
        video.setUri(Uri.parse(videoPath));
        playlist.add(video);
        currentVideoIndex = 0;
    }

    private void updateNavigationButtons() {
        boolean hasPrevious = currentVideoIndex > 0;
        boolean hasNext = currentVideoIndex < playlist.size() - 1;
        
        binding.btnPrevious.setEnabled(hasPrevious);
        binding.btnPrevious.setAlpha(hasPrevious ? 1.0f : 0.5f);
        binding.btnNext.setEnabled(hasNext);
        binding.btnNext.setAlpha(hasNext ? 1.0f : 0.5f);
    }

    private void updatePlayPauseButton() {
        if (player != null && player.isPlaying()) {
            binding.btnPlayPause.setImageResource(android.R.drawable.ic_media_pause);
        } else {
            binding.btnPlayPause.setImageResource(android.R.drawable.ic_media_play);
        }
    }

    private void updateTimeDisplay() {
        if (player != null) {
            long currentPosition = player.getCurrentPosition();
            binding.tvCurrentTime.setText(formatTime(currentPosition));
            binding.timeBar.setPosition(currentPosition);
        }
    }

    private String formatTime(long timeMs) {
        if (timeMs <= 0) return "00:00";
        
        long totalSeconds = timeMs / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        
        if (hours > 0) {
            return String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
        }
    }

    // Audio/Video controls
    private void setVolume(int volume) {
        currentVolume = Math.max(0, Math.min(maxVolume, volume));
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, currentVolume, 0);
        binding.volumeSeekbar.setProgress(currentVolume);
        binding.volumePercentage.setText((currentVolume * 100 / maxVolume) + "%");
        prefs.edit().putInt("volume_level", currentVolume).apply();
    }

    private void setBrightness(int brightness) {
        currentBrightness = Math.max(0, Math.min(BRIGHTNESS_MAX, brightness));
        
        WindowManager.LayoutParams layoutParams = getWindow().getAttributes();
        layoutParams.screenBrightness = currentBrightness / (float) BRIGHTNESS_MAX;
        getWindow().setAttributes(layoutParams);
        
        binding.brightnessSeekbar.setProgress((int) currentBrightness);
        binding.brightnessPercentage.setText(((int) currentBrightness * 100 / BRIGHTNESS_MAX) + "%");
        prefs.edit().putInt("brightness_level", (int) currentBrightness).apply();
    }

    private void showVolumeControl() {
        binding.volumeControlContainer.setVisibility(View.VISIBLE);
        new Handler().postDelayed(() -> 
            binding.volumeControlContainer.setVisibility(View.GONE), 3000);
    }

    private void showBrightnessControl() {
        binding.brightnessControlContainer.setVisibility(View.VISIBLE);
        new Handler().postDelayed(() -> 
            binding.brightnessControlContainer.setVisibility(View.GONE), 3000);
    }

    // Advanced features
    private void cyclePlaybackSpeed() {
        if (player == null) return;
        currentSpeedIndex = (currentSpeedIndex + 1) % playbackSpeeds.length;
        currentPlaybackSpeed = playbackSpeeds[currentSpeedIndex];
        player.setPlaybackSpeed(currentPlaybackSpeed);
        binding.tvPlaybackSpeed.setText(String.format(Locale.getDefault(), "%.1fx", currentPlaybackSpeed));
        Toast.makeText(this, "Speed: " + currentPlaybackSpeed + "X", Toast.LENGTH_SHORT).show();
    }

    private void cycleAspectRatio() {
        if (binding.playerView == null) return;
        
        int currentMode = binding.playerView.getResizeMode();
        int nextMode;
        String modeName;
        
        switch (currentMode) {
            case AspectRatioFrameLayout.RESIZE_MODE_FIT:
                nextMode = AspectRatioFrameLayout.RESIZE_MODE_FILL;
                modeName = "Fill";
                break;
            case AspectRatioFrameLayout.RESIZE_MODE_FILL:
                nextMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM;
                modeName = "Zoom";
                break;
            case AspectRatioFrameLayout.RESIZE_MODE_ZOOM:
                nextMode = AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH;
                modeName = "Fixed Width";
                break;
            case AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH:
                nextMode = AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT;
                modeName = "Fixed Height";
                break;
            default:
                nextMode = AspectRatioFrameLayout.RESIZE_MODE_FIT;
                modeName = "Fit";
                break;
        }
        
        binding.playerView.setResizeMode(nextMode);
        Toast.makeText(this, "Aspect Ratio: " + modeName, Toast.LENGTH_SHORT).show();
    }

    // Fixed: Changed from private to public and renamed to avoid override conflict
    @RequiresApi(api = Build.VERSION_CODES.O)
    public void enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && player != null) {
            try {
                VideoSize videoSize = player.getVideoSize();
                Rational aspectRatio = new Rational(
                    videoSize.width > 0 ? videoSize.width : 16,
                    videoSize.height > 0 ? videoSize.height : 9
                );
                
                PictureInPictureParams params = new PictureInPictureParams.Builder()
                    .setAspectRatio(aspectRatio)
                    .build();
                
                isInPipMode = true;
                if (!player.isPlaying()) player.play();
                enterPictureInPictureMode(params);
            } catch (Exception e) {
                Toast.makeText(this, "PiP not supported: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void takeScreenshot() {
        try {
            binding.playerView.setDrawingCacheEnabled(true);
            Bitmap bitmap = binding.playerView.getDrawingCache();
            
            if (bitmap != null) {
                String fileName = "Screenshot_" + System.currentTimeMillis() + ".jpg";
                String path = MediaStore.Images.Media.insertImage(getContentResolver(), 
                                                                bitmap, fileName, "Video screenshot");
                Toast.makeText(this, path != null ? "Screenshot saved" : "Failed to save", 
                             Toast.LENGTH_SHORT).show();
            }
            
            binding.playerView.setDrawingCacheEnabled(false);
        } catch (Exception e) {
            Toast.makeText(this, "Screenshot error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // Click handler
    @Override
    public void onClick(View v) {
        int id = v.getId();
        
        if (id == R.id.btn_back) {
            onBackPressed();
        } else if (id == R.id.btn_play_pause && player != null) {
            if (player.isPlaying()) player.pause();
            else player.play();
            updatePlayPauseButton();
            scheduleHideControls();
        } else if (id == R.id.btn_lock_screen_edge || id == R.id.btn_unlock) {
            toggleLockScreen();
        } else if (id == R.id.btn_screen_orientation_mx) {
            toggleScreenOrientation();
        } else if (id == R.id.btn_aspect_ratio) {
            cycleAspectRatio();
        } else if (id == R.id.btn_center_speed_icon) {
            cyclePlaybackSpeed();
        } else if (id == R.id.btn_center_screenshot) {
            takeScreenshot();
        } else if (id == R.id.btn_previous) {
            playPreviousVideo();
        } else if (id == R.id.btn_next) {
            playNextVideo();
        } else if (id == R.id.btn_pip_mode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            enterPipMode();
        }
        
        scheduleHideControls();
    }

    // Player event handlers
    @Override
    public void onPlaybackStateChanged(int playbackState) {
        if (player == null) return;
        
        if (playbackState == Player.STATE_READY) {
            long duration = player.getDuration();
            if (duration > 0) {
                binding.timeBar.setDuration(duration);
                binding.tvTotalTime.setText(formatTime(duration));
            }
        } else if (playbackState == Player.STATE_ENDED) {
            if (currentVideoIndex < playlist.size() - 1) {
                if (isInPipMode) playNextVideo();
                else showNextVideoCountdown();
            } else {
                updateGlobalPlayingState(false, null, null);
            }
        }
        updatePlayPauseButton();
    }

    @Override
    public void onIsPlayingChanged(boolean isPlaying) {
        updatePlayPauseButton();
        if (isPlaying) {
            scheduleHideControls();
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            updateGlobalPlayingState(true, videoPath, videoTitle);
            timeHandler.post(timeUpdateRunnable);
        } else {
            pauseControlsHiding();
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            timeHandler.removeCallbacks(timeUpdateRunnable);
        }
    }

    @Override
    public void onPlayerError(@NonNull PlaybackException error) {
        handlePlayerError("Playback Error: " + error.getErrorCodeName());
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, @NonNull Configuration newConfig) {
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
        }
    }

    // Lifecycle methods
    @Override
    protected void onResume() {
        super.onResume();
        if (player != null && player.isPlaying()) {
            timeHandler.post(timeUpdateRunnable);
        }
        showPlayerControls();
        applyOrientationSettings();
    }

    @Override
    protected void onPause() {
        super.onPause();
        timeHandler.removeCallbacks(timeUpdateRunnable);
        if (player != null && !isInPipMode) {
            player.setPlayWhenReady(false);
            updateGlobalPlayingState(false, videoPath, videoTitle);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (player != null) {
            updateGlobalPlayingState(false, null, null);
            player.release();
        }
        controlsHandler.removeCallbacks(hideControlsRunnable);
        timeHandler.removeCallbacks(timeUpdateRunnable);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    // Helper methods (simplified implementations)
    private void initializeBrightness() {
        try {
            currentBrightness = Settings.System.getInt(getContentResolver(), 
                                                     Settings.System.SCREEN_BRIGHTNESS);
        } catch (Exception e) {
            currentBrightness = BRIGHTNESS_MAX / 2;
        }
    }

    private void requestTVPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 1001);
        }
    }

    private void applyOrientationSettings() {
        if (isTV) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            hideSystemUI();
        } else {
            int orientation = getResources().getConfiguration().orientation;
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) hideSystemUI();
            else showSystemUI();
        }
    }

    private void hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    private void showSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(true);
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        }
    }

    private void toggleLockScreen() {
        isLocked = !isLocked;
        if (isLocked) {
            hidePlayerControls();
            binding.lockOverlay.setVisibility(View.VISIBLE);
        } else {
            binding.lockOverlay.setVisibility(View.GONE);
            showPlayerControls();
        }
    }

    private void toggleScreenOrientation() {
        if (isTV) {
            Toast.makeText(this, "Orientation change not supported on TV", Toast.LENGTH_SHORT).show();
            return;
        }
        
        int currentOrientation = getResources().getConfiguration().orientation;
        setRequestedOrientation(currentOrientation == Configuration.ORIENTATION_LANDSCAPE ?
                              ActivityInfo.SCREEN_ORIENTATION_PORTRAIT :
                              ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
    }

    private void showDoubleTapIndicator(boolean isForward) {
        View indicator = isForward ? binding.doubleTapOverlayRight : binding.doubleTapOverlayLeft;
        indicator.setVisibility(View.VISIBLE);
        new Handler().postDelayed(() -> indicator.setVisibility(View.GONE), 800);
    }

    private void showNextVideoCountdown() {
        if (currentVideoIndex >= playlist.size() - 1) return;
        
        binding.nextEpisodeFloatingContainer.setVisibility(View.VISIBLE);
        final int[] countdown = {10};
        
        Handler countdownHandler = new Handler();
        Runnable countdownRunnable = new Runnable() {
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

    private void handlePlayerError(String message) {
        Log.e(TAG, message);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        updateGlobalPlayingState(false, null, null);
    }

    private void updateGlobalPlayingState(boolean isPlaying, String path, String title) {
        prefs.edit()
            .putBoolean("is_playing_global", isPlaying)
            .putString("last_video_path", isPlaying ? path : null)
            .putString("last_video_title", isPlaying ? title : null)
            .apply();
    }

    @Override
    public void onBackPressed() {
        if (isLocked) {
            toggleLockScreen();
            return;
        }
        
        // Hide any visible overlays first
        if (binding.qualitySelectorDialog.getVisibility() == View.VISIBLE ||
            binding.errorOverlay.getVisibility() == View.VISIBLE ||
            binding.brightnessControlContainer.getVisibility() == View.VISIBLE ||
            binding.volumeControlContainer.getVisibility() == View.VISIBLE) {
            
            binding.qualitySelectorDialog.setVisibility(View.GONE);
            binding.errorOverlay.setVisibility(View.GONE);
            binding.brightnessControlContainer.setVisibility(View.GONE);
            binding.volumeControlContainer.setVisibility(View.GONE);
            return;
        }
        
        if (player != null) player.pause();
        updateGlobalPlayingState(false, null, null);
        super.onBackPressed();
    }
}