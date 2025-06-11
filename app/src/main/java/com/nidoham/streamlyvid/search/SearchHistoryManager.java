package com.nidoham.streamlyvid.search;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class SearchHistoryManager {
    private static final String PREFS_NAME = "search_history";
    private static final String KEY_SEARCH_HISTORY = "search_queries";
    private static final int MAX_HISTORY_SIZE = 50;
    
    private final SharedPreferences prefs;
    private final Gson gson;
    private final MutableLiveData<List<String>> searchHistory = new MutableLiveData<>();
    
    public SearchHistoryManager(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.gson = new Gson();
        loadSearchHistory();
    }
    
    public void addSearchQuery(String query) {
        if (query == null || query.trim().isEmpty()) return;
        
        query = query.trim();
        List<String> history = getCurrentHistory();
        
        // Remove if already exists to avoid duplicates
        history.remove(query);
        
        // Add to beginning
        history.add(0, query);
        
        // Limit size
        if (history.size() > MAX_HISTORY_SIZE) {
            history = history.subList(0, MAX_HISTORY_SIZE);
        }
        
        saveSearchHistory(history);
        searchHistory.setValue(new ArrayList<>(history));
    }
    
    public List<String> getSearchSuggestions(String query, int maxSuggestions) {
        if (query == null || query.trim().isEmpty()) {
            return getCurrentHistory().stream()
                    .limit(maxSuggestions)
                    .collect(Collectors.toList());
        }
        
        String lowerQuery = query.toLowerCase();
        return getCurrentHistory().stream()
                .filter(item -> item.toLowerCase().contains(lowerQuery))
                .limit(maxSuggestions)
                .collect(Collectors.toList());
    }
    
    public LiveData<List<String>> getSearchHistory() {
        return searchHistory;
    }
    
    public void removeSearchQuery(String query) {
        List<String> history = getCurrentHistory();
        if (history.remove(query)) {
            saveSearchHistory(history);
            searchHistory.setValue(new ArrayList<>(history));
        }
    }
    
    public void clearSearchHistory() {
        prefs.edit().remove(KEY_SEARCH_HISTORY).apply();
        searchHistory.setValue(new ArrayList<>());
    }
    
    private List<String> getCurrentHistory() {
        List<String> current = searchHistory.getValue();
        return current != null ? new ArrayList<>(current) : new ArrayList<>();
    }
    
    private void loadSearchHistory() {
        String json = prefs.getString(KEY_SEARCH_HISTORY, null);
        if (json != null) {
            try {
                Type listType = new TypeToken<List<String>>(){}.getType();
                List<String> history = gson.fromJson(json, listType);
                searchHistory.setValue(history != null ? history : new ArrayList<>());
            } catch (Exception e) {
                searchHistory.setValue(new ArrayList<>());
            }
        } else {
            searchHistory.setValue(new ArrayList<>());
        }
    }
    
    private void saveSearchHistory(List<String> history) {
        String json = gson.toJson(history);
        prefs.edit().putString(KEY_SEARCH_HISTORY, json).apply();
    }
}
