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
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.schabi.newpipe.extractor.exceptions.ExtractionException;

import java.io.IOException;
import java.util.List;

import su.sadrobot.yashlang.controller.ContentLoader;
import su.sadrobot.yashlang.model.PlaylistInfo;
import su.sadrobot.yashlang.model.VideoDatabase;
import su.sadrobot.yashlang.model.VideoItem;
import su.sadrobot.yashlang.view.OnListItemClickListener;
import su.sadrobot.yashlang.view.OnListItemSwitchListener;
import su.sadrobot.yashlang.view.PlaylistInfoArrayAdapter;

/**
 *
 */
public class ConfigurePlaylistsActivity extends AppCompatActivity {

    private RecyclerView playlistList;
    private Button addSrcBtn;
    private Button gotoBlacklistBtn;

    private Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_configure_playlists);

        playlistList = findViewById(R.id.video_src_list);



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

        gotoBlacklistBtn = findViewById(R.id.goto_blacklist_btn);
        gotoBlacklistBtn.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(ConfigurePlaylistsActivity.this, BlacklistActivity.class));
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
                                    public boolean onItemLongClick(final View view, final int position, final PlaylistInfo item) {
                                        new Thread(new Runnable() {
                                            @Override
                                            public void run() {
                                                try {
                                                    final List<VideoItem> videos = ContentLoader.getInstance().loadYtPlaylistNewItems(ConfigurePlaylistsActivity.this, item);
                                                    for (VideoItem vid : videos) {
                                                        System.out.println(vid.getYtId() + " " + vid.getName());
                                                    }
                                                } catch (IOException e) {
                                                    e.printStackTrace();
                                                } catch (ExtractionException e) {
                                                    e.printStackTrace();
                                                }
                                            }
                                        }).start();

                                        Toast.makeText(ConfigurePlaylistsActivity.this,
                                                "LONG CLLICK: " + position + " : " +
                                                        item.getId() + " : " + item.getUrl() + " : " + item.getType(),
                                                Toast.LENGTH_SHORT).show();
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
}
