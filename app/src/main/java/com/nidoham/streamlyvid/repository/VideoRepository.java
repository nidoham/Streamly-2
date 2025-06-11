package com.nidoham.streamlyvid.repository;

import android.content.Context;
import android.net.Uri;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.nidoham.streamlyvid.database.AppDatabase;
import com.nidoham.streamlyvid.database.RecentlyPlayedDao;
import com.nidoham.streamlyvid.database.RecentlyPlayedEntity;
import com.nidoham.streamlyvid.loader.VideoLoader;
import com.nidoham.streamlyvid.model.FolderModel;
import com.nidoham.streamlyvid.model.VideoModel;
import com.nidoham.streamlyvid.utils.FilterUtils;
import com.nidoham.streamlyvid.utils.SortUtils;
import java.util.ArrayList;
import java.util.List;

public class VideoRepository  {
    private final VideoLoader videoLoader;
    private final RecentlyPlayedDao recentlyPlayedDao;
    private final MutableLiveData<List<VideoModel>> allVideos = new MutableLiveData<>();
    private final MutableLiveData<List<FolderModel>> allFolders = new MutableLiveData<>();
    private final MutableLiveData<List<VideoModel>> recentlyAddedVideos = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();

    public VideoRepository(Context context) {
        this.videoLoader = new VideoLoader(context);
        AppDatabase database = AppDatabase.getDatabase(context);
        this.recentlyPlayedDao = database.recentlyPlayedDao();
    }

    // LiveData getters
    public LiveData<List<VideoModel>> getAllVideos() { return allVideos; }
    public LiveData<List<FolderModel>> getAllFolders() { return allFolders; }
    public LiveData<List<VideoModel>> getRecentlyAddedVideos() { return recentlyAddedVideos; }
    public LiveData<Boolean> getIsLoading() { return isLoading; }
    public LiveData<String> getErrorMessage() { return errorMessage; }

    public LiveData<List<RecentlyPlayedEntity>> getRecentlyPlayedVideos(int limit) {
        return recentlyPlayedDao.getRecentlyPlayed(limit);
    }

    public void loadAllVideos() {
        isLoading.setValue(true);
        videoLoader.loadAllVideos(new VideoLoader.VideoLoadCallback() {
            @Override
            public void onVideosLoaded(List<VideoModel> videos) {
                allVideos.postValue(videos);
                
                // Also update recently added (last 50 videos)
                List<VideoModel> recentVideos = new ArrayList<>();
                SortUtils.sortVideos(videos, SortUtils.SortType.DATE_NEW_TO_OLD);
                int count = Math.min(50, videos.size());
                for (int i = 0; i < count; i++) {
                    recentVideos.add(videos.get(i));
                }
                recentlyAddedVideos.postValue(recentVideos);
                
                isLoading.postValue(false);
            }

            @Override
            public void onError(Exception error) {
                errorMessage.postValue("Failed to load videos: " + error.getMessage());
                isLoading.postValue(false);
            }
        });
    }

    public void loadFolders() {
        isLoading.setValue(true);
        videoLoader.loadFolders(new VideoLoader.FolderLoadCallback() {
            @Override
            public void onFoldersLoaded(List<FolderModel> folders) {
                allFolders.postValue(folders);
                isLoading.postValue(false);
            }

            @Override
            public void onError(Exception error) {
                errorMessage.postValue("Failed to load folders: " + error.getMessage());
                isLoading.postValue(false);
            }
        });
    }

    public void loadVideosByFolder(String bucketId, VideoLoadCallback callback) {
        videoLoader.loadVideosByFolder(bucketId, new VideoLoader.VideoLoadCallback() {
            @Override
            public void onVideosLoaded(List<VideoModel> videos) {
                callback.onSuccess(videos);
            }

            @Override
            public void onError(Exception error) {
                callback.onError(error.getMessage());
            }
        });
    }

    public List<VideoModel> getSortedAndFilteredVideos(List<VideoModel> videos, 
                                                      SortUtils.SortType sortType,
                                                      FilterUtils.FilterCriteria filterCriteria) {
        if (videos == null) return new ArrayList<>();
        
        List<VideoModel> result = new ArrayList<>(videos);
        
        // Apply filters first
        if (filterCriteria != null) {
            result = FilterUtils.filterVideos(result, filterCriteria);
        }
        
        // Then apply sorting
        if (sortType != null) {
            SortUtils.sortVideos(result, sortType);
        }
        
        return result;
    }

    public void addToRecentlyPlayed(VideoModel video) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            RecentlyPlayedEntity entity = new RecentlyPlayedEntity();
            entity.setVideoId(video.getId());
            entity.setTitle(video.getTitle());
            entity.setDisplayName(video.getDisplayName());
            entity.setData(video.getData());
            entity.setUri(video.getUri().toString());
            entity.setSize(video.getSize());
            entity.setDuration(video.getDuration());
            entity.setMimeType(video.getMimeType());
            entity.setBucketDisplayName(video.getBucketDisplayName());
            entity.setWidth(video.getWidth());
            entity.setHeight(video.getHeight());
            entity.setLastPlayedTime(System.currentTimeMillis());
            
            // Check if video already exists in recently played
            RecentlyPlayedEntity existing = recentlyPlayedDao.getRecentlyPlayedVideo(video.getId());
            if (existing != null) {
                entity.setPlayCount(existing.getPlayCount() + 1);
            } else {
                entity.setPlayCount(1);
            }
            
            recentlyPlayedDao.insertRecentlyPlayed(entity);
            
            // Limit recently played to 100 items
            recentlyPlayedDao.limitRecentlyPlayed(100);
        });
    }

    public void clearRecentlyPlayed() {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            recentlyPlayedDao.clearAllRecentlyPlayed();
        });
    }

    public void refreshData() {
        loadAllVideos();
        loadFolders();
    }

    public interface VideoLoadCallback {
        void onSuccess(List<VideoModel> videos);
        void onError(String error);
    }
}