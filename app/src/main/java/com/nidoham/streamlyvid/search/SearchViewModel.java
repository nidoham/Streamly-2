package com.nidoham.streamlyvid.search;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.Observer;
import com.nidoham.streamlyvid.model.VideoModel;
import com.nidoham.streamlyvid.model.FolderModel;
import com.nidoham.streamlyvid.repository.VideoRepository;
import java.util.List;

public class SearchViewModel extends AndroidViewModel {
    private final SearchManager searchManager;
    private final VideoRepository videoRepository;
    private final MediatorLiveData<List<VideoModel>> allVideosMediator = new MediatorLiveData<>();
    private final MediatorLiveData<List<FolderModel>> allFoldersMediator = new MediatorLiveData<>();

    public SearchViewModel(@NonNull Application application) {
        super(application);
        this.searchManager = new SearchManager(application);
        this.videoRepository = new VideoRepository(application);
        
        setupMediators();
    }

    private void setupMediators() {
        // Update search manager when video data changes
        allVideosMediator.addSource(videoRepository.getAllVideos(), videos -> {
            if (videos != null) {
                searchManager.setAllVideos(videos);
                allVideosMediator.setValue(videos);
            }
        });
        
        allFoldersMediator.addSource(videoRepository.getAllFolders(), folders -> {
            if (folders != null) {
                searchManager.setAllFolders(folders);
                allFoldersMediator.setValue(folders);
            }
        });
    }

    // Search methods
    public void search(String query) {
        searchManager.searchVideos(query);
    }

    public void searchWithFilter(String query, SearchFilter filter) {
        searchManager.searchVideos(query, filter);
    }

    public void applyFilter(SearchFilter filter) {
        searchManager.applyFilter(filter);
    }

    public void clearFilter() {
        searchManager.clearFilter();
    }

    public void clearSearch() {
        searchManager.clearSearch();
    }

    // Quick search methods
    public void searchByFormat(String format) {
        searchManager.searchByFormat(format);
    }

    public void searchByFolder(String folderName) {
        searchManager.searchByFolder(folderName);
    }

    public void searchByResolution(int minWidth, int minHeight) {
        searchManager.searchByResolution(minWidth, minHeight);
    }

    public void searchLargeFiles(long minSizeMB) {
        searchManager.searchLargeFiles(minSizeMB);
    }

    public void searchRecentVideos(int days) {
        searchManager.searchRecentVideos(days);
    }

    // LiveData getters
    public LiveData<List<VideoModel>> getSearchResults() {
        return searchManager.getSearchResults();
    }

    public LiveData<List<FolderModel>> getFolderSearchResults() {
        return searchManager.getFolderSearchResults();
    }

    public LiveData<Boolean> getIsSearching() {
        return searchManager.getIsSearching();
    }

    public LiveData<String> getCurrentQuery() {
        return searchManager.getCurrentQuery();
    }

    public LiveData<SearchFilter> getCurrentFilter() {
        return searchManager.getCurrentFilter();
    }

    public LiveData<List<String>> getSearchHistory() {
        return searchManager.getSearchHistory();
    }

    // Search suggestions
    public List<String> getSearchSuggestions(String query) {
        return searchManager.getSearchSuggestions(query);
    }

    public void clearSearchHistory() {
        searchManager.clearSearchHistory();
    }

    // Data loading
    public void loadAllVideos() {
        videoRepository.loadAllVideos();
    }

    public void loadFolders() {
        videoRepository.loadFolders();
    }

    public void refreshData() {
        videoRepository.refreshData();
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        searchManager.shutdown();
    }
}
