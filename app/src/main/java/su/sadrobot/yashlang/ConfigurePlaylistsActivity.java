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

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import su.sadrobot.yashlang.model.PlaylistInfo;
import su.sadrobot.yashlang.model.VideoDatabase;
import su.sadrobot.yashlang.view.OnListItemClickListener;
import su.sadrobot.yashlang.view.OnListItemSwitchListener;
import su.sadrobot.yashlang.view.PlaylistInfoArrayAdapter;

/**
 *
 */
public class ConfigurePlaylistsActivity extends AppCompatActivity {

    private RecyclerView playlistList;
    private Button addSrcBtn;
    private Toolbar toolbar;

    private Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_configure_playlists);

        playlistList = findViewById(R.id.video_src_list);
        toolbar = findViewById(R.id.toolbar);

        // https://developer.android.com/training/appbar
        // https://www.vogella.com/tutorials/AndroidActionBar/article.html#custom-views-in-the-action-bar
        setSupportActionBar(toolbar);
        // кнопка "Назад" на акшенбаре
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);


        // set a LinearLayoutManager with default vertical orientation
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getApplicationContext());
        playlistList.setLayoutManager(linearLayoutManager);


        addSrcBtn = findViewById(R.id.add_src_btn);
        addSrcBtn.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(ConfigurePlaylistsActivity.this, AddPlaylistActivity.class));
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        new Thread(new Runnable() {
            @Override
            public void run() {
                final VideoDatabase videodb = VideoDatabase.getDb(ConfigurePlaylistsActivity.this);
                final List<PlaylistInfo> items = videodb.playlistInfoDao().getAll();
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        playlistList.setAdapter(new PlaylistInfoArrayAdapter(ConfigurePlaylistsActivity.this, items,
                                new OnListItemClickListener<PlaylistInfo>() {
                                    @Override
                                    public void onItemClick(final View view, final int position, final PlaylistInfo item) {
                                        final Intent intent = new Intent(ConfigurePlaylistsActivity.this, ViewPlaylistActivity.class);
                                        intent.putExtra(ViewPlaylistActivity.PARAM_PLAYLIST_ID, item.getId());
                                        startActivity(intent);
                                    }

                                    @Override
                                    public boolean onItemLongClick(final View view, final int position, final PlaylistInfo plInfo) {

                                        // параметр Gravity.CENTER не работает (и появился еще только в API 19+),
                                        // работает только вариант Gravity.RIGHT
                                        //final PopupMenu popup = new PopupMenu(ConfigurePlaylistsActivity.this, view, Gravity.CENTER);
                                        final PopupMenu popup = new PopupMenu(ConfigurePlaylistsActivity.this,
                                                view.findViewById(R.id.playlist_name_txt));
                                        popup.getMenuInflater().inflate(R.menu.playlist_actions, popup.getMenu());
                                        popup.setOnMenuItemClickListener(
                                                new PopupMenu.OnMenuItemClickListener() {
                                                    @Override
                                                    public boolean onMenuItemClick(final MenuItem item) {
                                                        switch (item.getItemId()) {
                                                            case R.id.action_copy_playlist_name: {
                                                                final ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                                                                final ClipData clip = ClipData.newPlainText(plInfo.getName(), plInfo.getName());
                                                                clipboard.setPrimaryClip(clip);

                                                                Toast.makeText(ConfigurePlaylistsActivity.this,
                                                                        getString(R.string.copied) + ": " + plInfo.getName(),
                                                                        Toast.LENGTH_LONG).show();
                                                                break;
                                                            }
                                                            case R.id.action_copy_playlist_url: {
                                                                final ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                                                                final ClipData clip = ClipData.newPlainText(plInfo.getUrl(), plInfo.getUrl());
                                                                clipboard.setPrimaryClip(clip);

                                                                Toast.makeText(ConfigurePlaylistsActivity.this,
                                                                        getString(R.string.copied) + ": " + plInfo.getUrl(),
                                                                        Toast.LENGTH_LONG).show();
                                                                break;
                                                            }
                                                        }
                                                        return true;
                                                    }
                                                }
                                        );
                                        popup.show();
                                        return true;
                                    }
                                },
                                new OnListItemSwitchListener<PlaylistInfo>() {
                                    @Override
                                    public void onItemCheckedChanged(final CompoundButton buttonView, final int position, final PlaylistInfo item, final boolean isChecked) {
                                        new Thread(new Runnable() {
                                            @Override
                                            public void run() {
                                                final VideoDatabase videodb = VideoDatabase.getDb(ConfigurePlaylistsActivity.this);
                                                // TODO: здесь замечательно пойдет метод с @Transaction в DAO
                                                // https://developer.android.com/reference/android/arch/persistence/room/Transaction.html
                                                videodb.runInTransaction(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        videodb.playlistInfoDao().setEnabled(item.getId(), isChecked);
                                                        videodb.videoItemDao().setPlaylistEnabled(item.getId(), isChecked);
                                                    }
                                                });
                                                videodb.close();

                                                // здесь тоже нужно обновить вручную, т.к. у нас в адаптере
                                                // хранятся уже загруженные из базы объекты и просто так
                                                // они сами себя не засинкают
                                                item.setEnabled(isChecked);
                                            }}).start();

                                        handler.post(new Runnable() {
                                            @Override
                                            public void run() {
                                                //playlistList.getAdapter().notifyDataSetChanged();
                                            }
                                        });
                                    }
                                }));
                    }
                });

            }
        }).start();
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

        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
