package com.nidoham.streamlyvid.loader;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;
import com.nidoham.streamlyvid.model.VideoModel;
import com.nidoham.streamlyvid.model.FolderModel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VideoLoader {
    private static final String TAG = "VideoLoader";
    private final Context context;
    private final ExecutorService executor;

    // MediaStore projection for video queries
    private static final String[] VIDEO_PROJECTION = {
        MediaStore.Video.Media._ID,
        MediaStore.Video.Media.TITLE,
        MediaStore.Video.Media.DISPLAY_NAME,
        MediaStore.Video.Media.DATA,
        MediaStore.Video.Media.SIZE,
        MediaStore.Video.Media.DURATION,
        MediaStore.Video.Media.MIME_TYPE,
        MediaStore.Video.Media.DATE_ADDED,
        MediaStore.Video.Media.DATE_MODIFIED,
        MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
        MediaStore.Video.Media.BUCKET_ID,
        MediaStore.Video.Media.WIDTH,
        MediaStore.Video.Media.HEIGHT
    };

    public interface VideoLoadCallback {
        void onVideosLoaded(List<VideoModel> videos);
        void onError(Exception error);
    }

    public interface FolderLoadCallback {
        void onFoldersLoaded(List<FolderModel> folders);
        void onError(Exception error);
    }

    public VideoLoader(Context context) {
        this.context = context.getApplicationContext();
        this.executor = Executors.newFixedThreadPool(2);
    }

    public void loadAllVideos(VideoLoadCallback callback) {
        executor.execute(() -> {
            try {
                List<VideoModel> videos = queryAllVideos();
                callback.onVideosLoaded(videos);
            } catch (Exception e) {
                Log.e(TAG, "Error loading videos", e);
                callback.onError(e);
            }
        });
    }

    public void loadVideosByFolder(String bucketId, VideoLoadCallback callback) {
        executor.execute(() -> {
            try {
                List<VideoModel> videos = queryVideosByFolder(bucketId);
                callback.onVideosLoaded(videos);
            } catch (Exception e) {
                Log.e(TAG, "Error loading videos by folder", e);
                callback.onError(e);
            }
        });
    }

    public void loadFolders(FolderLoadCallback callback) {
        executor.execute(() -> {
            try {
                List<FolderModel> folders = queryFolders();
                callback.onFoldersLoaded(folders);
            } catch (Exception e) {
                Log.e(TAG, "Error loading folders", e);
                callback.onError(e);
            }
        });
    }

    private List<VideoModel> queryAllVideos() {
        List<VideoModel> videos = new ArrayList<>();
        ContentResolver contentResolver = context.getContentResolver();

        String selection = MediaStore.Video.Media.DURATION + " > ? AND " +
                          MediaStore.Video.Media.SIZE + " > ?";
        String[] selectionArgs = {"1000", "1024"}; // > 1 second and > 1KB

        String sortOrder = MediaStore.Video.Media.DATE_ADDED + " DESC";

        try (Cursor cursor = contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                VIDEO_PROJECTION,
                selection,
                selectionArgs,
                sortOrder)) {

            if (cursor != null && cursor.moveToFirst()) {
                do {
                    VideoModel video = createVideoFromCursor(cursor);
                    if (video != null) {
                        videos.add(video);
                    }
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error querying videos", e);
        }

        Log.d(TAG, "Loaded " + videos.size() + " videos");
        return videos;
    }

    private List<VideoModel> queryVideosByFolder(String bucketId) {
        List<VideoModel> videos = new ArrayList<>();
        ContentResolver contentResolver = context.getContentResolver();

        String selection = MediaStore.Video.Media.BUCKET_ID + " = ? AND " +
                          MediaStore.Video.Media.DURATION + " > ? AND " +
                          MediaStore.Video.Media.SIZE + " > ?";
        String[] selectionArgs = {bucketId, "1000", "1024"};

        String sortOrder = MediaStore.Video.Media.TITLE + " ASC";

        try (Cursor cursor = contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                VIDEO_PROJECTION,
                selection,
                selectionArgs,
                sortOrder)) {

            if (cursor != null && cursor.moveToFirst()) {
                do {
                    VideoModel video = createVideoFromCursor(cursor);
                    if (video != null) {
                        videos.add(video);
                    }
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error querying videos by folder", e);
        }

        return videos;
    }

    private List<FolderModel> queryFolders() {
        Map<String, FolderModel> folderMap = new HashMap<>();
        ContentResolver contentResolver = context.getContentResolver();

        String selection = MediaStore.Video.Media.DURATION + " > ? AND " +
                          MediaStore.Video.Media.SIZE + " > ?";
        String[] selectionArgs = {"1000", "1024"};

        String sortOrder = MediaStore.Video.Media.BUCKET_DISPLAY_NAME + " ASC";

        try (Cursor cursor = contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                VIDEO_PROJECTION,
                selection,
                selectionArgs,
                sortOrder)) {

            if (cursor != null && cursor.moveToFirst()) {
                do {
                    VideoModel video = createVideoFromCursor(cursor);
                    if (video != null) {
                        String bucketId = video.getBucketId();
                        String bucketName = video.getBucketDisplayName();

                        if (bucketId != null && bucketName != null) {
                            FolderModel folder = folderMap.get(bucketId);
                            if (folder == null) {
                                folder = new FolderModel(bucketId, bucketName);
                                folderMap.put(bucketId, folder);
                            }
                            folder.addVideo(video);
                        }
                    }
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error querying folders", e);
        }

        return new ArrayList<>(folderMap.values());
    }

    private VideoModel createVideoFromCursor(Cursor cursor) {
        try {
            long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID));
            String title = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE));
            String displayName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME));
            String data = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA));
            long size = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE));
            long duration = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION));
            String mimeType = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE));
            long dateAdded = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED));
            long dateModified = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED));
            String bucketDisplayName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME));
            String bucketId = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_ID));
            int width = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH));
            int height = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT));

            Uri uri = Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, String.valueOf(id));

            return new VideoModel(id, title, displayName, data, uri, size, duration,
                    mimeType, dateAdded, dateModified, bucketDisplayName, bucketId, width, height);

        } catch (Exception e) {
            Log.e(TAG, "Error creating video from cursor", e);
            return null;
        }
    }

    public void shutdown() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }
}