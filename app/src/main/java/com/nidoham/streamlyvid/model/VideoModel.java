package com.nidoham.streamlyvid.model;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.NonNull;
import java.util.Objects;

public class VideoModel implements Parcelable {
    private long id;
    private String title;
    private String displayName;
    private String data; // File path
    private Uri uri;
    private long size;
    private long duration;
    private String mimeType;
    private long dateAdded;
    private long dateModified;
    private String bucketDisplayName; // Folder name
    private String bucketId;
    private int width;
    private int height;
    private String resolution;
    private long lastPlayedTime;
    private int playCount;

    public VideoModel() {}

    public VideoModel(long id, String title, String displayName, String data, Uri uri,
                     long size, long duration, String mimeType, long dateAdded,
                     long dateModified, String bucketDisplayName, String bucketId,
                     int width, int height) {
        this.id = id;
        this.title = title;
        this.displayName = displayName;
        this.data = data;
        this.uri = uri;
        this.size = size;
        this.duration = duration;
        this.mimeType = mimeType;
        this.dateAdded = dateAdded;
        this.dateModified = dateModified;
        this.bucketDisplayName = bucketDisplayName;
        this.bucketId = bucketId;
        this.width = width;
        this.height = height;
        this.resolution = width + "x" + height;
    }

    // Parcelable implementation
    protected VideoModel(Parcel in) {
        id = in.readLong();
        title = in.readString();
        displayName = in.readString();
        data = in.readString();
        uri = in.readParcelable(Uri.class.getClassLoader());
        size = in.readLong();
        duration = in.readLong();
        mimeType = in.readString();
        dateAdded = in.readLong();
        dateModified = in.readLong();
        bucketDisplayName = in.readString();
        bucketId = in.readString();
        width = in.readInt();
        height = in.readInt();
        resolution = in.readString();
        lastPlayedTime = in.readLong();
        playCount = in.readInt();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(id);
        dest.writeString(title);
        dest.writeString(displayName);
        dest.writeString(data);
        dest.writeParcelable(uri, flags);
        dest.writeLong(size);
        dest.writeLong(duration);
        dest.writeString(mimeType);
        dest.writeLong(dateAdded);
        dest.writeLong(dateModified);
        dest.writeString(bucketDisplayName);
        dest.writeString(bucketId);
        dest.writeInt(width);
        dest.writeInt(height);
        dest.writeString(resolution);
        dest.writeLong(lastPlayedTime);
        dest.writeInt(playCount);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<VideoModel> CREATOR = new Creator<VideoModel>() {
        @Override
        public VideoModel createFromParcel(Parcel in) {
            return new VideoModel(in);
        }

        @Override
        public VideoModel[] newArray(int size) {
            return new VideoModel[size];
        }
    };

    // Getters and Setters
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getData() { return data; }
    public void setData(String data) { this.data = data; }

    public Uri getUri() { return uri; }
    public void setUri(Uri uri) { this.uri = uri; }

    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }

    public long getDuration() { return duration; }
    public void setDuration(long duration) { this.duration = duration; }

    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }

    public long getDateAdded() { return dateAdded; }
    public void setDateAdded(long dateAdded) { this.dateAdded = dateAdded; }

    public long getDateModified() { return dateModified; }
    public void setDateModified(long dateModified) { this.dateModified = dateModified; }

    public String getBucketDisplayName() { return bucketDisplayName; }
    public void setBucketDisplayName(String bucketDisplayName) { this.bucketDisplayName = bucketDisplayName; }

    public String getBucketId() { return bucketId; }
    public void setBucketId(String bucketId) { this.bucketId = bucketId; }

    public int getWidth() { return width; }
    public void setWidth(int width) { this.width = width; }

    public int getHeight() { return height; }
    public void setHeight(int height) { this.height = height; }

    public String getResolution() { return resolution; }
    public void setResolution(String resolution) { this.resolution = resolution; }

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

    public String getFileExtension() {
        if (displayName != null && displayName.contains(".")) {
            return displayName.substring(displayName.lastIndexOf(".")).toLowerCase();
        }
        return "";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VideoModel that = (VideoModel) o;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @NonNull
    @Override
    public String toString() {
        return "VideoModel{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", displayName='" + displayName + '\'' +
                ", size=" + size +
                ", duration=" + duration +
                ", bucketDisplayName='" + bucketDisplayName + '\'' +
                '}';
    }
}