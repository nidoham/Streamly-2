package com.nidoham.streamlyvid.search;

import com.nidoham.streamlyvid.model.VideoModel;
import com.nidoham.streamlyvid.utils.SortUtils;
import java.util.HashSet;
import java.util.Set;

public class SearchFilter {
    private long minSize = 0; // in bytes
    private long maxSize = Long.MAX_VALUE;
    private long minDuration = 0; // in milliseconds
    private long maxDuration = Long.MAX_VALUE;
    private long minDateAdded = 0;
    private long maxDateAdded = Long.MAX_VALUE;
    private Set<String> allowedFormats = new HashSet<>();
    private Set<String> excludedFormats = new HashSet<>();
    private int minWidth = 0;
    private int maxWidth = Integer.MAX_VALUE;
    private int minHeight = 0;
    private int maxHeight = Integer.MAX_VALUE;
    private Set<String> includedFolders = new HashSet<>();
    private Set<String> excludedFolders = new HashSet<>();
    private SortUtils.SortType sortType;
    
    // Filter flags
    private boolean filterBySize = false;
    private boolean filterByDuration = false;
    private boolean filterByDate = false;
    private boolean filterByFormat = false;
    private boolean filterByResolution = false;
    private boolean filterByFolder = false;

    public SearchFilter() {}

    // Size filters
    public SearchFilter setMinSize(long minSizeMB) {
        this.minSize = minSizeMB * 1024 * 1024;
        this.filterBySize = true;
        return this;
    }

    public SearchFilter setMaxSize(long maxSizeMB) {
        this.maxSize = maxSizeMB * 1024 * 1024;
        this.filterBySize = true;
        return this;
    }

    public SearchFilter setSizeRange(long minSizeMB, long maxSizeMB) {
        setMinSize(minSizeMB);
        setMaxSize(maxSizeMB);
        return this;
    }

    // Duration filters
    public SearchFilter setMinDuration(long minDurationSeconds) {
        this.minDuration = minDurationSeconds * 1000;
        this.filterByDuration = true;
        return this;
    }

    public SearchFilter setMaxDuration(long maxDurationSeconds) {
        this.maxDuration = maxDurationSeconds * 1000;
        this.filterByDuration = true;
        return this;
    }

    public SearchFilter setDurationRange(long minDurationSeconds, long maxDurationSeconds) {
        setMinDuration(minDurationSeconds);
        setMaxDuration(maxDurationSeconds);
        return this;
    }

    // Date filters
    public SearchFilter setMinDateAdded(long minDateAdded) {
        this.minDateAdded = minDateAdded;
        this.filterByDate = true;
        return this;
    }

    public SearchFilter setMaxDateAdded(long maxDateAdded) {
        this.maxDateAdded = maxDateAdded;
        this.filterByDate = true;
        return this;
    }

    public SearchFilter setDateRange(long minDateAdded, long maxDateAdded) {
        setMinDateAdded(minDateAdded);
        setMaxDateAdded(maxDateAdded);
        return this;
    }

    // Format filters
    public SearchFilter addAllowedFormat(String format) {
        this.allowedFormats.add(format.toLowerCase().startsWith(".") ? format.toLowerCase() : "." + format.toLowerCase());
        this.filterByFormat = true;
        return this;
    }

    public SearchFilter addExcludedFormat(String format) {
        this.excludedFormats.add(format.toLowerCase().startsWith(".") ? format.toLowerCase() : "." + format.toLowerCase());
        this.filterByFormat = true;
        return this;
    }

    // Resolution filters
    public SearchFilter setMinResolution(int minWidth, int minHeight) {
        this.minWidth = minWidth;
        this.minHeight = minHeight;
        this.filterByResolution = true;
        return this;
    }

    public SearchFilter setMaxResolution(int maxWidth, int maxHeight) {
        this.maxWidth = maxWidth;
        this.maxHeight = maxHeight;
        this.filterByResolution = true;
        return this;
    }

    public SearchFilter setResolutionRange(int minWidth, int minHeight, int maxWidth, int maxHeight) {
        setMinResolution(minWidth, minHeight);
        setMaxResolution(maxWidth, maxHeight);
        return this;
    }

    // Folder filters
    public SearchFilter addIncludedFolder(String folderName) {
        this.includedFolders.add(folderName);
        this.filterByFolder = true;
        return this;
    }

    public SearchFilter addExcludedFolder(String folderName) {
        this.excludedFolders.add(folderName);
        this.filterByFolder = true;
        return this;
    }

    // Sorting
    public SearchFilter setSortType(SortUtils.SortType sortType) {
        this.sortType = sortType;
        return this;
    }

