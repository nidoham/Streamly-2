package com.nidoham.streamlyvid;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Tracks application lifecycle for memory management and analytics
 */
public class AppLifecycleTracker implements Application.ActivityLifecycleCallbacks {
    
    private static final String TAG = "AppLifecycleTracker";
    
    private int activityCount = 0;
    private boolean isAppInForeground = false;
    
    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
        Log.d(TAG, "Activity created: " + activity.getClass().getSimpleName());
    }
    
    @Override
    public void onActivityStarted(@NonNull Activity activity) {
        Log.d(TAG, "Activity started: " + activity.getClass().getSimpleName());
        
        activityCount++;
        if (!isAppInForeground) {
            isAppInForeground = true;
            onAppForegrounded();
        }
    }
    
    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        Log.d(TAG, "Activity resumed: " + activity.getClass().getSimpleName());
    }
    
    @Override
    public void onActivityPaused(@NonNull Activity activity) {
        Log.d(TAG, "Activity paused: " + activity.getClass().getSimpleName());
    }
    
    @Override
    public void onActivityStopped(@NonNull Activity activity) {
        Log.d(TAG, "Activity stopped: " + activity.getClass().getSimpleName());
        
        activityCount--;
        if (activityCount == 0 && isAppInForeground) {
            isAppInForeground = false;
            onAppBackgrounded();
        }
    }
    
    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
        Log.d(TAG, "Activity save instance state: " + activity.getClass().getSimpleName());
    }
    
    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
        Log.d(TAG, "Activity destroyed: " + activity.getClass().getSimpleName());
    }
    
    private void onAppForegrounded() {
        Log.i(TAG, "App moved to foreground");
        
        App app = App.getInstance();
        if (app != null) {
            app.setInBackground(false);
        }
    }
    
    private void onAppBackgrounded() {
        Log.i(TAG, "App moved to background");
        
        App app = App.getInstance();
        if (app != null) {
            app.setInBackground(true);
        }
    }
    
    public boolean isAppInForeground() {
        return isAppInForeground;
    }
    
    public int getActiveActivityCount() {
        return activityCount;
    }
}