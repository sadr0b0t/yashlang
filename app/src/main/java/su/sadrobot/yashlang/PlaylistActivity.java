package su.sadrobot.yashlang;

/*
 * Created by Anton Moiseev (sadr0b0t) in 2019.
 *
 * Copyright (C) Anton Moiseev 2019 <github.com/sadr0b0t>
 * StarredActivity.java is part of YaShlang.
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

import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
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

import su.sadrobot.yashlang.controller.PlaylistInfoActions;
import su.sadrobot.yashlang.controller.VideoItemActions;
import su.sadrobot.yashlang.controller.VideoThumbManager;
import su.sadrobot.yashlang.model.PlaylistInfo;
import su.sadrobot.yashlang.model.VideoDatabase;
import su.sadrobot.yashlang.model.VideoItem;
import su.sadrobot.yashlang.util.PlaylistUrlUtil;
import su.sadrobot.yashlang.view.OnListItemClickListener;
import su.sadrobot.yashlang.view.VideoItemPagedListAdapter;

/**
 *
 */
public class PlaylistActivity extends AppCompatActivity {

    public static final String PARAM_PLAYLIST_ID = "PARAM_PLAYLIST_ID";

    private Toolbar toolbar;

    private ImageView playlistThumbImg;
    private TextView playlistNameTxt;
    private TextView playlistUrlTxt;
    private TextView playlistSizeTxt;

    // Экран с пустым списком
    private View emptyView;

    //
    private View actionsView;
    private EditText filterPlaylistInput;
    private ImageButton sortBtn;
    private RecyclerView videoList;

    private final Handler handler = new Handler();

    private LiveData<PagedList<VideoItem>> videoItemsLiveData;

    private long playlistId = PlaylistInfo.ID_NONE;
    private PlaylistInfo plInfo;

