package com.nidoham.streamlyvid;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.nidoham.streamlyvid.databinding.ActivitySplashBinding;

import java.util.ArrayList;
import java.util.List;

public class SplashActivity extends AppCompatActivity {

    private ActivitySplashBinding binding;
    private static final long INITIAL_SPLASH_TIME = 1000; // 1 second initial splash
    private static final long PERMISSION_SPLASH_TIME = 1500; // 1.5 seconds after permission granted
    
    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final int MANAGE_EXTERNAL_STORAGE_REQUEST_CODE = 1002;
    
    // Required permissions based on Android version
    private String[] getRequiredPermissions() {
        List<String> permissions = new ArrayList<>();
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13+
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO);
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO);
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES);
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) { // Android 6+
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) { // Android 9 and below
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
        }
        
        // Common permissions for all versions
        permissions.add(Manifest.permission.INTERNET);
        permissions.add(Manifest.permission.ACCESS_NETWORK_STATE);
        permissions.add(Manifest.permission.WAKE_LOCK);
        permissions.add(Manifest.permission.FOREGROUND_SERVICE);
        permissions.add(Manifest.permission.MODIFY_AUDIO_SETTINGS);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            permissions.add(Manifest.permission.SYSTEM_ALERT_WINDOW);
        }
        
        return permissions.toArray(new String[0]);
    }

    // Activity result launcher for MANAGE_EXTERNAL_STORAGE permission
    private final ActivityResultLauncher<Intent> manageExternalStorageLauncher = 
        registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            // Don't check result parameter, just check current permission status
            checkManageExternalStoragePermissionStatus();
        });

    // Activity result launcher for SYSTEM_ALERT_WINDOW permission
    private final ActivityResultLauncher<Intent> systemAlertWindowLauncher = 
        registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            // Don't check result parameter, just check current permission status
            checkSystemAlertWindowPermissionStatus();
        });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySplashBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Initial splash screen for 1 second
        new Handler(Looper.getMainLooper()).postDelayed(this::checkPermissions, INITIAL_SPLASH_TIME);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // This will be called when user returns from settings
        // We'll check permissions again to handle the case where user granted permissions in settings
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        // TODO: Implement this method
    }
    

    private void checkSystemAlertWindowPermissionStatus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Settings.canDrawOverlays(this)) {
                checkManageExternalStoragePermission();
            } else {
                showPermissionDeniedDialog();
            }
        } else {
            checkManageExternalStoragePermission();
        }
    }

    private void checkManageExternalStoragePermissionStatus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                // Permission granted, proceed to main activity
                proceedToMainActivity();
            } else {
                // Permission denied, show explanation
                showPermissionDeniedDialog();
            }
        } else {
            // For older Android versions, proceed to main activity
            proceedToMainActivity();
        }
    }

    private void checkPermissions() {
        List<String> deniedPermissions = new ArrayList<>();
        String[] requiredPermissions = getRequiredPermissions();
        
        // Check regular permissions
        for (String permission : requiredPermissions) {
            if (!permission.equals(Manifest.permission.SYSTEM_ALERT_WINDOW) && 
                ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                deniedPermissions.add(permission);
            }
        }
        
        if (!deniedPermissions.isEmpty()) {
            // Request regular permissions
            ActivityCompat.requestPermissions(this, 
                deniedPermissions.toArray(new String[0]), 
                PERMISSION_REQUEST_CODE);
        } else {
            // Check special permissions
            checkSpecialPermissions();
        }
    }

    private void checkSpecialPermissions() {
        // Check SYSTEM_ALERT_WINDOW permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            requestSystemAlertWindowPermission();
        } else {
            checkManageExternalStoragePermission();
        }
    }

    private void requestSystemAlertWindowPermission() {
        new AlertDialog.Builder(this)
            .setTitle("Overlay Permission Required")
            .setMessage("This app needs overlay permission for picture-in-picture mode and floating player features.")
            .setPositiveButton("Grant Permission", (dialog, which) -> {
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, 
                        Uri.parse("package:" + getPackageName()));
                    systemAlertWindowLauncher.launch(intent);
                } catch (Exception e) {
                    Toast.makeText(this, "Unable to open settings", Toast.LENGTH_SHORT).show();
                    showPermissionDeniedDialog();
                }
            })
            .setNegativeButton("Cancel", (dialog, which) -> showPermissionDeniedDialog())
            .setCancelable(false)
            .show();
    }

    private void checkManageExternalStoragePermission() {
        // Check MANAGE_EXTERNAL_STORAGE permission for Android 11+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            requestManageExternalStoragePermission();
        } else {
            // All permissions granted, proceed to main activity
            proceedToMainActivity();
        }
    }

    private void requestManageExternalStoragePermission() {
        new AlertDialog.Builder(this)
            .setTitle("All Files Access Required")
            .setMessage("This app needs access to all files on your device to play media files from any location.")
            .setPositiveButton("Grant Permission", (dialog, which) -> {
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, 
                        Uri.parse("package:" + getPackageName()));
                    manageExternalStorageLauncher.launch(intent);
                } catch (Exception e) {
                    Toast.makeText(this, "Unable to open settings", Toast.LENGTH_SHORT).show();
                    showPermissionDeniedDialog();
                }
            })
            .setNegativeButton("Cancel", (dialog, which) -> showPermissionDeniedDialog())
            .setCancelable(false)
            .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, 
                                         @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allPermissionsGranted = true;
            
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false;
                    break;
                }
            }
            
            if (allPermissionsGranted) {
                // Check special permissions
                checkSpecialPermissions();
            } else {
                // Some permissions denied
                showPermissionDeniedDialog();
            }
        }
    }

    private void proceedToMainActivity() {
        // Show permission granted message briefly
        Toast.makeText(this, "All permissions granted!", Toast.LENGTH_SHORT).show();
        
        // Wait for 1.5 seconds then switch to MainActivity
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Intent intent = new Intent(SplashActivity.this, MainActivity.class);
            startActivity(intent);
            finish();
        }, PERMISSION_SPLASH_TIME);
    }

    private void showPermissionDeniedDialog() {
        new AlertDialog.Builder(this)
            .setTitle("Permissions Required")
            .setMessage("This app requires all permissions to function properly. Please grant all permissions to continue.")
            .setPositiveButton("Retry", (dialog, which) -> checkPermissions())
            .setNegativeButton("Exit", (dialog, which) -> {
                Toast.makeText(this, "App cannot function without required permissions", Toast.LENGTH_LONG).show();
                finish();
            })
            .setCancelable(false)
            .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (binding != null) {
            binding = null;
        }
    }
}