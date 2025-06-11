package com.nidoham.streamlyvid.fragments.adapter;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Lifecycle;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.nidoham.streamlyvid.fragments.ui.HomeFolderFragment;
import com.nidoham.streamlyvid.fragments.ui.HomePlaylistFragment;
import com.nidoham.streamlyvid.fragments.ui.HomeVideoFragment;

public class VideoPagerAdapter extends FragmentStateAdapter {

    private static final int NUM_TABS = 3; // Number of tabs: Video, Folder, Playlist

    public VideoPagerAdapter(@NonNull FragmentManager fragmentManager, @NonNull Lifecycle lifecycle) {
        super(fragmentManager, lifecycle);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0:
                return new HomeVideoFragment(); // Fragment for "Video" tab
            case 1:
                return new HomeFolderFragment(); // Fragment for "Folder" tab
            case 2:
                return new HomePlaylistFragment(); // Fragment for "Playlist" tab
            default:
                return new HomeVideoFragment(); // Default to VideoFragment
        }
    }

    @Override
    public int getItemCount() {
        return NUM_TABS;
    }
}