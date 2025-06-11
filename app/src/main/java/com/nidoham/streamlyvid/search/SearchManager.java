package com.nidoham.streamlyvid.search;

import android.content.Context;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.nidoham.streamlyvid.model.VideoModel;
import com.nidoham.streamlyvid.model.FolderModel;
import com.nidoham.streamlyvid.utils.FilterUtils;
import com.nidoham.streamlyvid.utils.SortUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SearchManager {
    private static final String TAG = "SearchManager";
    private final Context context;
    private final ExecutorService searchExecutor;
    private final SearchHistoryManager historyManager;
    
    private final MutableLiveData<List<VideoModel>> searchResults = new MutableLiveData<>();
    private final MutableLiveData<List<FolderModel>> folderSearchResults = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isSearching = new MutableLiveData<>(false);
    private final MutableLiveData<String> currentQuery = new MutableLiveData<>("");
    private final MutableLiveData<SearchFilter> currentFilter = new MutableLiveData<>();
    
    private List<VideoModel> allVideos = new ArrayList<>();
    private List<FolderModel> allFolders = new ArrayList<>();

    public SearchManager(Context context) {
        this.context = context.getApplicationContext();
        this.searchExecutor = Executors.newSingleThreadExecutor();
        this.historyManager = new SearchHistoryManager(context);
    }

    // LiveData getters
    public LiveData<List<VideoModel>> getSearchResults() { return searchResults; }
    public LiveData<List<FolderModel>> getFolderSearchResults() { return folderSearchResults; }
    public LiveData<Boolean> getIsSearching() { return isSearching; }
    public LiveData<String> getCurrentQuery() { return currentQuery; }
    public LiveData<SearchFilter> getCurrentFilter() { return currentFilter; }

    // Set data sources
    public void setAllVideos(List<VideoModel> videos) {
        this.allVideos = videos != null ? new ArrayList<>(videos) : new ArrayList<>();
    }

    public void setAllFolders(List<FolderModel> folders) {
        this.allFolders = folders != null ? new ArrayList<>(folders) : new ArrayList<>();
    }

    // Search methods
    public void searchVideos(String query) {
        searchVideos(query, currentFilter.getValue());
    }

    public void searchVideos(String query, SearchFilter filter) {
        if (query == null) query = "";
        
        final String finalQuery = query; // Make it effectively final
        final SearchFilter finalFilter = filter; // Make it effectively final
        
        currentQuery.setValue(finalQuery);
        currentFilter.setValue(finalFilter);
        
        if (finalQuery.trim().isEmpty() && (finalFilter == null || !finalFilter.hasActiveFilters())) {
            searchResults.setValue(new ArrayList<>());
            folderSearchResults.setValue(new ArrayList<>());
            return;
        }

        isSearching.setValue(true);
        
        searchExecutor.execute(() -> {
            try {
                final String trimmedQuery = finalQuery.trim(); // Create final variable for lambda
                List<VideoModel> results = performVideoSearch(trimmedQuery, finalFilter);
                List<FolderModel> folderResults = performFolderSearch(trimmedQuery);
            
                searchResults.postValue(results);
                folderSearchResults.postValue(folderResults);
            
                // Add to search history if query is not empty
                if (!trimmedQuery.isEmpty()) {
                    historyManager.addSearchQuery(trimmedQuery);
                }
            
            } catch (Exception e) {
                searchResults.postValue(new ArrayList<>());
                folderSearchResults.postValue(new ArrayList<>());
            } finally {
                isSearching.postValue(false);
            }
        });
    }

    private List<VideoModel> performVideoSearch(String query, SearchFilter filter) {
        List<VideoModel> results = new ArrayList<>();
        
        for (VideoModel video : allVideos) {
            if (matchesSearchCriteria(video, query, filter)) {
                results.add(video);
            }
        }
        
        // Apply sorting if specified in filter
        if (filter != null && filter.getSortType() != null) {
            SortUtils.sortVideos(results, filter.getSortType());
        } else {
            // Default sort by relevance (title matches first, then other matches)
            results.sort((v1, v2) -> {
                boolean v1TitleMatch = matchesTitle(v1, query);
                boolean v2TitleMatch = matchesTitle(v2, query);
                
                if (v1TitleMatch && !v2TitleMatch) return -1;
                if (!v1TitleMatch && v2TitleMatch) return 1;
                
                // If both or neither match title, sort by title alphabetically
                String title1 = v1.getTitle() != null ? v1.getTitle() : v1.getDisplayName();
                String title2 = v2.getTitle() != null ? v2.getTitle() : v2.getDisplayName();
                return title1.compareToIgnoreCase(title2);
            });
        }
        
        return results;
    }

    private List<FolderModel> performFolderSearch(String query) {
        List<FolderModel> results = new ArrayList<>();
        
        if (query.isEmpty()) return results;
        
        for (FolderModel folder : allFolders) {
            if (folder.getBucketDisplayName() != null && 
                folder.getBucketDisplayName().toLowerCase().contains(query.toLowerCase())) {
                results.add(folder);
            }
        }
        
        // Sort folders by name
        results.sort((f1, f2) -> f1.getBucketDisplayName().compareToIgnoreCase(f2.getBucketDisplayName()));
        
        return results;
    }

    private boolean matchesSearchCriteria(VideoModel video, String query, SearchFilter filter) {
        // First check if video matches the query
        if (!query.isEmpty() && !matchesQuery(video, query)) {
            return false;
        }
        
        // Then apply additional filters
        if (filter != null) {
            return filter.matches(video);
        }
        
        return true;
    }

    private boolean matchesQuery(VideoModel video, String query) {
        String lowerQuery = query.toLowerCase();
        
        // Check title
        if (matchesTitle(video, query)) return true;
        
        // Check display name
        if (video.getDisplayName() != null && 
            video.getDisplayName().toLowerCase().contains(lowerQuery)) {
            return true;
        }
        
        // Check folder name
        if (video.getBucketDisplayName() != null && 
            video.getBucketDisplayName().toLowerCase().contains(lowerQuery)) {
            return true;
        }
        
        // Check file extension
        if (video.getFileExtension().toLowerCase().contains(lowerQuery)) {
            return true;
        }
        
        // Check resolution
        if (video.getResolution() != null && 
            video.getResolution().toLowerCase().contains(lowerQuery)) {
            return true;
        }
        
        return false;
    }

    private boolean matchesTitle(VideoModel video, String query) {
        if (video.getTitle() != null) {
            return video.getTitle().toLowerCase().contains(query.toLowerCase());
        }
        return false;
    }

    // Filter methods
    public void applyFilter(SearchFilter filter) {
        searchVideos(currentQuery.getValue(), filter);
    }

    public void clearFilter() {
        searchVideos(currentQuery.getValue(), null);
    }

    // Search suggestions
    public List<String> getSearchSuggestions(String query) {
        return historyManager.getSearchSuggestions(query, 10);
    }

    public LiveData<List<String>> getSearchHistory() {
        return historyManager.getSearchHistory();
    }

    public void clearSearchHistory() {
        historyManager.clearSearchHistory();
    }

    // Quick search methods
    public void searchByFormat(String format) {
        SearchFilter filter = new SearchFilter();
        filter.addAllowedFormat(format);
        searchVideos("", filter);
    }

    public void searchByFolder(String folderName) {
        SearchFilter filter = new SearchFilter();
        filter.addIncludedFolder(folderName);
        searchVideos("", filter);
    }

    public void searchByResolution(int minWidth, int minHeight) {
        SearchFilter filter = new SearchFilter();
        filter.setMinResolution(minWidth, minHeight);
        searchVideos("", filter);
    }

    public void searchLargeFiles(long minSizeMB) {
        SearchFilter filter = new SearchFilter();
        filter.setMinSize(minSizeMB);
        searchVideos("", filter);
    }

    public void searchRecentVideos(int days) {
        SearchFilter filter = new SearchFilter();
        long cutoffTime = System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000L);
        filter.setMinDateAdded(cutoffTime);
        searchVideos("", filter);
    }

    // Clear search
    public void clearSearch() {
        currentQuery.setValue("");
        currentFilter.setValue(null);
        searchResults.setValue(new ArrayList<>());
        folderSearchResults.setValue(new ArrayList<>());
    }

    public void shutdown() {
        if (searchExecutor != null && !searchExecutor.isShutdown()) {
            searchExecutor.shutdown();
        }
    }
}
