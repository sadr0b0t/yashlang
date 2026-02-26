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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ui.PlayerControlView;
import com.google.android.exoplayer2.ui.PlayerView;

import su.sadrobot.yashlang.controller.StreamHelper;
import su.sadrobot.yashlang.controller.VideoItemActions;
import su.sadrobot.yashlang.model.PlaylistInfo;
import su.sadrobot.yashlang.model.StreamCache;
import su.sadrobot.yashlang.model.VideoItem;
import su.sadrobot.yashlang.player.RecommendationsProvider;
import su.sadrobot.yashlang.player.RecommendationsProviderFactory;
import su.sadrobot.yashlang.service.PlayerService;
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
     * Загрузить информацию о текущем видео и рекомендациях из сервиса
     */
    public static final String PARAM_INIT_FROM_SERVICE = "PARAM_INIT_FROM_SERVICE";

    /**
     * Загрузить информацию о видео по ID из базы
     */
    public static final String PARAM_VIDEO_ITEM_ID = "PARAM_VIDEO_ITEM_ID";

    /**
     * Загрузить информацию о видео онлайн
     */
    public static final String PARAM_VIDEO_ITEM_URL = "PARAM_VIDEO_ITEM_URL";

    /**
     * Перемотать список рекомендаций к выбранному ролику
     */
    public static final String PARAM_SCROLL_TO_IN_RECOMMENDATIONS = "PARAM_SCROLL_TO_IN_RECOMMENDATIONS";

    /**
     * Режим для списка рекомендаций: значение из RecommendationsMode
     */
    public static final String PARAM_RECOMMENDATIONS_MODE = "PARAM_RECOMMENDATIONS_MODE";


    private PlayerView videoPlayerView;
    private View audioPlayerView;
    private ImageView audioPlayerThumbImg;
    private PlayerControlView videoPlayerControlView;
    private TextView streamInfoTxt;
    private ImageButton prevVideoBtn;
    private ImageButton nextVideoBtn;

    private View videoPlayerErrorView;
    private TextView videoLoadErrorTxt;
    private Button reloadOnErrorBtn;

    private View videoPlayerNothingToPlayView;
    private Button selectStreamBtn;

    private View videoPlayerLoadingView;

    private RecyclerView videoList;

    private Toolbar toolbar;
    private CheckBox starredCheck;

    private PlayerService playerService;
    private ServiceConnection playerServiceConnection;
    // https://stackoverflow.com/questions/22079909/android-java-lang-illegalargumentexception-service-not-registered
    // https://developer.android.com/reference/android/app/Service
    private boolean playerServiceIsBound = false;

    private boolean stateFullscreen = false;

    private boolean initedOnce = false;

    private final Handler handler = new Handler();

    private interface RecommendationsListener {
        void onFirstItemLoaded(final VideoItem firstItem);
    }

    private RecommendationsListener recommendationsListener;

    private final RecyclerView.AdapterDataObserver emptyListObserver = new RecyclerView.AdapterDataObserver() {
        // https://stackoverflow.com/questions/47417645/empty-view-on-a-recyclerview
        // https://stackoverflow.com/questions/27414173/equivalent-of-listview-setemptyview-in-recyclerview
        // https://gist.github.com/sheharyarn/5602930ad84fa64c30a29ab18eb69c6e

        private boolean initialEmpty = true;

        private void checkIfEmpty() {
            // если режим загрузки первого видео из рекомендаций, нужно поймать момент, когда
            // в рекомендациях загрузилось видео первый раз
            final boolean listIsEmpty = videoList.getAdapter() == null || videoList.getAdapter().getItemCount() == 0;
            if (initialEmpty && !listIsEmpty) {
                // элементы появились в списке первый раз после создания
                initialEmpty = false;

                // сообщить о том. что появился первй ролик в списке рекомендаций
                final VideoItem firstItem = ((VideoItemPagedListAdapter) videoList.getAdapter()).getItem(0);
                if (recommendationsListener != null) {
                    recommendationsListener.onFirstItemLoaded(firstItem);
                }
            }

            // видимость некоторых элементов управления зависит от наличия элементов в
            // списке рекомендаций, а они могут загружаться в фоне
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

        setContentView(R.layout.activity_watch_video);

        videoPlayerView = findViewById(R.id.video_player_view);
        audioPlayerView = findViewById(R.id.audio_player_view);
        audioPlayerThumbImg = findViewById(R.id.audio_player_thumb_img);
        videoPlayerControlView = findViewById(R.id.video_player_control_view);
        streamInfoTxt = findViewById(R.id.stream_info_txt);
        prevVideoBtn = findViewById(R.id.prev_video_btn);
        nextVideoBtn = findViewById(R.id.next_video_btn);

        videoPlayerErrorView = findViewById(R.id.video_player_error_view);
        videoLoadErrorTxt = findViewById(R.id.video_load_error_txt);
        reloadOnErrorBtn = findViewById(R.id.reload_btn);

        videoPlayerNothingToPlayView = findViewById(R.id.video_player_nothing_to_play_view);
        selectStreamBtn = findViewById(R.id.select_stream_btn);


        videoPlayerLoadingView = findViewById(R.id.video_player_loading_view);

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

        prevVideoBtn.setEnabled(false);
        prevVideoBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (playerService != null) {
                    playerService.gotoPrevVideo();

                    prevVideoBtn.setEnabled(playerService.getPlaybackHistory().size() > 1);
                    updateControlsVisibility();
                }
            }
        });

        nextVideoBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (playerService != null) {
                    playerService.gotoNextVideo();
                }
            }
        });

        videoPlayerView.requestFocus();

        // контроллер отдельно, чтобы красиво добавить справа и слева от плеера кнопки назад и вперед
        videoPlayerView.setUseController(false);

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

        // Панель - ролик закружен, но потоки не выбраны
        videoPlayerNothingToPlayView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideNavigationBar();
            }
        });

        selectStreamBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                actionSelectStreams();
            }
        });


        // Панель - прогресс загрузки видео
        videoPlayerLoadingView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideNavigationBar();
                toggleFullscreen();
            }
        });

        streamInfoTxt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                actionSelectStreams();
            }
        });

        // Рекомендации
        // set a LinearLayoutManager with default vertical orientation
        final LinearLayoutManager linearLayoutManager = new LinearLayoutManager(
                getApplicationContext(), RecyclerView.HORIZONTAL, false);
        videoList.setLayoutManager(linearLayoutManager);
        videoList.setItemAnimator(new DefaultItemAnimator() {
            @Override
            public boolean canReuseUpdatedViewHolder(@NonNull RecyclerView.ViewHolder viewHolder) {
                // чтобы картинки и текст не сбивались в кучку при быстрой промотке
                // см: https://github.com/sadr0b0t/yashlang/issues/129
                return true;
            }
        });
        // рекомендации загружаем после подключения к сервису

        // настройки видимости элементов управления
        // (одного этого вызова в onCreate будет не достаточно, т.к.
        // видимость некоторых элементов управления зависит от наличия элементов в списке рекомендаций,
        // а они могут загружаться в фоне позже, чем мы сюда попадем)
        updateControlsVisibility();
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
        audioPlayerView = findViewById(R.id.audio_player_view);
        audioPlayerThumbImg = findViewById(R.id.audio_player_thumb_img);
        videoPlayerControlView = findViewById(R.id.video_player_control_view);
        streamInfoTxt = findViewById(R.id.stream_info_txt);
        prevVideoBtn = findViewById(R.id.prev_video_btn);
        nextVideoBtn = findViewById(R.id.next_video_btn);

        videoPlayerErrorView = findViewById(R.id.video_player_error_view);
        videoLoadErrorTxt = findViewById(R.id.video_load_error_txt);
        reloadOnErrorBtn = findViewById(R.id.reload_btn);

        videoPlayerNothingToPlayView = findViewById(R.id.video_player_nothing_to_play_view);
        selectStreamBtn = findViewById(R.id.select_stream_btn);

        videoPlayerLoadingView = findViewById(R.id.video_player_loading_view);

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

        prevVideoBtn.setEnabled(playerService.getPlaybackHistory().size() > 1);
        prevVideoBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (playerService != null) {
                    playerService.gotoPrevVideo();

                    prevVideoBtn.setEnabled(playerService.getPlaybackHistory().size() > 1);
                    updateControlsVisibility();
                }
            }
        });

        nextVideoBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (playerService != null) {
                    playerService.gotoNextVideo();
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

        // Панель - ролик закружен, но потоки не выбраны
        videoPlayerNothingToPlayView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideNavigationBar();
            }
        });

        selectStreamBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                actionSelectStreams();
            }
        });

        // Панель - прогресс загрузки видео
        videoPlayerLoadingView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideNavigationBar();
                toggleFullscreen();
            }
        });

        streamInfoTxt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                actionSelectStreams();
            }
        });

        // Рекомендации
        // set a LinearLayoutManager with default vertical orientation
        final LinearLayoutManager linearLayoutManager = new LinearLayoutManager(
                getApplicationContext(), RecyclerView.HORIZONTAL, false);
        videoList.setLayoutManager(linearLayoutManager);
        videoList.setItemAnimator(new DefaultItemAnimator() {
            @Override
            public boolean canReuseUpdatedViewHolder(@NonNull RecyclerView.ViewHolder viewHolder) {
                // чтобы картинки и текст не сбивались в кучку при быстрой промотке
                // см: https://github.com/sadr0b0t/yashlang/issues/129
                return true;
            }
        });
        videoList.setAdapter(videoListAdapter);

        // Режим полного или неполного экрана
        setFullscreen(stateFullscreen);

        // настройки видимости элементов управления
        // будет вызвано из setFullscreen(stateFullscreen);
        //updateControlsVisibility();

        // показать информацию о ролике
        updateControlsValues();
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        // https://developer.android.com/training/appbar/action-views.html

        // Создаём меню заново для каждого нового загруженного видео, при загрузке нового видео
        // в playVideoItem вызываем invalidateOptionsMenu.
        if (playerService != null && playerService.getCurrentVideo() != null) {

            toolbar.inflateMenu(R.menu.watch_video_actions);

            if (playerService.getCurrentVideo().getId() == VideoItem.ID_NONE) {
                toolbar.getMenu().findItem(R.id.action_star).setVisible(false);
                toolbar.getMenu().findItem(R.id.action_blacklist).setVisible(false);
                toolbar.getMenu().findItem(R.id.action_download_streams).setVisible(false);
            } else {
                starredCheck = (CheckBox) toolbar.getMenu().findItem(R.id.action_star).getActionView();
                starredCheck.setButtonDrawable(android.R.drawable.btn_star);

                starredCheck.setChecked(playerService.getCurrentVideo().isStarred());
                starredCheck.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(final CompoundButton buttonView, final boolean isChecked) {
                        if (playerService.getCurrentVideo() != null && playerService.getCurrentVideo().getId() != VideoItem.ID_NONE) {
                            final VideoItem _currentVideo = playerService.getCurrentVideo();
                            VideoItemActions.actionSetStarred(WatchVideoActivity.this, playerService.getCurrentVideo().getId(), isChecked,
                                    new VideoItemActions.OnVideoStarredChangeListener() {
                                        @Override
                                        public void onVideoStarredChange(final long videoId, final boolean starred) {
                                            // обновим кэш
                                            _currentVideo.setStarred(starred);

                                            // и еще обновим кэш, если этот же ролик вдруг есть в списке предыдущих видео
                                            // (там ролик будет тот же, а объект VideoItem - другой)
                                            for (final VideoItem item : playerService.getPlaybackHistory()) {
                                                if (item.getId() == videoId) {
                                                    item.setStarred(starred);
                                                }
                                            }
                                        }
                                    });
                        }
                    }
                });
            }

            if (playerService.getCurrentVideo().getPlaylistId() == PlaylistInfo.ID_NONE) {
                toolbar.getMenu().findItem(R.id.action_copy_playlist_name).setVisible(false);
                toolbar.getMenu().findItem(R.id.action_copy_playlist_url).setVisible(false);
            }

            toolbar.setOnMenuItemClickListener(
                    new Toolbar.OnMenuItemClickListener() {
                        @Override
                        public boolean onMenuItemClick(MenuItem item) {
                            return onOptionsItemSelected(item);
                        }
                    });
        }

        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!PlayerService.isRunning() && (initedOnce || WatchVideoActivity.this.getIntent().getBooleanExtra(PARAM_INIT_FROM_SERVICE, false))) {
            // сервис не запущен, а нам нужно загрузить информацию из него, т.к. мы уже один
            // раз открывали этот экран с плеером или плеер открыт в режиме загрузки информацию из сервиса
            // Это может произойти, если пользователь явно завершил сервис из области уведомлений,
            // а потом вернулся к экрану плеера
            finish();
        } else {
            // https://developer.android.com/guide/components/bound-services
            playerServiceConnection = new ServiceConnection() {
                @Override
                public void onServiceConnected(final ComponentName name, final IBinder service) {
                    playerService = ((PlayerService.PlayerServiceBinder) service).getService();

                    videoPlayerView.setPlayer(playerService.getPlayer());
                    videoPlayerControlView.setPlayer(playerService.getPlayer());

                    playerService.setServiceListener(new PlayerService.PlayerServiceListener() {
                        @Override
                        public void onContentsStateChange() {
                            invalidateOptionsMenu();
                            updateControlsVisibility();
                            updateControlsValues();
                        }

                        @Override
                        public void onPlayerStateChange() {
                            WatchVideoActivity.this.onPlayerStateChange();
                        }

                        @Override
                        public void currentVideoChange() {
                            // можно было бы переместить в updateControlsValues, но не обязательно, т.к. это скролл логично делать
                            // один раз во время выбора нового ролика, а не каждый раз при обновлении состояния экрана
                            if (playerService.getVideoListCurrentPosition() != -1) {
                                videoList.scrollToPosition(playerService.getVideoListCurrentPosition());
                            }
                        }
                    });

                    // рекомендации и текущее видео
                    if (initedOnce || WatchVideoActivity.this.getIntent().getBooleanExtra(PARAM_INIT_FROM_SERVICE, false)) {
                        // текущее видео и рекомендации из сервиса
                        if (playerService.getRecommendationsProvider() != null) {
                            playerService.getRecommendationsProvider().setupVideoList(
                                    WatchVideoActivity.this, WatchVideoActivity.this,
                                    videoList, emptyListObserver,
                                    new OnListItemClickListener<VideoItem>() {
                                        @Override
                                        public void onItemClick(final View view, final int position, final VideoItem videoItem) {
                                            playerService.getVideoListPosMap().put(videoItem.getId(), position);
                                            playerService.playVideoItem(videoItem, false);
                                        }

                                        @Override
                                        public boolean onItemLongClick(final View view, final int position, final VideoItem videoItem) {
                                            actionVideoContextMenu(view, videoItem);
                                            return true;
                                        }
                                    });

                            videoList.scrollToPosition(playerService.getVideoListCurrentPosition());

                            // на случай если у нас получился ArrayAdapter без emptyListObserver
                            updateControlsVisibility();
                        }
                    } else {
                        // загружаем видео
                        // если передан параметр videoId, то загружаем видео по id из базы;
                        // если videoId нет, но есть videoYtId, то используем его;
                        // если нет ни того, ни другого, загрузаем первй ролик из рекомендаций
                        final long videoId = WatchVideoActivity.this.getIntent().getLongExtra(PARAM_VIDEO_ITEM_ID, VideoItem.ID_NONE);
                        final String videoItemUrl = WatchVideoActivity.this.getIntent().getStringExtra(PARAM_VIDEO_ITEM_URL);

                        // cбросить историю текущего плейлиста
                        playerService.getVideoListPosMap().clear();
                        playerService.getPlaybackHistory().clear();
                        playerService.setVideoListCurrentPosition(-1);

                        if (videoId != VideoItem.ID_NONE) {
                            playerService.playVideoItem(videoId, false);

                            final int scrollTo = WatchVideoActivity.this.getIntent().getIntExtra(PARAM_SCROLL_TO_IN_RECOMMENDATIONS, -1);
                            if (scrollTo != -1) {
                                recommendationsListener = new RecommendationsListener() {
                                    @Override
                                    public void onFirstItemLoaded(final VideoItem firstItem) {
                                        // попробуем промотать список рекомендаций до выбранного видео

                                        // будем надеяться, что переданный через параметры индекс, совпадет
                                        // с реальным индексом текущего видео во вновь загруженных рекомендациях.
                                        // проверить здесь однозначно не получится, т.к. адаптер PagedListAdapter
                                        // загружает элементы порциями и если выбранный элемент окажется в списке
                                        // достаточно далеко, адаптер вернет для него здесь null, а реальная загрузка
                                        // произойдет позднее уже после того, как сработает videoList.scrollToPosition
                                        playerService.getVideoListPosMap().put(videoId, scrollTo);
                                        playerService.setVideoListCurrentPosition(scrollTo);
                                        videoList.scrollToPosition(scrollTo);
                                    }
                                };
                            } else {
                                recommendationsListener = new RecommendationsListener() {
                                    @Override
                                    public void onFirstItemLoaded(final VideoItem firstItem) {
                                        // проверим, совпадает ли первый элемент в рекомендациях с роликом,
                                        // который загружен; если совпадает, то установим индекс текущего ролика
                                        // нулём, чтобы плеер понимал, что загруженное видео - первое в списке
                                        // рекомендаций
                                        if (videoId == firstItem.getId()) {
                                            playerService.getVideoListPosMap().put(videoId, 0);
                                            playerService.setVideoListCurrentPosition(0);
                                        }
                                    }
                                };
                            }
                        } else if (videoItemUrl != null) {
                            playerService.playVideoItem(videoItemUrl, false);

                            final int scrollTo = WatchVideoActivity.this.getIntent().getIntExtra(PARAM_SCROLL_TO_IN_RECOMMENDATIONS, -1);
                            if (scrollTo != -1) {
                                recommendationsListener = new RecommendationsListener() {
                                    @Override
                                    public void onFirstItemLoaded(final VideoItem firstItem) {
                                        // попробуем промотать список рекомендаций до выбранного видео

                                        // будем надеяться, что переданный через параметры индекс, совпадет
                                        // с реальным индексом текущего видео во вновь загруженных рекомендациях.
                                        // проверить здесь однозначно не получится, т.к. адаптер PagedListAdapter
                                        // загружает элементы порциями и если выбранный элемент окажется в списке
                                        // достаточно далеко, адаптер вернет для него здесь null, а реальная загрузка
                                        // произойдет позднее уже после того, как сработает videoList.scrollToPosition
                                        playerService.getVideoListPosMap().put(videoId, scrollTo);
                                        playerService.setVideoListCurrentPosition(scrollTo);
                                        videoList.scrollToPosition(scrollTo);
                                    }
                                };
                            } else {
                                recommendationsListener = new RecommendationsListener() {
                                    @Override
                                    public void onFirstItemLoaded(final VideoItem firstItem) {
                                        // проверим, совпадает ли первый элемент в рекомендациях с роликом,
                                        // который загружен; если совпадает, то установим индекс текущего ролика
                                        // нулём, чтобы плеер понимал, что загруженное видео - первое в списке
                                        // рекомендаций
                                        if (videoItemUrl.equals(firstItem.getItemUrl())) {
                                            playerService.setVideoListCurrentPosition(0);
                                        }
                                    }
                                };
                            }
                        } else {
                            // возьмем первое видео из списка рекомендаций, когда они загрузятся
                            recommendationsListener = new RecommendationsListener() {
                                @Override
                                public void onFirstItemLoaded(VideoItem firstItem) {
                                    playerService.getVideoListPosMap().put(firstItem.getId(), 0);
                                    playerService.playVideoItem(firstItem, false);
                                }
                            };
                        }

                        // загружаем заново
                        loadRecommendations(videoId, recommendationsListener);

                        // инициировали один раз - в следующий раз будем брать из сервиса
                        initedOnce = true;
                    }

                    updateControlsVisibility();
                    updateControlsValues();
                }

                @Override
                public void onServiceDisconnected(final ComponentName name) {
                    playerService.removeServiceListener();
                    playerService = null;
                }
            };

            // чтобы сервис не завершался автоматически после скрытия текущего экрана плеера
            if (ConfigOptions.getBackgroundPlaybackOn(this)) {
                PlayerService.cmdStartOrStick(this);
            }
            bindService(new Intent(this, PlayerService.class), playerServiceConnection,
                    Context.BIND_AUTO_CREATE);
            playerServiceIsBound = true;

            // В onResume, т.к. после сворачивания и разворачивания приложения (или после выключения и
            // включения экрана) панель навигации появляется опять (еще она появляется при выборе меню
            // на акшенбаре).
            hideNavigationBar();
        }
    }

    @Override
    protected void onPause() {
        // так делать не рекомендуется, но мы не просто перекидываем плеер с одного вью на другое,
        // мы его здесь прячем на неопределенное время
        videoPlayerView.setPlayer(null);
        videoList.setAdapter(null);

        if (playerServiceIsBound) {
            unbindService(playerServiceConnection);
            playerServiceIsBound = false;
        }
        super.onPause();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_copy_video_name: {
                VideoItemActions.actionCopyVideoName(WatchVideoActivity.this, playerService.getCurrentVideo());
                break;
            }
            case R.id.action_copy_video_url: {
                VideoItemActions.actionCopyVideoUrl(WatchVideoActivity.this, playerService.getCurrentVideo());
                break;
            }
            case R.id.action_copy_playlist_name: {
                VideoItemActions.actionCopyPlaylistName(WatchVideoActivity.this, handler, playerService.getCurrentVideo());
                break;
            }
            case R.id.action_copy_playlist_url: {
                VideoItemActions.actionCopyPlaylistUrl(WatchVideoActivity.this, handler, playerService.getCurrentVideo());
                break;
            }
            case R.id.action_blacklist: {
                final VideoItem _currentVideo = playerService.getCurrentVideo();
                VideoItemActions.actionBlacklist(
                        WatchVideoActivity.this, handler, playerService.getCurrentVideo().getId(),
                        new VideoItemActions.OnVideoBlacklistedChangeListener() {
                            @Override
                            public void onVideoBlacklistedChange(final long videoId, final boolean blacklisted) {
                                // обновим кэш
                                _currentVideo.setBlacklisted(true);

                                // и еще обновим кэш, если этот же ролик вдруг есть в списке предыдущих видео
                                // (там ролик будет тот же, а объект VideoItem - другой)
                                for (final VideoItem item : playerService.getPlaybackHistory()) {
                                    if (item.getId() == videoId) {
                                        item.setBlacklisted(true);
                                    }
                                }
                                // TODO: здесь что-то нужно сделать после добавления видео в блеклист:
                                // удалить из истории, начать проигрывать какое-то другое видео
                                // (какое? первое из рекомендаций? Что если список рекомендаций пуст?),
                                // удалить его из списка рекомендаций (с текущим датасорсом из ROOM
                                // это произойдет автоматом) и т.п.
                            }
                        });
                break;
            }
            case R.id.action_download_streams: {
                actionDownloadStreams();
                break;
            }
            case R.id.action_select_streams: {
                actionSelectStreams();
                break;
            }
            case R.id.action_reload: {
                actionReload();
                break;
            }
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void onPlayerStateChange() {
        updateControlsValues();

        PlayerService.PlayerState playerState = this.playerService.getPlayerState();
        if (playerState == PlayerService.PlayerState.EMPTY || playerState == PlayerService.PlayerState.ERROR || playerState == PlayerService.PlayerState.NOTHING_TO_PLAY) {
            setFullscreen(false);
            // будет вызвано внутри setFullscreen
            // updateControlsVisibility();
        } else {
            updateControlsVisibility();
        }
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

    /**
     * Показываем или скрываем элементы управления в зависимости от текущих обстоятельств
     */
    private void updateControlsVisibility() {
        // в первую очередь определим, в полноэкранном режиме или не полноэкранном режиме
        if (stateFullscreen) {
            prevVideoBtn.setVisibility(View.GONE);
            nextVideoBtn.setVisibility(View.GONE);
            videoList.setVisibility(View.GONE);

            getSupportActionBar().hide();

            // сам плеер и элементы управления
            if (playerService != null) {
                switch (playerService.getPlayerState()) {
                    case EMPTY:
                    case ERROR:
                    case NOTHING_TO_PLAY:
                        //setFullscreen(false);
                        // в режиме FULLSCREEN мы сюда попасть не должы
                        break;

                    case LOADING:
                        videoPlayerView.setVisibility(View.GONE);
                        audioPlayerView.setVisibility(View.GONE);
                        videoPlayerControlView.setVisibility(View.INVISIBLE);
                        streamInfoTxt.setVisibility(View.GONE);
                        videoPlayerLoadingView.setVisibility(View.VISIBLE);
                        videoPlayerErrorView.setVisibility(View.GONE);
                        videoPlayerNothingToPlayView.setVisibility(View.GONE);

                        break;

                    case LOADED:
                        if (playerService.getPlayerStreamMode() == PlayerService.PlayerStreamMode.VIDEO) {
                            videoPlayerView.setVisibility(View.VISIBLE);
                            audioPlayerView.setVisibility(View.GONE);
                        } else { // PlayerStreamMode.AUDIO
                            videoPlayerView.setVisibility(View.GONE);
                            audioPlayerView.setVisibility(View.VISIBLE);
                        }

                        // если делать так, то статус играть/пауза не будет обновляться
                        //videoPlayerControlView.setVisibility(View.GONE);
                        videoPlayerControlView.hide();

                        streamInfoTxt.setVisibility(View.GONE);

                        videoPlayerLoadingView.setVisibility(View.GONE);
                        videoPlayerErrorView.setVisibility(View.GONE);
                        videoPlayerNothingToPlayView.setVisibility(View.GONE);

                        break;
                }
            }
        } else {
            if (videoList.getAdapter() == null || videoList.getAdapter().getItemCount() < 2) {
                prevVideoBtn.setVisibility(View.GONE);
                nextVideoBtn.setVisibility(View.GONE);

                if (super.getIntent().getSerializableExtra(PARAM_RECOMMENDATIONS_MODE) != RecommendationsProviderFactory.RecommendationsMode.ALL_NEW) {
                    // спрячем список рекомендаций, даже если в нем будет 1 элемент, т.к.
                    // обычно это будет тот же самый ролик, который сейчас загружен
                    videoList.setVisibility(View.GONE);
                } else {
                    // Но только не в случае, если у нас список рекомендаций - все новые элементы для
                    // всех плейлистов. В этом случае может прозойти совсем не очевидная ситуация:
                    // если в 1-м плейлисте с новыми элементами окажется всего ровно 1 новый элемент,
                    // то движок адаптера вызовет у VideoItemMultPlaylistsOnlyNewOnlineDataSource
                    // только loadInitial (внутри которого и будет загружен этот 1-й элемент),
                    // после чего, если мы бы спрятали список здесь, loadAfter для загрузки
                    // новых элементов никогда бы не был дальше вызван, т.к. он вызывается по требованию
                    // интерфейса при промотке списка, когда требуется отобразить недостающие элеметы,
                    // а скрытый список не требует ничего отображать. Поэтому мы так и останемся
                    // с единственным элементом, загруженным в loadInitial, и список рекомендаций
                    // так никогда не отобразится (чтобы отобразить список рекомендаций, нужно загрузить
                    // хотябы еще один элемент, а чтобы загрузить еще один элемент, нужно отобразить
                    // список рекомендаций). Поэтому список рекомендаций в режиме "всё новое" мы
                    // будем отображать всегда, даже если в нем всего один элемент, т.к. мы не знаем,
                    // появится ли там что-то еще или этот первый загруженный элемент вообще единственный.
                    videoList.setVisibility(View.VISIBLE);
                }

            } else {
                prevVideoBtn.setVisibility(playerService.getPlaybackHistory().size() > 1 ? View.VISIBLE : View.INVISIBLE);
                nextVideoBtn.setVisibility(View.VISIBLE);
                videoList.setVisibility(View.VISIBLE);
            }

            getSupportActionBar().show();

            // сам плеер и элементы управления
            if (playerService != null) {
                switch (playerService.getPlayerState()) {
                    case EMPTY:
                        //setFullscreen(false);

                        // обычно этот экран не видно никогда
                        videoPlayerView.setVisibility(View.INVISIBLE);
                        audioPlayerView.setVisibility(View.GONE);
                        videoPlayerControlView.setVisibility(View.GONE);
                        streamInfoTxt.setVisibility(View.GONE);
                        videoPlayerLoadingView.setVisibility(View.GONE);
                        videoPlayerErrorView.setVisibility(View.GONE);
                        videoPlayerNothingToPlayView.setVisibility(View.GONE);

                        break;

                    case ERROR:
                        //setFullscreen(false);

                        videoPlayerView.setVisibility(View.GONE);
                        audioPlayerView.setVisibility(View.GONE);
                        videoPlayerControlView.setVisibility(View.GONE);
                        streamInfoTxt.setVisibility(View.GONE);
                        videoPlayerLoadingView.setVisibility(View.GONE);
                        videoPlayerErrorView.setVisibility(View.VISIBLE);
                        videoPlayerNothingToPlayView.setVisibility(View.GONE);

                        break;

                    case LOADING:
                        videoPlayerView.setVisibility(View.GONE);
                        audioPlayerView.setVisibility(View.GONE);
                        videoPlayerControlView.setVisibility(View.INVISIBLE);
                        streamInfoTxt.setVisibility(View.VISIBLE);
                        videoPlayerLoadingView.setVisibility(View.VISIBLE);
                        videoPlayerErrorView.setVisibility(View.GONE);
                        videoPlayerNothingToPlayView.setVisibility(View.GONE);

                        break;

                    case LOADED:
                        if (playerService.getPlayerStreamMode() == PlayerService.PlayerStreamMode.VIDEO) {
                            videoPlayerView.setVisibility(View.VISIBLE);
                            audioPlayerView.setVisibility(View.GONE);
                        } else { // PlayerStreamMode.AUDIO
                            videoPlayerView.setVisibility(View.GONE);
                            audioPlayerView.setVisibility(View.VISIBLE);
                        }

                        // если делать так, то статус играть/пауза не будет обновляться
                        //videoPlayerControlView.setVisibility(View.VISIBLE);
                        videoPlayerControlView.show();

                        streamInfoTxt.setVisibility(View.VISIBLE);

                        videoPlayerLoadingView.setVisibility(View.GONE);
                        videoPlayerErrorView.setVisibility(View.GONE);
                        videoPlayerNothingToPlayView.setVisibility(View.GONE);

                        break;

                    case NOTHING_TO_PLAY:
                        // ролик загружен, но для проигрывания не выбраны
                        // потоки ни видео, ни аудио
                        // (отличие от состояния ошибки в том, что в этом случае можно
                        // выбрать поток для проигрывания вручшую)

                        videoPlayerView.setVisibility(View.GONE);
                        audioPlayerView.setVisibility(View.GONE);
                        videoPlayerControlView.setVisibility(View.GONE);
                        streamInfoTxt.setVisibility(View.GONE);
                        videoPlayerLoadingView.setVisibility(View.GONE);
                        videoPlayerErrorView.setVisibility(View.GONE);
                        videoPlayerNothingToPlayView.setVisibility(View.VISIBLE);
                }
            }
        }
    }

    private void updateControlsValues() {
        if (playerService != null && playerService.getCurrentVideo() != null) {
            getSupportActionBar().setTitle(playerService.getCurrentVideo().getName());
            getSupportActionBar().setSubtitle(playerService.getCurrentVideo().getUploader());

            if (playerService.getCurrentVideo().getPlaybackStreams() != null &&
                    playerService.getCurrentVideo().getPlaybackStreams().getVideoStream() != null) {
                // есть поток видео
                // если аудио потока нет (ни встроенного, ни отдельного), обозначим ситуацию игконкой "🔇" (unicode: muted speaker)
                // если поток сохранен локально, обозначим иконкой "💾" (unicode: floppy disk)
                // если при этом аудио поток играет онлайн, дополинтельно метка "🎵☁️" (unicode: musical note + cloud) (звук в облаке)
                // если поток видео играет онлайн, а поток аудио - оффлайн, дополнительная метка "🎵💾️"
                //   (unicode: musical note + floppy disk) (звук на дискетке)
                streamInfoTxt.setText(
                        playerService.getCurrentVideo().getPlaybackStreams().getVideoStream().getResolution() +
                                (!playerService.getCurrentVideo().getPlaybackStreams().getVideoStream().isOnline() ?
                                        " " + getString(R.string.icon_offline) : "") +
                                (playerService.getCurrentVideo().getPlaybackStreams().getVideoStream().getStreamType() == StreamCache.StreamType.VIDEO &&
                                        playerService.getCurrentVideo().getPlaybackStreams().getAudioStream() == null ? " " + getString(R.string.icon_no_sound) : "") +
                                (!playerService.getCurrentVideo().getPlaybackStreams().getVideoStream().isOnline() &&
                                        playerService.getCurrentVideo().getPlaybackStreams().getAudioStream() != null &&
                                        playerService.getCurrentVideo().getPlaybackStreams().getAudioStream().isOnline() ?
                                        " " + getString(R.string.icon_only_sound) + getString(R.string.icon_online) : "") +
                                (playerService.getCurrentVideo().getPlaybackStreams().getVideoStream().isOnline() &&
                                        playerService.getCurrentVideo().getPlaybackStreams().getAudioStream() != null &&
                                        !playerService.getCurrentVideo().getPlaybackStreams().getAudioStream().isOnline() ?
                                        " " + getString(R.string.icon_only_sound) + getString(R.string.icon_offline) : "")
                );
            } else if (playerService.getCurrentVideo().getPlaybackStreams() != null && playerService.getCurrentVideo().getPlaybackStreams().getAudioStream() != null) {
                // потока видео нет, но есть поток аудио
                // режим аудио-плеера: обозначим его иконкой "🎵" (unicode: musical note)
                // (еще вариант: радио, но это, вроде логичнее)
                // если поток сохранен локально, обозначим иконкой "💾" (unicode: floppy disk)
                streamInfoTxt.setText(
                        getString(R.string.icon_only_sound) +
                                (playerService.getCurrentVideo().getPlaybackStreams().getAudioStream().isOnline() ? "" : " " + getString(R.string.icon_offline))
                );
            } else {
                // потоки не закружены или загружены, но не выбраны (скорее всего в этом случае
                // поле всё равно будет сктрыто)
                streamInfoTxt.setText("");
            }

            // будет видно только в режиме проигрывания адио без видео
            if (playerService.getCurrentVideo().getThumbBitmap() != null) {
                audioPlayerThumbImg.setImageBitmap(playerService.getCurrentVideo().getThumbBitmap());
            } else {
                audioPlayerThumbImg.setImageResource(R.drawable.ic_yashlang_thumb);
            }
        } else {
            getSupportActionBar().setTitle("");
            getSupportActionBar().setSubtitle("");
            streamInfoTxt.setText("");

            audioPlayerThumbImg.setImageResource(R.drawable.ic_yashlang_thumb);
        }

        prevVideoBtn.setEnabled(playerService.getPlaybackHistory().size() > 1);
        videoLoadErrorTxt.setText(playerService.getVideoLoadErrorMsg());
    }

    private void loadRecommendations(final long firstItemId, final RecommendationsListener recommendationsListener) {
        final RecommendationsProviderFactory.RecommendationsMode recommendationsMode = super.getIntent().hasExtra(PARAM_RECOMMENDATIONS_MODE) ?
                (RecommendationsProviderFactory.RecommendationsMode) super.getIntent().getSerializableExtra(PARAM_RECOMMENDATIONS_MODE) :
                RecommendationsProviderFactory.RecommendationsMode.RANDOM;
        final RecommendationsProvider recommendationsProvider =
                RecommendationsProviderFactory.buildRecommendationsProvider(recommendationsMode, firstItemId, super.getIntent());
        if (recommendationsProvider != null) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    // для вариантов с ArrayAdapter и некоторых других обращение к базе нужно делать в фоне
                    recommendationsProvider.createVideoListAdapter(WatchVideoActivity.this, playerService);
                    playerService.setRecommendationsProvider(recommendationsProvider);

                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            // в режиме загрузки первого ролика из рекомендаций нужно взять первый элемент из адаптера;
                            // для ArrayAdapter он будет доступен уже здесь;
                            // для PagedListAdapter будем его ловить дальше в emptyListObserver
                            if (recommendationsProvider.getVideoListAdapter() instanceof VideoItemArrayAdapter && recommendationsListener != null) {
                                final VideoItem firstItem = ((VideoItemArrayAdapter)
                                        recommendationsProvider.getVideoListAdapter()).getItem(0);
                                recommendationsListener.onFirstItemLoaded(firstItem);
                            }

                            recommendationsProvider.setupVideoList(
                                    WatchVideoActivity.this, WatchVideoActivity.this,
                                    videoList, emptyListObserver,
                                    new OnListItemClickListener<VideoItem>() {
                                        @Override
                                        public void onItemClick(final View view, final int position, final VideoItem videoItem) {
                                            playerService.getVideoListPosMap().put(videoItem.getId(), position);
                                            playerService.playVideoItem(videoItem, false);
                                        }

                                        @Override
                                        public boolean onItemLongClick(final View view, final int position, final VideoItem videoItem) {
                                            actionVideoContextMenu(view, videoItem);
                                            return true;
                                        }
                                    });
                            // на случай если у нас получился ArrayAdapter без emptyListObserver
                            // emptyListObserver здесь не сработает (т.к. у нас ArrayAdapter),
                            // обновим видимость элементов управления прямо здесь
                            updateControlsVisibility();
                        }
                    });
                }
            }).start();
        }
    }

    private void setFullscreen(final boolean fullscreen) {
        stateFullscreen = fullscreen;
        updateControlsVisibility();
        if (stateFullscreen) {
            // продолжить играть, если была пауза
            videoPlayerView.getPlayer().setPlayWhenReady(true);
        }
    }

    private void toggleFullscreen() {
        setFullscreen(!stateFullscreen);
    }

    /**
     * Выбрать видеопоток для текущего ролика
     */
    private void actionSelectStreams() {
        if (playerService != null && playerService.getCurrentVideo() != null) {
            videoPlayerView.getPlayer().setPlayWhenReady(false);
            VideoItemActions.actionSelectStreams(this, handler, playerService.getCurrentVideo(),
                    new VideoItemActions.StreamDialogListener() {
                        @Override
                        public void onClose() {
                            // Прячем панель навигации, т.к. при выборе меню она появляется опять.
                            hideNavigationBar();
                        }

                        @Override
                        public void onStreamsSelected(final StreamHelper.StreamInfo videoStream, final StreamHelper.StreamInfo audioStream) {
                            playerService.playVideoItemStreams(videoStream, audioStream);
                        }
                    });
        }
    }

    /**
     * Загрузить видео для просмотра оффлайн.
     */
    private void actionDownloadStreams() {
        if (playerService != null && playerService.getCurrentVideo() != null) {
            videoPlayerView.getPlayer().setPlayWhenReady(false);
            VideoItemActions.actionDownloadStreams(this, handler, playerService.getCurrentVideo(),
                    new VideoItemActions.StreamDialogListener() {
                        @Override
                        public void onClose() {
                            // Прячем панель навигации, т.к. при выборе меню она появляется опять.
                            hideNavigationBar();
                        }

                        @Override
                        public void onStreamsSelected(StreamHelper.StreamInfo videoStream, StreamHelper.StreamInfo audioStream) {
                        }
                    });
        }
    }

    /**
     * Загрузить заново видеопоток для текущего ролика
     */
    private void actionReload() {
        if (playerService != null) {
            playerService.reloadCurrentVideo();
        }
    }

    private void actionVideoContextMenu(final View view, final VideoItem videoItem) {
        final PopupMenu popup = new PopupMenu(WatchVideoActivity.this, view);
        popup.getMenuInflater().inflate(R.menu.video_item_actions, popup.getMenu());
        popup.getMenu().removeItem(R.id.action_play_in_playlist);
        popup.getMenu().removeItem(R.id.action_play_in_playlist_shuffle);

        if (videoItem == null || videoItem.getPlaylistId() == PlaylistInfo.ID_NONE) {
            popup.getMenu().removeItem(R.id.action_copy_playlist_name);
            popup.getMenu().removeItem(R.id.action_copy_playlist_url);
        }

        if (videoItem == null || videoItem.getId() == VideoItem.ID_NONE) {
            popup.getMenu().removeItem(R.id.action_blacklist);
            popup.getMenu().removeItem(R.id.action_download_streams);
        }

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
                                VideoItemActions.actionCopyVideoName(WatchVideoActivity.this, videoItem);
                                break;
                            }
                            case R.id.action_copy_video_url: {
                                VideoItemActions.actionCopyVideoUrl(WatchVideoActivity.this, videoItem);
                                break;
                            }
                            case R.id.action_copy_playlist_name: {
                                VideoItemActions.actionCopyPlaylistName(WatchVideoActivity.this, handler, videoItem);
                                break;
                            }
                            case R.id.action_copy_playlist_url: {
                                VideoItemActions.actionCopyPlaylistUrl(WatchVideoActivity.this, handler, videoItem);
                                break;
                            }
                            case R.id.action_blacklist: {
                                VideoItemActions.actionBlacklist(
                                        WatchVideoActivity.this, handler, videoItem.getId(),
                                        new VideoItemActions.OnVideoBlacklistedChangeListener() {
                                            @Override
                                            public void onVideoBlacklistedChange(final long videoId, final boolean blacklisted) {
                                                // обновим кэш для текущего видео если вдруг так получилось,
                                                // что мы играем сейчас тот самый ролик, на котором кликнули
                                                // в рекомендациях
                                                if (playerService != null && playerService.getCurrentVideo() != null && playerService.getCurrentVideo().getId() == videoItem.getId()) {
                                                    playerService.getCurrentVideo().setBlacklisted(blacklisted);
                                                }

                                                // и еще обновим кэш, если этот же ролик вдруг есть в списке предыдущих видео
                                                // (там ролик будет тот же, а объект VideoItem - другой)
                                                for (final VideoItem item : playerService.getPlaybackHistory()) {
                                                    if (item.getId() == videoId) {
                                                        item.setBlacklisted(blacklisted);
                                                    }
                                                }
                                                // TODO: здесь что-то нужно сделать после добавления видео в блеклист:
                                                // например, удалить из текущего списка рекомендаций
                                            }
                                        });
                                break;
                            }
                            case R.id.action_download_streams: {
                                VideoItemActions.actionDownloadStreams(
                                        WatchVideoActivity.this, handler, videoItem, null);
                                break;
                            }
                        }
                        return true;
                    }
                }
        );
        popup.show();
    }
}