    private final RecyclerView.AdapterDataObserver emptyListObserver = new RecyclerView.AdapterDataObserver() {
        // https://stackoverflow.com/questions/47417645/empty-view-on-a-recyclerview
        // https://stackoverflow.com/questions/27414173/equivalent-of-listview-setemptyview-in-recyclerview
        // https://gist.github.com/sheharyarn/5602930ad84fa64c30a29ab18eb69c6e
        private void checkIfEmpty() {
            updateControlsVisibility();
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
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_playlist);

        toolbar = findViewById(R.id.toolbar);

        playlistThumbImg = findViewById(R.id.playlist_thumb_img);
        playlistNameTxt = findViewById(R.id.playlist_name_txt);
        playlistUrlTxt = findViewById(R.id.playlist_url_txt);
        playlistSizeTxt = findViewById(R.id.playlist_size_txt);

        emptyView = findViewById(R.id.empty_view);

        actionsView = findViewById(R.id.actions_view);
        filterPlaylistInput = findViewById(R.id.filter_playlist_input);
        sortBtn = findViewById(R.id.sort_btn);
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

        filterPlaylistInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(final TextView v, final int actionId, final KeyEvent event) {
                setupVideoListAdapter(playlistId, v.getText().toString().trim(),
                        ConfigOptions.getPlaylistSortBy(PlaylistActivity.this),
                        ConfigOptions.getPlaylistSortDir(PlaylistActivity.this));

                return false;
            }
        });

        filterPlaylistInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void afterTextChanged(Editable s) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                setupVideoListAdapter(playlistId, s.toString().trim(),
                        ConfigOptions.getPlaylistSortBy(PlaylistActivity.this),
                        ConfigOptions.getPlaylistSortDir(PlaylistActivity.this));
            }
        });


        sortBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // параметр Gravity.CENTER не работает (и появился еще только в API 19+),
                // работает только вариант Gravity.RIGHT
                //final PopupMenu popup = new PopupMenu(ConfigurePlaylistsActivity.this, view, Gravity.CENTER);
                final PopupMenu popup = new PopupMenu(PlaylistActivity.this,
                        view.findViewById(R.id.sort_btn));
                popup.getMenuInflater().inflate(R.menu.sort_playlist_actions, popup.getMenu());
                popup.setOnMenuItemClickListener(
                        new PopupMenu.OnMenuItemClickListener() {
                            @Override
                            public boolean onMenuItemClick(final MenuItem item) {
                                switch (item.getItemId()) {
                                    case R.id.action_sort_by_name_asc: {
                                        ConfigOptions.setPlaylistSortBy(PlaylistActivity.this,
                                                ConfigOptions.SortBy.NAME);
                                        ConfigOptions.setPlaylistSortDir(PlaylistActivity.this,
                                                true);
                                        setupVideoListAdapter(playlistId, filterPlaylistInput.getText().toString().trim(),
                                                ConfigOptions.getPlaylistSortBy(PlaylistActivity.this),
                                                ConfigOptions.getPlaylistSortDir(PlaylistActivity.this));
                                        break;
                                    }
                                    case R.id.action_sort_by_name_desc: {
                                        ConfigOptions.setPlaylistSortBy(PlaylistActivity.this,
                                                ConfigOptions.SortBy.NAME);
                                        ConfigOptions.setPlaylistSortDir(PlaylistActivity.this,
                                                false);
                                        setupVideoListAdapter(playlistId, filterPlaylistInput.getText().toString().trim(),
                                                ConfigOptions.getPlaylistSortBy(PlaylistActivity.this),
                                                ConfigOptions.getPlaylistSortDir(PlaylistActivity.this));
                                        break;
                                    }
                                    case R.id.action_sort_by_time_added_asc: {
                                        ConfigOptions.setPlaylistSortBy(PlaylistActivity.this,
                                                ConfigOptions.SortBy.TIME_ADDED);
                                        ConfigOptions.setPlaylistSortDir(PlaylistActivity.this,
                                                true);
                                        setupVideoListAdapter(playlistId, filterPlaylistInput.getText().toString().trim(),
                                                ConfigOptions.getPlaylistSortBy(PlaylistActivity.this),
                                                ConfigOptions.getPlaylistSortDir(PlaylistActivity.this));
                                        break;
                                    }
                                    case R.id.action_sort_by_time_added_desc: {
                                        ConfigOptions.setPlaylistSortBy(PlaylistActivity.this,
                                                ConfigOptions.SortBy.TIME_ADDED);
                                        ConfigOptions.setPlaylistSortDir(PlaylistActivity.this,
                                                false);
                                        setupVideoListAdapter(playlistId, filterPlaylistInput.getText().toString().trim(),
                                                ConfigOptions.getPlaylistSortBy(PlaylistActivity.this),
                                                ConfigOptions.getPlaylistSortDir(PlaylistActivity.this));
                                        break;
                                    }
                                    case R.id.action_sort_by_duration_asc: {
                                        ConfigOptions.setPlaylistSortBy(PlaylistActivity.this,
                                                ConfigOptions.SortBy.DURATION);
                                        ConfigOptions.setPlaylistSortDir(PlaylistActivity.this,
                                                true);
                                        setupVideoListAdapter(playlistId, filterPlaylistInput.getText().toString().trim(),
                                                ConfigOptions.getPlaylistSortBy(PlaylistActivity.this),
                                                ConfigOptions.getPlaylistSortDir(PlaylistActivity.this));
                                        break;
                                    }
                                    case R.id.action_sort_by_duration_desc: {
                                        ConfigOptions.setPlaylistSortBy(PlaylistActivity.this,
                                                ConfigOptions.SortBy.DURATION);
                                        ConfigOptions.setPlaylistSortDir(PlaylistActivity.this,
                                                false);
                                        setupVideoListAdapter(playlistId, filterPlaylistInput.getText().toString().trim(),
                                                ConfigOptions.getPlaylistSortBy(PlaylistActivity.this),
                                                ConfigOptions.getPlaylistSortDir(PlaylistActivity.this));
                                        break;
                                    }
                                }
                                return true;
                            }
                        }
                );
                popup.show();
            }
        });


        playlistId = getIntent().getLongExtra(PARAM_PLAYLIST_ID, PlaylistInfo.ID_NONE);

        // и здесь же загрузим список видео (если делать это в onResume,
        // то список будет каждый раз сбрасываться при потере фокуса активити)
        updateVideoListBg();
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        // https://developer.android.com/training/appbar/action-views.html

        // вообще, может быть правильнее не создавать меню до тех пор, пока plInfo
        // не загрузилась из базы (см ConfigurePlaylistActivity), но здесь это ок, т.к.
        // в тулбаре нет дополнительных виджетов (например, переключателя), которому
        // нужно подключать именно здесь слушателя.
        // Вместо этого перед каждым акшеном будем проверять plInfo на null
        toolbar.inflateMenu(R.menu.playlist_actions);

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
            case R.id.action_play_all:
                if(videoList.getAdapter().getItemCount() > 0) {
                    VideoItemActions.actionPlayInPlaylist(
                            PlaylistActivity.this,
                            ((VideoItemPagedListAdapter) videoList.getAdapter()).getItem(0),
                            filterPlaylistInput.getText().toString().trim(),
                            ConfigOptions.getPlaylistSortBy(PlaylistActivity.this),
                            ConfigOptions.getPlaylistSortDir(PlaylistActivity.this));
                } else {
                    Toast.makeText(this, R.string.nothing_to_play, Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.action_play_all_shuffle:
                if(videoList.getAdapter().getItemCount() > 0) {
                    VideoItemActions.actionPlayInPlaylistShuffle(
                            PlaylistActivity.this,
                            ((VideoItemPagedListAdapter) videoList.getAdapter()).getItem(0),
                            filterPlaylistInput.getText().toString().trim());
                } else {
                    Toast.makeText(this, R.string.nothing_to_play, Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.action_copy_playlist_name:
                if (plInfo != null) {
                    PlaylistInfoActions.actionCopyPlaylistName(PlaylistActivity.this, plInfo);
                }
                break;
            case R.id.action_copy_playlist_url:
                if (plInfo != null) {
                    PlaylistInfoActions.actionCopyPlaylistUrl(PlaylistActivity.this, plInfo);
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

    private void updateControlsVisibility() {
        // считаем, что список пустой только если в поле фильтра ничего не введено
        final boolean listIsEmpty = filterPlaylistInput.getText().length() == 0 &&
                (videoList.getAdapter() == null || videoList.getAdapter().getItemCount() == 0);
        if (listIsEmpty) {
            emptyView.setVisibility(View.VISIBLE);
            actionsView.setVisibility(View.GONE);
            videoList.setVisibility(View.GONE);
        } else {
            emptyView.setVisibility(View.GONE);
            actionsView.setVisibility(View.VISIBLE);
            videoList.setVisibility(View.VISIBLE);
        }
    }

    private void updateVideoListBg() {
        this.updateVideoListBg(playlistId);
    }

    /**
     * Update video list in background
     *
     * @param plId
     */
    private void updateVideoListBg(final long plId) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                // информация из базы данных - загрузится быстро и без интернета
                final VideoDatabase videodb = VideoDatabase.getDbInstance(PlaylistActivity.this);
                plInfo = VideoDatabase.getDbInstance(PlaylistActivity.this).playlistInfoDao().getById(plId);
                final int plVideosCount = videodb.videoItemDao().countVideos(plId);

                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        playlistNameTxt.setText(plInfo.getName());
                        playlistUrlTxt.setText(PlaylistUrlUtil.cleanupUrl(plInfo.getUrl()));
                        playlistSizeTxt.setText(" (" + plVideosCount + ")");
                    }
                });

                // иконка плейлиста - может грузиться подольше, без интернета вообще не загрузится
                try {
                    final Bitmap plThumb = VideoThumbManager.getInstance().loadPlaylistThumb(
                            PlaylistActivity.this, plInfo.getThumbUrl());
                    plInfo.setThumbBitmap(plThumb);

                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            playlistThumbImg.setImageBitmap(plInfo.getThumbBitmap());
                        }
                    });
                } catch (final Exception e) {
                }
            }
        }).start();

        setupVideoListAdapter(plId, filterPlaylistInput.getText().toString().trim(),
                ConfigOptions.getPlaylistSortBy(PlaylistActivity.this),
                ConfigOptions.getPlaylistSortDir(PlaylistActivity.this));
    }

    private void setupVideoListAdapter(final long plId, final String sstr, final ConfigOptions.SortBy sortBy, final boolean sortDirAsc) {
        if (videoItemsLiveData != null) {
            videoItemsLiveData.removeObservers(this);
        }
        if (videoList.getAdapter() != null) {
            videoList.getAdapter().unregisterAdapterDataObserver(emptyListObserver);
        }

        final VideoItemPagedListAdapter adapter = new VideoItemPagedListAdapter(this,
                new OnListItemClickListener<VideoItem>() {
                    @Override
                    public void onItemClick(final View view, final int position, final VideoItem videoItem) {
                        VideoItemActions.actionPlay(PlaylistActivity.this, videoItem);
                    }

                    @Override
                    public boolean onItemLongClick(final View view, final int position, final VideoItem videoItem) {
                        final PopupMenu popup = new PopupMenu(PlaylistActivity.this,
                                view.findViewById(R.id.video_name_txt));
                        popup.getMenuInflater().inflate(R.menu.video_item_actions, popup.getMenu());
                        popup.setOnMenuItemClickListener(
                                new PopupMenu.OnMenuItemClickListener() {
                                    @Override
                                    public boolean onMenuItemClick(final MenuItem item) {
                                        switch (item.getItemId()) {
                                            case R.id.action_play_in_playlist: {
                                                VideoItemActions.actionPlayInPlaylist(
                                                        PlaylistActivity.this, videoItem,
                                                        filterPlaylistInput.getText().toString().trim(),
                                                        ConfigOptions.getPlaylistSortBy(PlaylistActivity.this),
                                                        ConfigOptions.getPlaylistSortDir(PlaylistActivity.this));
                                                break;
                                            }
                                            case R.id.action_play_in_playlist_shuffle: {
                                                VideoItemActions.actionPlayInPlaylistShuffle(
                                                        PlaylistActivity.this, videoItem,
                                                        filterPlaylistInput.getText().toString().trim());
                                                break;
                                            }
                                            case R.id.action_copy_video_name: {
                                                VideoItemActions.actionCopyVideoName(PlaylistActivity.this, videoItem);
                                                break;
                                            }
                                            case R.id.action_copy_video_url: {
                                                VideoItemActions.actionCopyVideoUrl(PlaylistActivity.this, videoItem);
                                                break;
                                            }
                                            case R.id.action_copy_playlist_name: {
                                                VideoItemActions.actionCopyPlaylistName(PlaylistActivity.this, handler, videoItem);
                                                break;
                                            }
                                            case R.id.action_copy_playlist_url: {
                                                VideoItemActions.actionCopyPlaylistUrl(PlaylistActivity.this, handler, videoItem);
                                                break;
                                            }
                                            case R.id.action_blacklist: {
                                                VideoItemActions.actionBlacklist(PlaylistActivity.this, handler, videoItem, null);
                                                break;
                                            }
                                            case R.id.action_download_streams: {
                                                VideoItemActions.actionDownloadStreams(PlaylistActivity.this, handler, videoItem, null);
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
        // если список пустой, показываем специальный экранчик с сообщением
        adapter.registerAdapterDataObserver(emptyListObserver);

        // Initial page size to fetch can also be configured here too
        final PagedList.Config config = new PagedList.Config.Builder().setPageSize(20).build();

        final DataSource.Factory factory;
        if(sortBy == ConfigOptions.SortBy.NAME) {
            if (sortDirAsc) {
                factory = VideoDatabase.getDbInstance(
                        PlaylistActivity.this).videoItemDao().getByPlaylistSortByNameAscDs(plId, sstr);
            } else {
                factory = VideoDatabase.getDbInstance(
                        PlaylistActivity.this).videoItemDao().getByPlaylistSortByNameDescDs(plId, sstr);
            }
        } else if(sortBy == ConfigOptions.SortBy.DURATION) {
            if(sortDirAsc) {
                factory = VideoDatabase.getDbInstance(
                        PlaylistActivity.this).videoItemDao().getByPlaylistSortByDurationAscDs(plId, sstr);
            }else {
                factory = VideoDatabase.getDbInstance(
                        PlaylistActivity.this).videoItemDao().getByPlaylistSortByDurationDescDs(plId, sstr);
            }
        } else { // TIME_ADDED
            if(sortDirAsc) {
                factory = VideoDatabase.getDbInstance(
                        PlaylistActivity.this).videoItemDao().getByPlaylistSortByTimeAddedAscDs(plId, sstr);
            } else {
                factory = VideoDatabase.getDbInstance(
                        PlaylistActivity.this).videoItemDao().getByPlaylistSortByTimeAddedDescDs(plId, sstr);
            }
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
