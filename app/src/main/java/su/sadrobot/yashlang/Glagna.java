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

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.paging.DataSource;
import androidx.paging.LivePagedListBuilder;
import androidx.paging.PagedList;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import su.sadrobot.yashlang.model.PlaylistInfo;
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
    private VideoDatabase videodb;

    private final Handler handler = new Handler();

    private RecyclerView.AdapterDataObserver emptyListObserver = new RecyclerView.AdapterDataObserver() {
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

        // подключимся к базе один раз при создании активити,
        // закрывать подключение в onDestroy
        videodb = VideoDatabase.getDb(Glagna.this);
    }


    @Override
    protected void onResume() {
        super.onResume();

        //
        setupVideoListAdapter();
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();

        if(videodb != null) {
            videodb.close();
        }
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
                final Intent intent = new Intent(Glagna.this, WatchVideoActivity.class);
                intent.putExtra(WatchVideoActivity.PARAM_VIDEO_ID, videoItem.getId());
                intent.putExtra(WatchVideoActivity.PARAM_RECOMMENDATIONS_MODE, WatchVideoActivity.RecommendationsMode.RANDOM);
                startActivity(intent);
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
                                        final Intent intent = new Intent(Glagna.this, WatchVideoActivity.class);
                                        intent.putExtra(WatchVideoActivity.PARAM_VIDEO_ID, videoItem.getId());
                                        intent.putExtra(WatchVideoActivity.PARAM_RECOMMENDATIONS_MODE, WatchVideoActivity.RecommendationsMode.PLAYLIST_ID);
                                        intent.putExtra(WatchVideoActivity.PARAM_PLAYLIST_ID, videoItem.getPlaylistId());
                                        startActivity(intent);
                                        break;
                                    }
                                    case R.id.action_play_in_playlist_shuffle: {
                                        final Intent intent = new Intent(Glagna.this, WatchVideoActivity.class);
                                        intent.putExtra(WatchVideoActivity.PARAM_VIDEO_ID, videoItem.getId());
                                        intent.putExtra(WatchVideoActivity.PARAM_RECOMMENDATIONS_MODE, WatchVideoActivity.RecommendationsMode.PLAYLIST_ID);
                                        intent.putExtra(WatchVideoActivity.PARAM_PLAYLIST_ID, videoItem.getPlaylistId());
                                        intent.putExtra(WatchVideoActivity.PARAM_SHUFFLE, true);
                                        startActivity(intent);
                                        break;
                                    }
                                    case R.id.action_copy_video_name: {
                                        final ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                                        final ClipData clip = ClipData.newPlainText(videoItem.getName(), videoItem.getName());
                                        clipboard.setPrimaryClip(clip);

                                        Toast.makeText(Glagna.this,
                                                getString(R.string.copied) + ": " + videoItem.getName(),
                                                Toast.LENGTH_LONG).show();
                                        break;
                                    }
                                    case R.id.action_copy_video_url: {
                                        final String vidUrl = videoItem.getItemUrl();
                                        final ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                                        final ClipData clip = ClipData.newPlainText(vidUrl, vidUrl);
                                        clipboard.setPrimaryClip(clip);

                                        Toast.makeText(Glagna.this,
                                                getString(R.string.copied) + ": " + vidUrl,
                                                Toast.LENGTH_LONG).show();
                                        break;
                                    }
                                    case R.id.action_copy_playlist_name:
                                        if (videoItem != null && videoItem.getPlaylistId() != PlaylistInfo.ID_NONE) {
                                            new Thread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    final PlaylistInfo plInfo = videodb.playlistInfoDao().getById(videoItem.getPlaylistId());
                                                    if(plInfo != null) {
                                                        handler.post(new Runnable() {
                                                            @Override
                                                            public void run() {
                                                                final ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                                                                final ClipData clip = ClipData.newPlainText(plInfo.getName(), plInfo.getName());
                                                                clipboard.setPrimaryClip(clip);

                                                                Toast.makeText(Glagna.this,
                                                                        getString(R.string.copied) + ": " + plInfo.getName(),
                                                                        Toast.LENGTH_LONG).show();
                                                            }
                                                        });
                                                    }
                                                }
                                            }).start();
                                        } else if(videoItem != null && videoItem.getPlaylistId() == PlaylistInfo.ID_NONE) {
                                            Toast.makeText(Glagna.this, getString(R.string.err_playlist_not_defined),
                                                    Toast.LENGTH_LONG).show();
                                        }
                                        break;
                                    case R.id.action_copy_playlist_url:
                                        if (videoItem != null && videoItem.getPlaylistId() != PlaylistInfo.ID_NONE) {
                                            new Thread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    final PlaylistInfo plInfo = videodb.playlistInfoDao().getById(videoItem.getPlaylistId());
                                                    if(plInfo != null) {
                                                        handler.post(new Runnable() {
                                                            @Override
                                                            public void run() {
                                                                final ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                                                                final ClipData clip = ClipData.newPlainText(plInfo.getUrl(), plInfo.getUrl());
                                                                clipboard.setPrimaryClip(clip);

                                                                Toast.makeText(Glagna.this,
                                                                        getString(R.string.copied) + ": " + plInfo.getUrl(),
                                                                        Toast.LENGTH_LONG).show();
                                                            }
                                                        });
                                                    }
                                                }
                                            }).start();
                                        } else if(videoItem != null && videoItem.getPlaylistId() == PlaylistInfo.ID_NONE) {
                                            Toast.makeText(Glagna.this, getString(R.string.err_playlist_not_defined),
                                                    Toast.LENGTH_LONG).show();
                                        }
                                        break;
                                    case R.id.action_blacklist:
                                        if (videoItem != null && videoItem.getId() != PlaylistInfo.ID_NONE) {
                                            new AlertDialog.Builder(Glagna.this)
                                                    .setTitle(getString(R.string.blacklist_video_title))
                                                    .setMessage(getString(R.string.blacklist_video_message))
                                                    .setIcon(android.R.drawable.ic_dialog_alert)
                                                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {

                                                        public void onClick(DialogInterface dialog, int whichButton) {
                                                            new Thread(new Runnable() {
                                                                @Override
                                                                public void run() {
                                                                    videodb.videoItemDao().setBlacklisted(videoItem.getId(), true);
                                                                    // обновим кэш
                                                                    videoItem.setBlacklisted(true);
                                                                    handler.post(new Runnable() {
                                                                        @Override
                                                                        public void run() {
                                                                            Toast.makeText(Glagna.this, getString(R.string.video_is_blacklisted),
                                                                                    Toast.LENGTH_LONG).show();
                                                                        }
                                                                    });
                                                                    // (на этом экране список рекомендаций обновится автоматом)
                                                                }
                                                            }).start();

                                                        }
                                                    })
                                                    .setNegativeButton(android.R.string.no, null).show();
                                        }
                                        break;
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

        final DataSource.Factory factory =
                videodb.videoItemDao().recommendVideosDs();

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
