package su.sadrobot.yashlang;

/*
 * Created by Anton Moiseev (sadr0b0t) in 2019.
 *
 * Copyright (C) Anton Moiseev 2019 <github.com/sadr0b0t>
 * WatchVideoActivity.java is part of YaShlang.
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
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.paging.DataSource;
import androidx.paging.LivePagedListBuilder;
import androidx.paging.PagedList;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.exoplayer2.DefaultControlDispatcher;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.ui.PlayerControlView;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;

import org.schabi.newpipe.extractor.exceptions.ExtractionException;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import su.sadrobot.yashlang.controller.ContentLoader;
import su.sadrobot.yashlang.model.PlaylistInfo;
import su.sadrobot.yashlang.model.VideoDatabase;
import su.sadrobot.yashlang.model.VideoItem;
import su.sadrobot.yashlang.util.PlaylistUrlUtil;
import su.sadrobot.yashlang.view.OnListItemClickListener;
import su.sadrobot.yashlang.view.VideoItemArrayAdapter;
import su.sadrobot.yashlang.view.VideoItemPagedListAdapter;


public class WatchVideoActivity extends AppCompatActivity {
    // https://github.com/google/ExoPlayer
    // https://exoplayer.dev/
    // https://exoplayer.dev/hello-world.html
    // https://exoplayer.dev/ui-components.html
    // https://androidwave.com/play-youtube-video-in-exoplayer/

    /**
     * Загрузить информацию о видео по ID из базы
     */
    public static final String PARAM_VIDEO_ID = "PARAM_VIDEO_ID";

    /**
     * Загрузить информацию о видео онлайн
     */
    public static final String PARAM_VIDEO_YTID = "PARAM_VIDEO_YTID";

    /**
     * Список рекомендаций - результат поиска по запросу
     */
    public static final String PARAM_SEARCH_STR = "PARAM_SEARCH_STR";

    /**
     * Список рекомендаций - плейлист
     */
    public static final String PARAM_PLAYLIST_ID = "PARAM_PLAYLIST_ID";

    /**
     * Список рекомендаций - пустой
     */
    public static final String PARAM_RECOMMENDATIONS_OFF = "PARAM_RECOMMENDATIONS_OFF";



    private PlayerView videoPlayerView;
    private PlayerControlView videoPlayerControlView;
    private ImageButton prevVideoBtn;
    private ImageButton nextVideoBtn;

    private View videoPlayerErrorView;
    private TextView videoLoadErrorTxt;
    private Button reloadOnErrorBtn;

    private RecyclerView videoList;

    private Toolbar toolbar;
    private CheckBox starredCheck;

    private com.google.android.exoplayer2.upstream.DataSource.Factory videoDataSourceFactory;

    private VideoItem currentVideo;
    // для функции перехода на следующее видео
    private int currentVideoPosition = -1;
    private Map<Long, Integer> posMap = new HashMap<Long, Integer>();

    private Stack<VideoItem> playbackHistory = new Stack<VideoItem>();

    private boolean stateFullscreen = false;

    private boolean stateVideoLoadError = false;
    private String videoLoadErrorMsg = "";

    // рекомендации
    private LiveData<PagedList<VideoItem>> videoItemsLiveData;
    private VideoDatabase videodb;

    private final Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_watch_video);

        videoPlayerView = findViewById(R.id.video_player_view);
        videoPlayerControlView = findViewById(R.id.video_player_control_view);
        prevVideoBtn = findViewById(R.id.prev_video_btn);
        nextVideoBtn = findViewById(R.id.next_video_btn);

        videoPlayerErrorView = findViewById(R.id.video_player_error_view);
        videoLoadErrorTxt = findViewById(R.id.video_load_error_txt);
        reloadOnErrorBtn = findViewById(R.id.reload_btn);

        videoList = findViewById(R.id.video_recommend_list);

        toolbar = findViewById(R.id.toolbar);

        // https://developer.android.com/training/appbar
        // https://www.vogella.com/tutorials/AndroidActionBar/article.html#custom-views-in-the-action-bar
        setSupportActionBar(toolbar);
        // кнопка "Назад" на акшенбаре
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        getSupportActionBar().addOnMenuVisibilityListener(new ActionBar.OnMenuVisibilityListener() {
            @Override
            public void onMenuVisibilityChanged(boolean isVisible) {
                // Прячем панель навигации, т.к. при выборе меню на акшенбаре она появляется опять.
                hideNavigationBar();
            }
        });

        ///

        prevVideoBtn.setEnabled(false);
        prevVideoBtn.setVisibility(View.INVISIBLE);

        prevVideoBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (playbackHistory.size() > 1) {
                    playbackHistory.pop();
                    playVideoItem(playbackHistory.pop(), false);

                    if (playbackHistory.size() <= 1) {
                        prevVideoBtn.setEnabled(false);
                        prevVideoBtn.setVisibility(View.INVISIBLE);
                    }
                }
            }
        });

        nextVideoBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // переходим на следующее видео по списку рекомендаций
                final int nextVideoPosition = currentVideoPosition >= videoList.getAdapter().getItemCount() - 1 ?
                        0 : currentVideoPosition + 1;
                final VideoItem item;
                if (videoList.getAdapter() instanceof VideoItemPagedListAdapter) {
                    // здесь не случайные рекомендации, а, например, список выдачи по поисковому запросу
                    item = ((VideoItemPagedListAdapter) videoList.getAdapter()).getItem(nextVideoPosition);
                } else if (videoList.getAdapter() instanceof VideoItemArrayAdapter) {
                    // здесь скорее всего случайные рекомендации
                    item = ((VideoItemArrayAdapter) videoList.getAdapter()).getItem(nextVideoPosition);
                } else {
                    item = null;
                }
                if (item != null) {
                    posMap.put(item.getId(), nextVideoPosition);
                    playVideoItem(item, false);
                }
            }
        });


        // Плеер
        final DefaultBandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();
        final TrackSelection.Factory videoTrackSelectionFactory = new AdaptiveTrackSelection.Factory(bandwidthMeter);
        final TrackSelector trackSelector = new DefaultTrackSelector(videoTrackSelectionFactory);
        final SimpleExoPlayer exoPlayer = ExoPlayerFactory.newSimpleInstance(this, trackSelector);

        videoDataSourceFactory = new DefaultDataSourceFactory(this,
                Util.getUserAgent(this, "yashlang"), bandwidthMeter);

        videoPlayerView.requestFocus();
        videoPlayerView.setPlayer(exoPlayer);

        // контроллер отдельно, чтобы красиво добавить справа и слева от плеера кнопки назад и вперед
        videoPlayerView.setUseController(false);
        videoPlayerControlView.setPlayer(exoPlayer);

        // не прятать кнопки управления автоматом
        //videoPlayerView.setControllerShowTimeoutMs(0);
        videoPlayerControlView.setShowTimeoutMs(0);

        // Будем прятать элементы управления в полноэкранном режиме при клике по плееру
        // и всегда показывать в режиме с уменьшенным экраном видео с кнопками управления
        // и списком рекомендаций.
        // (вообще, вот так тоже работает: videoPlayerView.setOnClickListener и на клик реагирует
        // не только область видео, но и вся область вокруг)
        videoPlayerView.getVideoSurfaceView().setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Прячем панель навигации, т.к. в некоторых ситуациях она все равно может появиться
                // (например, если должго задать кнопку выключения телефона и вызвать экран выключения),
                // хотя мы ее и так где только не выключаем и прячем.
                hideNavigationBar();

                toggleFullscreen();
            }
        });

        // клик по видео (см выше) пусть убирает меню и переключает фулскрин,
        // клик по области за пределами видео пусть просто убирает меню без переключения фулскрина
        findViewById(R.id.watch_content_view).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideNavigationBar();
            }
        });

        //videoPlayerView.setControlDispatcher(new DefaultControlDispatcher() {
        videoPlayerControlView.setControlDispatcher(new DefaultControlDispatcher() {
            @Override
            public boolean dispatchSetPlayWhenReady(final Player player, final boolean playWhenReady) {
                // https://stackoverflow.com/questions/47731779/detect-pause-resume-in-exoplayer
                // определить, что пользователь кникнул на паузу
                if (playWhenReady) {
                    // Play button clicked
                } else {
                    //System.out.println("#### PAUSED AT " + player.getCurrentPosition());
                    // Paused button clicked
                    saveVideoCurrPos();
                }
                return super.dispatchSetPlayWhenReady(player, playWhenReady);
            }

            @Override
            public boolean dispatchSeekTo(final Player player, final int windowIndex, final long positionMs) {
                saveVideoCurrPos();
                return super.dispatchSeekTo(player, windowIndex, positionMs);
            }
        });

        exoPlayer.addListener(new Player.EventListener() {
            @Override
            public void onTimelineChanged(Timeline timeline, Object manifest, int reason) {

            }

            @Override
            public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {

            }

            @Override
            public void onLoadingChanged(boolean isLoading) {

            }

            @Override
            public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
                if (playbackState == Player.STATE_ENDED) {
                    // ролик завершился - переходим к следующему
                    // TODO: сделайть экран с таймаутом секунд на 10, прогрессбаром и кнопкой
                    // перейти сейчас, отменить, играть заново текущий.

                    // переходим на следующее видео по списку рекомендаций
                    final int nextVideoPosition = currentVideoPosition >= videoList.getAdapter().getItemCount() - 1 ?
                            0 : currentVideoPosition + 1;
                    final VideoItem item;
                    if (videoList.getAdapter() instanceof VideoItemPagedListAdapter) {
                        // здесь не случайные рекомендации, а, например, список выдачи по поисковому запросу
                        item = ((VideoItemPagedListAdapter) videoList.getAdapter()).getItem(nextVideoPosition);
                    } else if (videoList.getAdapter() instanceof VideoItemArrayAdapter) {
                        // здесь скорее всего случайные рекомендации
                        item = ((VideoItemArrayAdapter) videoList.getAdapter()).getItem(nextVideoPosition);
                    } else {
                        item = null;
                    }
                    if (item != null) {
                        posMap.put(item.getId(), nextVideoPosition);
                        // перед загрузкой нового видео обнулим текущую позицию
                        playVideoItem(item, true);
                    }
                }
            }

            @Override
            public void onRepeatModeChanged(int repeatMode) {

            }

            @Override
            public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {

            }

            @Override
            public void onPlayerError(ExoPlaybackException error) {

            }

            @Override
            public void onPositionDiscontinuity(int reason) {

            }

            @Override
            public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {

            }

            @Override
            public void onSeekProcessed() {

            }
        });

        // Панель ошибки загрузки видео
        videoPlayerErrorView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideNavigationBar();
            }
        });

        reloadOnErrorBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                actionReload();
            }
        });


        // подключимся к базе один раз при создании активити,
        // закрывать подключение в onDestroy
        videodb = VideoDatabase.getDb(WatchVideoActivity.this);

        // Рекомендации
        // set a LinearLayoutManager with default vertical orientation
        final LinearLayoutManager linearLayoutManager = new LinearLayoutManager(
                getApplicationContext(), RecyclerView.HORIZONTAL, false);
        videoList.setLayoutManager(linearLayoutManager);


        final boolean recommendationsOff = super.getIntent().getBooleanExtra(PARAM_RECOMMENDATIONS_OFF, false);
        if(recommendationsOff) {
            videoList.setVisibility(View.GONE);
        } else {
            final String searchStr = super.getIntent().getStringExtra(PARAM_SEARCH_STR);
            final long playlistId = super.getIntent().getLongExtra(PARAM_PLAYLIST_ID, -1);
            if (searchStr != null) {
                // будем считать, что в случае с передачей поисковой строки нам передают для
                // проигрывания первый элемент из поисковой выдачи, поэтому, чтобы кнопка
                // "следующее видео" не повторяла первый ролик два раза, начнем считать индекс
                // текущего ролика сразу с 0-ля (т.е. первый элемент списка рекомендаций)
                // (но, чтобы это сработало, нужно еще ниже положить:
                // posMap.put(videoItem.getId(), currentVideoPosition))
                currentVideoPosition = 0;
                setupVideoListPagedListAdapter(searchStr);
            } else if (playlistId != -1) {
                setupVideoListPagedListAdapter(playlistId);
            } else {
                setupVideoListArrayAdapter();
            }
        }

        // загружаем видео
        // если передан параметр videoId, то загружаем видео по id из базы, если videoId
        // нет, но есть videoYtId, то используем его
        final long videoId = super.getIntent().getLongExtra(PARAM_VIDEO_ID, -1);
        final String videoYtId = super.getIntent().getStringExtra(PARAM_VIDEO_YTID);
        if (videoId != -1) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    final VideoItem videoItem = videodb.videoItemDao().getById(videoId);
                    posMap.put(videoItem.getId(), currentVideoPosition);

                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            playVideoItem(videoItem, false);
                        }
                    });
                }
            }).start();
        } else if (videoYtId != null) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    // грузим информацию из онлайна
                    VideoItem _videoItem;
                    try {
                        _videoItem = ContentLoader.getInstance().getYtVideoItem(videoYtId);
                    } catch (ExtractionException | IOException e) {
                        _videoItem = null;
                        //e.printStackTrace();
                    }

                    final VideoItem videoItem = _videoItem;
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            playVideoItem(videoItem, false);
                        }
                    });
                }
            }).start();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // Экран повернули - нужно загрузить новый лэйаут. Придется заново выставить
        // ссылки на элементы интерфейса, но постараемся по максимуму перетащить
        // их состояния и содержимое без перезагрузки.

        // Плеер можно перекинуть прямо во время проигрывания
        final Player exoPlayer = videoPlayerView.getPlayer();
        // Адаптер с рекомендациями тоже получится перекинуть
        final RecyclerView.Adapter videoListAdapter = videoList.getAdapter();

        // Новый лэйаут
        setContentView(R.layout.activity_watch_video);

        videoPlayerView = findViewById(R.id.video_player_view);
        videoPlayerControlView = findViewById(R.id.video_player_control_view);
        prevVideoBtn = findViewById(R.id.prev_video_btn);
        nextVideoBtn = findViewById(R.id.next_video_btn);

        videoPlayerErrorView = findViewById(R.id.video_player_error_view);
        videoLoadErrorTxt = findViewById(R.id.video_load_error_txt);
        reloadOnErrorBtn = findViewById(R.id.reload_btn);

        videoList = findViewById(R.id.video_recommend_list);

        toolbar = findViewById(R.id.toolbar);

        // https://developer.android.com/training/appbar
        // https://www.vogella.com/tutorials/AndroidActionBar/article.html#custom-views-in-the-action-bar
        setSupportActionBar(toolbar);
        // кнопка "Назад" на акшенбаре
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        getSupportActionBar().addOnMenuVisibilityListener(new ActionBar.OnMenuVisibilityListener() {
            @Override
            public void onMenuVisibilityChanged(boolean isVisible) {
                // Прячем панель навигации, т.к. при выборе меню на акшенбаре она появляется опять.
                hideNavigationBar();
            }
        });

        prevVideoBtn.setEnabled(playbackHistory.size() > 1);
        prevVideoBtn.setVisibility(playbackHistory.size() > 1 ? View.VISIBLE : View.INVISIBLE);

        prevVideoBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (playbackHistory.size() > 1) {
                    playbackHistory.pop();
                    // здесь снимаем ролик с вершины, но он там снова сразу окажется в playVideoItem
                    playVideoItem(playbackHistory.pop(), false);

                    if (playbackHistory.size() <= 1) {
                        prevVideoBtn.setEnabled(false);
                        prevVideoBtn.setVisibility(View.INVISIBLE);
                    }
                }
            }
        });

        nextVideoBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // переходим на следующее видео по списку рекомендаций
                final int nextVideoPosition = currentVideoPosition >= videoList.getAdapter().getItemCount() - 1 ?
                        0 : currentVideoPosition + 1;
                final VideoItem item;
                if (videoList.getAdapter() instanceof VideoItemPagedListAdapter) {
                    // здесь не случайные рекомендации, а, например, список выдачи по поисковому запросу
                    item = ((VideoItemPagedListAdapter) videoList.getAdapter()).getItem(nextVideoPosition);
                } else if (videoList.getAdapter() instanceof VideoItemArrayAdapter) {
                    // здесь скорее всего случайные рекомендации
                    item = ((VideoItemArrayAdapter) videoList.getAdapter()).getItem(nextVideoPosition);
                } else {
                    item = null;
                }
                if (item != null) {
                    posMap.put(item.getId(), nextVideoPosition);
                    playVideoItem(item, false);
                }
            }
        });

        // Плеер
        videoPlayerView.requestFocus();
        videoPlayerView.setPlayer(exoPlayer);

        // контроллер отдельно, чтобы красиво добавить справа и слева от плеера кнопки назад и вперед
        videoPlayerView.setUseController(false);
        videoPlayerControlView.setPlayer(exoPlayer);

        // не прятать кнопки управления автоматом
        //videoPlayerView.setControllerShowTimeoutMs(0);
        videoPlayerControlView.setShowTimeoutMs(0);

        // Будем прятать элементы управления в полноэкранном режиме при клике по плееру
        // и всегда показывать в режиме с уменьшенным экраном видео с кнопками управления
        // и списком рекомендаций.
        // (вообще, вот так тоже работает: videoPlayerView.setOnClickListener и на клик реагирует
        // не только область видео, но и вся область вокруг)
        videoPlayerView.getVideoSurfaceView().setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Прячем панель навигации, т.к. в некоторых ситуациях она все равно может появиться
                // (например, если должго задать кнопку выключения телефона и вызвать экран выключения),
                // хотя мы ее и так где только не выключаем и прячем.
                hideNavigationBar();

                toggleFullscreen();
            }
        });

        // клик по видео (см выше) пусть убирает меню и переключает фулскрин,
        // клик по области за пределами видео пусть просто убирает меню без переключения фулскрина
        findViewById(R.id.watch_content_view).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideNavigationBar();
            }
        });

        //videoPlayerView.setControlDispatcher(new DefaultControlDispatcher() {
        videoPlayerControlView.setControlDispatcher(new DefaultControlDispatcher() {
            @Override
            public boolean dispatchSetPlayWhenReady(final Player player, final boolean playWhenReady) {
                // https://stackoverflow.com/questions/47731779/detect-pause-resume-in-exoplayer
                // определить, что пользователь кникнул на паузу
                if (playWhenReady) {
                    // Play button clicked
                } else {
                    //System.out.println("#### PAUSED AT " + player.getCurrentPosition());
                    // Paused button clicked
                    saveVideoCurrPos();
                }
                return super.dispatchSetPlayWhenReady(player, playWhenReady);
            }

            @Override
            public boolean dispatchSeekTo(final Player player, final int windowIndex, final long positionMs) {
                saveVideoCurrPos();
                return super.dispatchSeekTo(player, windowIndex, positionMs);
            }
        });

        // Панель ошибки загрузки видео
        videoPlayerErrorView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideNavigationBar();
            }
        });

        reloadOnErrorBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                actionReload();
            }
        });

        // Рекомендации
        // set a LinearLayoutManager with default vertical orientation
        final LinearLayoutManager linearLayoutManager = new LinearLayoutManager(
                getApplicationContext(), RecyclerView.HORIZONTAL, false);
        videoList.setLayoutManager(linearLayoutManager);
        videoList.setAdapter(videoListAdapter);

        // Режим полного или неполного экрана
        setFullscreen(stateFullscreen);

        // показать информацию о ролике
        if (currentVideo != null) {
            getSupportActionBar().setTitle(currentVideo.getName());
            getSupportActionBar().setSubtitle(currentVideo.getUploader());
        }

        // видео загружено или ошибка
        setLoadError(stateVideoLoadError, videoLoadErrorMsg);
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        // https://developer.android.com/training/appbar/action-views.html

        toolbar.inflateMenu(R.menu.watch_video_actions);
        starredCheck = (CheckBox) toolbar.getMenu().findItem(R.id.action_star).getActionView();
        starredCheck.setButtonDrawable(android.R.drawable.btn_star);

        // Текущее значение и клик-листенер для starredCheck задается внутри playVideoItem.
        // Но при загрузке аквити у нас получается так, что currentVideo появляется раньше в onCreate,
        // чем создается меню здесь, поэтому playVideoItem вызывается раньше, чем появляется starredCheck,
        // поэтому там клик-лисенер для starredCheck не назначается, нужно назначить его здесь.
        if (currentVideo != null) {
            // еще так же делаем в playVideoItem
            starredCheck.setChecked(currentVideo.isStarred());
            starredCheck.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(final CompoundButton buttonView, final boolean isChecked) {
                    // сохраним переменные здесь, чтобы потом спокойно их использовать внутри потока
                    // и не бояться, что текущее видео будет переключено до того, как состояние сохранится
                    final VideoItem _currentVideo = currentVideo;
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            if (_currentVideo != null) {
                                videodb.videoItemDao().setStarred(_currentVideo.getId(), isChecked);
                                // обновим кэш
                                _currentVideo.setStarred(isChecked);
                            }
                        }
                    }).start();
                }
            });
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
    protected void onPause() {
        super.onPause();

        videoPlayerView.getPlayer().setPlayWhenReady(false);
        saveVideoCurrPos();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // В onResume, т.к. после сворачивания и разворачивания приложения (или после выключения и
        // включения экрана) панель навигации появляется опять (еще она появляется при выборе меню
        // на акшенбаре).
        hideNavigationBar();
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();

        videoPlayerView.getPlayer().release();

        if (videodb != null) {
            videodb.close();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_copy_video_name:
                if (currentVideo != null) {
                    final ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    final ClipData clip = ClipData.newPlainText(currentVideo.getName(), currentVideo.getName());
                    clipboard.setPrimaryClip(clip);

                    Toast.makeText(WatchVideoActivity.this,
                            getString(R.string.copied) + ": " + currentVideo.getName(),
                            Toast.LENGTH_LONG).show();
                }
                break;
            case R.id.action_copy_video_url:
                if (currentVideo != null) {
                    final String vidUrl = PlaylistUrlUtil.getVideoUrl(currentVideo);
                    final ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    final ClipData clip = ClipData.newPlainText(vidUrl, vidUrl);
                    clipboard.setPrimaryClip(clip);

                    Toast.makeText(WatchVideoActivity.this,
                            getString(R.string.copied) + ": " + vidUrl,
                            Toast.LENGTH_LONG).show();
                }
                break;
            case R.id.action_copy_playlist_name:
                if (currentVideo != null && currentVideo.getPlaylistId() != -1) {
                    final VideoItem _currentVideo = currentVideo;
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            final PlaylistInfo plInfo = videodb.playlistInfoDao().getById(_currentVideo.getPlaylistId());
                            if (plInfo != null) {
                                handler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        final ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                                        final ClipData clip = ClipData.newPlainText(plInfo.getName(), plInfo.getName());
                                        clipboard.setPrimaryClip(clip);

                                        Toast.makeText(WatchVideoActivity.this,
                                                getString(R.string.copied) + ": " + plInfo.getName(),
                                                Toast.LENGTH_LONG).show();
                                    }
                                });
                            }
                        }
                    }).start();
                } else if (currentVideo != null && currentVideo.getPlaylistId() == -1) {
                    Toast.makeText(WatchVideoActivity.this, getString(R.string.err_playlist_not_defined),
                            Toast.LENGTH_LONG).show();
                }
                break;
            case R.id.action_copy_playlist_url:
                if (currentVideo != null && currentVideo.getPlaylistId() != -1) {
                    final VideoItem _currentVideo = currentVideo;
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            final PlaylistInfo plInfo = videodb.playlistInfoDao().getById(_currentVideo.getPlaylistId());
                            if (plInfo != null) {
                                handler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        final ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                                        final ClipData clip = ClipData.newPlainText(plInfo.getUrl(), plInfo.getUrl());
                                        clipboard.setPrimaryClip(clip);

                                        Toast.makeText(WatchVideoActivity.this,
                                                getString(R.string.copied) + ": " + plInfo.getUrl(),
                                                Toast.LENGTH_LONG).show();
                                    }
                                });
                            }
                        }
                    }).start();
                } else if (currentVideo != null && currentVideo.getPlaylistId() == -1) {
                    Toast.makeText(WatchVideoActivity.this, getString(R.string.err_playlist_not_defined),
                            Toast.LENGTH_LONG).show();
                }
                break;
            case R.id.action_blacklist:
                if (currentVideo != null && currentVideo.getId() != -1) {
                    // сохраним переменные здесь, чтобы потом спокойно их использовать внутри потока
                    // и не бояться, что текущее видео будет переключено до того, как состояние сохранится
                    final VideoItem _currentVideo = currentVideo;
                    new AlertDialog.Builder(WatchVideoActivity.this)
                            .setTitle(getString(R.string.blacklist_video_title))
                            .setMessage(getString(R.string.blacklist_video_message))
                            .setIcon(android.R.drawable.ic_dialog_alert)
                            .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {

                                public void onClick(DialogInterface dialog, int whichButton) {
                                    new Thread(new Runnable() {
                                        @Override
                                        public void run() {
                                            videodb.videoItemDao().setBlacklisted(_currentVideo.getId(), true);
                                            // обновим кэш
                                            _currentVideo.setBlacklisted(true);
                                            handler.post(new Runnable() {
                                                @Override
                                                public void run() {
                                                    Toast.makeText(WatchVideoActivity.this, getString(R.string.video_is_blacklisted),
                                                            Toast.LENGTH_LONG).show();
                                                }
                                            });
                                            // TODO: здесь что-то нужно сделать после добавления видео в блеклист:
                                            // удалить из истории, начать проигрывать какое-то другое видео
                                            // (какое? первое из рекомендаций? Что если список рекомендаций пуст?),
                                            // удалить его из списка рекомендаций (с текущим датасорсом из ROOM
                                            // это произойдет автоматом) и т.п.
                                        }
                                    }).start();

                                }
                            })
                            .setNegativeButton(android.R.string.no, null).show();
                }
                break;
            case R.id.action_reload:
                actionReload();
                break;

        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    /**
     * Избавиться от панели навигации, которая где только не вылезает и потом не прячется сама:
     * - после сворачивания и разворачивания приложения
     * - после выключения и включения экрана
     * - еще она появляется при выборе меню на акшенбаре
     * - если вызвать экран выключения телефона (долгим кликом на кнопку питания)
     */
    private void hideNavigationBar() {
        // Чтобы в полном экране спрятать виртуальную панельку навигации не достаточно флагов в styles.xml
        // https://stackoverflow.com/questions/14178237/setsystemuivisibilitysystem-ui-flag-layout-hide-navigation-does-not-work
        // https://www.vogella.com/tutorials/AndroidActionBar/article.html
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        // с этим флагом акшенбар начнет сверху перекрывать содержимое экрана
                        // (но только если мы не используем Toolbar, а мы используем)
                        //| View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        // с этими флагами весь экран перекорежит и на эмуляторе и на телефоне
                        //| View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        //| View.SYSTEM_UI_FLAG_FULLSCREEN
                        // без этого флага навигация будет опять появляться по первому клику
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );
    }

    private void toggleFullscreen() {
        setFullscreen(!stateFullscreen);
    }

    private void setFullscreen(final boolean fullscreen) {
        stateFullscreen = fullscreen;
        if (stateFullscreen) {
            prevVideoBtn.setVisibility(View.GONE);
            nextVideoBtn.setVisibility(View.GONE);
            videoList.setVisibility(View.GONE);

            getSupportActionBar().hide();

            //videoPlayerView.hideController();
            videoPlayerControlView.hide();
            // продолжить играть, если была пауза
            videoPlayerView.getPlayer().setPlayWhenReady(true);
        } else {
            //prevVideoBtn.setVisibility(View.VISIBLE);
            prevVideoBtn.setVisibility(playbackHistory.size() > 1 ? View.VISIBLE : View.INVISIBLE);
            nextVideoBtn.setVisibility(View.VISIBLE);

            videoList.setVisibility(View.VISIBLE);

            getSupportActionBar().show();

            //videoPlayerView.showController();
            videoPlayerControlView.show();
        }
    }

    private void setLoadError(final boolean loadError, final String errorMsg) {
        stateVideoLoadError = loadError;
        videoLoadErrorMsg = loadError ? errorMsg : "";

        if(stateVideoLoadError) {
            setFullscreen(false);

            videoPlayerView.setVisibility(View.GONE);
            videoPlayerControlView.setVisibility(View.GONE);

            videoPlayerErrorView.setVisibility(View.VISIBLE);

            videoLoadErrorTxt.setText(videoLoadErrorMsg);
        } else {
            videoPlayerView.setVisibility(View.VISIBLE);
            if(!stateFullscreen) {
                videoPlayerControlView.setVisibility(View.VISIBLE);
            }
            videoPlayerErrorView.setVisibility(View.GONE);

            videoLoadErrorTxt.setText("");
        }
    }

    /**
     * Сохраним текущую позицию видео в базу
     */
    private void saveVideoCurrPos() {
        // сохраним переменные здесь, чтобы потом спокойно их использовать внутри потока
        // и не бояться, что текущее видео будет переключено до того, как состояние сохранится
        final VideoItem _currentVideo = currentVideo;
        final long _currentPos = videoPlayerView.getPlayer().getCurrentPosition();
        // для текущего кэша, да
        if (currentVideo != null && !stateVideoLoadError) {
            currentVideo.setPausedAt(_currentPos);
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (_currentVideo != null && !stateVideoLoadError) {
                    videodb.videoItemDao().setPausedAt(_currentVideo.getId(), _currentPos);
                }
            }
        }).start();
    }

    /**
     * Обнулить текущую позицию видео в базе
     */
    private void resetVideoCurrPos() {
        // сохраним переменные здесь, чтобы потом спокойно их использовать внутри потока
        // и не бояться, что текущее видео будет переключено до того, как состояние сохранится
        final VideoItem _currentVideo = currentVideo;
        // для текущего кэша, да
        if (currentVideo != null) {
            currentVideo.setPausedAt(0);
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (_currentVideo != null) {
                    videodb.videoItemDao().setPausedAt(_currentVideo.getId(), 0);
                }
            }
        }).start();
    }

    // начать проигрывание нового ролика - показать информацию о видео, решить вопросы
    // с сохранением позиций предыдущего видео, стеком истории проигрывания и т.п.
    private void playVideoItem(final VideoItem videoItem, boolean resetCurrPos) {
        // сбросим или сохраним текущую позицию предыдущего видео
        if (resetCurrPos) {
            resetVideoCurrPos();
        } else {
            saveVideoCurrPos();
        }

        // загружаем новое видео
        currentVideo = videoItem;
        currentVideoPosition = posMap.containsKey(videoItem.getId()) ? posMap.get(videoItem.getId()) : -1;
        if (currentVideoPosition != -1) {
            videoList.scrollToPosition(currentVideoPosition);
        }
        if (videoItem != null) {
            playbackHistory.push(videoItem);
            if (playbackHistory.size() > 1) {
                prevVideoBtn.setEnabled(true);
                if (!stateFullscreen) {
                    prevVideoBtn.setVisibility(View.VISIBLE);
                }
            }

            // показать информацию о ролике
            getSupportActionBar().setTitle(videoItem.getName());
            getSupportActionBar().setSubtitle(videoItem.getUploader());

            // передобавлять слушателя здесь, чтобы лишний раз не перезаписывать звездочку в базе
            // (starredCheck у нас появляется в onCreateOptionsMenu, поэтому он может быть null,
            // если вызываем playVideoItem из onCreate'а)
            if (starredCheck != null) {
                starredCheck.setOnCheckedChangeListener(null);
                starredCheck.setChecked(videoItem.isStarred());
                starredCheck.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(final CompoundButton buttonView, final boolean isChecked) {
                        // сохраним переменные здесь, чтобы потом спокойно их использовать внутри потока
                        // и не бояться, что текущее видео будет переключено до того, как состояние сохранится
                        final VideoItem _currentVideo = currentVideo;
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                if (_currentVideo != null) {
                                    videodb.videoItemDao().setStarred(_currentVideo.getId(), isChecked);
                                    // обновим кэш
                                    _currentVideo.setStarred(isChecked);
                                }
                            }
                        }).start();
                    }
                });
            }

            // теперь то, что в фоне
            new Thread(new Runnable() {
                @Override
                public void run() {
                    // посчитать просмотр (для ролика, загруженного из базы)
                    if (videoItem.getId() != -1) {
                        videodb.videoItemDao().countView(videoItem.getId());
                    }

                    loadVideoItem(videoItem);
                }
            }).start();
        }
    }

    // загрузка контента видео - выбранного ролика, здесь касается только области проигрывания,
    // т.е. виджет плеера.
    private void loadVideoItem(final VideoItem videoItem) {
        try {
            // загрузить поток видео
            final String vidStreamUrl = ContentLoader.getInstance().extractYtStreamUrl(videoItem.getYtId());
            if (vidStreamUrl != null) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        setLoadError(false, null);

                        playVideoStream(vidStreamUrl, videoItem.getPausedAt());
                    }
                });
            } else {
                // здесь может быть NULL для некоторых роликов:
                // - из-за глюков экстрактора
                // - у некоторых специальных роликов изначально не определена продолжительность и
                // нет ссылки на поток видео (например, см ролик "Топ мультиков Союзмультфильм")
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        setLoadError(true, getString(R.string.err_video_stream_url_null));

                        playVideoStream(null, 0);
                    }
                });
            }
        } catch (final ExtractionException | IOException e) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    setLoadError(true, e.getMessage());

                    playVideoStream(null, 0);
                }
            });
        }
    }

    // собственно, запустить на проигрывание видеопоток по известному адресу
    private void playVideoStream(final String streamUrl, final long seekTo) {
        if (streamUrl == null) {
            // остановить проигрывание текущего ролика, если был загружен
            videoPlayerView.getPlayer().stop(true);
        } else {
            // https://exoplayer.dev/
            // https://github.com/google/ExoPlayer

            // датасорсы к видео в плеере NewPipe:
            // - про продолжение с установленной позиции в коде не вижу или не нашел
            // - (как играть видео конкретно с ютюба не вижу тоже, там ацкий ООП)
            // - короче, толку от них ноль, пусть будут пока ссылки для справки
            // https://github.com/TeamNewPipe/NewPipe/blob/master/app/src/main/java/org/schabi/newpipe/player/helper/PlayerDataSource.java
            // https://github.com/TeamNewPipe/NewPipe/blob/master/app/src/main/java/org/schabi/newpipe/player/resolver/PlaybackResolver.java

            final Uri mp4VideoUri = Uri.parse(streamUrl);
            final MediaSource videoSource = new ProgressiveMediaSource.Factory(videoDataSourceFactory)
                    .createMediaSource(mp4VideoUri);

            // Поставим на паузу старое видео, пока готовим новое
            if (videoPlayerView.getPlayer().getPlaybackState() != Player.STATE_ENDED) {
                // Если ставить на паузу здесь после того, как плеер встал на паузу сам, закончив
                // играть видео, получим здесь второе событие STATE_ENDED, поэтому нам нужна здесь
                // специальная проверка.
                // При этом значение getPlayWhenReady() останется true, поэтому проверяем именно состояние.
                // https://github.com/google/ExoPlayer/issues/2272
                videoPlayerView.getPlayer().setPlayWhenReady(false);
            }

            // Prepare the player with the source.
            ((SimpleExoPlayer) videoPlayerView.getPlayer()).prepare(videoSource);

            // Укажем текущую позицию сразу при загрузке видео
            // (в коментах что-то пишут что-то про датасорсы, которые поддерживают или не поддерживают
            // переходы seek при загрузке, похоже, что это фигня - просто делаем seek сразу после загрузки)
            // Exoplayer plays new Playlist from the beginning instead of provided position
            // https://github.com/google/ExoPlayer/issues/4375
            // How to load stream in the desired position? #2197
            // https://github.com/google/ExoPlayer/issues/2197
            // в этом месте нормлаьный duration еще не доступен, поэтому его не проверяем
            //if(seekTo > 0 && seekTo < videoPlayerView.getPlayer().getDuration()) {
            if (seekTo > 0) {
                // на 5 секунд раньше
                videoPlayerView.getPlayer().seekTo(seekTo - 5000 > 0 ? seekTo - 5000 : 0);
            }
            videoPlayerView.getPlayer().setPlayWhenReady(true);
        }
    }

    /**
     * Загрузить заново видеопоток для текущего ролика
     */
    private void actionReload() {
        if (currentVideo != null && currentVideo.getId() != -1) {
            // загрузить поток видео заново (иногда после разрыва соединения
            // видео может перестать загружаться и появление соединения процесс
            // не возобновляет)

            // сохраним переменные здесь, чтобы потом спокойно их использовать внутри потока
            // и не бояться, что текущее видео будет переключено до того, как состояние сохранится
            final VideoItem _currentVideo = currentVideo;
            final long _currentPos = videoPlayerView.getPlayer().getCurrentPosition();
            // для текущего кэша, да
            if (currentVideo != null && !stateVideoLoadError) {
                currentVideo.setPausedAt(_currentPos);
            }
            // сохраним текущую позицию (если она больше нуля) в б/д и загрузим
            // видео заново - обе операции в фоновом потоке
            new Thread(new Runnable() {
                @Override
                public void run() {
                    // если за время запуска потока видео успели переключить, всё отменяем
                    if (_currentVideo != null && _currentVideo == currentVideo) {
                        if (!stateVideoLoadError) {
                            // сохраним текущую позицию только в том случае, если ролик был загружен
                            // (может быть ситуация, когда мы переключились на видео с ранее
                            // сохраненной позицией, а оно не загрузилось, тогда бы у нас
                            // сбросилась старая сохраненная позиция, а это не хорошо)
                            videodb.videoItemDao().setPausedAt(_currentVideo.getId(), _currentPos);
                        }

                        loadVideoItem(currentVideo);
                    }
                }
            }).start();
        }
    }

    private void actionVideoContextMenu(final View view, final VideoItem videoItem) {
        final PopupMenu popup = new PopupMenu(WatchVideoActivity.this, view);
        popup.getMenuInflater().inflate(R.menu.video_actions, popup.getMenu());
        popup.setOnDismissListener(new PopupMenu.OnDismissListener() {
            @Override
            public void onDismiss(PopupMenu menu) {
                // Прячем панель навигации, т.к. при выборе меню она появляется опять.
                hideNavigationBar();
            }
        });
        popup.setOnMenuItemClickListener(
                new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(final MenuItem item) {
                        switch (item.getItemId()) {
                            case R.id.action_copy_video_name: {
                                final ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                                final ClipData clip = ClipData.newPlainText(videoItem.getName(), videoItem.getName());
                                clipboard.setPrimaryClip(clip);

                                Toast.makeText(WatchVideoActivity.this,
                                        getString(R.string.copied) + ": " + videoItem.getName(),
                                        Toast.LENGTH_LONG).show();
                                break;
                            }
                            case R.id.action_copy_video_url: {
                                final String vidUrl = PlaylistUrlUtil.getVideoUrl(videoItem);
                                final ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                                final ClipData clip = ClipData.newPlainText(vidUrl, vidUrl);
                                clipboard.setPrimaryClip(clip);

                                Toast.makeText(WatchVideoActivity.this,
                                        getString(R.string.copied) + ": " + vidUrl,
                                        Toast.LENGTH_LONG).show();
                                break;
                            }
                            case R.id.action_copy_playlist_name:
                                if (videoItem != null && videoItem.getPlaylistId() != -1) {
                                    new Thread(new Runnable() {
                                        @Override
                                        public void run() {
                                            final PlaylistInfo plInfo = videodb.playlistInfoDao().getById(videoItem.getPlaylistId());
                                            if (plInfo != null) {
                                                handler.post(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        final ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                                                        final ClipData clip = ClipData.newPlainText(plInfo.getName(), plInfo.getName());
                                                        clipboard.setPrimaryClip(clip);

                                                        Toast.makeText(WatchVideoActivity.this,
                                                                getString(R.string.copied) + ": " + plInfo.getName(),
                                                                Toast.LENGTH_LONG).show();
                                                    }
                                                });
                                            }
                                        }
                                    }).start();
                                } else if (videoItem != null && videoItem.getPlaylistId() == -1) {
                                    Toast.makeText(WatchVideoActivity.this, getString(R.string.err_playlist_not_defined),
                                            Toast.LENGTH_LONG).show();
                                }
                                break;
                            case R.id.action_copy_playlist_url:
                                if (videoItem != null && videoItem.getPlaylistId() != -1) {
                                    new Thread(new Runnable() {
                                        @Override
                                        public void run() {
                                            final PlaylistInfo plInfo = videodb.playlistInfoDao().getById(videoItem.getPlaylistId());
                                            if (plInfo != null) {
                                                handler.post(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        final ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                                                        final ClipData clip = ClipData.newPlainText(plInfo.getUrl(), plInfo.getUrl());
                                                        clipboard.setPrimaryClip(clip);

                                                        Toast.makeText(WatchVideoActivity.this,
                                                                getString(R.string.copied) + ": " + plInfo.getUrl(),
                                                                Toast.LENGTH_LONG).show();
                                                    }
                                                });
                                            }
                                        }
                                    }).start();
                                } else if (videoItem != null && videoItem.getPlaylistId() == -1) {
                                    Toast.makeText(WatchVideoActivity.this, getString(R.string.err_playlist_not_defined),
                                            Toast.LENGTH_LONG).show();
                                }
                                break;
                            case R.id.action_blacklist:
                                if (videoItem != null && videoItem.getId() != -1) {
                                    new AlertDialog.Builder(WatchVideoActivity.this)
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
                                                                    Toast.makeText(WatchVideoActivity.this, getString(R.string.video_is_blacklisted),
                                                                            Toast.LENGTH_LONG).show();
                                                                }
                                                            });
                                                            // TODO: здесь что-то нужно сделать после добавления видео в блеклист:
                                                            // например, удалить из текущего списка рекомендаций
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
    }

    /**
     * Случайные рекомандации внизу под основным видео. ArrayAdapter, а не PagedListAdapter
     * потому, что в случае с PagedListAdapter выдача рекомендаций будет автоматом обновляться
     * при каждой записи в базу (например, при переключении видео с сохранением текущей позиции
     * или при клике на кнопку со звездочкой)
     */
    private void setupVideoListArrayAdapter() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<VideoItem> videoItems = videodb.videoItemDao().recommendVideos(200);
                final VideoItemArrayAdapter adapter = new VideoItemArrayAdapter(
                        WatchVideoActivity.this, videoItems, new OnListItemClickListener<VideoItem>() {
                    @Override
                    public void onItemClick(final View view, final int position, final VideoItem videoItem) {
                        posMap.put(videoItem.getId(), position);
                        playVideoItem(videoItem, false);
                    }

                    @Override
                    public boolean onItemLongClick(final View view, final int position, final VideoItem videoItem) {
                        actionVideoContextMenu(view, videoItem);
                        return true;
                    }
                }, null, VideoItemArrayAdapter.ORIENTATION_HORIZONTAL);

                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        videoList.setAdapter(adapter);
                    }
                });
            }
        }).start();
    }


    private void setupVideoListPagedListAdapter(final String searchStr) {
        if (videoItemsLiveData != null) {
            videoItemsLiveData.removeObservers(this);
        }

        final VideoItemPagedListAdapter adapter = new VideoItemPagedListAdapter(
                this, new OnListItemClickListener<VideoItem>() {
            @Override
            public void onItemClick(final View view, final int position, final VideoItem videoItem) {
                posMap.put(videoItem.getId(), position);
                playVideoItem(videoItem, false);
            }

            @Override
            public boolean onItemLongClick(View view, int position, VideoItem videoItem) {
                actionVideoContextMenu(view, videoItem);
                return true;
            }
        }, null, VideoItemPagedListAdapter.ORIENTATION_HORIZONTAL);

        // Initial page size to fetch can also be configured here too
        final PagedList.Config config = new PagedList.Config.Builder().setPageSize(20).build();

        final DataSource.Factory factory =
                videodb.videoItemDao().searchEnabledVideosDs(searchStr);

        videoItemsLiveData = new LivePagedListBuilder(factory, config).build();

        videoItemsLiveData.observe(this, new Observer<PagedList<VideoItem>>() {
            @Override
            public void onChanged(@Nullable PagedList<VideoItem> videos) {
                adapter.submitList(videos);
            }
        });

        videoList.setAdapter(adapter);
    }

    private void setupVideoListPagedListAdapter(final long playlistId) {
        if (videoItemsLiveData != null) {
            videoItemsLiveData.removeObservers(this);
        }

        final VideoItemPagedListAdapter adapter = new VideoItemPagedListAdapter(
                this, new OnListItemClickListener<VideoItem>() {
            @Override
            public void onItemClick(final View view, final int position, final VideoItem videoItem) {
                posMap.put(videoItem.getId(), position);
                playVideoItem(videoItem, false);
            }

            @Override
            public boolean onItemLongClick(View view, int position, VideoItem videoItem) {
                actionVideoContextMenu(view, videoItem);
                return true;
            }
        }, null, VideoItemPagedListAdapter.ORIENTATION_HORIZONTAL);

        // Initial page size to fetch can also be configured here too
        final PagedList.Config config = new PagedList.Config.Builder().setPageSize(20).build();

        final DataSource.Factory factory =
                videodb.videoItemDao().getByPlaylistDs(playlistId);

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
