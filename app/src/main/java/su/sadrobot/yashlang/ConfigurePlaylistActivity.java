package su.sadrobot.yashlang;

/*
 * Created by Anton Moiseev (sadr0b0t) in 2019.
 *
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

import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.CompoundButton;
import android.widget.Switch;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import su.sadrobot.yashlang.controller.PlaylistInfoActions;
import su.sadrobot.yashlang.model.PlaylistInfo;
import su.sadrobot.yashlang.model.VideoDatabase;

/**
 *
 */
public class ConfigurePlaylistActivity extends AppCompatActivity {
    // https://developer.android.com/guide/components/fragments
    // https://developer.android.com/guide/navigation/navigation-swipe-view-2

    public static final String PARAM_PLAYLIST_ID = "PARAM_PLAYLIST_ID";


    private TabLayout tabs;
    private ViewPager2 pager;

    private Toolbar toolbar;
    private Switch enabledSwitch;

    private ConfigurePlaylistFragment viewPlaylistFrag;
    private ConfigurePlaylistNewItemsFragment viewPlaylistNewItemsFrag;

    private final Handler handler = new Handler();

    private long playlistId = PlaylistInfo.ID_NONE;
    private PlaylistInfo plInfo;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_view_playlist);

        tabs = findViewById(R.id.tabs);
        pager = findViewById(R.id.pager);
        toolbar = findViewById(R.id.toolbar);

        // https://developer.android.com/training/appbar
        // https://www.vogella.com/tutorials/AndroidActionBar/article.html#custom-views-in-the-action-bar
        setSupportActionBar(toolbar);
        // кнопка "Назад" на акшенбаре
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        viewPlaylistFrag = new ConfigurePlaylistFragment();
        viewPlaylistNewItemsFrag = new ConfigurePlaylistNewItemsFragment();

        pager.setAdapter(new FragmentStateAdapter(getSupportFragmentManager(), getLifecycle()) {
            @Override
            public int getItemCount() {
                return 2;
            }

            @NonNull
            @Override
            public Fragment createFragment(int position) {
                if (position == 0) {
                    return viewPlaylistFrag;
                } else {
                    return viewPlaylistNewItemsFrag;
                }
            }
        });

        new TabLayoutMediator(tabs, pager,
                new TabLayoutMediator.TabConfigurationStrategy() {
                    @Override
                    public void onConfigureTab(@NonNull TabLayout.Tab tab, int position) {
                        if (position == 0) {
                            tab.setText(R.string.tab_item_playlist);
                        } else {
                            tab.setText(R.string.tab_item_new);
                        }
                    }
                }).attach();

        viewPlaylistNewItemsFrag.setPlaylistUpdateListener(new ConfigurePlaylistNewItemsFragment.PlaylistUpdateListener() {
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

        playlistId = getIntent().getLongExtra(PARAM_PLAYLIST_ID, PlaylistInfo.ID_NONE);

        if (playlistId != PlaylistInfo.ID_NONE) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    plInfo = VideoDatabase.getDbInstance(ConfigurePlaylistActivity.this).
                            playlistInfoDao().getById(playlistId);

                    // пересозданим меню
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            invalidateOptionsMenu();
                        }
                    });
                }
            }).start();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        // https://developer.android.com/training/appbar/action-views.html

        // Без живого plInfo не будем показывать тулбар
        // plInfo загружается в фоновом потоке из onCreate, после загрузки вызываем invalidateOptionsMenu,
        // чтобы иметь возможность пересоздать меню с живым plInfo.
        // Но plInfo обычно появляется раньше, чем вызывается onCreateOptionsMenu, поэтому здесь plInfo
        // обычно сразу будет не null
        if (plInfo != null) {
            toolbar.inflateMenu(R.menu.configure_playlist_actions);
            enabledSwitch = (Switch) toolbar.getMenu().findItem(R.id.action_enable).getActionView();

            enabledSwitch.setChecked(plInfo.isEnabled());
            enabledSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(final CompoundButton buttonView, final boolean isChecked) {
                    if (plInfo != null) {
                        PlaylistInfoActions.actionSetPlaylistEnabled(
                                ConfigurePlaylistActivity.this,
                                plInfo.getId(), isChecked, new PlaylistInfoActions.OnPlaylistEnabledChangeListener() {
                                    @Override
                                    public void onPlaylistEnabledChange() {
                                        // обновим кэш
                                        plInfo.setEnabled(isChecked);
                                    }
                                });
                    }
                }
            });

            toolbar.setOnMenuItemClickListener(
                    new Toolbar.OnMenuItemClickListener() {
                        @Override
                        public boolean onMenuItemClick(MenuItem item) {
                            return onOptionsItemSelected(item);
                        }
                    });
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_copy_playlist_name:
                PlaylistInfoActions.actionCopyPlaylistName(ConfigurePlaylistActivity.this, plInfo);
                break;
            case R.id.action_copy_playlist_url:
                PlaylistInfoActions.actionCopyPlaylistUrl(ConfigurePlaylistActivity.this, plInfo);
                break;
            case R.id.action_delete:
                PlaylistInfoActions.actionDeletePlaylist(
                        ConfigurePlaylistActivity.this, playlistId,
                        new PlaylistInfoActions.OnPlaylistDeletedListener() {
                            @Override
                            public void onPlaylistDeleted() {
                                handler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        ConfigurePlaylistActivity.this.finish();
                                    }
                                });
                            }
                        }
                );
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
