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

import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Bundle;
import android.os.Handler;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.Toast;

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

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import su.sadrobot.yashlang.controller.VideoItemActions;
import su.sadrobot.yashlang.model.Profile;
import su.sadrobot.yashlang.model.VideoDatabase;
import su.sadrobot.yashlang.model.VideoItem;
import su.sadrobot.yashlang.util.NfcUtil;
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

    //
    private View statusNfcView;
    private View statusOfflineModeView;

    private LiveData<PagedList<VideoItem>> videoItemsLiveData;

    private final Handler handler = new Handler();
    // достаточно одного фонового потока
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();

    // NFC
    // https://developer.android.com/develop/connectivity/nfc/nfc#java
    // https://www.geeksforgeeks.org/android/nfc-reader-and-writer-kotlin-android-application/
    // https://github.com/marc136/tonuino-nfc-tools/tree/main
    // https://github.com/marc136/tonuino-nfc-tools/blob/main/app/src/main/java/de/mw136/tonuino/nfc/NfcIntentActivity.kt
    // https://github.com/marc136/tonuino-nfc-tools/blob/main/app/src/main/java/de/mw136/tonuino/nfc/TagHelper.kt
    private NfcAdapter nfcAdapter;
    private PendingIntent pendingIntent;
    private IntentFilter intentFiltersArray[];

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

        statusNfcView = findViewById(R.id.status_nfc_view);
        statusOfflineModeView = findViewById(R.id.status_offline_mode_view);

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

        // NFC
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (nfcAdapter != null) {
            // nfcAdapter здесь не нужен, но без nfcAdapter это делать незачем
            final Intent intent = new Intent(this, Glagna.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_MUTABLE);
            intentFiltersArray = NfcUtil.createIntentFilterArray();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (ConfigOptions.getOfflineModeOn(this)) {
            statusOfflineModeView.setVisibility(View.VISIBLE);
        } else {
            statusOfflineModeView.setVisibility(View.GONE);
        }

        if (nfcAdapter != null) {
            nfcAdapter.enableForegroundDispatch(this, pendingIntent, intentFiltersArray, NfcUtil.NFC_TECH_LISTS_ARRAY);
        }

        if (nfcAdapter != null && nfcAdapter.isEnabled()) {
            // проверить, есть ли в базе профили с привязанными метками nfc,
            // и только в этом случае показывать иконку
            dbExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    if (VideoDatabase.getDbInstance(Glagna.this).profileDao().getAllNfcTags().size() > 0) {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                statusNfcView.setVisibility(View.VISIBLE);
                            }
                        });
                    } else {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                statusNfcView.setVisibility(View.GONE);
                            }
                        });
                    }
                }
            });
        } else {
            statusNfcView.setVisibility(View.GONE);
        }

        setupVideoListAdapter();
    }

    @Override
    protected void onPause() {
        if (nfcAdapter != null) {
            nfcAdapter.disableForegroundDispatch(this);
        }
        super.onPause();
    }

    @Override
    protected void onNewIntent(final Intent intent) {
        super.onNewIntent(intent);
        // ожидаем здесь NFC Intent
        if (intent.getAction() == NfcAdapter.ACTION_NDEF_DISCOVERED || intent.getAction() == NfcAdapter.ACTION_TECH_DISCOVERED) {
            final Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            final String tagIdStr = NfcUtil.nfcTagIdToString(tag.getId());

            dbExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    final VideoDatabase videodb = VideoDatabase.getDbInstance(Glagna.this);
                    final long profileId = videodb.profileDao().getByNfcTagId(tagIdStr);

                    if (profileId > 0) {
                        // плейлисты для профиля
                        final List<Long> plIds = videodb.profileDao().getProfilePlaylistsIds(profileId);
                        videodb.playlistInfoDao().enableOnlyPlaylists(plIds);

                        // список рекомендаций обновится сам, т.к. интент вызывает onResume,
                        // а в onResume здесь обновляет список рекомендаций, даже если профиль
                        // не поменялся

                        final Profile profile = videodb.profileDao().getById(profileId);
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(
                                        Glagna.this,
                                        getString(R.string.applied_profile).replace("%s", profile.getName()),
                                        Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                }
            });
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
        final PagedList.Config config = new PagedList.Config.Builder().setPageSize(ConfigOptions.PAGED_LIST_PAGE_SIZE).build();

        final DataSource.Factory factory = VideoDatabase.getDbInstance(this).
                videoItemPubListsDao().recommendVideosDs();

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
