package com.nidoham.streamlyvid.model;

import android.net.Uri;
import java.util.ArrayList;
import java.util.List;

public class FolderModel {
    private String bucketId;
    private String bucketDisplayName;
    private int videoCount;
    private Uri thumbnailUri;
    private long totalSize;
    private long lastModified;
    private List<VideoModel> videos;

    public FolderModel() {
        this.videos = new ArrayList<>();
    }

    public FolderModel(String bucketId, String bucketDisplayName) {
        this.bucketId = bucketId;
        this.bucketDisplayName = bucketDisplayName;
        this.videos = new ArrayList<>();
    }

    // Getters and Setters
    public String getBucketId() { return bucketId; }
    public void setBucketId(String bucketId) { this.bucketId = bucketId; }

    public String getBucketDisplayName() { return bucketDisplayName; }
    public void setBucketDisplayName(String bucketDisplayName) { this.bucketDisplayName = bucketDisplayName; }

    public int getVideoCount() { return videoCount; }
    public void setVideoCount(int videoCount) { this.videoCount = videoCount; }

    public Uri getThumbnailUri() { return thumbnailUri; }
    public void setThumbnailUri(Uri thumbnailUri) { this.thumbnailUri = thumbnailUri; }

    public long getTotalSize() { return totalSize; }
    public void setTotalSize(long totalSize) { this.totalSize = totalSize; }

    public long getLastModified() { return lastModified; }
    public void setLastModified(long lastModified) { this.lastModified = lastModified; }

    public List<VideoModel> getVideos() { return videos; }
    public void setVideos(List<VideoModel> videos) { 
        this.videos = videos;
        updateStats();
    }

    public void addVideo(VideoModel video) {
        this.videos.add(video);
        updateStats();
    }

    private void updateStats() {
        this.videoCount = videos.size();
        this.totalSize = videos.stream().mapToLong(VideoModel::getSize).sum();
        this.lastModified = videos.stream().mapToLong(VideoModel::getDateModified).max().orElse(0);
        
        // Set thumbnail to first video's URI
        if (!videos.isEmpty() && thumbnailUri == null) {
            this.thumbnailUri = videos.get(0).getUri();
        }
    }

    public String getFormattedTotalSize() {
        if (totalSize < 1024) return totalSize + " B";
        if (totalSize < 1024 * 1024) return String.format("%.1f KB", totalSize / 1024.0);
        if (totalSize < 1024 * 1024 * 1024) return String.format("%.1f MB", totalSize / (1024.0 * 1024));
        return String.format("%.1f GB", totalSize / (1024.0 * 1024 * 1024));
    }
}