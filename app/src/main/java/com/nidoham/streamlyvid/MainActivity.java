package com.nidoham.streamlyvid;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.nidoham.streamlyvid.databinding.ActivityMainBinding;
import com.nidoham.streamlyvid.fragments.adapter.MainPagerAdapter;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private ViewPager2 viewPager;
    private BottomNavigationView bottomNav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewPager = binding.vidmateContentPager;
        bottomNav = binding.vidmateBottomNav;

        MainPagerAdapter pagerAdapter = new MainPagerAdapter(getSupportFragmentManager(), getLifecycle());
        viewPager.setAdapter(pagerAdapter);

        // --- ADD THIS LINE TO DISABLE SWIPING ---
        viewPager.setUserInputEnabled(false);
        // -----------------------------------------

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                switch (position) {
                    case 0:
                        bottomNav.setSelectedItemId(R.id.nav_videos);
                        break;
                    case 1:
                        bottomNav.setSelectedItemId(R.id.nav_music);
                        break;
                    case 2:
                        bottomNav.setSelectedItemId(R.id.nav_me);
                        break;
                }
            }
        });

        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_videos) {
                viewPager.setCurrentItem(0);
                return true;
            } else if (itemId == R.id.nav_music) {
                viewPager.setCurrentItem(1);
                return true;
            } else if (itemId == R.id.nav_me) {
                viewPager.setCurrentItem(2);
                return true;
            }
            return false;
        });
    }
}