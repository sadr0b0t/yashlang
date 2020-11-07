package su.sadrobot.yashlang;

/*
 * Created by Anton Moiseev (sadr0b0t) in 2019.
 *
 * Copyright (C) Anton Moiseev 2019 <github.com/sadr0b0t>
 * AddPlaylistActivity.java is part of YaShlang.
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
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.paging.DataSource;
import androidx.paging.LivePagedListBuilder;
import androidx.paging.PagedList;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import su.sadrobot.yashlang.controller.ContentLoader;
import su.sadrobot.yashlang.controller.VideoThumbManager;
import su.sadrobot.yashlang.model.PlaylistInfo;
import su.sadrobot.yashlang.model.VideoItem;
import su.sadrobot.yashlang.util.PlaylistUrlUtil;
import su.sadrobot.yashlang.view.DataSourceListener;
import su.sadrobot.yashlang.view.OnListItemClickListener;
import su.sadrobot.yashlang.view.VideoItemOnlineDataSourceFactory;
import su.sadrobot.yashlang.view.VideoItemPagedListAdapter;

/**
 *
 */
public class AddPlaylistActivity extends AppCompatActivity {

    //
    // Панель плейлиста - ввод адреса плюс дочерние панели:
    //   информация о плейлисте список видео, статусы загрузки и т.п.
    private View playlistView;
    private EditText playlistUrlInput;
    private ImageButton searchPlaylistBtn;

    // Начальный экран с пустым списком
    private View playlistEmptyInitialView;
    private Button playlistSearch2Btn;

    // Прогресс загрузки плейлиста
    private View playlistLoadProgressView;

    // Ошибка загрузки плейлиста
    private View playlistLoadErrorView;
    private Button playlistSearch3Btn;
    private Button playlistReloadBtn;
    private TextView playlistLoadErrorTxt;

    // Загруженный плейлист
    private View playlistLoadedView;
    private ImageView playlistThumbImg;
    private TextView playlistNameTxt;
    private Button addPlaylistBtn;
    private RecyclerView videoList;

    //
    // Панель - прогресс добавления плейлиста
    private View playlistAddProgressView;
    private ImageView playlistAddPlThumbImg;
    private TextView playlistAddPlNameTxt;
    private TextView playlistAddPlUrlTxt;
    private TextView playlistAddStatusTxt;
    private ProgressBar playlistAddProgress;

    // Ошибка добавления плейлиста (внутри панели прогресса добавления плейлиста)
    private View playlistAddErrorView;
    private TextView playlistAddErrorTxt;
    private Button playlistAddRetryBtn;
    private Button playlistAddCancelBtn;

    private LiveData<PagedList<VideoItem>> videoItemsLiveData;

    private Handler handler = new Handler();

    private boolean playlistLoadError = false;

    private PlaylistInfo loadedPlaylist = null;

