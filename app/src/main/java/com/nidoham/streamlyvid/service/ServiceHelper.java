package com.nidoham.streamlyvid.service;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

/**
 * Helper class to manage PlayerService connection and communication
 */
public class ServiceHelper {
    
    private static final String TAG = "ServiceHelper";
    
    private Context context;
    private PlayerService playerService;
    private boolean isServiceBound = false;
    private ServiceConnectionListener connectionListener;
    
    public interface ServiceConnectionListener {
        void onServiceConnected(PlayerService service);
        void onServiceDisconnected();
    }
    
    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            PlayerService.PlayerServiceBinder binder = (PlayerService.PlayerServiceBinder) service;
            playerService = binder.getService();
            isServiceBound = true;
            Log.d(TAG, "Service connected");
            
            if (connectionListener != null) {
                connectionListener.onServiceConnected(playerService);
            }
        }
        
        @Override
        public void onServiceDisconnected(ComponentName name) {
            playerService = null;
            isServiceBound = false;
            Log.d(TAG, "Service disconnected");
            
            if (connectionListener != null) {
                connectionListener.onServiceDisconnected();
            }
        }
    };
    
    public ServiceHelper(Context context) {
        this.context = context;
    }
    
    public void setConnectionListener(ServiceConnectionListener listener) {
        this.connectionListener = listener;
    }
    
    public void bindService() {
        if (!isServiceBound) {
            Intent intent = new Intent(context, PlayerService.class);
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        }
    }
    
    public void unbindService() {
        if (isServiceBound) {
            context.unbindService(serviceConnection);
            isServiceBound = false;
            playerService = null;
        }
    }
    
    public void startPlayback(String mediaPath, String mediaTitle, String mediaArtist, boolean isAudioOnly) {
        Intent intent = new Intent(context, PlayerService.class);
        intent.setAction(PlayerService.ACTION_START_PLAYBACK);
        intent.putExtra("mediaPath", mediaPath);
        intent.putExtra("mediaTitle", mediaTitle);
        intent.putExtra("mediaArtist", mediaArtist != null ? mediaArtist : "Unknown Artist");
        intent.putExtra("isAudioOnly", isAudioOnly);
        context.startService(intent);
    }
    
    public void pausePlayback() {
        if (isServiceBound && playerService != null) {
            playerService.pausePlayback();
        } else {
            Intent intent = new Intent(context, PlayerService.class);
            intent.setAction(PlayerService.ACTION_PAUSE_PLAYBACK);
            context.startService(intent);
        }
    }
    
    public void resumePlayback() {
        if (isServiceBound && playerService != null) {
            playerService.resumePlayback();
        } else {
            Intent intent = new Intent(context, PlayerService.class);
            intent.setAction(PlayerService.ACTION_RESUME_PLAYBACK);
            context.startService(intent);
        }
    }
    
    public void stopPlayback() {
        if (isServiceBound && playerService != null) {
            playerService.stopPlayback();
        } else {
            Intent intent = new Intent(context, PlayerService.class);
            intent.setAction(PlayerService.ACTION_STOP_PLAYBACK);
            context.startService(intent);
        }
    }
    
    public void seekTo(long position) {
        if (isServiceBound && playerService != null) {
            playerService.seekTo(position);
        } else {
            Intent intent = new Intent(context, PlayerService.class);
            intent.setAction(PlayerService.ACTION_SEEK_TO);
            intent.putExtra("position", position);
            context.startService(intent);
        }
    }
    
    public boolean isPlaying() {
        return isServiceBound && playerService != null && playerService.isPlaying();
    }
    
    public long getCurrentPosition() {
        return isServiceBound && playerService != null ? playerService.getCurrentPosition() : 0;
    }
    
    public long getDuration() {
        return isServiceBound && playerService != null ? playerService.getDuration() : 0;
    }
    
    public String getCurrentMediaPath() {
        return isServiceBound && playerService != null ? playerService.getCurrentMediaPath() : null;
    }
    
    public String getCurrentMediaTitle() {
        return isServiceBound && playerService != null ? playerService.getCurrentMediaTitle() : null;
    }
    
    public boolean isServiceBound() {
        return isServiceBound;
    }
    
    public PlayerService getPlayerService() {
        return playerService;
    }
}