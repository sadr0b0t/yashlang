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
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.paging.DataSource;
import androidx.paging.LivePagedListBuilder;
import androidx.paging.PagedList;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import su.sadrobot.yashlang.controller.VideoItemActions;
import su.sadrobot.yashlang.model.VideoDatabase;
import su.sadrobot.yashlang.model.VideoItem;
import su.sadrobot.yashlang.view.OnListItemClickListener;
import su.sadrobot.yashlang.view.VideoItemPagedListAdapter;

/**
 *
 */
public class SearchVideoActivity extends AppCompatActivity {

    private Toolbar toolbar;

    private EditText searchVideoInput;
    private RecyclerView videoList;

    private final Handler handler = new Handler();

    private LiveData<PagedList<VideoItem>> videoItemsLiveData;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_search_video);

        toolbar = findViewById(R.id.toolbar);

        searchVideoInput = findViewById(R.id.search_video_input);
        videoList = findViewById(R.id.video_list);

        // https://developer.android.com/training/appbar
        // https://www.vogella.com/tutorials/AndroidActionBar/article.html#custom-views-in-the-action-bar
        setSupportActionBar(toolbar);
        // кнопка "Назад" на акшенбаре
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // set a LinearLayoutManager with default vertical orientation
        final LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getApplicationContext());
        videoList.setLayoutManager(linearLayoutManager);
        videoList.setItemAnimator(new DefaultItemAnimator() {
            @Override
            public boolean canReuseUpdatedViewHolder(@NonNull RecyclerView.ViewHolder viewHolder) {
                // чтобы картинки и текст не сбивались в кучку при быстрой промотке
                // см: https://github.com/sadr0b0t/yashlang/issues/129
                return true;
            }
        });


        searchVideoInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(final TextView v, final int actionId, final KeyEvent event) {
                setupVideoListAdapter(v.getText().toString().trim());

                return false;
            }
        });

        searchVideoInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void afterTextChanged(Editable s) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                setupVideoListAdapter(s.toString().trim());
            }
        });

        // при пустой поисковой строке будет показывать все видео по алфавиту
        setupVideoListAdapter(null);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (ConfigOptions.getOfflineModeOn(this)) {
            getSupportActionBar().setTitle(getString(R.string.icon_offline) + " " +
                    getString(R.string.yashlang_search));
        }
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        // https://developer.android.com/training/appbar/action-views.html

        toolbar.inflateMenu(R.menu.search_video_actions);

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
            case R.id.action_play_results:
                if(videoList.getAdapter().getItemCount() > 0) {
                    VideoItemActions.actionPlayWithSearchResults(
                            SearchVideoActivity.this,
                            ((VideoItemPagedListAdapter) videoList.getAdapter()).getItem(0),
                            searchVideoInput.getText().toString().trim());
                } else {
                    Toast.makeText(this, R.string.nothing_to_play, Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.action_play_results_shuffle:
                if(videoList.getAdapter().getItemCount() > 0) {
                    VideoItemActions.actionPlayWithSearchResultsShuffle(
                            SearchVideoActivity.this,
                            ((VideoItemPagedListAdapter) videoList.getAdapter()).getItem(0),
                            searchVideoInput.getText().toString().trim());
                } else {
                    Toast.makeText(this, R.string.nothing_to_play, Toast.LENGTH_SHORT).show();
                }
                break;
        }

        return super.onOptionsItemSelected(item);
    }


    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void setupVideoListAdapter(final String sstr) {
        if (videoItemsLiveData != null) {
            videoItemsLiveData.removeObservers(this);
        }

        final VideoItemPagedListAdapter adapter = new VideoItemPagedListAdapter(this, new OnListItemClickListener<VideoItem>() {
            @Override
            public void onItemClick(final View view, final int position, final VideoItem videoItem) {
                VideoItemActions.actionPlay(SearchVideoActivity.this, videoItem);
            }

            @Override
            public boolean onItemLongClick(final View view, final int position, final VideoItem videoItem) {
                final PopupMenu popup = new PopupMenu(SearchVideoActivity.this,
                        view.findViewById(R.id.video_name_txt));
                popup.getMenuInflater().inflate(R.menu.video_item_actions, popup.getMenu());
                popup.setOnMenuItemClickListener(
                        new PopupMenu.OnMenuItemClickListener() {
                            @Override
                            public boolean onMenuItemClick(final MenuItem item) {
                                switch (item.getItemId()) {
                                    case R.id.action_play_in_playlist: {
                                        VideoItemActions.actionPlayInPlaylist(SearchVideoActivity.this, videoItem);
                                        break;
                                    }
                                    case R.id.action_play_in_playlist_shuffle: {
                                        VideoItemActions.actionPlayInPlaylistShuffle(SearchVideoActivity.this, videoItem);
                                        break;
                                    }
                                    case R.id.action_copy_video_name: {
                                        VideoItemActions.actionCopyVideoName(SearchVideoActivity.this, videoItem);
                                        break;
                                    }
                                    case R.id.action_copy_video_url: {
                                        VideoItemActions.actionCopyVideoUrl(SearchVideoActivity.this, videoItem);
                                        break;
                                    }
                                    case R.id.action_copy_playlist_name: {
                                        VideoItemActions.actionCopyPlaylistName(SearchVideoActivity.this, handler, videoItem);
                                        break;
                                    }
                                    case R.id.action_copy_playlist_url: {
                                        VideoItemActions.actionCopyPlaylistUrl(SearchVideoActivity.this, handler, videoItem);
                                        break;
                                    }
                                    case R.id.action_blacklist: {
                                        VideoItemActions.actionBlacklist(SearchVideoActivity.this, handler, videoItem.getId(), null);
                                        break;
                                    }
                                    case R.id.action_download_streams: {
                                        VideoItemActions.actionDownloadStreams(SearchVideoActivity.this, handler, videoItem, null);
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
        }, null);

        // Initial page size to fetch can also be configured here too
        final PagedList.Config config = new PagedList.Config.Builder().setPageSize(20).build();
        final DataSource.Factory factory;
        if (sstr != null && !sstr.isEmpty()) {
            factory = VideoDatabase.getDbInstance(this).videoItemPubListsDao().searchVideosDs(sstr);
        } else {
            factory = VideoDatabase.getDbInstance(this).videoItemPubListsDao().getAllDs();
        }
        videoItemsLiveData = new LivePagedListBuilder(factory, config).build();

        videoItemsLiveData.observe(this, new Observer<PagedList<VideoItem>>() {
            @Override
            public void onChanged(@Nullable PagedList<VideoItem> videos) {
                adapter.submitList(videos);
            }
        });

        videoList.setAdapter(adapter);
    }
}
