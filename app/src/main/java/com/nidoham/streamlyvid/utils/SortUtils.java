package com.nidoham.streamlyvid.utils;

import com.nidoham.streamlyvid.model.VideoModel;
import java.util.Comparator;
import java.util.List;
import java.util.Collections;

public class SortUtils {
    
    public enum SortType {
        TITLE_ASC,
        TITLE_DESC,
        DATE_NEW_TO_OLD,
        DATE_OLD_TO_NEW,
        SIZE_LARGE_TO_SMALL,
        SIZE_SMALL_TO_LARGE,
        RESOLUTION_HIGH_TO_LOW,
        RESOLUTION_LOW_TO_HIGH,
        DURATION_LONG_TO_SHORT,
        DURATION_SHORT_TO_LONG,
        RECENTLY_PLAYED
    }

    public static void sortVideos(List<VideoModel> videos, SortType sortType) {
        if (videos == null || videos.isEmpty()) return;

        Comparator<VideoModel> comparator = getComparator(sortType);
        if (comparator != null) {
            Collections.sort(videos, comparator);
        }
    }

    private static Comparator<VideoModel> getComparator(SortType sortType) {
        switch (sortType) {
            case TITLE_ASC:
                return (v1, v2) -> {
                    String title1 = v1.getTitle() != null ? v1.getTitle() : v1.getDisplayName();
                    String title2 = v2.getTitle() != null ? v2.getTitle() : v2.getDisplayName();
                    return title1.compareToIgnoreCase(title2);
                };
                
            case TITLE_DESC:
                return (v1, v2) -> {
                    String title1 = v1.getTitle() != null ? v1.getTitle() : v1.getDisplayName();
                    String title2 = v2.getTitle() != null ? v2.getTitle() : v2.getDisplayName();
                    return title2.compareToIgnoreCase(title1);
                };
                
            case DATE_NEW_TO_OLD:
                return (v1, v2) -> Long.compare(v2.getDateAdded(), v1.getDateAdded());
                
            case DATE_OLD_TO_NEW:
                return (v1, v2) -> Long.compare(v1.getDateAdded(), v2.getDateAdded());
                
            case SIZE_LARGE_TO_SMALL:
                return (v1, v2) -> Long.compare(v2.getSize(), v1.getSize());
                
            case SIZE_SMALL_TO_LARGE:
                return (v1, v2) -> Long.compare(v1.getSize(), v2.getSize());
                
            case RESOLUTION_HIGH_TO_LOW:
                return (v1, v2) -> {
                    int pixels1 = v1.getWidth() * v1.getHeight();
                    int pixels2 = v2.getWidth() * v2.getHeight();
                    return Integer.compare(pixels2, pixels1);
                };
                
            case RESOLUTION_LOW_TO_HIGH:
                return (v1, v2) -> {
                    int pixels1 = v1.getWidth() * v1.getHeight();
                    int pixels2 = v2.getWidth() * v2.getHeight();
                    return Integer.compare(pixels1, pixels2);
                };
                
            case DURATION_LONG_TO_SHORT:
                return (v1, v2) -> Long.compare(v2.getDuration(), v1.getDuration());
                
            case DURATION_SHORT_TO_LONG:
                return (v1, v2) -> Long.compare(v1.getDuration(), v2.getDuration());
                
            case RECENTLY_PLAYED:
                return (v1, v2) -> Long.compare(v2.getLastPlayedTime(), v1.getLastPlayedTime());
                
            default:
                return null;
        }
    }

    public static String getSortDisplayName(SortType sortType) {
        switch (sortType) {
            case TITLE_ASC: return "Title (A-Z)";
            case TITLE_DESC: return "Title (Z-A)";
            case DATE_NEW_TO_OLD: return "Date (New to Old)";
            case DATE_OLD_TO_NEW: return "Date (Old to New)";
            case SIZE_LARGE_TO_SMALL: return "Size (Large to Small)";
            case SIZE_SMALL_TO_LARGE: return "Size (Small to Large)";
            case RESOLUTION_HIGH_TO_LOW: return "Resolution (High to Low)";
            case RESOLUTION_LOW_TO_HIGH: return "Resolution (Low to High)";
            case DURATION_LONG_TO_SHORT: return "Duration (Long to Short)";
            case DURATION_SHORT_TO_LONG: return "Duration (Short to Long)";
            case RECENTLY_PLAYED: return "Recently Played";
            default: return "Unknown";
        }
    }
}