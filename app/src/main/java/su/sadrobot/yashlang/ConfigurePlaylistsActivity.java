package su.sadrobot.yashlang;

/*
 * Copyright (C) Anton Moiseev 2019 <github.com/sadr0b0t>
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

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

/**
 *
 */
public class ConfigurePlaylistsActivity extends AppCompatActivity {
    // https://developer.android.com/guide/components/fragments
    // https://developer.android.com/guide/navigation/navigation-swipe-view-2

    private TabLayout tabs;
    private ViewPager2 pager;

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

        pager.setAdapter(new FragmentStateAdapter(getSupportFragmentManager(), getLifecycle()) {
            @Override
            public int getItemCount() {
                return 3;
            }

            @NonNull
            @Override
            public Fragment createFragment(int position) {
                if (position == 0) {
                    return configurePlaylistsFrag;
                } else if (position == 1) {
                    return configurePlaylistsNewItemsFrag;
                } else {
                    return configurePlaylistsProfilesFrag;
                }
            }
        });

        new TabLayoutMediator(tabs, pager,
                new TabLayoutMediator.TabConfigurationStrategy() {
                    @Override
                    public void onConfigureTab(@NonNull TabLayout.Tab tab, int position) {
                        if (position == 0) {
                            tab.setText(R.string.tab_item_playlists);
                        } else if (position == 1) {
                            tab.setText(R.string.tab_item_new);
                        } else {
                            tab.setText(R.string.tab_item_profiles);
                        }
                    }
                }).attach();
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
            case R.id.action_video_cache:
                startActivity(new Intent(ConfigurePlaylistsActivity.this, StreamCacheActivity.class));
                break;
            case R.id.action_configure_moar:
                startActivity(new Intent(ConfigurePlaylistsActivity.this, ConfigureMoarActivity.class));
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
