package su.sadrobot.yashlang;

/*
 * Created by Anton Moiseev (sadr0b0t) in 2019.
 *
 * Copyright (C) Anton Moiseev 2019 <github.com/sadr0b0t>
 * SearchVideoActivity.java is part of YaShlang.
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

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.paging.DataSource;
import androidx.paging.LivePagedListBuilder;
import androidx.paging.PagedList;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import su.sadrobot.yashlang.model.PlaylistInfo;
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

    private Handler handler = new Handler();

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


        searchVideoInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(final TextView v, final int actionId, final KeyEvent event) {
                setupVideoListAdapter(searchVideoInput.getText().toString());

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
                setupVideoListAdapter(s.toString());
            }
        });

        // при пустой поисковой строке будет показывать все видео по алфавиту
        setupVideoListAdapter(null);
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
                    final Intent intent = new Intent(SearchVideoActivity.this, WatchVideoActivity.class);
                    intent.putExtra(WatchVideoActivity.PARAM_VIDEO_ID,
                            ((VideoItemPagedListAdapter)videoList.getAdapter()).getItem(0).getId());
                    intent.putExtra(WatchVideoActivity.PARAM_RECOMMENDATIONS_MODE, WatchVideoActivity.RecommendationsMode.SEARCH_STR);
                    intent.putExtra(WatchVideoActivity.PARAM_SEARCH_STR, searchVideoInput.getText().toString());
                    startActivity(intent);
                } else {
                    Toast.makeText(this, R.string.nothing_to_play, Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.action_play_results_shuffle:
                if(videoList.getAdapter().getItemCount() > 0) {
                    final Intent intent = new Intent(SearchVideoActivity.this, WatchVideoActivity.class);
                    intent.putExtra(WatchVideoActivity.PARAM_VIDEO_ID,
                            ((VideoItemPagedListAdapter)videoList.getAdapter()).getItem(0).getId());
                    intent.putExtra(WatchVideoActivity.PARAM_RECOMMENDATIONS_MODE, WatchVideoActivity.RecommendationsMode.SEARCH_STR);
                    intent.putExtra(WatchVideoActivity.PARAM_SEARCH_STR, searchVideoInput.getText().toString());
                    intent.putExtra(WatchVideoActivity.PARAM_SHUFFLE, true);
                    startActivity(intent);
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
                final Intent intent = new Intent(SearchVideoActivity.this, WatchVideoActivity.class);
                intent.putExtra(WatchVideoActivity.PARAM_VIDEO_ID, videoItem.getId());
                startActivity(intent);
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
                                        final Intent intent = new Intent(SearchVideoActivity.this, WatchVideoActivity.class);
                                        intent.putExtra(WatchVideoActivity.PARAM_VIDEO_ID, videoItem.getId());
                                        intent.putExtra(WatchVideoActivity.PARAM_RECOMMENDATIONS_MODE, WatchVideoActivity.RecommendationsMode.PLAYLIST_ID);
                                        intent.putExtra(WatchVideoActivity.PARAM_PLAYLIST_ID, videoItem.getPlaylistId());
                                        startActivity(intent);
                                        break;
                                    }
                                    case R.id.action_play_in_playlist_shuffle: {
                                        final Intent intent = new Intent(SearchVideoActivity.this, WatchVideoActivity.class);
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

                                        Toast.makeText(SearchVideoActivity.this,
                                                getString(R.string.copied) + ": " + videoItem.getName(),
                                                Toast.LENGTH_LONG).show();
                                        break;
                                    }
                                    case R.id.action_copy_video_url: {
                                        final String vidUrl = videoItem.getItemUrl();
                                        final ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                                        final ClipData clip = ClipData.newPlainText(vidUrl, vidUrl);
                                        clipboard.setPrimaryClip(clip);

                                        Toast.makeText(SearchVideoActivity.this,
                                                getString(R.string.copied) + ": " + vidUrl,
                                                Toast.LENGTH_LONG).show();
                                        break;
                                    }
                                    case R.id.action_copy_playlist_name:
                                        if (videoItem != null && videoItem.getPlaylistId() != PlaylistInfo.ID_NONE) {
                                            new Thread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    final VideoDatabase videodb = VideoDatabase.getDb(SearchVideoActivity.this);
                                                    final PlaylistInfo plInfo = videodb.playlistInfoDao().getById(videoItem.getPlaylistId());
                                                    videodb.close();
                                                    if(plInfo != null) {
                                                        handler.post(new Runnable() {
                                                            @Override
                                                            public void run() {
                                                                final ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                                                                final ClipData clip = ClipData.newPlainText(plInfo.getName(), plInfo.getName());
                                                                clipboard.setPrimaryClip(clip);

                                                                Toast.makeText(SearchVideoActivity.this,
                                                                        getString(R.string.copied) + ": " + plInfo.getName(),
                                                                        Toast.LENGTH_LONG).show();
                                                            }
                                                        });
                                                    }
                                                }
                                            }).start();
                                        } else if(videoItem != null && videoItem.getPlaylistId() == PlaylistInfo.ID_NONE) {
                                            Toast.makeText(SearchVideoActivity.this, getString(R.string.err_playlist_not_defined),
                                                    Toast.LENGTH_LONG).show();
                                        }
                                        break;
                                    case R.id.action_copy_playlist_url:
                                        if (videoItem != null && videoItem.getPlaylistId() != PlaylistInfo.ID_NONE) {
                                            new Thread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    final VideoDatabase videodb = VideoDatabase.getDb(SearchVideoActivity.this);
                                                    final PlaylistInfo plInfo = videodb.playlistInfoDao().getById(videoItem.getPlaylistId());
                                                    videodb.close();
                                                    if(plInfo != null) {
                                                        handler.post(new Runnable() {
                                                            @Override
                                                            public void run() {
                                                                final ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                                                                final ClipData clip = ClipData.newPlainText(plInfo.getUrl(), plInfo.getUrl());
                                                                clipboard.setPrimaryClip(clip);

                                                                Toast.makeText(SearchVideoActivity.this,
                                                                        getString(R.string.copied) + ": " + plInfo.getUrl(),
                                                                        Toast.LENGTH_LONG).show();
                                                            }
                                                        });
                                                    }
                                                }
                                            }).start();
                                        } else if(videoItem != null && videoItem.getPlaylistId() == PlaylistInfo.ID_NONE) {
                                            Toast.makeText(SearchVideoActivity.this, getString(R.string.err_playlist_not_defined),
                                                    Toast.LENGTH_LONG).show();
                                        }
                                        break;
                                    case R.id.action_blacklist:
                                        if (videoItem != null && videoItem.getId() != VideoItem.ID_NONE) {
                                            new AlertDialog.Builder(SearchVideoActivity.this)
                                                    .setTitle(getString(R.string.blacklist_video_title))
                                                    .setMessage(getString(R.string.blacklist_video_message))
                                                    .setIcon(android.R.drawable.ic_dialog_alert)
                                                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {

                                                        public void onClick(DialogInterface dialog, int whichButton) {
                                                            new Thread(new Runnable() {
                                                                @Override
                                                                public void run() {
                                                                    final VideoDatabase videodb = VideoDatabase.getDb(SearchVideoActivity.this);
                                                                    videodb.videoItemDao().setBlacklisted(videoItem.getId(), true);
                                                                    videodb.close();
                                                                    // обновим кэш
                                                                    videoItem.setBlacklisted(true);
                                                                    handler.post(new Runnable() {
                                                                        @Override
                                                                        public void run() {
                                                                            Toast.makeText(SearchVideoActivity.this, getString(R.string.video_is_blacklisted),
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
        }, null);

        // Initial page size to fetch can also be configured here too
        final PagedList.Config config = new PagedList.Config.Builder().setPageSize(20).build();

        final DataSource.Factory factory;
        if (sstr != null && !sstr.isEmpty()) {
            final VideoDatabase videodb = VideoDatabase.getDb(SearchVideoActivity.this);
            factory = videodb.videoItemDao().searchVideosDs(sstr);
            videodb.close();
        } else {
            final VideoDatabase videodb = VideoDatabase.getDb(SearchVideoActivity.this);
            factory = videodb.videoItemDao().getAllEnabledDs();
            videodb.close();
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
