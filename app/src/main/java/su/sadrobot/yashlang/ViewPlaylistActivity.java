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

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.tabs.TabLayout;

import su.sadrobot.yashlang.model.PlaylistInfo;
import su.sadrobot.yashlang.model.VideoDatabase;
import su.sadrobot.yashlang.model.VideoItem;

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

    private Toolbar toolbar;
    private Switch enabledSwitch;

    private ViewPlaylistFragment viewPlaylistFrag;
    private ViewPlaylistNewItemsFragment viewPlaylistNewItemsFrag;

    private Handler handler = new Handler();

    private VideoDatabase videodb;
    private long playlistId = -1;
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
                    return getString(R.string.tab_item_playlist);
                } else {
                    return getString(R.string.tab_item_new);
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

        playlistId = getIntent().getLongExtra(PARAM_PLAYLIST_ID, -1);


        // подключимся к базе один раз при создании активити,
        // закрывать подключение в onDestroy
        videodb = VideoDatabase.getDb(this);

        if (playlistId != -1) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    plInfo = videodb.playlistInfoDao().getById(playlistId);
                }
            }).start();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        // https://developer.android.com/training/appbar/action-views.html

        toolbar.inflateMenu(R.menu.view_playlist_actions);
        enabledSwitch = (Switch) toolbar.getMenu().findItem(R.id.action_enable).getActionView();

        // plInfo заргужается в фоновом потоке из onCreate, но все равно появляется раньше,
        // чем вызывается onCreateOptionsMenu, поэтому здесь plInfo вообще говоря не должен быть null
        if (plInfo != null) {
            enabledSwitch.setChecked(plInfo.isEnabled());
            enabledSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(final CompoundButton buttonView, final boolean isChecked) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            if (plInfo != null) {
                                videodb.playlistInfoDao().setEnabled(plInfo.getId(), isChecked);
                                // обновим кэш
                                plInfo.setEnabled(isChecked);
                            }
                        }
                    }).start();
                }
            });
        }

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
    public void onDestroy() {
        super.onDestroy();

        if (videodb != null) {
            videodb.close();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_enable:
                if (plInfo != null) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            videodb.videoItemDao().setStarred(plInfo.getId(), !plInfo.isEnabled());
                            // обновим кэш
                            plInfo.setEnabled(!plInfo.isEnabled());
                        }
                    }).start();
                }
                break;
            case R.id.action_copy_playlist_url:
                if (plInfo != null) {
                    final ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    final ClipData clip = ClipData.newPlainText(plInfo.getUrl(), plInfo.getUrl());
                    clipboard.setPrimaryClip(clip);
                }
                break;
            case R.id.action_delete_playlist:
                new AlertDialog.Builder(ViewPlaylistActivity.this)
                        .setTitle(getString(R.string.delete_playlist_title))
                        .setMessage(getString(R.string.delete_playlist_message))
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {

                            public void onClick(DialogInterface dialog, int whichButton) {

                                new Thread(new Runnable() {
                                    @Override
                                    public void run() {
                                        final PlaylistInfo plInfo = videodb.playlistInfoDao().getById(playlistId);
                                        videodb.playlistInfoDao().delete(plInfo);

                                        handler.post(new Runnable() {
                                            @Override
                                            public void run() {
                                                ViewPlaylistActivity.this.finish();
                                            }
                                        });
                                    }
                                }).start();

                            }
                        })
                        .setNegativeButton(android.R.string.no, null).show();
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
