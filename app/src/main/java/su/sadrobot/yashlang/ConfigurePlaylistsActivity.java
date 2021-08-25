package su.sadrobot.yashlang;

/*
 * Created by Anton Moiseev (sadr0b0t) in 2019.
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

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.tabs.TabLayout;

/**
 *
 */
public class ConfigurePlaylistsActivity extends AppCompatActivity {
    // https://developer.android.com/guide/components/fragments
    // https://developer.android.com/guide/navigation/navigation-swipe-view
    // https://developer.android.com/reference/androidx/fragment/app/FragmentPagerAdapter

    private TabLayout tabs;
    private ViewPager pager;

    private Toolbar toolbar;

    private ConfigurePlaylistsFragment configurePlaylistsFrag;
    private ConfigurePlaylistsNewItemsFragment configurePlaylistsNewItemsFrag;
    private ConfigurePlaylistsProfilesFragment configurePlaylistsProfilesFrag;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_configure_playlists);

        tabs = findViewById(R.id.tabs);
        pager = findViewById(R.id.pager);
        toolbar = findViewById(R.id.toolbar);

        // https://developer.android.com/training/appbar
        // https://www.vogella.com/tutorials/AndroidActionBar/article.html#custom-views-in-the-action-bar
        setSupportActionBar(toolbar);
        // кнопка "Назад" на акшенбаре
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        configurePlaylistsFrag = new ConfigurePlaylistsFragment();
        configurePlaylistsNewItemsFrag = new ConfigurePlaylistsNewItemsFragment();
        configurePlaylistsProfilesFrag = new ConfigurePlaylistsProfilesFragment();

        pager.setAdapter(new FragmentPagerAdapter(getSupportFragmentManager()) {
            @Override
            public int getCount() {
                return 3;
            }

            @Override
            public Fragment getItem(int position) {
                if (position == 0) {
                    return configurePlaylistsFrag;
                } else if(position == 1) {
                    return configurePlaylistsNewItemsFrag;
                } else {
                    return configurePlaylistsProfilesFrag;
                }
            }

            @Override
            public CharSequence getPageTitle(int position) {
                if (position == 0) {
                    return getString(R.string.tab_item_playlists);
                } else if(position == 1) {
                    return getString(R.string.tab_item_new);
                } else {
                    return getString(R.string.tab_item_profiles);
                }
            }
        });

        tabs.setupWithViewPager(pager);
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        // https://developer.android.com/training/appbar/action-views.html

        toolbar.inflateMenu(R.menu.configure_playlists_actions);

        toolbar.setOnMenuItemClickListener(
                new Toolbar.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        return onOptionsItemSelected(item);
                    }
                });

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_goto_blacklist:
                startActivity(new Intent(ConfigurePlaylistsActivity.this, BlacklistActivity.class));
                break;
            case R.id.action_add_recommended:
                startActivity(new Intent(ConfigurePlaylistsActivity.this, AddRecommendedPlaylistsActivity.class));
                break;
            case R.id.action_import:
                startActivity(new Intent(ConfigurePlaylistsActivity.this, ImportDataActivity.class));
                break;
            case R.id.action_export:
                startActivity(new Intent(ConfigurePlaylistsActivity.this, ExportDataActivity.class));
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
