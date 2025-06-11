package com.nidoham.streamlyvid.viewmodel;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import com.nidoham.streamlyvid.database.RecentlyPlayedEntity;
import com.nidoham.streamlyvid.model.FolderModel;
import com.nidoham.streamlyvid.model.VideoModel;
import com.nidoham.streamlyvid.repository.VideoRepository;
import com.nidoham.streamlyvid.utils.FilterUtils;
import com.nidoham.streamlyvid.utils.SortUtils;
import java.util.List;

public class VideoViewModel extends AndroidViewModel {
    private final VideoRepository repository;
    private final MutableLiveData<SortUtils.SortType> currentSortType = new MutableLiveData<>(SortUtils.SortType.DATE_NEW_TO_OLD);
    private final MutableLiveData<FilterUtils.FilterCriteria> currentFilterCriteria = new MutableLiveData<>();
    private final MediatorLiveData<List<VideoModel>> filteredAndSortedVideos = new MediatorLiveData<>();

    public VideoViewModel(@NonNull Application application) {
        super(application);
        repository = new VideoRepository(application);
        setupFilteredAndSortedVideos();
    }

    private void setupFilteredAndSortedVideos() {
        filteredAndSortedVideos.addSource(repository.getAllVideos(), videos -> {
            updateFilteredAndSortedVideos();
        });
        
        filteredAndSortedVideos.addSource(currentSortType, sortType -> {
            updateFilteredAndSortedVideos();
        });
        
        filteredAndSortedVideos.addSource(currentFilterCriteria, filterCriteria -> {
            updateFilteredAndSortedVideos();
        });
    }

    private void updateFilteredAndSortedVideos() {
        List<VideoModel> videos = repository.getAllVideos().getValue();
        if (videos != null) {
            List<VideoModel> result = repository.getSortedAndFilteredVideos(
                videos, 
                currentSortType.getValue(), 
                currentFilterCriteria.getValue()
            );
            filteredAndSortedVideos.setValue(result);
        }
    }

    // Public methods for UI
    public LiveData<List<VideoModel>> getAllVideos() {
        return repository.getAllVideos();
    }

    public LiveData<List<VideoModel>> getFilteredAndSortedVideos() {
        return filteredAndSortedVideos;
    }

    public LiveData<List<FolderModel>> getAllFolders() {
        return repository.getAllFolders();
    }

    public LiveData<List<VideoModel>> getRecentlyAddedVideos() {
        return repository.getRecentlyAddedVideos();
    }

    public LiveData<List<RecentlyPlayedEntity>> getRecentlyPlayedVideos() {
        return repository.getRecentlyPlayedVideos(20);
    }

    public LiveData<Boolean> getIsLoading() {
        return repository.getIsLoading();
    }

    public LiveData<String> getErrorMessage() {
        return repository.getErrorMessage();
    }

    public LiveData<SortUtils.SortType> getCurrentSortType() {
        return currentSortType;
    }

    public LiveData<FilterUtils.FilterCriteria> getCurrentFilterCriteria() {
        return currentFilterCriteria;
    }

    // Actions
    public void loadAllVideos() {
        repository.loadAllVideos();
    }

    public void loadFolders() {
        repository.loadFolders();
    }

    public void refreshData() {
        repository.refreshData();
    }

    public void setSortType(SortUtils.SortType sortType) {
        currentSortType.setValue(sortType);
    }

    public void setFilterCriteria(FilterUtils.FilterCriteria filterCriteria) {
        currentFilterCriteria.setValue(filterCriteria);
    }

    public void clearFilters() {
        currentFilterCriteria.setValue(null);
    }

    public void addToRecentlyPlayed(VideoModel video) {
        repository.addToRecentlyPlayed(video);
    }

    public void clearRecentlyPlayed() {
        repository.clearRecentlyPlayed();
    }

    public void loadVideosByFolder(String bucketId, VideoRepository.VideoLoadCallback callback) {
        repository.loadVideosByFolder(bucketId, callback);
    }
}
