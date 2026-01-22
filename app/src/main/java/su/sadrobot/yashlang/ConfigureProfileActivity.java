package su.sadrobot.yashlang;

/*
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

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import su.sadrobot.yashlang.controller.PlaylistInfoActions;
import su.sadrobot.yashlang.model.PlaylistInfo;
import su.sadrobot.yashlang.model.Profile;
import su.sadrobot.yashlang.model.VideoDatabase;
import su.sadrobot.yashlang.view.ListItemSwitchController;
import su.sadrobot.yashlang.view.OnListItemClickListener;
import su.sadrobot.yashlang.view.PlaylistInfoPagedListAdapter;

/**
 *
 */
public class ConfigureProfileActivity extends AppCompatActivity {

    /**
     * Id профиля для редактирования.
     * Если не указан или указан Profile.ID_NONE, будет создан новый профиль.
     */
    public static final String PARAM_PROFILE_ID = "PARAM_PROFILE_ID";

    private Toolbar toolbar;

    private EditText profileNameTxt;

    // Экран с пустым списком
    private View emptyView;

    //
    private View actionsView;
    private EditText filterPlaylistListInput;
    private ImageButton sortBtn;
    private RecyclerView playlistList;

    private final Handler handler = new Handler();
    // достаточно одного фонового потока
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();

    private long profileId = Profile.ID_NONE;
    private Profile profile;
    private final Set<Long> checkedPlaylists = new HashSet<>();

    private boolean saveOnFinish = true;


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

        setContentView(R.layout.activity_configure_profile);

        toolbar = findViewById(R.id.toolbar);

        profileNameTxt = findViewById(R.id.profile_name_txt);
        emptyView = findViewById(R.id.empty_view);

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

        filterPlaylistListInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(final TextView v, final int actionId, final KeyEvent event) {
                setupPlaylistInfoPagedListAdapter(v.getText().toString().trim(),
                        ConfigOptions.getPlaylistsSortBy(ConfigureProfileActivity.this),
                        ConfigOptions.getPlaylistsSortDir(ConfigureProfileActivity.this));
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
                        ConfigOptions.getPlaylistsSortBy(ConfigureProfileActivity.this),
                        ConfigOptions.getPlaylistsSortDir(ConfigureProfileActivity.this));
            }
        });

        sortBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // параметр Gravity.CENTER не работает (и появился еще только в API 19+),
                // работает только вариант Gravity.RIGHT
                //final PopupMenu popup = new PopupMenu(ConfigurePlaylistsActivity.this, view, Gravity.CENTER);
                final PopupMenu popup = new PopupMenu(ConfigureProfileActivity.this,
                        view.findViewById(R.id.sort_btn));
                popup.getMenuInflater().inflate(R.menu.sort_playlists_actions, popup.getMenu());
                popup.setOnMenuItemClickListener(
                        new PopupMenu.OnMenuItemClickListener() {
                            @Override
                            public boolean onMenuItemClick(final MenuItem item) {
                                switch (item.getItemId()) {
                                    case R.id.action_sort_by_name_asc: {
                                        ConfigOptions.setPlaylistsSortBy(ConfigureProfileActivity.this,
                                                ConfigOptions.SortBy.NAME);
                                        ConfigOptions.setPlaylistsSortDir(ConfigureProfileActivity.this,
                                                true);
                                        setupPlaylistInfoPagedListAdapter(filterPlaylistListInput.getText().toString().trim(),
                                                ConfigOptions.getPlaylistsSortBy(ConfigureProfileActivity.this),
                                                ConfigOptions.getPlaylistsSortDir(ConfigureProfileActivity.this));
                                        break;
                                    }
                                    case R.id.action_sort_by_name_desc: {
                                        ConfigOptions.setPlaylistsSortBy(ConfigureProfileActivity.this,
                                                ConfigOptions.SortBy.NAME);
                                        ConfigOptions.setPlaylistsSortDir(ConfigureProfileActivity.this,
                                                false);
                                        setupPlaylistInfoPagedListAdapter(filterPlaylistListInput.getText().toString().trim(),
                                                ConfigOptions.getPlaylistsSortBy(ConfigureProfileActivity.this),
                                                ConfigOptions.getPlaylistsSortDir(ConfigureProfileActivity.this));
                                        break;
                                    }
                                    case R.id.action_sort_by_url_asc: {
                                        ConfigOptions.setPlaylistsSortBy(ConfigureProfileActivity.this,
                                                ConfigOptions.SortBy.URL);
                                        ConfigOptions.setPlaylistsSortDir(ConfigureProfileActivity.this,
                                                true);
                                        setupPlaylistInfoPagedListAdapter(filterPlaylistListInput.getText().toString().trim(),
                                                ConfigOptions.getPlaylistsSortBy(ConfigureProfileActivity.this),
                                                ConfigOptions.getPlaylistsSortDir(ConfigureProfileActivity.this));
                                        break;
                                    }
                                    case R.id.action_sort_by_url_desc: {
                                        ConfigOptions.setPlaylistsSortBy(ConfigureProfileActivity.this,
                                                ConfigOptions.SortBy.URL);
                                        ConfigOptions.setPlaylistsSortDir(ConfigureProfileActivity.this,
                                                false);
                                        setupPlaylistInfoPagedListAdapter(filterPlaylistListInput.getText().toString().trim(),
                                                ConfigOptions.getPlaylistsSortBy(ConfigureProfileActivity.this),
                                                ConfigOptions.getPlaylistsSortDir(ConfigureProfileActivity.this));
                                        break;
                                    }
                                    case R.id.action_sort_by_time_added_asc: {
                                        ConfigOptions.setPlaylistsSortBy(ConfigureProfileActivity.this,
                                                ConfigOptions.SortBy.TIME_ADDED);
                                        ConfigOptions.setPlaylistsSortDir(ConfigureProfileActivity.this,
                                                true);
                                        setupPlaylistInfoPagedListAdapter(filterPlaylistListInput.getText().toString().trim(),
                                                ConfigOptions.getPlaylistsSortBy(ConfigureProfileActivity.this),
                                                ConfigOptions.getPlaylistsSortDir(ConfigureProfileActivity.this));
                                        break;
                                    }
                                    case R.id.action_sort_by_time_added_desc: {
                                        ConfigOptions.setPlaylistsSortBy(ConfigureProfileActivity.this,
                                                ConfigOptions.SortBy.TIME_ADDED);
                                        ConfigOptions.setPlaylistsSortDir(ConfigureProfileActivity.this,
                                                false);
                                        setupPlaylistInfoPagedListAdapter(filterPlaylistListInput.getText().toString().trim(),
                                                ConfigOptions.getPlaylistsSortBy(ConfigureProfileActivity.this),
                                                ConfigOptions.getPlaylistsSortDir(ConfigureProfileActivity.this));
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

        profileId = getIntent().getLongExtra(PARAM_PROFILE_ID, Profile.ID_NONE);
        loadProfile();
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        // https://developer.android.com/training/appbar/action-views.html

        toolbar.inflateMenu(R.menu.configure_profile_actions);
        if(profileId == Profile.ID_NONE) {
            toolbar.getMenu().removeItem(R.id.action_delete);
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
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_select_all:
                dbExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        final List<Long> allPlaylists = VideoDatabase.getDbInstance(
                                ConfigureProfileActivity.this).playlistInfoDao().getAllIds();

                        checkedPlaylists.clear();
                        checkedPlaylists.addAll(allPlaylists);

                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                playlistList.getAdapter().notifyDataSetChanged();
                            }
                        });
                    }
                });

                break;
            case R.id.action_select_none:
                checkedPlaylists.clear();
                playlistList.getAdapter().notifyDataSetChanged();
                break;
            case R.id.action_apply_current:
                dbExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        final List<Long> allPlaylists = VideoDatabase.getDbInstance(
                                ConfigureProfileActivity.this).playlistInfoDao().getEnabledIds();

                        checkedPlaylists.clear();
                        checkedPlaylists.addAll(allPlaylists);

                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                playlistList.getAdapter().notifyDataSetChanged();
                            }
                        });
                    }
                });
                break;
            case R.id.action_cancel:
                saveOnFinish = false;
                ConfigureProfileActivity.this.finish();
                break;
            case R.id.action_delete:
                if(profileId != Profile.ID_NONE) {
                    new AlertDialog.Builder(ConfigureProfileActivity.this)
                            .setTitle(getString(R.string.delete_profile_title).replace("%s", profile.getName()))
                            .setMessage(getString(R.string.delete_profile_message))
                            .setIcon(android.R.drawable.ic_dialog_alert)
                            .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {

                                public void onClick(DialogInterface dialog, int whichButton) {

                                    dbExecutor.execute(new Runnable() {
                                        @Override
                                        public void run() {
                                            VideoDatabase.getDbInstance(ConfigureProfileActivity.this).profileDao().delete(profile);

                                            handler.post(new Runnable() {
                                                @Override
                                                public void run() {
                                                    Toast.makeText(
                                                            ConfigureProfileActivity.this,
                                                            getString(R.string.profile_is_deleted).replace("%s", profile.getName()),
                                                            Toast.LENGTH_LONG).show();

                                                    saveOnFinish = false;
                                                    ConfigureProfileActivity.this.finish();
                                                }
                                            });
                                        }
                                    });

                                }
                            })
                            .setNegativeButton(android.R.string.no, null).show();
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

    @Override
    public void onPause() {
        super.onPause();

        if(saveOnFinish) {
            saveProfile();
        }
    }

    private void updateControlsVisibility() {
        // считаем, что список пустой только если в поле фильтра ничего не введено
        final boolean listIsEmpty = filterPlaylistListInput.getText().length() == 0 &&
                (playlistList.getAdapter() == null || playlistList.getAdapter().getItemCount() == 0);
        if (listIsEmpty) {
            emptyView.setVisibility(View.VISIBLE);
            actionsView.setVisibility(View.GONE);
            playlistList.setVisibility(View.GONE);
        } else {
            emptyView.setVisibility(View.GONE);
            actionsView.setVisibility(View.VISIBLE);
            playlistList.setVisibility(View.VISIBLE);
        }
    }

    private void saveProfile() {
        dbExecutor.execute(new Runnable() {
            @Override
            public void run() {
                profile.setName(profileNameTxt.getText().toString());
                if (profileId == Profile.ID_NONE) {
                    profileId = VideoDatabase.getDbInstance(ConfigureProfileActivity.this).profileDao().insert(profile, checkedPlaylists);
                } else {
                    VideoDatabase.getDbInstance(ConfigureProfileActivity.this).profileDao().update(profile, checkedPlaylists);
                }

                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(
                                ConfigureProfileActivity.this,
                                getString(R.string.profile_is_saved).replace("%s", profile.getName()),
                                Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private void loadProfile() {
        // загрузка профиля из базы должна быть в фоне
        dbExecutor.execute(new Runnable() {
            @Override
            public void run() {
                if(profileId == Profile.ID_NONE) {
                    // профиля нет в базе данных
                    profile = new Profile(Profile.ID_NONE, ConfigureProfileActivity.this.getString(R.string.new_profile_name));
                    checkedPlaylists.clear();
                } else {
                    // профиль есть в базе данных
                    final VideoDatabase videodb = VideoDatabase.getDbInstance(ConfigureProfileActivity.this);
                    profile = videodb.profileDao().getById(profileId);
                    checkedPlaylists.clear();
                    checkedPlaylists.addAll(videodb.profileDao().getProfilePlaylistsIds(profileId));
                }

                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        profileNameTxt.setText(profile.getName());

                        setupPlaylistInfoPagedListAdapter(null,
                                ConfigOptions.getPlaylistsSortBy(ConfigureProfileActivity.this),
                                ConfigOptions.getPlaylistsSortDir(ConfigureProfileActivity.this));
                    }
                });
            }
        });
    }

    private void setupPlaylistInfoPagedListAdapter(final String sstr, final ConfigOptions.SortBy sortBy, final boolean sortDirAsc) {
        if (playlistInfosLiveData != null) {
            playlistInfosLiveData.removeObservers(this);
        }
        if (playlistList.getAdapter() != null) {
            playlistList.getAdapter().unregisterAdapterDataObserver(emptyListObserver);
        }

        final PlaylistInfoPagedListAdapter adapter = new PlaylistInfoPagedListAdapter(this,
                new OnListItemClickListener<PlaylistInfo>() {
                    @Override
                    public void onItemClick(final View view, final int position, final PlaylistInfo item) {
                    }

                    @Override
                    public boolean onItemLongClick(final View view, final int position, final PlaylistInfo plInfo) {

                        // параметр Gravity.CENTER не работает (и появился еще только в API 19+),
                        // работает только вариант Gravity.RIGHT
                        //final PopupMenu popup = new PopupMenu(ConfigurePlaylistsActivity.this, view, Gravity.CENTER);
                        final PopupMenu popup = new PopupMenu(ConfigureProfileActivity.this,
                                view.findViewById(R.id.playlist_name_txt));
                        popup.getMenuInflater().inflate(R.menu.playlist_item_actions, popup.getMenu());
                        popup.setOnMenuItemClickListener(
                                new PopupMenu.OnMenuItemClickListener() {
                                    @Override
                                    public boolean onMenuItemClick(final MenuItem item) {
                                        switch (item.getItemId()) {
                                            case R.id.action_copy_playlist_name: {
                                                PlaylistInfoActions.actionCopyPlaylistName(
                                                        ConfigureProfileActivity.this,
                                                        plInfo);
                                                break;
                                            }
                                            case R.id.action_copy_playlist_url: {
                                                PlaylistInfoActions.actionCopyPlaylistUrl(
                                                        ConfigureProfileActivity.this,
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
                },
                new ListItemSwitchController<PlaylistInfo>() {
                    @Override
                    public void onItemCheckedChanged(final CompoundButton buttonView, final int position, final PlaylistInfo item, final boolean isChecked) {
                        if (isChecked) {
                            checkedPlaylists.add(item.getId());
                        } else {
                            checkedPlaylists.remove(item.getId());
                        }
                    }

                    @Override
                    public boolean isItemChecked(final PlaylistInfo item) {
                        return checkedPlaylists.contains(item.getId());
                    }

                    @Override
                    public boolean showItemCheckbox(final PlaylistInfo item) {
                        return true;
                    }
                });
        // если список пустой, показываем специальный экранчик с кнопками
        adapter.registerAdapterDataObserver(emptyListObserver);

        // Initial page size to fetch can also be configured here too
        final PagedList.Config config = new PagedList.Config.Builder().setPageSize(ConfigOptions.PAGED_LIST_PAGE_SIZE).build();
        final DataSource.Factory factory;
        if (sstr != null && !sstr.isEmpty()) {
            if(sortBy == ConfigOptions.SortBy.NAME) {
                if (sortDirAsc) {
                    factory = VideoDatabase.getDbInstance(this).playlistInfoDao().searchAllSortByNameAscDs(sstr);
                } else {
                    factory = VideoDatabase.getDbInstance(this).playlistInfoDao().searchAllSortByNameDescDs(sstr);
                }
            } else if(sortBy == ConfigOptions.SortBy.URL) {
                if (sortDirAsc) {
                    factory = VideoDatabase.getDbInstance(this).playlistInfoDao().searchAllSortByUrlAscDs(sstr);
                } else {
                    factory = VideoDatabase.getDbInstance(this).playlistInfoDao().searchAllSortByUrlDescDs(sstr);
                }
            } else {
                if (sortDirAsc) {
                    factory = VideoDatabase.getDbInstance(this).playlistInfoDao().searchAllAscDs(sstr);
                } else {
                    factory = VideoDatabase.getDbInstance(this).playlistInfoDao().searchAllDescDs(sstr);
                }
            }
        } else {
            if(sortBy == ConfigOptions.SortBy.NAME) {
                if (sortDirAsc) {
                    factory = VideoDatabase.getDbInstance(this).playlistInfoDao().getAllSortByNameAscDs();
                } else {
                    factory = VideoDatabase.getDbInstance(this).playlistInfoDao().getAllSortByNameDescDs();
                }
            } else if(sortBy == ConfigOptions.SortBy.URL) {
                if (sortDirAsc) {
                    factory = VideoDatabase.getDbInstance(this).playlistInfoDao().getAllSortByUrlAscDs();
                } else {
                    factory = VideoDatabase.getDbInstance(this).playlistInfoDao().getAllSortByUrlDescDs();
                }
            } else {
                if (sortDirAsc) {
                    factory = VideoDatabase.getDbInstance(this).playlistInfoDao().getAllAscDs();
                } else {
                    factory = VideoDatabase.getDbInstance(this).playlistInfoDao().getAllDescDs();
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
