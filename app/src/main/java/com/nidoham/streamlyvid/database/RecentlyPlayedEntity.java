package com.nidoham.streamlyvid.database;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

@Entity(tableName = "recently_played")
public class RecentlyPlayedEntity {
    @PrimaryKey
    private long videoId;
    
    private String title;
    private String displayName;
    private String data; // File path
    private String uri;
    private long size;
    private long duration;
    private String mimeType;
    private String bucketDisplayName; // Folder name
    private int width;
    private int height;
    private long lastPlayedTime;
    private int playCount;

    public RecentlyPlayedEntity() {}

    // Getters and Setters
    public long getVideoId() { return videoId; }
    public void setVideoId(long videoId) { this.videoId = videoId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getData() { return data; }
    public void setData(String data) { this.data = data; }

    public String getUri() { return uri; }
    public void setUri(String uri) { this.uri = uri; }

    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }

    public long getDuration() { return duration; }
    public void setDuration(long duration) { this.duration = duration; }

    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }

    public String getBucketDisplayName() { return bucketDisplayName; }
    public void setBucketDisplayName(String bucketDisplayName) { this.bucketDisplayName = bucketDisplayName; }

    public int getWidth() { return width; }
    public void setWidth(int width) { this.width = width; }

    public int getHeight() { return height; }
    public void setHeight(int height) { this.height = height; }

    public long getLastPlayedTime() { return lastPlayedTime; }
    public void setLastPlayedTime(long lastPlayedTime) { this.lastPlayedTime = lastPlayedTime; }

    public int getPlayCount() { return playCount; }
    public void setPlayCount(int playCount) { this.playCount = playCount; }

    // Utility methods
    public String getFormattedSize() {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        if (size < 1024 * 1024 * 1024) return String.format("%.1f MB", size / (1024.0 * 1024));
        return String.format("%.1f GB", size / (1024.0 * 1024 * 1024));
    }

    public String getFormattedDuration() {
        long seconds = duration / 1000;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        seconds = seconds % 60;
        
        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%d:%02d", minutes, seconds);
        }
    }

    public String getResolution() {
        return width + "x" + height;
    }

    @NonNull
    @Override
    public String toString() {
        return "RecentlyPlayedEntity{" +
                "videoId=" + videoId +
                ", title='" + title + '\'' +
                ", displayName='" + displayName + '\'' +
                ", lastPlayedTime=" + lastPlayedTime +
                ", playCount=" + playCount +
                '}';
    }
}