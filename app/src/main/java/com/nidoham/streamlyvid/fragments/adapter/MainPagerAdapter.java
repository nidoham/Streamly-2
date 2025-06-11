package com.nidoham.streamlyvid.fragments.adapter;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Lifecycle;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import com.nidoham.streamlyvid.fragments.ui.VideosFragment;
import com.nidoham.streamlyvid.fragments.ui.MusicFragment;
import com.nidoham.streamlyvid.fragments.ui.MeFragment;

public class MainPagerAdapter extends FragmentStateAdapter {

    private static final int NUM_TABS = 3; // Videos, Music, Me

    public MainPagerAdapter(@NonNull FragmentManager fragmentManager, @NonNull Lifecycle lifecycle) {
        super(fragmentManager, lifecycle);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        // Return a new fragment instance for the given position
        switch (position) {
            case 0:
                return new VideosFragment();
            case 1:
                return new MusicFragment();
            case 2:
                return new MeFragment();
            default:
                // This should ideally not happen if NUM_TABS is correct
                return new VideosFragment();
        }
    }

    @Override
    public int getItemCount() {
        return NUM_TABS;
    }
}