package com.nidoham.streamlyvid.utils;

import com.nidoham.streamlyvid.model.VideoModel;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FilterUtils {
    
    public static class FilterCriteria {
        private long minSize = 0; // in bytes
        private long maxSize = Long.MAX_VALUE;
        private long minDuration = 0; // in milliseconds
        private long maxDuration = Long.MAX_VALUE;
        private Set<String> allowedFormats = new HashSet<>();
        private Set<String> excludedFormats = new HashSet<>();
        private int minWidth = 0;
        private int maxWidth = Integer.MAX_VALUE;
        private int minHeight = 0;
        private int maxHeight = Integer.MAX_VALUE;
        private Set<String> includedFolders = new HashSet<>();
        private Set<String> excludedFolders = new HashSet<>();
        private boolean filterBySize = false;
        private boolean filterByDuration = false;
        private boolean filterByFormat = false;
        private boolean filterByResolution = false;
        private boolean filterByFolder = false;

        // Size filters
        public FilterCriteria setMinSize(long minSizeMB) {
            this.minSize = minSizeMB * 1024 * 1024;
            this.filterBySize = true;
            return this;
        }

        public FilterCriteria setMaxSize(long maxSizeMB) {
            this.maxSize = maxSizeMB * 1024 * 1024;
            this.filterBySize = true;
            return this;
        }

        // Duration filters
        public FilterCriteria setMinDuration(long minDurationSeconds) {
            this.minDuration = minDurationSeconds * 1000;
            this.filterByDuration = true;
            return this;
        }

        public FilterCriteria setMaxDuration(long maxDurationSeconds) {
            this.maxDuration = maxDurationSeconds * 1000;
            this.filterByDuration = true;
            return this;
        }

        // Format filters
        public FilterCriteria addAllowedFormat(String format) {
            this.allowedFormats.add(format.toLowerCase());
            this.filterByFormat = true;
            return this;
        }

        public FilterCriteria addExcludedFormat(String format) {
            this.excludedFormats.add(format.toLowerCase());
            this.filterByFormat = true;
            return this;
        }

        // Resolution filters
        public FilterCriteria setMinResolution(int minWidth, int minHeight) {
            this.minWidth = minWidth;
            this.minHeight = minHeight;
            this.filterByResolution = true;
            return this;
        }

        public FilterCriteria setMaxResolution(int maxWidth, int maxHeight) {
            this.maxWidth = maxWidth;
            this.maxHeight = maxHeight;
            this.filterByResolution = true;
            return this;
        }

        // Folder filters
        public FilterCriteria addIncludedFolder(String folderName) {
            this.includedFolders.add(folderName);
            this.filterByFolder = true;
            return this;
        }

        public FilterCriteria addExcludedFolder(String folderName) {
            this.excludedFolders.add(folderName);
            this.filterByFolder = true;
            return this;
        }

        // Getters
        public long getMinSize() { return minSize; }
        public long getMaxSize() { return maxSize; }
        public long getMinDuration() { return minDuration; }
        public long getMaxDuration() { return maxDuration; }
        public Set<String> getAllowedFormats() { return allowedFormats; }
        public Set<String> getExcludedFormats() { return excludedFormats; }
        public int getMinWidth() { return minWidth; }
        public int getMaxWidth() { return maxWidth; }
        public int getMinHeight() { return minHeight; }
        public int getMaxHeight() { return maxHeight; }
        public Set<String> getIncludedFolders() { return includedFolders; }
        public Set<String> getExcludedFolders() { return excludedFolders; }
        public boolean isFilterBySize() { return filterBySize; }
        public boolean isFilterByDuration() { return filterByDuration; }
        public boolean isFilterByFormat() { return filterByFormat; }
        public boolean isFilterByResolution() { return filterByResolution; }
        public boolean isFilterByFolder() { return filterByFolder; }
    }

    public static List<VideoModel> filterVideos(List<VideoModel> videos, FilterCriteria criteria) {
        if (videos == null || videos.isEmpty() || criteria == null) {
            return videos;
        }

        List<VideoModel> filteredVideos = new ArrayList<>();

        for (VideoModel video : videos) {
            if (passesFilter(video, criteria)) {
                filteredVideos.add(video);
            }
        }

        return filteredVideos;
    }

    private static boolean passesFilter(VideoModel video, FilterCriteria criteria) {
        // Size filter
        if (criteria.isFilterBySize()) {
            if (video.getSize() < criteria.getMinSize() || video.getSize() > criteria.getMaxSize()) {
                return false;
            }
        }

        // Duration filter
        if (criteria.isFilterByDuration()) {
            if (video.getDuration() < criteria.getMinDuration() || video.getDuration() > criteria.getMaxDuration()) {
                return false;
            }
        }

        // Format filter
        if (criteria.isFilterByFormat()) {
            String extension = video.getFileExtension();
            
            // Check excluded formats first
            if (!criteria.getExcludedFormats().isEmpty() && criteria.getExcludedFormats().contains(extension)) {
                return false;
            }
            
            // Check allowed formats
            if (!criteria.getAllowedFormats().isEmpty() && !criteria.getAllowedFormats().contains(extension)) {
                return false;
            }
        }

        // Resolution filter
        if (criteria.isFilterByResolution()) {
            if (video.getWidth() < criteria.getMinWidth() || video.getWidth() > criteria.getMaxWidth() ||
                video.getHeight() < criteria.getMinHeight() || video.getHeight() > criteria.getMaxHeight()) {
                return false;
            }
        }

        // Folder filter
        if (criteria.isFilterByFolder()) {
            String folderName = video.getBucketDisplayName();
            
            // Check excluded folders first
            if (!criteria.getExcludedFolders().isEmpty() && criteria.getExcludedFolders().contains(folderName)) {
                return false;
            }
            
            // Check included folders
            if (!criteria.getIncludedFolders().isEmpty() && !criteria.getIncludedFolders().contains(folderName)) {
                return false;
            }
        }

        return true;
    }

    // Predefined filter presets
    public static FilterCriteria createLargeFilesFilter() {
        return new FilterCriteria().setMinSize(100); // 100MB+
    }

    public static FilterCriteria createShortVideosFilter() {
        return new FilterCriteria().setMaxDuration(300); // 5 minutes or less
    }

    public static FilterCriteria createHDFilter() {
        return new FilterCriteria().setMinResolution(1280, 720);
    }

    public static FilterCriteria createMP4OnlyFilter() {
        return new FilterCriteria().addAllowedFormat(".mp4");
    }

    public static FilterCriteria createNoSmallFilesFilter() {
        return new FilterCriteria().setMinSize(10); // 10MB+
    }
}