    // Check if video matches all filter criteria
    public boolean matches(VideoModel video) {
        // Size filter
        if (filterBySize) {
            if (video.getSize() < minSize || video.getSize() > maxSize) {
                return false;
            }
        }

        // Duration filter
        if (filterByDuration) {
            if (video.getDuration() < minDuration || video.getDuration() > maxDuration) {
                return false;
            }
        }

        // Date filter
        if (filterByDate) {
            long dateAdded = video.getDateAdded() * 1000; // Convert to milliseconds
            if (dateAdded < minDateAdded || dateAdded > maxDateAdded) {
                return false;
            }
        }

        // Format filter
        if (filterByFormat) {
            String extension = video.getFileExtension();
            
            // Check excluded formats first
            if (!excludedFormats.isEmpty() && excludedFormats.contains(extension)) {
                return false;
            }
            
            // Check allowed formats
            if (!allowedFormats.isEmpty() && !allowedFormats.contains(extension)) {
                return false;
            }
        }

        // Resolution filter
        if (filterByResolution) {
            if (video.getWidth() < minWidth || video.getWidth() > maxWidth ||
                video.getHeight() < minHeight || video.getHeight() > maxHeight) {
                return false;
            }
        }

        // Folder filter
        if (filterByFolder) {
            String folderName = video.getBucketDisplayName();
            
            // Check excluded folders first
            if (!excludedFolders.isEmpty() && excludedFolders.contains(folderName)) {
                return false;
            }
            
            // Check included folders
            if (!includedFolders.isEmpty() && !includedFolders.contains(folderName)) {
                return false;
            }
        }

        return true;
    }

    // Check if any filters are active
    public boolean hasActiveFilters() {
        return filterBySize || filterByDuration || filterByDate || 
               filterByFormat || filterByResolution || filterByFolder;
    }

    // Getters
    public long getMinSize() { return minSize; }
    public long getMaxSize() { return maxSize; }
    public long getMinDuration() { return minDuration; }
    public long getMaxDuration() { return maxDuration; }
    public long getMinDateAdded() { return minDateAdded; }
    public long getMaxDateAdded() { return maxDateAdded; }
    public Set<String> getAllowedFormats() { return allowedFormats; }
    public Set<String> getExcludedFormats() { return excludedFormats; }
    public int getMinWidth() { return minWidth; }
    public int getMaxWidth() { return maxWidth; }
    public int getMinHeight() { return minHeight; }
    public int getMaxHeight() { return maxHeight; }
    public Set<String> getIncludedFolders() { return includedFolders; }
    public Set<String> getExcludedFolders() { return excludedFolders; }
    public SortUtils.SortType getSortType() { return sortType; }
    
    public boolean isFilterBySize() { return filterBySize; }
    public boolean isFilterByDuration() { return filterByDuration; }
    public boolean isFilterByDate() { return filterByDate; }
    public boolean isFilterByFormat() { return filterByFormat; }
    public boolean isFilterByResolution() { return filterByResolution; }
    public boolean isFilterByFolder() { return filterByFolder; }

    // Predefined filters
    public static SearchFilter createHDFilter() {
        return new SearchFilter().setMinResolution(1280, 720);
    }

    public static SearchFilter create4KFilter() {
        return new SearchFilter().setMinResolution(3840, 2160);
    }

    public static SearchFilter createLargeFilesFilter() {
        return new SearchFilter().setMinSize(100); // 100MB+
    }

    public static SearchFilter createShortVideosFilter() {
        return new SearchFilter().setMaxDuration(300); // 5 minutes or less
    }

    public static SearchFilter createLongVideosFilter() {
        return new SearchFilter().setMinDuration(3600); // 1 hour or more
    }

    public static SearchFilter createMP4OnlyFilter() {
        return new SearchFilter().addAllowedFormat(".mp4");
    }

    public static SearchFilter createRecentFilter(int days) {
        long cutoffTime = System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000L);
        return new SearchFilter().setMinDateAdded(cutoffTime);
    }

    // Clear all filters
    public void clear() {
        minSize = 0;
        maxSize = Long.MAX_VALUE;
        minDuration = 0;
        maxDuration = Long.MAX_VALUE;
        minDateAdded = 0;
        maxDateAdded = Long.MAX_VALUE;
        allowedFormats.clear();
        excludedFormats.clear();
        minWidth = 0;
        maxWidth = Integer.MAX_VALUE;
        minHeight = 0;
        maxHeight = Integer.MAX_VALUE;
        includedFolders.clear();
        excludedFolders.clear();
        sortType = null;
        
        filterBySize = false;
        filterByDuration = false;
        filterByDate = false;
        filterByFormat = false;
        filterByResolution = false;
        filterByFolder = false;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("SearchFilter{");
        if (filterBySize) sb.append("size:").append(minSize/1024/1024).append("-").append(maxSize/1024/1024).append("MB, ");
        if (filterByDuration) sb.append("duration:").append(minDuration/1000).append("-").append(maxDuration/1000).append("s, ");
        if (filterByFormat) sb.append("formats:").append(allowedFormats).append(", ");
        if (filterByResolution) sb.append("resolution:").append(minWidth).append("x").append(minHeight).append("+, ");
        if (filterByFolder) sb.append("folders:").append(includedFolders).append(", ");
        sb.append("}");
        return sb.toString();
    }
}
