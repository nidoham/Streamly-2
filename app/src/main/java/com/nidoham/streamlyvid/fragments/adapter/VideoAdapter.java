package com.nidoham.streamlyvid.adapter;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.nidoham.streamlyvid.R;
import com.nidoham.streamlyvid.databinding.ItemVideoBinding;
import com.nidoham.streamlyvid.model.VideoModel;

import java.util.ArrayList;
import java.util.List;

public class VideoAdapter extends RecyclerView.Adapter<VideoAdapter.VideoViewHolder> {
    
    private static final String TAG = "VideoAdapter";
    
    private Context context;
    private List<VideoModel> videoList;
    private OnVideoClickListener listener;

    public interface OnVideoClickListener {
        void onVideoClick(VideoModel video, int position);
        void onVideoLongClick(VideoModel video, int position);
        void onMoreOptionsClick(VideoModel video, int position, View anchorView);
    }

    public VideoAdapter(Context context, List<VideoModel> videoList, OnVideoClickListener listener) {
        this.context = context;
        this.videoList = videoList != null ? new ArrayList<>(videoList) : new ArrayList<>();
        this.listener = listener;
        Log.d(TAG, "VideoAdapter created with " + this.videoList.size() + " videos");
    }

    @NonNull
    @Override
    public VideoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Log.d(TAG, "onCreateViewHolder called");
        // Use view binding instead of findViewById
        ItemVideoBinding binding = ItemVideoBinding.inflate(
            LayoutInflater.from(parent.getContext()), parent, false);
        return new VideoViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull VideoViewHolder holder, int position) {
        if (position < 0 || position >= videoList.size()) {
            Log.e(TAG, "Invalid position: " + position + ", list size: " + videoList.size());
            return;
        }
        
        VideoModel video = videoList.get(position);
        if (video == null) {
            Log.e(TAG, "Null video at position: " + position);
            return;
        }
        
        Log.d(TAG, "Binding video at position " + position + ": " + video.getDisplayName());
        holder.bind(video, position);
    }

    @Override
    public int getItemCount() {
        int count = videoList != null ? videoList.size() : 0;
        Log.d(TAG, "getItemCount: " + count);
        return count;
    }

    @Override
    public long getItemId(int position) {
        if (position >= 0 && position < videoList.size()) {
            return videoList.get(position).getId();
        }
        return RecyclerView.NO_ID;
    }

    public class VideoViewHolder extends RecyclerView.ViewHolder {
        
        private final ItemVideoBinding binding;

        public VideoViewHolder(@NonNull ItemVideoBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(VideoModel video, int position) {
            if (video == null) {
                Log.e(TAG, "Attempted to bind null video");
                return;
            }
            
            Log.d(TAG, "Binding video: " + video.getDisplayName() + " at position " + position);
            
            // Set video title - use title first, then displayName as fallback
            String title = video.getTitle();
            if (title == null || title.trim().isEmpty()) {
                title = video.getDisplayName();
            }
            if (title == null || title.trim().isEmpty()) {
                title = "Unknown Video";
            }
            binding.titleTextView.setText(title);
            
            // Set duration
            String duration = video.getFormattedDuration();
            binding.durationTextView.setText(duration != null ? duration : "00:00");
            
            // Set file size
            String size = video.getFormattedSize();
            binding.sizeTextView.setText(size != null ? size : "0 MB");
            
            // Set resolution
            String resolution = video.getResolution();
            if (resolution == null || resolution.trim().isEmpty()) {
                resolution = video.getWidth() + "x" + video.getHeight();
            }
            binding.resolutionTextView.setText(resolution);
            
            // Set file path (using getData() method from VideoModel)
            if (binding.filePathTextView != null) {
                String path = video.getData(); // Use getData() instead of getPath()
                if (path != null && !path.trim().isEmpty()) {
                    binding.filePathTextView.setText(path);
                    binding.filePathTextView.setVisibility(View.VISIBLE);
                } else {
                    binding.filePathTextView.setVisibility(View.GONE);
                }
            }
            
            // Load thumbnail
            loadThumbnail(video);
            
            // Set click listeners
            setupClickListeners(video, position);
        }

        private void loadThumbnail(VideoModel video) {
            // Clear previous image to prevent recycling issues
            Glide.with(context).clear(binding.thumbnailImageView);
            
            RequestOptions options = new RequestOptions()
                    .centerCrop()
                    .placeholder(R.drawable.ic_video_placeholder)
                    .error(R.drawable.ic_video_placeholder)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .override(200, 120) // Optimize thumbnail size
                    .dontAnimate(); // Disable animations for better performance

            // Load thumbnail from video URI
            if (video.getUri() != null) {
                Log.d(TAG, "Loading thumbnail from URI: " + video.getUri());
                Glide.with(context)
                        .load(video.getUri())
                        .apply(options)
                        .into(binding.thumbnailImageView);
            } else if (video.getData() != null) {
                // Fallback to file path if URI is not available
                Log.d(TAG, "Loading thumbnail from path: " + video.getData());
                Glide.with(context)
                        .load(video.getData())
                        .apply(options)
                        .into(binding.thumbnailImageView);
            } else {
                // Show placeholder if no source available
                Log.d(TAG, "No thumbnail source, using placeholder");
                binding.thumbnailImageView.setImageResource(R.drawable.ic_video_placeholder);
            }
        }

        private void setupClickListeners(VideoModel video, int position) {
            // Main item click (play video)
            binding.getRoot().setOnClickListener(v -> {
                if (listener != null) {
                    Log.d(TAG, "Video clicked: " + video.getDisplayName());
                    listener.onVideoClick(video, position);
                }
            });
            
            // Long click for selection/context menu
            binding.getRoot().setOnLongClickListener(v -> {
                if (listener != null) {
                    Log.d(TAG, "Video long-clicked: " + video.getDisplayName());
                    listener.onVideoLongClick(video, position);
                }
                return true;
            });
            
            // Play icon click (same as item click)
            if (binding.playIconImageView != null) {
                binding.playIconImageView.setOnClickListener(v -> {
                    if (listener != null) {
                        Log.d(TAG, "Play icon clicked: " + video.getDisplayName());
                        listener.onVideoClick(video, position);
                    }
                });
            }
            
            // More options button click
            if (binding.moreOptionsButton != null) {
                binding.moreOptionsButton.setOnClickListener(v -> {
                    if (listener != null) {
                        Log.d(TAG, "More options clicked: " + video.getDisplayName());
                        listener.onMoreOptionsClick(video, position, v);
                    }
                });
            }
        }
    }

    // Utility methods for better list management
    public void updateVideoList(List<VideoModel> newVideoList) {
        Log.d(TAG, "updateVideoList called with " + 
              (newVideoList != null ? newVideoList.size() : 0) + " videos");
        
        if (this.videoList == null) {
            this.videoList = new ArrayList<>();
        } else {
            this.videoList.clear();
        }
        
        if (newVideoList != null && !newVideoList.isEmpty()) {
            this.videoList.addAll(newVideoList);
            Log.d(TAG, "Added " + newVideoList.size() + " videos to adapter");
        }
        
        // Use notifyDataSetChanged to ensure the adapter refreshes completely
        notifyDataSetChanged();
        Log.d(TAG, "Adapter data set changed, new size: " + this.videoList.size());
    }

    public void addVideo(VideoModel video) {
        if (video != null) {
            if (videoList == null) {
                videoList = new ArrayList<>();
            }
            videoList.add(video);
            notifyItemInserted(videoList.size() - 1);
            Log.d(TAG, "Added video: " + video.getDisplayName());
        }
    }

    public void removeVideo(int position) {
        if (videoList != null && position >= 0 && position < videoList.size()) {
            VideoModel removed = videoList.remove(position);
            notifyItemRemoved(position);
            notifyItemRangeChanged(position, videoList.size());
            Log.d(TAG, "Removed video at position " + position + ": " + 
                  (removed != null ? removed.getDisplayName() : "null"));
        }
    }

    public void updateVideo(int position, VideoModel video) {
        if (videoList != null && position >= 0 && position < videoList.size() && video != null) {
            videoList.set(position, video);
            notifyItemChanged(position);
            Log.d(TAG, "Updated video at position " + position + ": " + video.getDisplayName());
        }
    }

    public VideoModel getVideoAt(int position) {
        if (videoList != null && position >= 0 && position < videoList.size()) {
            return videoList.get(position);
        }
        return null;
    }

    public void clearVideos() {
        if (videoList != null) {
            int size = videoList.size();
            videoList.clear();
            notifyItemRangeRemoved(0, size);
            Log.d(TAG, "Cleared " + size + " videos");
        }
    }

    // Filter methods for search functionality
    public void filterVideos(List<VideoModel> filteredList) {
        Log.d(TAG, "Filtering videos, filtered size: " + 
              (filteredList != null ? filteredList.size() : 0));
        updateVideoList(filteredList);
    }

    // Method to get current video list
    public List<VideoModel> getVideoList() {
        return videoList != null ? new ArrayList<>(videoList) : new ArrayList<>();
    }
}