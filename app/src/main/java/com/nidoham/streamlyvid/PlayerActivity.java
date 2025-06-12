package com.nidoham.streamlyvid;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.PictureInPictureParams;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
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
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
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

import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.Glide;
import com.nidoham.streamlyvid.databinding.ActivityPlayerBinding;
import com.nidoham.streamlyvid.model.VideoModel;
import com.nidoham.streamlyvid.service.PlayerService;
import com.nidoham.streamlyvid.service.ServiceHelper;

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
    
    // Service components
    private ServiceHelper serviceHelper;
    private boolean useBackgroundService = false;
    private boolean isAudioOnlyMode = false;

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
    
    // UI elements for audio mode
    private TextView audioModeIndicator;
    private ImageView thumbnailImageView;
    
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

    // Service broadcast receiver
    private BroadcastReceiver serviceReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action != null) {
                switch (action) {
                    case PlayerService.BROADCAST_PLAYBACK_STATE:
                        boolean isPlaying = intent.getBooleanExtra("isPlaying", false);
                        updatePlayPauseButton();
                        if (isPlaying) {
                            scheduleHideControls();
                            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                        } else {
                            pauseControlsHiding();
                            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                        }
                        break;
                        
                    case PlayerService.BROADCAST_POSITION_UPDATE:
                        long currentPosition = intent.getLongExtra("currentPosition", 0);
                        long duration = intent.getLongExtra("duration", 0);
                        updateTimeDisplayFromService(currentPosition, duration);
                        break;
                        
                    case "NEXT_TRACK":
                        playNextVideo();
                        break;
                        
                    case "PREVIOUS_TRACK":
                        playPreviousVideo();
                        break;
                        
                    case "MEDIA_ENDED":
                        if (currentVideoIndex < playlist.size() - 1) {
                            playNextVideo();
                        }
                        break;
                        
                    case "PLAYER_ERROR":
                        handlePlayerError("Service playback error occurred");
                        break;
                }
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
        
        // Register service receiver
        registerServiceReceiver();
        
        // Always start with local ExoPlayer (not background service)
        initializePlayer();
    }
    
    private void hideSystemUI() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_IMMERSIVE
            | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
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
        
        // Initialize audio mode UI elements
        initializeAudioModeUI();
    }

    private void initializeAudioModeUI() {
        // Create audio mode indicator if not exists
        if (audioModeIndicator == null) {
            audioModeIndicator = new TextView(this);
            audioModeIndicator.setText("🎵 Audio Only Mode");
            audioModeIndicator.setTextColor(Color.WHITE);
            audioModeIndicator.setBackgroundColor(Color.parseColor("#80000000"));
            audioModeIndicator.setPadding(16, 8, 16, 8);
            audioModeIndicator.setVisibility(View.GONE);
            
            // Add to layout (you may need to adjust this based on your layout structure)
            if (binding.getRoot() instanceof androidx.coordinatorlayout.widget.CoordinatorLayout) {
                androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams params = 
                    new androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams(
                        androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams.WRAP_CONTENT,
                        androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams.WRAP_CONTENT
                    );
                params.gravity = android.view.Gravity.TOP | android.view.Gravity.CENTER_HORIZONTAL;
                params.topMargin = 100;
                ((androidx.coordinatorlayout.widget.CoordinatorLayout) binding.getRoot()).addView(audioModeIndicator, params);
            }
        }
        
        // Initialize thumbnail for current video if in playlist
        if (!playlist.isEmpty() && currentVideoIndex < playlist.size()) {
            loadThumbnailForCurrentVideo();
        }
    }

    private void loadThumbnailForCurrentVideo() {
        if (currentVideoIndex >= 0 && currentVideoIndex < playlist.size()) {
            VideoModel currentVideo = playlist.get(currentVideoIndex);
            loadVideoThumbnail(currentVideo);
        }
    }

    private void loadVideoThumbnail(VideoModel video) {
        if (binding.audioView == null) return;
        
        RequestOptions options = new RequestOptions()
            .centerCrop()
            .placeholder(R.drawable.ic_video_placeholder)
            .error(R.drawable.ic_video_placeholder)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .dontAnimate()
            .override(400, 400); // Optimize image size
            
        try {
            // First try to load from URI
            if (video.getUri() != null) {
                Glide.with(getApplicationContext())
                    .load(video.getUri())
                    .apply(options)
                    .into(binding.audioView);
            } 
            // Then try from file path
            else if (video.getData() != null) {
                // Try to extract thumbnail from video file
                extractAndLoadThumbnail(video.getData());
            } 
            // Fallback to placeholder
            else {
                binding.audioView.setImageResource(R.drawable.ic_video_placeholder);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading thumbnail", e);
            binding.audioView.setImageResource(R.drawable.ic_video_placeholder);
        }
    }

    private void extractAndLoadThumbnail(String videoPath) {
        // Use background thread for thumbnail extraction
        new Thread(() -> {
            try {
                MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                retriever.setDataSource(videoPath);
                
                // Get frame at 1 second or 10% of duration, whichever is smaller
                String durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                long duration = durationStr != null ? Long.parseLong(durationStr) : 0;
                long timeUs = Math.min(1000000, duration * 100); // 1 second or 10% of duration
                
                Bitmap thumbnail = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                retriever.release();
                
                // Update UI on main thread
                runOnUiThread(() -> {
                    if (thumbnail != null && binding.audioView != null) {
                        binding.audioView.setImageBitmap(thumbnail);
                    } else {
                        binding.audioView.setImageResource(R.drawable.ic_video_placeholder);
                    }
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Error extracting thumbnail from video", e);
                runOnUiThread(() -> {
                    if (binding.audioView != null) {
                        binding.audioView.setImageResource(R.drawable.ic_video_placeholder);
                    }
                });
            }
        }).start();
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

    private void showAudioTrackDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Playback Mode");
        
        String[] options = {"Video Mode", "Audio Only Mode"};
        int currentSelection = isAudioOnlyMode ? 1 : 0;
        
        builder.setSingleChoiceItems(options, currentSelection, (dialog, which) -> {
            boolean newAudioOnlyMode = (which == 1);
            
            if (newAudioOnlyMode != isAudioOnlyMode) {
                isAudioOnlyMode = newAudioOnlyMode;
                switchPlaybackMode(isAudioOnlyMode);
                
                Toast.makeText(this, 
                    isAudioOnlyMode ? "Switched to Audio Only Mode - Background playback enabled" : "Switched to Video Mode", 
                    Toast.LENGTH_LONG).show();
            }
            
            dialog.dismiss();
        });
        
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void switchPlaybackMode(boolean audioOnlyMode) {
        // Save current state
        boolean wasPlaying = false;
        long currentPosition = 0;
        
        if (useBackgroundService && serviceHelper != null) {
            wasPlaying = serviceHelper.isPlaying();
            currentPosition = serviceHelper.getCurrentPosition();
            serviceHelper.stopPlayback();
            serviceHelper.unbindService();
        } else if (player != null) {
            wasPlaying = player.isPlaying();
            currentPosition = player.getCurrentPosition();
            player.release();
            player = null;
        }
        
        // Switch mode
        useBackgroundService = audioOnlyMode;
        isAudioOnlyMode = audioOnlyMode;
        
        if (audioOnlyMode) {
            // Hide video view and show audio view with thumbnail
            binding.playerView.setVisibility(View.GONE);
            binding.audioView.setVisibility(View.VISIBLE);
            showAudioOnlyIndicator();
            
            // Load thumbnail for current video
            loadThumbnailForCurrentVideo();
            
            // Update audio view with current video info
            updateAudioViewInfo();
        } else {
            // Show video view and hide audio view
            binding.playerView.setVisibility(View.VISIBLE);
            binding.audioView.setVisibility(View.GONE);
            hideAudioOnlyIndicator();
        }
        
        // Reinitialize player
        initializePlayer();
        
        // Restore state
        if (currentPosition > 0) {
            if (useBackgroundService && serviceHelper != null) {
                serviceHelper.seekTo(currentPosition);
                if (wasPlaying) serviceHelper.resumePlayback();
            } else if (player != null) {
                player.seekTo(currentPosition);
                if (wasPlaying) player.play();
            }
        }
    }

    private void updateAudioViewInfo() {
        if (currentVideoIndex >= 0 && currentVideoIndex < playlist.size()) {
            VideoModel currentVideo = playlist.get(currentVideoIndex);
            
            // Update title if you have a title view in audio mode
            if (binding.videoTitle != null) {
                binding.videoTitle.setText(currentVideo.getTitle());
            }
            
            // You can add more audio view updates here like artist, duration, etc.
        }
    }

    private void showAudioOnlyIndicator() {
        if (audioModeIndicator != null) {
            audioModeIndicator.setVisibility(View.VISIBLE);
        }
    }

    private void hideAudioOnlyIndicator() {
        if (audioModeIndicator != null) {
            audioModeIndicator.setVisibility(View.GONE);
        }
    }

    private void initializeServiceHelper() {
        if (serviceHelper == null) {
            serviceHelper = new ServiceHelper(this);
            serviceHelper.setConnectionListener(new ServiceHelper.ServiceConnectionListener() {
                @Override
                public void onServiceConnected(PlayerService service) {
                    Log.d(TAG, "Connected to PlayerService");
                    updatePlayPauseButton();
                }
                
                @Override
                public void onServiceDisconnected() {
                    Log.d(TAG, "Disconnected from PlayerService");
                }
            });
        }
    }

    private void registerServiceReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(PlayerService.BROADCAST_PLAYBACK_STATE);
        filter.addAction(PlayerService.BROADCAST_POSITION_UPDATE);
        filter.addAction("NEXT_TRACK");
        filter.addAction("PREVIOUS_TRACK");
        filter.addAction("MEDIA_ENDED");
        filter.addAction("PLAYER_ERROR");
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(serviceReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(serviceReceiver, filter);
        }
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
                if (isLocked || isAudioOnlyMode) return false; // Disable double tap in audio mode
                
                float screenWidth = binding.gestureOverlay.getWidth();
                long seekTime = e.getX() < screenWidth / 2 ? -10000 : 10000;
                
                if (useBackgroundService && serviceHelper != null) {
                    long newPosition = Math.max(0, Math.min(serviceHelper.getDuration(), 
                                              serviceHelper.getCurrentPosition() + seekTime));
                    serviceHelper.seekTo(newPosition);
                } else if (player != null) {
                    player.seekTo(Math.max(0, Math.min(player.getDuration(), 
                                         player.getCurrentPosition() + seekTime)));
                }
                
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
                            
                            if (gestureType == 1) {
                                showVolumeControl();
                            } else if (!isAudioOnlyMode) {
                                showBrightnessControl(); // Only show brightness in video mode
                            }
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
                        } else if (gestureType == 2 && !isAudioOnlyMode) {
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
                binding.tvCurrentTime.setText(formatTime(position));
            }

            @Override
            public void onScrubStop(@NonNull TimeBar timeBar, long position, boolean cancelled) {
                if (!cancelled) {
                    if (useBackgroundService && serviceHelper != null) {
                        serviceHelper.seekTo(position);
                    } else if (player != null) {
                        player.seekTo(position);
                    }
                }
                scheduleHideControls();
            }
        });
    }

    private void setupClickListeners() {
        int[] buttonIds = {R.id.btn_back, R.id.btn_play_pause, R.id.btn_lock_screen_edge,
                          R.id.btn_unlock, R.id.btn_screen_orientation_mx, R.id.btn_aspect_ratio,
                          R.id.btn_center_speed_icon, R.id.btn_center_screenshot, R.id.btn_playlist,
                          R.id.btn_pip_mode, R.id.btn_previous, R.id.btn_next, R.id.btn_center_audio_track};
        
        for (int id : buttonIds) {
            View button = findViewById(id);
            if (button != null) {
                button.setOnClickListener(this);
            }
        }
    }

    private void initializePlayer() {
        if (useBackgroundService && isAudioOnlyMode) {
            // Use background service for audio-only playback
            initializeServiceHelper();
            serviceHelper.bindService();
            
            // Start playback through service
            binding.playerView.setVisibility(View.GONE);
            binding.audioView.setVisibility(View.VISIBLE);
            
            // Load thumbnail before starting service
            loadThumbnailForCurrentVideo();
            
            serviceHelper.startPlayback(videoPath, videoTitle, "Unknown Artist", true);
            updateGlobalPlayingState(false, videoPath, videoTitle);
        } else {
            // Use ExoPlayer for video playback (default mode)
            try {
                player = new ExoPlayer.Builder(this).build();
                binding.playerView.setPlayer(player);
                binding.playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
                player.addListener(this);
                
                // Ensure video view is visible and audio view is hidden
                binding.playerView.setVisibility(View.VISIBLE);
                binding.audioView.setVisibility(View.GONE);
                
                prepareMedia(videoPath);
                player.setPlayWhenReady(false);
                updateGlobalPlayingState(false, videoPath, videoTitle);
                
                player.prepare();
                player.play();
            } catch (Exception e) {
                handlePlayerError("Error initializing player: " + e.getMessage());
            }
        }
    }

    private boolean isAudioFile(String path) {
        if (path == null) return false;
        String lowerPath = path.toLowerCase();
        return lowerPath.endsWith(".mp3") || lowerPath.endsWith(".aac") || 
               lowerPath.endsWith(".wav") || lowerPath.endsWith(".flac") ||
               lowerPath.endsWith(".ogg") || lowerPath.endsWith(".m4a");
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
        boolean isPlaying = false;
        
        if (useBackgroundService && serviceHelper != null) {
            isPlaying = serviceHelper.isPlaying();
        } else if (player != null) {
            isPlaying = player.isPlaying();
        }
        
        if (isPlaying && controlsVisible && !isLocked) {
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
            
            // Update thumbnail if in audio-only mode
            if (isAudioOnlyMode) {
                loadThumbnailForCurrentVideo();
                updateAudioViewInfo();
            }
            
            if (useBackgroundService && serviceHelper != null) {
                serviceHelper.stopPlayback();
                serviceHelper.startPlayback(videoPath, videoTitle, "Unknown Artist", isAudioOnlyMode);
            } else {
                if (player != null) player.release();
                initializePlayer();
            }
            
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
        boolean isPlaying = false;
        
        if (useBackgroundService && serviceHelper != null) {
            isPlaying = serviceHelper.isPlaying();
        } else if (player != null) {
            isPlaying = player.isPlaying();
        }
        
        if (isPlaying) {
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

    private void updateTimeDisplayFromService(long currentPosition, long duration) {
        binding.tvCurrentTime.setText(formatTime(currentPosition));
        if (duration > 0) {
            binding.timeBar.setDuration(duration);
            binding.tvTotalTime.setText(formatTime(duration));
        }
        binding.timeBar.setPosition(currentPosition);
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
        if (isAudioOnlyMode) return; // Don't adjust brightness in audio-only mode
        
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
        if (isAudioOnlyMode) return; // Don't show brightness control in audio-only mode
        
        binding.brightnessControlContainer.setVisibility(View.VISIBLE);
        new Handler().postDelayed(() -> 
            binding.brightnessControlContainer.setVisibility(View.GONE), 3000);
    }

    // Advanced features
    private void cyclePlaybackSpeed() {
        currentSpeedIndex = (currentSpeedIndex + 1) % playbackSpeeds.length;
        currentPlaybackSpeed = playbackSpeeds[currentSpeedIndex];
        
        if (useBackgroundService) {
            Toast.makeText(this, "Speed control not available in audio-only mode", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (player != null) {
            player.setPlaybackSpeed(currentPlaybackSpeed);
            binding.tvPlaybackSpeed.setText(String.format(Locale.getDefault(), "%.1fx", currentPlaybackSpeed));
            Toast.makeText(this, "Speed: " + currentPlaybackSpeed + "X", Toast.LENGTH_SHORT).show();
        }
    }

    private void cycleAspectRatio() {
        if (isAudioOnlyMode || binding.playerView == null) {
            Toast.makeText(this, "Aspect ratio not available in audio-only mode", Toast.LENGTH_SHORT).show();
            return;
        }
        
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

    @RequiresApi(api = Build.VERSION_CODES.O)
    public void enterPipMode() {
        if (isAudioOnlyMode) {
            Toast.makeText(this, "Picture-in-Picture not available in audio-only mode", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                Rational aspectRatio;
                
                if (player != null) {
                    VideoSize videoSize = player.getVideoSize();
                    aspectRatio = new Rational(
                        videoSize.width > 0 ? videoSize.width : 16,
                        videoSize.height > 0 ? videoSize.height : 9
                    );
                } else {
                    aspectRatio = new Rational(16, 9);
                }
                
                PictureInPictureParams params = new PictureInPictureParams.Builder()
                    .setAspectRatio(aspectRatio)
                    .build();
                
                isInPipMode = true;
                
                // Ensure playback continues in PiP
                if (player != null && !player.isPlaying()) {
                    player.play();
                }
                
                enterPictureInPictureMode(params);
            } catch (Exception e) {
                Toast.makeText(this, "PiP not supported: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void takeScreenshot() {
        if (isAudioOnlyMode) {
            Toast.makeText(this, "Screenshot not available in audio-only mode", Toast.LENGTH_SHORT).show();
            return;
        }
        
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
        } else if (id == R.id.btn_play_pause) {
            if (useBackgroundService && serviceHelper != null) {
                if (serviceHelper.isPlaying()) {
                    serviceHelper.pausePlayback();
                } else {
                    serviceHelper.resumePlayback();
                }
            } else if (player != null) {
                if (player.isPlaying()) player.pause();
                else player.play();
            }
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
        } else if (id == R.id.btn_center_audio_track) {
            showAudioTrackDialog();
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
            if (binding.qualitySelectorDialog != null) {
                binding.qualitySelectorDialog.setVisibility(View.GONE);
            }
            binding.brightnessControlContainer.setVisibility(View.GONE);
            binding.volumeControlContainer.setVisibility(View.GONE);
            
            // Make sure video is playing in PiP mode
            if (player != null && !player.isPlaying()) {
                player.play();
            }
            updatePlayPauseButton();
        } else {
            // Show controls when exiting PiP mode
            showPlayerControls();
        }
    }

    // Lifecycle methods
    @Override
    protected void onResume() {
        super.onResume();
        
        if (useBackgroundService && serviceHelper != null) {
            serviceHelper.bindService();
        } else if (player != null && player.isPlaying()) {
            timeHandler.post(timeUpdateRunnable);
        }
        
        showPlayerControls();
        applyOrientationSettings();
        hideSystemUI();
    }

    @Override
    protected void onPause() {
        super.onPause();
        timeHandler.removeCallbacks(timeUpdateRunnable);
        
        // Only pause if not in audio-only mode and not in PiP
        if (!useBackgroundService && player != null && !isInPipMode) {
            player.setPlayWhenReady(false);
            updateGlobalPlayingState(false, videoPath, videoTitle);
        }
        // Background service continues playing in audio-only mode
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // Unregister service receiver
        try {
            unregisterReceiver(serviceReceiver);
        } catch (Exception e) {
            Log.e(TAG, "Error unregistering service receiver", e);
        }
        
        // Unbind from service
        if (serviceHelper != null) {
            serviceHelper.unbindService();
        }
        
        // Clean up ExoPlayer
        if (player != null) {
            updateGlobalPlayingState(false, null, null);
            player.release();
        }
        
        controlsHandler.removeCallbacks(hideControlsRunnable);
        timeHandler.removeCallbacks(timeUpdateRunnable);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    // Helper methods
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
        
        final int[] countdown = {3}; // 3 second countdown
        
        Handler countdownHandler = new Handler();
        Runnable countdownRunnable = new Runnable() {
            @Override
            public void run() {
                if (countdown[0] > 0) {
                    Toast.makeText(PlayerActivity.this, 
                        "Next video in " + countdown[0] + " seconds", 
                        Toast.LENGTH_SHORT).show();
                    countdown[0]--;
                    countdownHandler.postDelayed(this, 1000);
                } else {
                    playNextVideo();
                }
            }
        };
        
        countdownHandler.post(countdownRunnable);
    }

    private void handlePlayerError(String message) {
        Log.e(TAG, message);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        updateGlobalPlayingState(false, null, null);
        
        // Show error overlay if available
        if (binding.errorOverlay != null) {
            binding.errorOverlay.setVisibility(View.VISIBLE);
        }
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
        boolean hasVisibleOverlay = false;
        
        if (binding.qualitySelectorDialog != null && binding.qualitySelectorDialog.getVisibility() == View.VISIBLE) {
            binding.qualitySelectorDialog.setVisibility(View.GONE);
            hasVisibleOverlay = true;
        }
        
        if (binding.errorOverlay != null && binding.errorOverlay.getVisibility() == View.VISIBLE) {
            binding.errorOverlay.setVisibility(View.GONE);
            hasVisibleOverlay = true;
        }
        
        if (binding.brightnessControlContainer.getVisibility() == View.VISIBLE) {
            binding.brightnessControlContainer.setVisibility(View.GONE);
            hasVisibleOverlay = true;
        }
        
        if (binding.volumeControlContainer.getVisibility() == View.VISIBLE) {
            binding.volumeControlContainer.setVisibility(View.GONE);
            hasVisibleOverlay = true;
        }
        
        if (hasVisibleOverlay) {
            return;
        }
        
        // Only pause if not in audio-only mode
        if (!useBackgroundService && player != null) {
            player.pause();
            updateGlobalPlayingState(false, null, null);
        }
        
        super.onBackPressed();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Handle hardware volume keys
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            int newVolume = keyCode == KeyEvent.KEYCODE_VOLUME_UP ? 
                Math.min(maxVolume, currentVolume + 1) : 
                Math.max(0, currentVolume - 1);
            setVolume(newVolume);
            showVolumeControl();
            return true;
        }
        
        return super.onKeyDown(keyCode, event);
    }
}