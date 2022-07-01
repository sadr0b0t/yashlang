package su.sadrobot.yashlang;

/*
 * Created by Anton Moiseev (sadr0b0t) in 2021.
 *
 * Copyright (C) Anton Moiseev 2019 <github.com/sadr0b0t>
 * ConfigurePlaylistActivity.java is part of YaShlang.
 *
 * YaShlang is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * YaShlang is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with YaShlang.  If not, see <http://www.gnu.org/licenses/>.
 */

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.tabs.TabLayout;

/**
 *
 */
public class ConfigureMoarActivity extends AppCompatActivity {
    // https://developer.android.com/guide/components/fragments
    // https://developer.android.com/guide/navigation/navigation-swipe-view
    // https://developer.android.com/reference/androidx/fragment/app/FragmentPagerAdapter

    private TabLayout tabs;
    private ViewPager pager;

    private ConfigureVideoQualityFragment configureVideoQualityFrag;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_configure_moar);

        tabs = findViewById(R.id.tabs);
        pager = findViewById(R.id.pager);

        // кнопка "Назад" на акшенбаре
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        configureVideoQualityFrag = new ConfigureVideoQualityFragment();

        pager.setAdapter(new FragmentPagerAdapter(getSupportFragmentManager()) {
            @Override
            public int getCount() {
                return 1;
            }

            @NonNull
            @Override
            public Fragment getItem(int position) {
                return configureVideoQualityFrag;
            }

            @Override
            public CharSequence getPageTitle(int position) {
                return getString(R.string.tab_item_video_quality);
            }
        });

        tabs.setupWithViewPager(pager);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
