package com.nidoham.streamlyvid.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface RecentlyPlayedDao {
    
    @Query("SELECT * FROM recently_played ORDER BY lastPlayedTime DESC LIMIT :limit")
    LiveData<List<RecentlyPlayedEntity>> getRecentlyPlayed(int limit);
    
    @Query("SELECT * FROM recently_played WHERE videoId = :videoId LIMIT 1")
    RecentlyPlayedEntity getRecentlyPlayedVideo(long videoId);
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertRecentlyPlayed(RecentlyPlayedEntity recentlyPlayed);
    
    @Query("DELETE FROM recently_played")
    void clearAllRecentlyPlayed();
    
    @Query("DELETE FROM recently_played WHERE videoId NOT IN (SELECT videoId FROM recently_played ORDER BY lastPlayedTime DESC LIMIT :limit)")
    void limitRecentlyPlayed(int limit);
    
    @Query("DELETE FROM recently_played WHERE videoId = :videoId")
    void deleteRecentlyPlayed(long videoId);
    
    @Query("SELECT COUNT(*) FROM recently_played")
    int getRecentlyPlayedCount();
}