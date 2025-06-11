package com.nidoham.streamlyvid.fragments.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.nidoham.streamlyvid.databinding.FragmentsVideosBinding;
import com.nidoham.streamlyvid.fragments.adapter.VideoPagerAdapter;

public class VideosFragment extends Fragment {

    private FragmentsVideosBinding binding;
    private VideoPagerAdapter adapter;

    public VideosFragment() {
        // Required empty public constructor
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentsVideosBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        adapter = new VideoPagerAdapter(getChildFragmentManager(), getLifecycle());
        binding.vidmateContentPager.setAdapter(adapter);
        
        binding.vidmateContentPager.setUserInputEnabled(false);

        new TabLayoutMediator(binding.vidmateTabLayout, binding.vidmateContentPager,
                (tab, position) -> {
                    switch (position) {
                        case 0:
                            tab.setText("Video");
                            break;
                        case 1:
                            tab.setText("Folder");
                            break;
                        case 2:
                            tab.setText("Playlist");
                            break;
                    }
                }
        ).attach();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}