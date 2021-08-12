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

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
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
import android.widget.ImageView;
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

    private EditText filterPlaylistInput;
    private View emptyView;
    private RecyclerView videoList;

    private Handler handler = new Handler();

    private LiveData<PagedList<VideoItem>> videoItemsLiveData;

    private long playlistId = PlaylistInfo.ID_NONE;
    private PlaylistInfo plInfo;

    private RecyclerView.AdapterDataObserver emptyListObserver = new RecyclerView.AdapterDataObserver() {
        // https://stackoverflow.com/questions/47417645/empty-view-on-a-recyclerview
        // https://stackoverflow.com/questions/27414173/equivalent-of-listview-setemptyview-in-recyclerview
        // https://gist.github.com/sheharyarn/5602930ad84fa64c30a29ab18eb69c6e
        private void checkIfEmpty() {
            // считаем, что плейлист пустой только если в поле фильтра ничего не введено
            final boolean listIsEmpty = filterPlaylistInput.getText().length() == 0 &&
                    (videoList.getAdapter() == null || videoList.getAdapter().getItemCount() == 0);
            emptyView.setVisibility(listIsEmpty ? View.VISIBLE : View.GONE);
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
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_playlist);

        toolbar = findViewById(R.id.toolbar);

        playlistThumbImg = findViewById(R.id.playlist_thumb_img);
        playlistNameTxt = findViewById(R.id.playlist_name_txt);
        playlistUrlTxt = findViewById(R.id.playlist_url_txt);
        playlistSizeTxt = findViewById(R.id.playlist_size_txt);

        filterPlaylistInput = findViewById(R.id.filter_playlist_input);
        emptyView = findViewById(R.id.empty_view);
        videoList = findViewById(R.id.video_list);

        // https://developer.android.com/training/appbar
        // https://www.vogella.com/tutorials/AndroidActionBar/article.html#custom-views-in-the-action-bar
        setSupportActionBar(toolbar);
        // кнопка "Назад" на акшенбаре
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // set a LinearLayoutManager with default vertical orientation
        final LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getApplicationContext());
        videoList.setLayoutManager(linearLayoutManager);

        filterPlaylistInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(final TextView v, final int actionId, final KeyEvent event) {
                setupVideoListAdapter(playlistId, v.getText().toString().trim());

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
                setupVideoListAdapter(playlistId, s.toString().trim());
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
                    final Intent intent = new Intent(PlaylistActivity.this, WatchVideoActivity.class);
                    intent.putExtra(WatchVideoActivity.PARAM_VIDEO_ID,
                            ((VideoItemPagedListAdapter)videoList.getAdapter()).getItem(0).getId());
                    intent.putExtra(WatchVideoActivity.PARAM_RECOMMENDATIONS_MODE, WatchVideoActivity.RecommendationsMode.PLAYLIST_ID);
                    intent.putExtra(WatchVideoActivity.PARAM_PLAYLIST_ID, playlistId);
                    startActivity(intent);
                } else {
                    Toast.makeText(this, R.string.nothing_to_play, Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.action_play_all_shuffle:
                if(videoList.getAdapter().getItemCount() > 0) {
                    final Intent intent = new Intent(PlaylistActivity.this, WatchVideoActivity.class);
                    intent.putExtra(WatchVideoActivity.PARAM_VIDEO_ID,
                            ((VideoItemPagedListAdapter)videoList.getAdapter()).getItem(0).getId());
                    intent.putExtra(WatchVideoActivity.PARAM_RECOMMENDATIONS_MODE, WatchVideoActivity.RecommendationsMode.PLAYLIST_ID);
                    intent.putExtra(WatchVideoActivity.PARAM_PLAYLIST_ID, playlistId);
                    intent.putExtra(WatchVideoActivity.PARAM_SHUFFLE, true);
                    startActivity(intent);
                } else {
                    Toast.makeText(this, R.string.nothing_to_play, Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.action_copy_playlist_name:
                if (plInfo != null) {
                    final ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    final ClipData clip = ClipData.newPlainText(plInfo.getName(), plInfo.getName());
                    clipboard.setPrimaryClip(clip);

                    Toast.makeText(PlaylistActivity.this,
                            getString(R.string.copied) + ": " + plInfo.getName(),
                            Toast.LENGTH_LONG).show();
                }
                break;
            case R.id.action_copy_playlist_url:
                if (plInfo != null) {
                    final ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    final ClipData clip = ClipData.newPlainText(plInfo.getUrl(), plInfo.getUrl());
                    clipboard.setPrimaryClip(clip);

                    Toast.makeText(PlaylistActivity.this,
                            getString(R.string.copied) + ": " + plInfo.getUrl(),
                            Toast.LENGTH_LONG).show();
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


        setupVideoListAdapter(plId, filterPlaylistInput.getText().toString().trim());
    }

    private void setupVideoListAdapter(final long plId, final String sstr) {
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
                        final Intent intent = new Intent(PlaylistActivity.this, WatchVideoActivity.class);
                        intent.putExtra(WatchVideoActivity.PARAM_VIDEO_ID, videoItem.getId());
                        startActivity(intent);
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
                                                final Intent intent = new Intent(PlaylistActivity.this, WatchVideoActivity.class);
                                                intent.putExtra(WatchVideoActivity.PARAM_VIDEO_ID, videoItem.getId());
                                                intent.putExtra(WatchVideoActivity.PARAM_RECOMMENDATIONS_MODE, WatchVideoActivity.RecommendationsMode.PLAYLIST_ID);
                                                intent.putExtra(WatchVideoActivity.PARAM_PLAYLIST_ID, videoItem.getPlaylistId());
                                                startActivity(intent);
                                                break;
                                            }
                                            case R.id.action_play_in_playlist_shuffle: {
                                                final Intent intent = new Intent(PlaylistActivity.this, WatchVideoActivity.class);
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

                                                Toast.makeText(PlaylistActivity.this,
                                                        getString(R.string.copied) + ": " + videoItem.getName(),
                                                        Toast.LENGTH_LONG).show();
                                                break;
                                            }
                                            case R.id.action_copy_video_url: {
                                                final String vidUrl = videoItem.getItemUrl();
                                                final ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                                                final ClipData clip = ClipData.newPlainText(vidUrl, vidUrl);
                                                clipboard.setPrimaryClip(clip);

                                                Toast.makeText(PlaylistActivity.this,
                                                        getString(R.string.copied) + ": " + vidUrl,
                                                        Toast.LENGTH_LONG).show();
                                                break;
                                            }
                                            case R.id.action_copy_playlist_name:
                                                if (videoItem != null && videoItem.getPlaylistId() != PlaylistInfo.ID_NONE) {
                                                    new Thread(new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            final PlaylistInfo plInfo = VideoDatabase.getDbInstance(
                                                                    PlaylistActivity.this).playlistInfoDao().getById(videoItem.getPlaylistId());
                                                            if(plInfo != null) {
                                                                handler.post(new Runnable() {
                                                                    @Override
                                                                    public void run() {
                                                                        final ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                                                                        final ClipData clip = ClipData.newPlainText(plInfo.getName(), plInfo.getName());
                                                                        clipboard.setPrimaryClip(clip);

                                                                        Toast.makeText(PlaylistActivity.this,
                                                                                getString(R.string.copied) + ": " + plInfo.getName(),
                                                                                Toast.LENGTH_LONG).show();
                                                                    }
                                                                });
                                                            }
                                                        }
                                                    }).start();
                                                } else if(videoItem != null && videoItem.getPlaylistId() == PlaylistInfo.ID_NONE) {
                                                    Toast.makeText(PlaylistActivity.this, getString(R.string.err_playlist_not_defined),
                                                            Toast.LENGTH_LONG).show();
                                                }
                                                break;
                                            case R.id.action_copy_playlist_url:
                                                if (videoItem != null && videoItem.getPlaylistId() != PlaylistInfo.ID_NONE) {
                                                    new Thread(new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            final PlaylistInfo plInfo = VideoDatabase.getDbInstance(
                                                                    PlaylistActivity.this).playlistInfoDao().getById(videoItem.getPlaylistId());
                                                            if(plInfo != null) {
                                                                handler.post(new Runnable() {
                                                                    @Override
                                                                    public void run() {
                                                                        final ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                                                                        final ClipData clip = ClipData.newPlainText(plInfo.getUrl(), plInfo.getUrl());
                                                                        clipboard.setPrimaryClip(clip);

                                                                        Toast.makeText(PlaylistActivity.this,
                                                                                getString(R.string.copied) + ": " + plInfo.getUrl(),
                                                                                Toast.LENGTH_LONG).show();
                                                                    }
                                                                });
                                                            }
                                                        }
                                                    }).start();
                                                } else if(videoItem != null && videoItem.getPlaylistId() == PlaylistInfo.ID_NONE) {
                                                    Toast.makeText(PlaylistActivity.this, getString(R.string.err_playlist_not_defined),
                                                            Toast.LENGTH_LONG).show();
                                                }
                                                break;
                                            case R.id.action_blacklist:
                                                if (videoItem != null && videoItem.getId() != VideoItem.ID_NONE) {
                                                    new AlertDialog.Builder(PlaylistActivity.this)
                                                            .setTitle(getString(R.string.blacklist_video_title))
                                                            .setMessage(getString(R.string.blacklist_video_message))
                                                            .setIcon(android.R.drawable.ic_dialog_alert)
                                                            .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {

                                                                public void onClick(DialogInterface dialog, int whichButton) {
                                                                    new Thread(new Runnable() {
                                                                        @Override
                                                                        public void run() {
                                                                            VideoDatabase.getDbInstance(
                                                                                    PlaylistActivity.this).videoItemDao().setBlacklisted(videoItem.getId(), true);
                                                                            // обновим кэш
                                                                            videoItem.setBlacklisted(true);
                                                                            handler.post(new Runnable() {
                                                                                @Override
                                                                                public void run() {
                                                                                    Toast.makeText(PlaylistActivity.this, getString(R.string.video_is_blacklisted),
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
        // если список пустой, показываем специальный экранчик с сообщением
        adapter.registerAdapterDataObserver(emptyListObserver);

        // Initial page size to fetch can also be configured here too
        final PagedList.Config config = new PagedList.Config.Builder().setPageSize(20).build();

        final DataSource.Factory factory = VideoDatabase.getDbInstance(
                PlaylistActivity.this).videoItemDao().getByPlaylistDs(plId, sstr);

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