    private RecyclerView.AdapterDataObserver emptyListObserver = new RecyclerView.AdapterDataObserver() {
        // https://stackoverflow.com/questions/47417645/empty-view-on-a-recyclerview
        // https://stackoverflow.com/questions/27414173/equivalent-of-listview-setemptyview-in-recyclerview
        // https://gist.github.com/sheharyarn/5602930ad84fa64c30a29ab18eb69c6e
        private void checkIfEmpty() {
            // в любом случае прячем прогресс, если он был включен
            playlistEmptyInitialView.setVisibility(View.GONE);
            playlistLoadProgressView.setVisibility(View.GONE);
            if (playlistLoadError) {
                playlistLoadErrorView.setVisibility(View.VISIBLE);
                playlistLoadedView.setVisibility(View.GONE);
            } else {
                playlistLoadErrorView.setVisibility(View.GONE);
                playlistLoadedView.setVisibility(View.VISIBLE);
            }

            //final boolean listIsEmpty = videoList.getAdapter() == null || videoList.getAdapter().getItemCount() == 0;
            //addPlaylistBtn.setVisibility(listIsEmpty ? View.GONE : View.VISIBLE);
            //addPlaylistBtn.setEnabled(!listIsEmpty);
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

        setContentView(R.layout.activity_add_playlist);

        playlistView = findViewById(R.id.playlist_view);
        playlistUrlInput = findViewById(R.id.playlist_url_input);
        searchPlaylistBtn = findViewById(R.id.playlist_search_btn);

        playlistEmptyInitialView = findViewById(R.id.playlist_empty_initial_view);
        playlistSearch2Btn = findViewById(R.id.playlist_search2_btn);

        playlistLoadProgressView = findViewById(R.id.playlist_load_progress_view);

        playlistLoadErrorView = findViewById(R.id.playlist_load_error_view);
        playlistSearch3Btn = findViewById(R.id.playlist_search3_btn);
        playlistReloadBtn = findViewById(R.id.playlist_reload_btn);
        playlistLoadErrorTxt = findViewById(R.id.playlist_load_error_txt);

        playlistLoadedView = findViewById(R.id.playlist_loaded_view);
        playlistThumbImg = findViewById(R.id.playlist_thumb_img);
        playlistNameTxt = findViewById(R.id.playlist_name_txt);
        addPlaylistBtn = findViewById(R.id.playlist_add_btn);
        videoList = findViewById(R.id.video_list);

        playlistAddProgressView = findViewById(R.id.playlist_add_progress_view);
        playlistAddPlThumbImg = findViewById(R.id.playlist_add_pl_thumb_img);
        playlistAddPlNameTxt = findViewById(R.id.playlist_add_pl_name_txt);
        playlistAddPlUrlTxt = findViewById(R.id.playlist_add_pl_url_txt);
        playlistAddStatusTxt = findViewById(R.id.playlist_add_status_txt);
        playlistAddProgress = findViewById(R.id.playlist_add_progress);

        playlistAddErrorView = findViewById(R.id.playlist_add_error_view);
        playlistAddErrorTxt = findViewById(R.id.playlist_add_error_txt);
        playlistAddRetryBtn = findViewById(R.id.playlist_add_retry_btn);
        playlistAddCancelBtn = findViewById(R.id.playlist_add_cancel_btn);

        // кнопка "Назад" на акшенбаре
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        if(ConfigOptions.DEVEL_MODE_ON) {
            playlistUrlInput.setText("https://www.youtube.com/c/eralash");
        }

        // set a LinearLayoutManager with default vertical orientation
        final LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getApplicationContext());
        videoList.setLayoutManager(linearLayoutManager);

        playlistUrlInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(final TextView v, final int actionId, final KeyEvent event) {
                final String playlistUrl = playlistUrlInput.getText().toString();
                if(playlistUrl.length() > 0) {
                    updateVideoListBg(playlistUrl);
                }
                return false;
            }
        });
        playlistUrlInput.addTextChangedListener(new TextWatcher() {

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (count > 10) {
                    // пробовать загрузить плейлист если в поле ввода более 10 символов:
                    // - будем считать, что это вставка адреса из контекстного меню
                    // - даже если это не вставка, загрузка в процессе набора не повредит
                    // https://stackoverflow.com/questions/14980227/android-intercept-paste-copy-cut-on-edittext/
                    final String playlistUrl = playlistUrlInput.getText().toString();
                    updateVideoListBg(playlistUrl);
                }
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            public void afterTextChanged(Editable s) {

            }
        });

        searchPlaylistBtn.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivityForResult(new Intent(AddPlaylistActivity.this, SearchOnlinePlaylistActivity.class),
                        SearchOnlinePlaylistActivity.REQUEST_SEARCH_PLAYLIST);
            }
        });

        playlistSearch2Btn.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivityForResult(new Intent(AddPlaylistActivity.this, SearchOnlinePlaylistActivity.class),
                        SearchOnlinePlaylistActivity.REQUEST_SEARCH_PLAYLIST);
            }
        });

        playlistSearch3Btn.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivityForResult(new Intent(AddPlaylistActivity.this, SearchOnlinePlaylistActivity.class),
                        SearchOnlinePlaylistActivity.REQUEST_SEARCH_PLAYLIST);
            }
        });

        // перезагрузка в случае ошибки загрузки плейлиста
        playlistReloadBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playlistLoadErrorTxt.setText("");

                final String playlistUrl = playlistUrlInput.getText().toString();
                updateVideoListBg(playlistUrl);
            }
        });


        addPlaylistBtn.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                addLoadedPlaylistBg();
            }
        });

        playlistAddRetryBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addLoadedPlaylistBg();
            }
        });

        playlistAddCancelBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                // скрыть панель статуса добавления плейлиста
                playlistView.setVisibility(View.VISIBLE);
                playlistAddProgressView.setVisibility(View.GONE);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == SearchOnlinePlaylistActivity.REQUEST_SEARCH_PLAYLIST && data != null) {
            final String playlistUrl = data.getStringExtra(SearchOnlinePlaylistActivity.RESULT_PLAYLIST_URL);
            playlistUrlInput.setText(playlistUrl);
            updateVideoListBg(playlistUrl);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void addLoadedPlaylistBg() {
        // разрешено добавлять только после того, как о плейлисте загружена предварительная
        // информация, т.е. объект loadedPlaylist будет не null
        //final String playlistUrl = playlistUrlInput.getText().toString();
        playlistAddPlThumbImg.setImageBitmap(loadedPlaylist.getThumbBitmap());
        playlistAddPlNameTxt.setText(loadedPlaylist.getName());
        playlistAddPlUrlTxt.setText(PlaylistUrlUtil.cleanupUrl(loadedPlaylist.getUrl()));

        // обнулить состояние виджетов
        playlistAddStatusTxt.setText("");
        playlistAddProgress.setVisibility(View.GONE);
        playlistAddErrorView.setVisibility(View.GONE);
        playlistAddErrorTxt.setText("");

        // показать панель статуса добавления плейлиста
        playlistView.setVisibility(View.GONE);
        playlistAddProgressView.setVisibility(View.VISIBLE);

        // канал или плейлист
        final ContentLoader.TaskController taskController = new ContentLoader.TaskController();
        taskController.setTaskListener(new ContentLoader.TaskListener() {
            @Override
            public void onStart() {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        //addPlaylistBtn.setEnabled(false);
                        playlistAddProgress.setVisibility(View.VISIBLE);
                        playlistAddStatusTxt.setText(taskController.getStatusMsg());
                    }
                });
            }

            @Override
            public void onFinish() {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        //addPlaylistBtn.setEnabled(true);
                        playlistAddProgress.setVisibility(View.GONE);
                        playlistAddStatusTxt.setText(taskController.getStatusMsg());
                    }
                });
            }

            @Override
            public void onStatusChange(final String status, final Exception e) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        playlistAddStatusTxt.setText(status);
                        if (e != null) {
                            playlistAddErrorView.setVisibility(View.VISIBLE);
                            playlistAddErrorTxt.setText(e.getMessage()
                                    + (e.getCause() != null ? "\n(" + e.getCause().getMessage() + ")" : ""));
                        }
                    }
                });
            }
        });

        new Thread(new Runnable() {
            @Override
            public void run() {
                final long plId = ContentLoader.getInstance().addPlaylist(
                        AddPlaylistActivity.this, loadedPlaylist.getUrl(), taskController);

                if (plId != -1) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            // открываем активити со списком
                            final Intent intent = new Intent(AddPlaylistActivity.this, ViewPlaylistActivity.class);
                            intent.putExtra(ViewPlaylistActivity.PARAM_PLAYLIST_ID, plId);
                            startActivity(intent);

                            AddPlaylistActivity.this.finish();
                        }
                    });
                }
            }
        }).start();
    }

    private void updateVideoListBg(final String plUrl) {
        playlistEmptyInitialView.setVisibility(View.GONE);
        playlistLoadProgressView.setVisibility(View.VISIBLE);
        playlistLoadErrorView.setVisibility(View.GONE);
        playlistLoadedView.setVisibility(View.GONE);

        // значения по умолчанию
        playlistNameTxt.setText("");
        playlistThumbImg.setImageResource(R.drawable.ic_yashlang_thumb);
        playlistLoadErrorTxt.setText("");

        //
        loadedPlaylist = null;
        playlistLoadError = false;

        new Thread(new Runnable() {
            @Override
            public void run() {
                // информация о канале
                PlaylistInfo _plInfo = null;
                Bitmap _plThumb = null;
                String _errMsg = "";
                try {
                    _plInfo = ContentLoader.getInstance().getPlaylistInfo(plUrl);

                    // иконка канала
                    _plThumb = VideoThumbManager.getInstance().loadPlaylistThumb(
                            AddPlaylistActivity.this, _plInfo.getThumbUrl());
                } catch (final Exception e) {
                    playlistLoadError = true;
                    _errMsg = e.getMessage()
                            + (e.getCause() != null ? "\n(" + e.getCause().getMessage() + ")" : "");
                }

                if (!playlistLoadError) {
                    // здесь loadedPlaylist будет не null даже в том случае, если произойдет какая-то
                    // ошибка при загрузке списка видео с 1й страницы, хотя информация о загруженном
                    // плейлисте (и кнопка добавления плейлиста) появится только после того,
                    // как будет загружена и первая страница списка
                    loadedPlaylist = _plInfo;
                    loadedPlaylist.setThumbBitmap(_plThumb);
                }

                final String errMsg = _errMsg;
                // сохраним значения здесь, чтобы значения не повредились к тому моменту,
                // когда к ним обратимся в потоке handler
                final boolean _playlistLoadError = playlistLoadError;
                final PlaylistInfo _loadedPlaylist = loadedPlaylist;
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (!_playlistLoadError) {
                            // информация о канале (не будет видна до завершения загрузки первой
                            // страницы списка)
                            // На null не проверяем, т.к. достаточно проверки флага playlistLoadError
                            playlistNameTxt.setText(_loadedPlaylist.getName());
                            playlistThumbImg.setImageBitmap(_loadedPlaylist.getThumbBitmap());

                            // загрузить список видео
                            setupVideoListAdapter(plUrl);
                        } else {
                            // ошибочка вышла
                            playlistLoadErrorTxt.setText(errMsg);

                            playlistEmptyInitialView.setVisibility(View.GONE);
                            playlistLoadProgressView.setVisibility(View.GONE);
                            playlistLoadErrorView.setVisibility(View.VISIBLE);
                            playlistLoadedView.setVisibility(View.GONE);
                            playlistAddProgressView.setVisibility(View.GONE);
                        }
                    }
                });
            }
        }).start();
    }

    private void setupVideoListAdapter(final String plUrl) {
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
                        final Intent intent = new Intent(AddPlaylistActivity.this, WatchVideoActivity.class);
                        intent.putExtra(WatchVideoActivity.PARAM_VIDEO_ITEM_URL, videoItem.getItemUrl());
                        intent.putExtra(WatchVideoActivity.PARAM_RECOMMENDATIONS_OFF, true);
                        startActivity(intent);
                    }

                    @Override
                    public boolean onItemLongClick(final View view, final int position, final VideoItem videoItem) {
                        final PopupMenu popup = new PopupMenu(AddPlaylistActivity.this,
                                view.findViewById(R.id.video_name_txt));
                        popup.getMenuInflater().inflate(R.menu.video_actions, popup.getMenu());
                        popup.getMenu().removeItem(R.id.action_copy_playlist_name);
                        popup.getMenu().removeItem(R.id.action_copy_playlist_url);
                        popup.getMenu().removeItem(R.id.action_blacklist);
                        popup.setOnMenuItemClickListener(
                                new PopupMenu.OnMenuItemClickListener() {
                                    @Override
                                    public boolean onMenuItemClick(final MenuItem item) {
                                        switch (item.getItemId()) {
                                            case R.id.action_copy_video_name: {
                                                final ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                                                final ClipData clip = ClipData.newPlainText(videoItem.getName(), videoItem.getName());
                                                clipboard.setPrimaryClip(clip);

                                                Toast.makeText(AddPlaylistActivity.this,
                                                        getString(R.string.copied) + ": " + videoItem.getName(),
                                                        Toast.LENGTH_LONG).show();
                                                break;
                                            }
                                            case R.id.action_copy_video_url: {
                                                final String vidUrl = videoItem.getItemUrl();
                                                final ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                                                final ClipData clip = ClipData.newPlainText(vidUrl, vidUrl);
                                                clipboard.setPrimaryClip(clip);

                                                Toast.makeText(AddPlaylistActivity.this,
                                                        getString(R.string.copied) + ": " + vidUrl,
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
                }, null);
        // если список пустой, показываем специальный экранчик с кнопкой
        adapter.registerAdapterDataObserver(emptyListObserver);

        // Initial page size to fetch can also be configured here too
        final PagedList.Config config = new PagedList.Config.Builder().setPageSize(20).build();
        // Pass in dependency
        final DataSource.Factory factory =
                new VideoItemOnlineDataSourceFactory(plUrl, new DataSourceListener() {
                    @Override
                    public void onLoadInitialError(final Exception e) {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                playlistLoadError = true;
                                playlistLoadErrorTxt.setText(e.getMessage()
                                        + (e.getCause() != null ? "\n(" + e.getCause().getMessage() + ")" : ""));
                            }
                        });
                    }

                    @Override
                    public void onLoadBeforeError(final Exception e) {

                    }

                    @Override
                    public void onLoadAfterError(final Exception e) {
                        // TODO: здесь можно показывать снизу отдельную панельку с ошибкой подгрузки
                        // в список новых элементов при промотке вниз уже после того,
                        // как список загрузился
                    }
                });

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
