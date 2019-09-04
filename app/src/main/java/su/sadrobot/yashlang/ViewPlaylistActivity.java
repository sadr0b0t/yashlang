package su.sadrobot.yashlang;

/*
 * Created by Anton Moiseev (sadr0b0t) in 2019.
 *
 * Copyright (C) Anton Moiseev 2019 <github.com/sadr0b0t>
 * ViewPlaylistActivity.java is part of YaShlang.
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
import android.os.Handler;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.tabs.TabLayout;

/**
 *
 */
public class ViewPlaylistActivity extends AppCompatActivity {
    // https://developer.android.com/guide/components/fragments
    // https://developer.android.com/guide/navigation/navigation-swipe-view
    // https://developer.android.com/reference/androidx/fragment/app/FragmentPagerAdapter

    public static final String PARAM_PLAYLIST_ID = "PARAM_PLAYLIST_ID";


    private TabLayout tabs;
    private ViewPager pager;

    private ViewPlaylistFragment viewPlaylistFrag;
    private ViewPlaylistNewItemsFragment viewPlaylistNewItemsFrag;

    private Handler handler = new Handler();

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_view_playlist);

        tabs = findViewById(R.id.tabs);
        pager = findViewById(R.id.pager);

        viewPlaylistFrag = new ViewPlaylistFragment();
        viewPlaylistNewItemsFrag = new ViewPlaylistNewItemsFragment();

        pager.setAdapter(new FragmentPagerAdapter(getSupportFragmentManager()) {
            @Override
            public int getCount() {
                return 2;
            }

            @Override
            public Fragment getItem(int position) {
                if (position == 0) {
                    return viewPlaylistFrag;
                } else {
                    return viewPlaylistNewItemsFrag;
                }
            }

            @Override
            public CharSequence getPageTitle(int position) {
                if (position == 0) {
                    return "PLAYLIST";
                } else {
                    return "NEW";
                }
            }
        });

        tabs.setupWithViewPager(pager);

        viewPlaylistNewItemsFrag.setPlaylistUpdateListener(new ViewPlaylistNewItemsFragment.PlaylistUpdateListener() {
            @Override
            public void onPlaylistUpdated() {
                // TODO: здесь нам нужно обновить плейлист на основной вкладке после того,
                // как на вкладке обновлений были добавлены новые элементы.
                // Простой способ - как здесь, нагородить событий.
                // Возможно, более правильно будет распознать обновление базы внутри адаптера,
                // в таком случае этот огород можно будет почистить
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        viewPlaylistFrag.updateVideoListBg();
                    }
                });
            }
        });
    }
}
