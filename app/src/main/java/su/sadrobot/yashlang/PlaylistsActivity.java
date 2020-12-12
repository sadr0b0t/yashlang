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
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
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
import su.sadrobot.yashlang.view.PlaylistInfoArrayAdapter;

/**
 *
 */
public class PlaylistsActivity extends AppCompatActivity {

    private Toolbar toolbar;

    // Экран с пустым списком
    private View playlistListEmptyView;
    private Button addRecommendedBtn;

    //
    private RecyclerView playlistList;

    private Handler handler = new Handler();

    private RecyclerView.AdapterDataObserver emptyListObserver = new RecyclerView.AdapterDataObserver() {
        // https://stackoverflow.com/questions/47417645/empty-view-on-a-recyclerview
        // https://stackoverflow.com/questions/27414173/equivalent-of-listview-setemptyview-in-recyclerview
        // https://gist.github.com/sheharyarn/5602930ad84fa64c30a29ab18eb69c6e
        private void checkIfEmpty() {
            final boolean listIsEmpty = playlistList.getAdapter() == null || playlistList.getAdapter().getItemCount() == 0;
            playlistListEmptyView.setVisibility(listIsEmpty ? View.VISIBLE : View.GONE);
            playlistList.setVisibility(listIsEmpty ? View.GONE : View.VISIBLE);
        }

        @Override
        public void onChanged() {
            checkIfEmpty();
        }

        @Override
        public void onItemRangeInserted(int positionStart, int itemCount) {
            checkIfEmpty();
        }

        @Override
        public void onItemRangeRemoved(int positionStart, int itemCount) {
            checkIfEmpty();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_playlists);

        toolbar = findViewById(R.id.toolbar);

        playlistListEmptyView = findViewById(R.id.playlist_list_empty_view);
        addRecommendedBtn = findViewById(R.id.add_recommended_btn);


        playlistList = findViewById(R.id.playlist_list);

        // https://developer.android.com/training/appbar
        // https://www.vogella.com/tutorials/AndroidActionBar/article.html#custom-views-in-the-action-bar
        setSupportActionBar(toolbar);
        // кнопка "Назад" на акшенбаре
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);


        // set a LinearLayoutManager with default vertical orientation
        final LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getApplicationContext());
        playlistList.setLayoutManager(linearLayoutManager);

        addRecommendedBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(PlaylistsActivity.this, AddRecommendedPlaylistsActivity.class));
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        setupPlaylistListAdapter();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    void setupPlaylistListAdapter() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<PlaylistInfo> items = VideoDatabase.getDbInstance(PlaylistsActivity.this).
                        playlistInfoDao().getEnabled();
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (playlistList.getAdapter() != null) {
                            playlistList.getAdapter().unregisterAdapterDataObserver(emptyListObserver);
                        }

                        playlistList.setAdapter(new PlaylistInfoArrayAdapter(PlaylistsActivity.this, items,
                                new OnListItemClickListener<PlaylistInfo>() {
                                    @Override
                                    public void onItemClick(final View view, final int position, final PlaylistInfo item) {
                                        final Intent intent = new Intent(PlaylistsActivity.this, PlaylistActivity.class);
                                        intent.putExtra(PlaylistActivity.PARAM_PLAYLIST_ID, item.getId());
                                        startActivity(intent);
                                    }

                                    @Override
                                    public boolean onItemLongClick(final View view, final int position, final PlaylistInfo plInfo) {

                                        // параметр Gravity.CENTER не работает (и появился еще только в API 19+),
                                        // работает только вариант Gravity.RIGHT
                                        //final PopupMenu popup = new PopupMenu(ConfigurePlaylistsActivity.this, view, Gravity.CENTER);
                                        final PopupMenu popup = new PopupMenu(PlaylistsActivity.this,
                                                view.findViewById(R.id.playlist_name_txt));
                                        popup.getMenuInflater().inflate(R.menu.playlist_item_actions, popup.getMenu());
                                        popup.setOnMenuItemClickListener(
                                                new PopupMenu.OnMenuItemClickListener() {
                                                    @Override
                                                    public boolean onMenuItemClick(final MenuItem item) {
                                                        switch (item.getItemId()) {
                                                            case R.id.action_copy_playlist_name: {
                                                                final ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                                                                final ClipData clip = ClipData.newPlainText(plInfo.getName(), plInfo.getName());
                                                                clipboard.setPrimaryClip(clip);

                                                                Toast.makeText(PlaylistsActivity.this,
                                                                        getString(R.string.copied) + ": " + plInfo.getName(),
                                                                        Toast.LENGTH_LONG).show();
                                                                break;
                                                            }
                                                            case R.id.action_copy_playlist_url: {
                                                                final ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                                                                final ClipData clip = ClipData.newPlainText(plInfo.getUrl(), plInfo.getUrl());
                                                                clipboard.setPrimaryClip(clip);

                                                                Toast.makeText(PlaylistsActivity.this,
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
                                },null));
                        // если список пустой, показываем специальный экранчик с кнопками
                        playlistList.getAdapter().registerAdapterDataObserver(emptyListObserver);
                    }
                });

            }
        }).start();
    }
}
