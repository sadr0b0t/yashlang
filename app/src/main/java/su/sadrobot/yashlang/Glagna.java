package su.sadrobot.yashlang;

/*
 * Created by Anton Moiseev (sadr0b0t) in 2019.
 *
 * Copyright (C) Anton Moiseev 2019 <github.com/sadr0b0t>
 * Glagna.java is part of YaShlang.
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
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.PopupMenu;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.paging.DataSource;
import androidx.paging.LivePagedListBuilder;
import androidx.paging.PagedList;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import su.sadrobot.yashlang.controller.VideoItemActions;
import su.sadrobot.yashlang.model.VideoDatabase;
import su.sadrobot.yashlang.model.VideoItem;
import su.sadrobot.yashlang.view.OnListItemClickListener;
import su.sadrobot.yashlang.view.VideoItemPagedListAdapter;


public class Glagna extends AppCompatActivity {


    private ImageButton starredBtn;
    private ImageButton historyBtn;
    private ImageButton playlistsBtn;
    private ImageButton searchBtn;
    private ImageButton configBtn;

    // Экран с пустым списком
    private View playlistEmptyView;
    private Button configurePlaylistsBtn;
    private Button addRecommendedBtn;

    //
    private RecyclerView videoList;

    private LiveData<PagedList<VideoItem>> videoItemsLiveData;

    private final Handler handler = new Handler();

    private final RecyclerView.AdapterDataObserver emptyListObserver = new RecyclerView.AdapterDataObserver() {
        // https://stackoverflow.com/questions/47417645/empty-view-on-a-recyclerview
        // https://stackoverflow.com/questions/27414173/equivalent-of-listview-setemptyview-in-recyclerview
        // https://gist.github.com/sheharyarn/5602930ad84fa64c30a29ab18eb69c6e
        private void checkIfEmpty() {
            final boolean listIsEmpty = videoList.getAdapter() == null || videoList.getAdapter().getItemCount() == 0;
            playlistEmptyView.setVisibility(listIsEmpty ? View.VISIBLE : View.GONE);
            videoList.setVisibility(listIsEmpty ? View.GONE : View.VISIBLE);
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

        setContentView(R.layout.activity_glagna);

        starredBtn = findViewById(R.id.starred_btn);
        historyBtn = findViewById(R.id.history_btn);
        playlistsBtn = findViewById(R.id.playlists_btn);
        searchBtn = findViewById(R.id.search_btn);
        configBtn = findViewById(R.id.config_btn);

        playlistEmptyView = findViewById(R.id.collection_empty_view);
        configurePlaylistsBtn = findViewById(R.id.configure_playlists_btn);
        addRecommendedBtn = findViewById(R.id.add_recommended_btn);

        videoList = findViewById(R.id.video_recommend_list);

        // Рекомендации
        final GridLayoutManager gridLayoutManager = new GridLayoutManager(
                getApplicationContext(), 2, GridLayoutManager.HORIZONTAL, false);
        videoList.setLayoutManager(gridLayoutManager);
        videoList.setItemAnimator(new DefaultItemAnimator() {
            @Override
            public boolean canReuseUpdatedViewHolder(@NonNull RecyclerView.ViewHolder viewHolder) {
                // чтобы картинки и текст не сбивались в кучку при быстрой промотке
                // см: https://github.com/sadr0b0t/yashlang/issues/129
                return true;
            }
        });

        //
        starredBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Glagna.this, StarredActivity.class));
            }
        });

        historyBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Glagna.this, HistoryActivity.class));
            }
        });
        playlistsBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Glagna.this, PlaylistsActivity.class));
            }
        });
        searchBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Glagna.this, SearchVideoActivity.class));
            }
        });
        configBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Glagna.this, ConfigurePlaylistsActivity.class));
            }
        });

        // from empty view
        configurePlaylistsBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Glagna.this, ConfigurePlaylistsActivity.class));
            }
        });
        addRecommendedBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Glagna.this, AddRecommendedPlaylistsActivity.class));
            }
        });


    }


    @Override
    protected void onResume() {
        super.onResume();

        setupVideoListAdapter();
    }

    private void setupVideoListAdapter() {
        if (videoItemsLiveData != null) {
            videoItemsLiveData.removeObservers(this);
        }

        if (videoList.getAdapter() != null) {
            videoList.getAdapter().unregisterAdapterDataObserver(emptyListObserver);
        }

        final VideoItemPagedListAdapter adapter = new VideoItemPagedListAdapter(
                this, new OnListItemClickListener<VideoItem>() {
            @Override
            public void onItemClick(final View view, final int position, final VideoItem videoItem) {
                VideoItemActions.actionPlay(Glagna.this, videoItem);
            }

            @Override
            public boolean onItemLongClick(final View view, final int position, final VideoItem videoItem) {
                final View anchor = view.findViewById(R.id.video_thumb_img);

                final PopupMenu popup = new PopupMenu(Glagna.this, anchor);
                popup.getMenuInflater().inflate(R.menu.video_item_actions, popup.getMenu());
                popup.setOnMenuItemClickListener(
                        new PopupMenu.OnMenuItemClickListener() {
                            @Override
                            public boolean onMenuItemClick(final MenuItem item) {
                                switch (item.getItemId()) {
                                    case R.id.action_play_in_playlist: {
                                        VideoItemActions.actionPlayInPlaylist(Glagna.this, videoItem);
                                        break;
                                    }
                                    case R.id.action_play_in_playlist_shuffle: {
                                        VideoItemActions.actionPlayInPlaylistShuffle(Glagna.this, videoItem);
                                        break;
                                    }
                                    case R.id.action_copy_video_name: {
                                        VideoItemActions.actionCopyVideoName(Glagna.this, videoItem);
                                        break;
                                    }
                                    case R.id.action_copy_video_url: {
                                        VideoItemActions.actionCopyVideoUrl(Glagna.this, videoItem);
                                        break;
                                    }
                                    case R.id.action_copy_playlist_name: {
                                        VideoItemActions.actionCopyPlaylistName(Glagna.this, handler, videoItem);
                                        break;
                                    }
                                    case R.id.action_copy_playlist_url: {
                                        VideoItemActions.actionCopyPlaylistUrl(Glagna.this, handler, videoItem);
                                        break;
                                    }
                                    case R.id.action_blacklist: {
                                        VideoItemActions.actionBlacklist(Glagna.this, handler, videoItem.getId(), null);
                                        break;
                                    }
                                    case R.id.action_download_streams: {
                                        VideoItemActions.actionDownloadStreams(Glagna.this, handler, videoItem, null);
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
        }, null, VideoItemPagedListAdapter.ORIENTATION_HORIZONTAL);

        // если список пустой, показываем специальный экранчик с кнопками
        adapter.registerAdapterDataObserver(emptyListObserver);

        // Initial page size to fetch can also be configured here too
        final PagedList.Config config = new PagedList.Config.Builder().setPageSize(20).build();

        final DataSource.Factory factory = VideoDatabase.getDbInstance(this).
                videoItemDao().recommendVideosDs();

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
