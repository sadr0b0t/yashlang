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
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;

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
import su.sadrobot.yashlang.model.PlaylistInfo;
import su.sadrobot.yashlang.model.VideoDatabase;
import su.sadrobot.yashlang.view.OnListItemClickListener;
import su.sadrobot.yashlang.view.PlaylistInfoPagedListAdapter;

/**
 *
 */
public class PlaylistsActivity extends AppCompatActivity {

    private Toolbar toolbar;

    // Экран с пустым списком
    private View emptyView;
    private Button addRecommendedBtn;


    //
    private View actionsView;
    private EditText filterPlaylistListInput;
    private ImageButton sortBtn;
    private RecyclerView playlistList;

    private LiveData<PagedList<PlaylistInfo>> playlistInfosLiveData;

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
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_playlists);

        toolbar = findViewById(R.id.toolbar);

        emptyView = findViewById(R.id.empty_view);
        addRecommendedBtn = findViewById(R.id.add_recommended_btn);

        actionsView = findViewById(R.id.actions_view);
        filterPlaylistListInput = findViewById(R.id.filter_playlist_list_input);
        sortBtn = findViewById(R.id.sort_btn);
        playlistList = findViewById(R.id.playlist_list);

        // https://developer.android.com/training/appbar
        // https://www.vogella.com/tutorials/AndroidActionBar/article.html#custom-views-in-the-action-bar
        setSupportActionBar(toolbar);
        // кнопка "Назад" на акшенбаре
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // set a LinearLayoutManager with default vertical orientation
        final LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getApplicationContext());
        playlistList.setLayoutManager(linearLayoutManager);
        playlistList.setItemAnimator(new DefaultItemAnimator() {
            @Override
            public boolean canReuseUpdatedViewHolder(@NonNull RecyclerView.ViewHolder viewHolder) {
                // чтобы картинки и текст не сбивались в кучку при быстрой промотке
                // см: https://github.com/sadr0b0t/yashlang/issues/129
                return true;
            }
        });

        addRecommendedBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(PlaylistsActivity.this, AddRecommendedPlaylistsActivity.class));
            }
        });

        filterPlaylistListInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(final TextView v, final int actionId, final KeyEvent event) {
                setupPlaylistInfoPagedListAdapter(v.getText().toString().trim(),
                        ConfigOptions.getPlaylistsSortBy(PlaylistsActivity.this),
                        ConfigOptions.getPlaylistsSortDir(PlaylistsActivity.this));
                return false;
            }
        });

        filterPlaylistListInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void afterTextChanged(Editable s) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                setupPlaylistInfoPagedListAdapter(s.toString().trim(),
                        ConfigOptions.getPlaylistsSortBy(PlaylistsActivity.this),
                        ConfigOptions.getPlaylistsSortDir(PlaylistsActivity.this));
            }
        });

        sortBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // параметр Gravity.CENTER не работает (и появился еще только в API 19+),
                // работает только вариант Gravity.RIGHT
                //final PopupMenu popup = new PopupMenu(ConfigurePlaylistsActivity.this, view, Gravity.CENTER);
                final PopupMenu popup = new PopupMenu(PlaylistsActivity.this,
                        view.findViewById(R.id.sort_btn));
                popup.getMenuInflater().inflate(R.menu.sort_playlists_actions, popup.getMenu());
                popup.setOnMenuItemClickListener(
                        new PopupMenu.OnMenuItemClickListener() {
                            @Override
                            public boolean onMenuItemClick(final MenuItem item) {
                                switch (item.getItemId()) {
                                    case R.id.action_sort_by_name_asc: {
                                        ConfigOptions.setPlaylistsSortBy(PlaylistsActivity.this,
                                                ConfigOptions.SortBy.NAME);
                                        ConfigOptions.setPlaylistsSortDir(PlaylistsActivity.this,
                                                true);
                                        setupPlaylistInfoPagedListAdapter(filterPlaylistListInput.getText().toString().trim(),
                                                ConfigOptions.getPlaylistsSortBy(PlaylistsActivity.this),
                                                ConfigOptions.getPlaylistsSortDir(PlaylistsActivity.this));
                                        break;
                                    }
                                    case R.id.action_sort_by_name_desc: {
                                        ConfigOptions.setPlaylistsSortBy(PlaylistsActivity.this,
                                                ConfigOptions.SortBy.NAME);
                                        ConfigOptions.setPlaylistsSortDir(PlaylistsActivity.this,
                                                false);
                                        setupPlaylistInfoPagedListAdapter(filterPlaylistListInput.getText().toString().trim(),
                                                ConfigOptions.getPlaylistsSortBy(PlaylistsActivity.this),
                                                ConfigOptions.getPlaylistsSortDir(PlaylistsActivity.this));
                                        break;
                                    }
                                    case R.id.action_sort_by_url_asc: {
                                        ConfigOptions.setPlaylistsSortBy(PlaylistsActivity.this,
                                                ConfigOptions.SortBy.URL);
                                        ConfigOptions.setPlaylistsSortDir(PlaylistsActivity.this,
                                                true);
                                        setupPlaylistInfoPagedListAdapter(filterPlaylistListInput.getText().toString().trim(),
                                                ConfigOptions.getPlaylistsSortBy(PlaylistsActivity.this),
                                                ConfigOptions.getPlaylistsSortDir(PlaylistsActivity.this));
                                        break;
                                    }
                                    case R.id.action_sort_by_url_desc: {
                                        ConfigOptions.setPlaylistsSortBy(PlaylistsActivity.this,
                                                ConfigOptions.SortBy.URL);
                                        ConfigOptions.setPlaylistsSortDir(PlaylistsActivity.this,
                                                false);
                                        setupPlaylistInfoPagedListAdapter(filterPlaylistListInput.getText().toString().trim(),
                                                ConfigOptions.getPlaylistsSortBy(PlaylistsActivity.this),
                                                ConfigOptions.getPlaylistsSortDir(PlaylistsActivity.this));
                                        break;
                                    }
                                    case R.id.action_sort_by_time_added_asc: {
                                        ConfigOptions.setPlaylistsSortBy(PlaylistsActivity.this,
                                                ConfigOptions.SortBy.TIME_ADDED);
                                        ConfigOptions.setPlaylistsSortDir(PlaylistsActivity.this,
                                                true);
                                        setupPlaylistInfoPagedListAdapter(filterPlaylistListInput.getText().toString().trim(),
                                                ConfigOptions.getPlaylistsSortBy(PlaylistsActivity.this),
                                                ConfigOptions.getPlaylistsSortDir(PlaylistsActivity.this));
                                        break;
                                    }
                                    case R.id.action_sort_by_time_added_desc: {
                                        ConfigOptions.setPlaylistsSortBy(PlaylistsActivity.this,
                                                ConfigOptions.SortBy.TIME_ADDED);
                                        ConfigOptions.setPlaylistsSortDir(PlaylistsActivity.this,
                                                false);
                                        setupPlaylistInfoPagedListAdapter(filterPlaylistListInput.getText().toString().trim(),
                                                ConfigOptions.getPlaylistsSortBy(PlaylistsActivity.this),
                                                ConfigOptions.getPlaylistsSortDir(PlaylistsActivity.this));
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
    }

    @Override
    protected void onResume() {
        super.onResume();

        setupPlaylistInfoPagedListAdapter(filterPlaylistListInput.getText().toString().trim(),
                ConfigOptions.getPlaylistsSortBy(PlaylistsActivity.this),
                ConfigOptions.getPlaylistsSortDir(PlaylistsActivity.this));
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void updateControlsVisibility() {
        // считаем, что список пустой только если в поле фильтра ничего не введено
        final boolean listIsEmpty = filterPlaylistListInput.getText().length() == 0 &&
                (playlistList.getAdapter() == null || playlistList.getAdapter().getItemCount() == 0);
        if(listIsEmpty) {
            emptyView.setVisibility(View.VISIBLE);
            actionsView.setVisibility(View.GONE);
            playlistList.setVisibility(View.GONE);
        } else {
            emptyView.setVisibility(View.GONE);
            actionsView.setVisibility(View.VISIBLE);
            playlistList.setVisibility(View.VISIBLE);
        }
    }

    private void setupPlaylistInfoPagedListAdapter(final String sstr, final ConfigOptions.SortBy sortBy, final boolean sortDirAsc) {
        if (playlistInfosLiveData != null) {
            playlistInfosLiveData.removeObservers(this);
        }
        if (playlistList.getAdapter() != null) {
            playlistList.getAdapter().unregisterAdapterDataObserver(emptyListObserver);
        }

        final PlaylistInfoPagedListAdapter adapter = new PlaylistInfoPagedListAdapter(PlaylistsActivity.this,
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
                                                PlaylistInfoActions.actionCopyPlaylistName(
                                                        PlaylistsActivity.this,
                                                        plInfo);
                                                break;
                                            }
                                            case R.id.action_copy_playlist_url: {
                                                PlaylistInfoActions.actionCopyPlaylistUrl(
                                                        PlaylistsActivity.this,
                                                        plInfo);
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
                },null);
        // если список пустой, показываем специальный экранчик с кнопками
        adapter.registerAdapterDataObserver(emptyListObserver);

        // Initial page size to fetch can also be configured here too
        final PagedList.Config config = new PagedList.Config.Builder().setPageSize(20).build();
        final DataSource.Factory factory;
        if (sstr != null && !sstr.isEmpty()) {
            if(sortBy == ConfigOptions.SortBy.NAME) {
                if (sortDirAsc) {
                    factory = VideoDatabase.getDbInstance(this).playlistInfoDao().searchEnabledSortByNameAscDs(sstr);
                } else {
                    factory = VideoDatabase.getDbInstance(this).playlistInfoDao().searchEnabledSortByNameDescDs(sstr);
                }
            } else if(sortBy == ConfigOptions.SortBy.URL) {
                if (sortDirAsc) {
                    factory = VideoDatabase.getDbInstance(this).playlistInfoDao().searchEnabledSortByUrlAscDs(sstr);
                } else {
                    factory = VideoDatabase.getDbInstance(this).playlistInfoDao().searchEnabledSortByUrlDescDs(sstr);
                }
            } else { // TIME_ADDED (unsorted)
                if (sortDirAsc) {
                    factory = VideoDatabase.getDbInstance(this).playlistInfoDao().searchEnabledAscDs(sstr);
                } else {
                    factory = VideoDatabase.getDbInstance(this).playlistInfoDao().searchEnabledDescDs(sstr);
                }
            }
        } else {
            if(sortBy == ConfigOptions.SortBy.NAME) {
                if (sortDirAsc) {
                    factory = VideoDatabase.getDbInstance(this).playlistInfoDao().getEnabledSortByNameAscDs();
                } else {
                    factory = VideoDatabase.getDbInstance(this).playlistInfoDao().getEnabledSortByNameDescDs();
                }
            } else if(sortBy == ConfigOptions.SortBy.URL) {
                if (sortDirAsc) {
                    factory = VideoDatabase.getDbInstance(this).playlistInfoDao().getEnabledSortByUrlAscDs();
                } else {
                    factory = VideoDatabase.getDbInstance(this).playlistInfoDao().getEnabledSortByUrlDescDs();
                }
            } else { // TIME_ADDED (unsorted)
                if (sortDirAsc) {
                    factory = VideoDatabase.getDbInstance(this).playlistInfoDao().getEnabledAscDs();
                } else {
                    factory = VideoDatabase.getDbInstance(this).playlistInfoDao().getEnabledDescDs();
                }
            }
        }

        playlistInfosLiveData = new LivePagedListBuilder(factory, config).build();

        playlistInfosLiveData.observe(this, new Observer<PagedList<PlaylistInfo>>() {
            @Override
            public void onChanged(@Nullable PagedList<PlaylistInfo> videos) {
                adapter.submitList(videos);
            }
        });

        playlistList.setAdapter(adapter);
    }
}
