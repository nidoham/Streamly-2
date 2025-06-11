package com.nidoham.streamlyvid.fragments.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.nidoham.streamlyvid.PlayerActivity;
import com.nidoham.streamlyvid.R;
import com.nidoham.streamlyvid.adapter.VideoAdapter;
import com.nidoham.streamlyvid.databinding.FragmentHomeVideoBinding;
import com.nidoham.streamlyvid.model.VideoModel;
import com.nidoham.streamlyvid.viewmodel.VideoViewModel;

import java.util.ArrayList;
import java.util.List;

public class HomeVideoFragment extends Fragment implements VideoAdapter.OnVideoClickListener {
    
    private static final String TAG = "HomeVideoFragment";
    private static final int PERMISSION_REQUEST_CODE = 100;
    
    // View Binding
    private FragmentHomeVideoBinding binding;
    
    private VideoAdapter videoAdapter;
    private VideoViewModel videoViewModel;
    private List<VideoModel> videoList = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        
        Log.d(TAG, "onCreateView: Creating fragment view");
        
        // Initialize View Binding
        binding = FragmentHomeVideoBinding.inflate(inflater, container, false);
        
        setupRecyclerView();
        setupViewModel();
        setupClickListeners();
        
        // Don't show toast here - videos haven't loaded yet
        Log.d(TAG, "onCreateView: Initial video list size: " + videoList.size());
        
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Log.d(TAG, "onViewCreated: View created, checking permissions");
        checkPermissionsAndLoadVideos();
    }

    private void setupRecyclerView() {
        Log.d(TAG, "setupRecyclerView: Setting up RecyclerView");
        
        // Use GridLayoutManager for a grid layout similar to MX Player
        GridLayoutManager gridLayoutManager = new GridLayoutManager(getContext(), 2);
        binding.recyclerViewVideos.setLayoutManager(gridLayoutManager);
        
        videoAdapter = new VideoAdapter(getContext(), videoList, this);
        videoAdapter.setHasStableIds(true); // Enable stable IDs for better performance
        binding.recyclerViewVideos.setAdapter(videoAdapter);
        
        // Add item decoration for spacing
        int spacing = getResources().getDimensionPixelSize(R.dimen.grid_spacing);
        binding.recyclerViewVideos.addItemDecoration(new GridSpacingItemDecoration(2, spacing, true));
        
        // Optimize RecyclerView performance
        binding.recyclerViewVideos.setHasFixedSize(true);
        binding.recyclerViewVideos.setItemViewCacheSize(20);
        binding.recyclerViewVideos.setDrawingCacheEnabled(true);
        binding.recyclerViewVideos.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_HIGH);
    }

    private void setupViewModel() {
        Log.d(TAG, "setupViewModel: Setting up ViewModel");
        
        videoViewModel = new ViewModelProvider(this).get(VideoViewModel.class);
        
        // Observe video list changes
        videoViewModel.getAllVideos().observe(getViewLifecycleOwner(), videos -> {
            Log.d(TAG, "Videos observed: " + (videos != null ? videos.size() : 0) + " videos");
            if (videos != null) {
                updateVideoList(videos);
            } else {
                Log.w(TAG, "Received null video list from ViewModel");
                updateVideoList(new ArrayList<>());
            }
        });
        
        // Observe loading state
        videoViewModel.getIsLoading().observe(getViewLifecycleOwner(), isLoading -> {
            Log.d(TAG, "Loading state changed: " + isLoading);
            if (isLoading != null) {
                if (isLoading) {
                    showLoading();
                } else {
                    hideLoading();
                }
            }
        });
        
        // Observe error messages
        videoViewModel.getErrorMessage().observe(getViewLifecycleOwner(), errorMessage -> {
            if (errorMessage != null && !errorMessage.isEmpty()) {
                Log.e(TAG, "Error from ViewModel: " + errorMessage);
                showError(errorMessage);
            }
        });
    }

    private void setupClickListeners() {
        // Refresh button click listener
        binding.buttonRefresh.setOnClickListener(v -> {
            Log.d(TAG, "Refresh button clicked");
            refreshVideos();
        });
    }

    private void checkPermissionsAndLoadVideos() {
        Log.d(TAG, "checkPermissionsAndLoadVideos: Checking permissions");
        
        // For Android 13 (API 33) and above, use READ_MEDIA_VIDEO
        // For older versions, use READ_EXTERNAL_STORAGE
        String permission;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permission = Manifest.permission.READ_MEDIA_VIDEO;
        } else {
            permission = Manifest.permission.READ_EXTERNAL_STORAGE;
        }
        
        if (ContextCompat.checkSelfPermission(requireContext(), permission) 
                != PackageManager.PERMISSION_GRANTED) {
            
            Log.d(TAG, "Permission not granted, requesting: " + permission);
            ActivityCompat.requestPermissions(requireActivity(),
                    new String[]{permission},
                    PERMISSION_REQUEST_CODE);
        } else {
            Log.d(TAG, "Permission already granted, loading videos");
            loadVideos();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, 
                                         @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        Log.d(TAG, "onRequestPermissionsResult: requestCode=" + requestCode);
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Permission granted, loading videos");
                loadVideos();
            } else {
                Log.w(TAG, "Permission denied");
                showPermissionDeniedMessage();
            }
        }
    }

    private void loadVideos() {
        Log.d(TAG, "loadVideos: Starting video loading");
        if (videoViewModel != null) {
            showLoading(); // Show loading immediately
            videoViewModel.loadAllVideos();
        } else {
            Log.e(TAG, "VideoViewModel is null!");
            showError("Failed to initialize video loader");
        }
    }

    private void updateVideoList(List<VideoModel> videos) {
        Log.d(TAG, "updateVideoList: Updating with " + (videos != null ? videos.size() : 0) + " videos");
        
        if (videos == null) {
            videos = new ArrayList<>();
        }
        
        videoList.clear();
        videoList.addAll(videos);
        
        // Show toast with actual loaded video count
        if (getContext() != null) {
            
        }
        
        if (videoAdapter != null) {
            videoAdapter.updateVideoList(videoList);
            Log.d(TAG, "Adapter updated with " + videoList.size() + " videos");
        } else {
            Log.e(TAG, "VideoAdapter is null!");
        }
        
        // Show/hide appropriate states
        if (videos.isEmpty()) {
            Log.d(TAG, "No videos found, showing empty state");
            showEmptyState();
        } else {
            Log.d(TAG, "Videos found, hiding empty state");
            hideEmptyState();
        }
    }

    private void showEmptyState() {
        Log.d(TAG, "showEmptyState: Displaying empty state");
        binding.recyclerViewVideos.setVisibility(View.GONE);
        binding.layoutEmptyState.setVisibility(View.VISIBLE);
        binding.layoutLoading.setVisibility(View.GONE);
        
        // Set appropriate empty state message
        binding.textViewEmptyTitle.setText(R.string.no_videos_title);
        binding.textViewEmptySubtitle.setText(R.string.no_videos_subtitle);
    }

    private void hideEmptyState() {
        Log.d(TAG, "hideEmptyState: Hiding empty state");
        binding.recyclerViewVideos.setVisibility(View.VISIBLE);
        binding.layoutEmptyState.setVisibility(View.GONE);
        binding.layoutLoading.setVisibility(View.GONE);
    }

    private void showLoading() {
        Log.d(TAG, "showLoading: Displaying loading state");
        binding.layoutLoading.setVisibility(View.VISIBLE);
        binding.recyclerViewVideos.setVisibility(View.GONE);
        binding.layoutEmptyState.setVisibility(View.GONE);
        
        binding.progressIndicator.show();
    }

    private void hideLoading() {
        Log.d(TAG, "hideLoading: Hiding loading state");
        binding.layoutLoading.setVisibility(View.GONE);
        binding.progressIndicator.hide();
    }

    private void showError(String errorMessage) {
        Log.e(TAG, "showError: " + errorMessage);
        if (getContext() != null) {
            Toast.makeText(getContext(), "Error: " + errorMessage, Toast.LENGTH_LONG).show();
        }
        hideLoading();
        showEmptyState(); // Show empty state on error
    }

    private void showPermissionDeniedMessage() {
        Log.w(TAG, "showPermissionDeniedMessage: Permission denied by user");
        binding.recyclerViewVideos.setVisibility(View.GONE);
        binding.layoutEmptyState.setVisibility(View.VISIBLE);
        binding.layoutLoading.setVisibility(View.GONE);
        
        binding.textViewEmptyTitle.setText("Permission Required");
        binding.textViewEmptySubtitle.setText("Storage permission is required to load videos from your device. Please grant permission and try again.");
        
        // Make refresh button visible for retry
        binding.buttonRefresh.setVisibility(View.VISIBLE);
        binding.buttonRefresh.setText("Grant Permission");
        binding.buttonRefresh.setOnClickListener(v -> checkPermissionsAndLoadVideos());
    }

    // VideoAdapter.OnVideoClickListener implementation
    @Override
    public void onVideoClick(VideoModel video, int position) {
        Log.d(TAG, "onVideoClick: " + video.getDisplayName());
        // Handle video click - open video player
        openVideoPlayer(video);
        
        // Add to recently played
        if (videoViewModel != null) {
           // videoViewModel.addToRecentlyPlayed(video);
        }
    }

    @Override
    public void onVideoLongClick(VideoModel video, int position) {
        Log.d(TAG, "onVideoLongClick: " + video.getDisplayName());
        // Handle long click - show context menu
        showVideoContextMenu(video, position);
    }

    @Override
    public void onMoreOptionsClick(VideoModel video, int position, View anchorView) {
        Log.d(TAG, "onMoreOptionsClick: " + video.getDisplayName());
        // Show popup menu for more options
        showMoreOptionsMenu(video, position, anchorView);
    }

    private void openVideoPlayer(VideoModel video) {
        // TODO: Implement video player opening
        if (getContext() != null) {

            Intent intent = new Intent();
            intent.setClass(getContext(), PlayerActivity.class);
            startActivity(intent);
        }
    }

    private void showVideoContextMenu(VideoModel video, int position) {
        // TODO: Implement context menu (delete, share, properties, etc.)
        if (getContext() != null) {
            Toast.makeText(getContext(), "Long clicked: " + video.getDisplayName(), Toast.LENGTH_SHORT).show();
        }
    }

    private void showMoreOptionsMenu(VideoModel video, int position, View anchorView) {
        if (getContext() == null) return;
        
        PopupMenu popupMenu = new PopupMenu(getContext(), anchorView);
        popupMenu.getMenuInflater().inflate(R.menu.video_options_menu, popupMenu.getMenu());
        
        popupMenu.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.action_play) {
                openVideoPlayer(video);
                return true;
            } else if (itemId == R.id.action_share) {
                shareVideo(video);
                return true;
            } else if (itemId == R.id.action_delete) {
                deleteVideo(video, position);
                return true;
            } else if (itemId == R.id.action_properties) {
                showVideoProperties(video);
                return true;
            }
            return false;
        });
        
        popupMenu.show();
    }

    private void shareVideo(VideoModel video) {
        // TODO: Implement video sharing
        if (getContext() != null) {
            Toast.makeText(getContext(), "Share: " + video.getDisplayName(), Toast.LENGTH_SHORT).show();
        }
    }

    private void deleteVideo(VideoModel video, int position) {
        // TODO: Implement video deletion with confirmation dialog
        if (getContext() != null) {
            Toast.makeText(getContext(), "Delete: " + video.getDisplayName(), Toast.LENGTH_SHORT).show();
        }
    }

    private void showVideoProperties(VideoModel video) {
        // TODO: Implement video properties dialog
        if (getContext() != null) {
            Toast.makeText(getContext(), "Properties: " + video.getDisplayName(), Toast.LENGTH_SHORT).show();
        }
    }

    public void refreshVideos() {
        Log.d(TAG, "refreshVideos: Refreshing video list");
        if (videoViewModel != null) {
            videoViewModel.refreshData();
        } else {
            Log.e(TAG, "Cannot refresh - VideoViewModel is null");
            checkPermissionsAndLoadVideos(); // Fallback to permission check
        }
    }

    // Public method to get video count for external use
    public int getVideoCount() {
        return videoList != null ? videoList.size() : 0;
    }

    // Public method to check if videos are loaded
    public boolean hasVideos() {
        return videoList != null && !videoList.isEmpty();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        Log.d(TAG, "onDestroyView: Cleaning up");
        
        // Clean up View Binding reference to prevent memory leaks
        binding = null;
        
        // Clean up adapter references
        if (videoAdapter != null) {
            videoAdapter.clearVideos();
        }
    }

    // Inner class for grid spacing
    private static class GridSpacingItemDecoration extends RecyclerView.ItemDecoration {
        private int spanCount;
        private int spacing;
        private boolean includeEdge;

        public GridSpacingItemDecoration(int spanCount, int spacing, boolean includeEdge) {
            this.spanCount = spanCount;
            this.spacing = spacing;
            this.includeEdge = includeEdge;
        }

        @Override
        public void getItemOffsets(android.graphics.Rect outRect, View view, 
                                 RecyclerView parent, RecyclerView.State state) {
            int position = parent.getChildAdapterPosition(view);
            int column = position % spanCount;

            if (includeEdge) {
                outRect.left = spacing - column * spacing / spanCount;
                outRect.right = (column + 1) * spacing / spanCount;

                if (position < spanCount) {
                    outRect.top = spacing;
                }
                outRect.bottom = spacing;
            } else {
                outRect.left = column * spacing / spanCount;
                outRect.right = spacing - (column + 1) * spacing / spanCount;
                if (position >= spanCount) {
                    outRect.top = spacing;
                }
            }
        }
    }
}
